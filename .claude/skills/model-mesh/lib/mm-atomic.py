#!/usr/bin/env python3
"""mm-atomic.py — journaled atomic writes for model-mesh install.sh (python3 stdlib only, 3.6+).

Every installer write goes through here (DESIGN §8):
  * the temp file is created next to the destination's REALPATH, so a symlinked destination is written
    through and never replaced by a regular file;
  * the temp is written and fsync'd, then the journal entry (prior: absent | sha256 + saved copy;
    installed: sha256) is written, fsync'd and published, THEN the temp is renamed over the destination
    and the directory is fsync'd;
  * a content-identical write is skipped and journals nothing (idempotent re-runs).

Journal layout, one directory per run: <root>/<RID>/
  meta.json            {rid, mode, target, created}
  pending.log          intent log, fsync'd BEFORE the act: "T <temp path>" / "D <dir the run creates>"
  entries/NNNNNN.json  one published entry per applied change
  saved/NNNNNN         byte copy of the prior content
  complete | reverted | abandoned | reverted-settings.json   markers

Subcommands (all paths absolute):
  rid                                               print a fresh run id (<UTC>-<16 hex>)
  repo-hash <path>                                  16-hex id of a repo's realpath (journal/vault/<hash>)
  init --journal J --mode M [--target T]            create J (must not exist)
  write --journal J --dest D (--src F | --text S) [--mode 0644]
  remove --journal J --dest D                       remove a file or symlink (not a directory)
  symlink --journal J --dest D --target T           create a symlink where nothing exists
  settings --journal J --dest D [--hook-cmd C] [--remove-hook-cmd C] [--model M] [--effort-medium]
           [--default-mode MODE] [--bak] [--events E,E…] [--pretool-matcher M] [--fail-closed]
                                                    JSON merge, keeps indent and every other key; --events names
                                                    the hook events (default: the six Claude Code events; v2.6: the
                                                    Codex seat's ~/.codex/hooks.json takes three)
  claude-md --journal J --dest D (--block F --switch ON|OFF [--label L] | --strip)
  note-dir --journal J DIR                          log a directory this run created (pruned on revert if empty)
  complete --journal J
  revert --journal J                                journaled reversal (exit 3 when a path was left)
  revert-latest --root R                            revert the newest non-empty, not-yet-reverted journal
  revert-settings --root R --dest D --journal J     remove journaled hook entries, restore `model`
  recover --root R [--full]                         incomplete journals: drop their temps (--full: revert)
  json-check FILE...                                exit 2 if an existing file is not a JSON object
  check-contained --root R REL...                   exit 2 if a path has a symlink component / escapes R
  drift --dest D --seat S                           print saved modelSettings.*.effortLevel for the seat
  sha256 FILE

Test seam (honoured only with MODEL_MESH_TEST=1): MM_FAULT=temp-write|journal-publish|pre-rename kills this
process (os._exit 97) at that point of the write whose journal sequence number is MM_FAULT_SEQ (default 1);
MM_FAULT_KILL=1 also SIGKILLs the parent (the installer) first, simulating a crash of the whole run.
"""
import argparse
import copy
import datetime
import hashlib
import json
import os
import re
import shutil
import signal
import sys

HOOK_EVENTS = ("SessionStart", "UserPromptSubmit", "PreModelSwitch", "PostModelSwitch", "PreToolUse", "SubagentStop")
PRETOOL_MATCHER = "Agent|Task|Workflow"
TERMINAL_MARKERS = ("complete", "reverted", "abandoned")


def die(msg, code=2):
    sys.stderr.write("mm-atomic: %s\n" % msg)
    sys.exit(code)


def say(msg):
    sys.stdout.write(msg + "\n")
    sys.stdout.flush()


def utc_stamp():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def new_rid():
    return "%s-%s" % (utc_stamp(), os.urandom(8).hex())


# ---------------------------------------------------------------------------- fault seam
def fault(point, seq):
    if os.environ.get("MODEL_MESH_TEST") != "1" or os.environ.get("MM_FAULT") != point:
        return
    try:
        want = int(os.environ.get("MM_FAULT_SEQ", "1"))
    except ValueError:
        want = 1
    if seq != want:
        return
    sys.stderr.write("mm-atomic: TEST FAULT at %s (seq %d)\n" % (point, seq))
    sys.stderr.flush()
    if os.environ.get("MM_FAULT_KILL") == "1":
        try:
            os.kill(os.getppid(), signal.SIGKILL)
        except OSError:
            pass
    os._exit(97)


# ---------------------------------------------------------------------------- fs primitives
def fsync_dir(d):
    try:
        fd = os.open(d, os.O_RDONLY)
    except OSError:
        return
    try:
        os.fsync(fd)
    except OSError:
        pass
    finally:
        os.close(fd)


def sha_bytes(b):
    return hashlib.sha256(b).hexdigest()


def sha_file(p):
    """sha256 of a regular file's content (following symlinks), or None when absent / not a file."""
    try:
        if not os.path.isfile(p):
            return None
        h = hashlib.sha256()
        with open(p, "rb") as f:
            for chunk in iter(lambda: f.read(65536), b""):
                h.update(chunk)
        return h.hexdigest()
    except OSError:
        return None


def file_mode(p):
    try:
        return os.stat(p).st_mode & 0o7777
    except OSError:
        return None


def link_real(dest):
    """Path of the directory entry itself (parent resolved, last component kept) — for link-level ops."""
    return os.path.join(os.path.realpath(os.path.dirname(dest)), os.path.basename(dest))


def write_all(fd, data):
    view = memoryview(data)
    while len(view):
        n = os.write(fd, view)
        view = view[n:]


def write_fsync(path, data, mode=0o600, excl=True):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if excl else os.O_TRUNC)
    fd = os.open(path, flags, 0o600)
    try:
        write_all(fd, data)
        os.fchmod(fd, mode)
        os.fsync(fd)
    finally:
        os.close(fd)


def plain_atomic_write(real, data, mode):
    """Unjournaled atomic write (used by reversals and backups): temp beside real, fsync, rename, fsync dir."""
    d = os.path.dirname(real)
    tmp = os.path.join(d, ".%s.mm-restore-%s.tmp" % (os.path.basename(real), os.urandom(6).hex()))
    write_fsync(tmp, data, mode)
    os.rename(tmp, real)
    fsync_dir(d)


# ---------------------------------------------------------------------------- journal
class Journal(object):
    def __init__(self, path):
        self.path = path
        if not os.path.isdir(path):
            die("journal %s does not exist (run init first)" % path)
        try:
            with open(os.path.join(path, "meta.json")) as f:
                self.meta = json.load(f)
        except (OSError, ValueError) as e:
            die("journal %s has no readable meta.json (%s)" % (path, e))
        self.rid = self.meta.get("rid", os.path.basename(path))
        self.entries_dir = os.path.join(path, "entries")
        self.saved_dir = os.path.join(path, "saved")

    def next_seq(self):
        n = 0
        for name in os.listdir(self.entries_dir):
            m = re.match(r"^(\d{6})\.json$", name)
            if m:
                n = max(n, int(m.group(1)))
        return n + 1

    def log_pending(self, kind, p):
        with open(os.path.join(self.path, "pending.log"), "a") as f:
            f.write("%s %s\n" % (kind, p))
            f.flush()
            os.fsync(f.fileno())

    def pending(self):
        out = []
        try:
            with open(os.path.join(self.path, "pending.log")) as f:
                for line in f:
                    line = line.rstrip("\n")
                    if len(line) > 2 and line[1] == " " and line[0] in "TD":
                        out.append((line[0], line[2:]))
        except OSError:
            pass
        return out

    def ensure_parents(self, real_dir):
        missing = []
        d = real_dir
        while not os.path.lexists(d):
            missing.append(d)
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
        for m in reversed(missing):
            self.log_pending("D", m)
        for m in reversed(missing):
            os.mkdir(m, 0o755)
            fsync_dir(os.path.dirname(m))

    def publish(self, seq, entry, prior_real=None):
        """Save the prior copy, then write + fsync + rename the entry into entries/ (fault: journal-publish)."""
        if prior_real is not None:
            saved = os.path.join(self.saved_dir, "%06d" % seq)
            with open(prior_real, "rb") as f:
                data = f.read()
            if os.path.lexists(saved):
                os.unlink(saved)
            write_fsync(saved, data, 0o600)
            fsync_dir(self.saved_dir)
            entry["prior"]["saved"] = "saved/%06d" % seq
        tmp = os.path.join(self.entries_dir, ".%06d.json.tmp" % seq)
        if os.path.lexists(tmp):
            os.unlink(tmp)
        write_fsync(tmp, (json.dumps(entry, indent=1, sort_keys=True) + "\n").encode(), 0o600)
        fault("journal-publish", seq)
        os.rename(tmp, os.path.join(self.entries_dir, "%06d.json" % seq))
        fsync_dir(self.entries_dir)

    def entries(self):
        out = []
        for name in sorted(os.listdir(self.entries_dir)):
            if re.match(r"^\d{6}\.json$", name):
                try:
                    with open(os.path.join(self.entries_dir, name)) as f:
                        out.append(json.load(f))
                except (OSError, ValueError):
                    sys.stderr.write("mm-atomic: unreadable journal entry %s/%s — skipped\n" % (self.entries_dir, name))
        return out

    def saved_bytes(self, entry):
        with open(os.path.join(self.path, entry["prior"]["saved"]), "rb") as f:
            return f.read()

    def has(self, marker):
        return os.path.exists(os.path.join(self.path, marker))

    def mark(self, marker, text=""):
        p = os.path.join(self.path, marker)
        with open(p, "w") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
        fsync_dir(self.path)


def init_journal(path, mode, target):
    parent = os.path.dirname(path)
    os.makedirs(parent, exist_ok=True)
    os.mkdir(path, 0o700)
    os.mkdir(os.path.join(path, "entries"), 0o700)
    os.mkdir(os.path.join(path, "saved"), 0o700)
    meta = {"rid": os.path.basename(path), "mode": mode, "target": target, "created": utc_stamp()}
    write_fsync(os.path.join(path, "meta.json"), (json.dumps(meta, indent=1) + "\n").encode(), 0o600)
    fsync_dir(path)
    fsync_dir(parent)


# ---------------------------------------------------------------------------- journaled operations
def journaled_write(j, dest, data, mode=None, extra=None, no_follow=False):
    """Returns True when the destination changed. no_follow: refuse a destination that is a symlink."""
    if no_follow and os.path.islink(dest):
        die("%s is a symlink — refusing to write through it" % dest)
    real = os.path.realpath(dest)
    if os.path.isdir(real):
        die("%s is a directory — refusing to write over it" % dest)
    cur_sha = sha_file(real)
    cur_mode = file_mode(real) if cur_sha is not None else None
    new_sha = sha_bytes(data)
    want_mode = mode if mode is not None else (cur_mode if cur_mode is not None else 0o644)
    if cur_sha == new_sha and cur_mode == want_mode:
        return False
    seq = j.next_seq()
    j.ensure_parents(os.path.dirname(real))
    tmp = os.path.join(os.path.dirname(real), ".%s.mm-%s-%d.tmp" % (os.path.basename(real), j.rid, seq))
    j.log_pending("T", tmp)
    if os.path.lexists(tmp):
        os.unlink(tmp)
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        half = len(data) // 2
        write_all(fd, data[:half])
        fault("temp-write", seq)
        write_all(fd, data[half:])
        os.fchmod(fd, want_mode)
        os.fsync(fd)
    finally:
        os.close(fd)
    if cur_sha is None:
        prior = {"state": "absent"}
    else:
        prior = {"state": "file", "sha256": cur_sha, "mode": cur_mode}
    entry = {"seq": seq, "kind": "file", "dest": dest, "real": real, "prior": prior,
             "installed": {"state": "file", "sha256": new_sha, "mode": want_mode}, "extra": extra or {}}
    j.publish(seq, entry, prior_real=real if cur_sha is not None else None)
    fault("pre-rename", seq)
    os.rename(tmp, real)
    fsync_dir(os.path.dirname(real))
    return True


COPY_SKIP_DIRS = ("__pycache__", ".git", ".github")


def copy_skip(name):
    return name == ".DS_Store" or ".bak" in name or name.endswith(".pyc")


def cmd_copy_tree(a):
    """Copy a folder file by file through the journal: every file written with its source mode, every regular file
    under dest that the source no longer has removed (journaled), symlinks inside dest left alone and reported."""
    j = Journal(a.journal)
    src = os.path.realpath(a.src)
    if not os.path.isdir(src):
        die("copy-tree: source %s is not a directory" % a.src)
    if os.path.islink(a.dest):
        die("copy-tree: destination %s is a symlink — refused (it would write into another folder)" % a.dest)
    if os.path.lexists(a.dest) and not os.path.isdir(a.dest):
        die("copy-tree: destination %s exists and is not a directory — refused" % a.dest)
    want = set()
    for root, dirs, files in os.walk(src):
        dirs[:] = sorted(d for d in dirs if d not in COPY_SKIP_DIRS)
        for f in sorted(files):
            if copy_skip(f):
                continue
            sp = os.path.join(root, f)
            if os.path.islink(sp) or not os.path.isfile(sp):
                continue
            rel = os.path.relpath(sp, src)
            want.add(rel)
            dp = os.path.join(a.dest, rel)
            if os.path.islink(dp):
                die("copy-tree: %s is a symlink — refused" % dp)
            with open(sp, "rb") as fh:
                data = fh.read()
            journaled_write(j, dp, data, file_mode(sp) or 0o644)
    removed = []
    if os.path.isdir(a.dest):
        for root, dirs, files in os.walk(a.dest):
            dirs[:] = [d for d in dirs if d not in COPY_SKIP_DIRS and not os.path.islink(os.path.join(root, d))]
            for f in files:
                dp = os.path.join(root, f)
                rel = os.path.relpath(dp, a.dest)
                if rel in want or copy_skip(f):
                    continue
                if os.path.islink(dp):
                    say("model-mesh: %s is a symlink the skill does not have — left as is" % dp)
                    continue
                journaled_remove(j, dp)
                removed.append(rel)
    say("model-mesh: copied %d skill file(s) into %s (journaled)%s" % (
        len(want), a.dest, "; removed %d stale: %s" % (len(removed), ", ".join(sorted(removed))) if removed else ""))
    return 0


def journaled_remove(j, dest):
    real = link_real(dest)
    if not os.path.lexists(real):
        return False
    seq = j.next_seq()
    if os.path.islink(real):
        prior = {"state": "symlink", "target": os.readlink(real)}
        j.publish(seq, {"seq": seq, "kind": "remove", "dest": dest, "real": real, "prior": prior,
                        "installed": {"state": "absent"}, "extra": {}})
    elif os.path.isfile(real):
        prior = {"state": "file", "sha256": sha_file(real), "mode": file_mode(real)}
        j.publish(seq, {"seq": seq, "kind": "remove", "dest": dest, "real": real, "prior": prior,
                        "installed": {"state": "absent"}, "extra": {}}, prior_real=real)
    else:
        die("%s is not a file or symlink — refusing to remove it" % dest)
    fault("pre-rename", seq)
    os.unlink(real)
    fsync_dir(os.path.dirname(real))
    return True


def journaled_symlink(j, dest, target):
    real = link_real(dest)
    if os.path.lexists(real):
        if os.path.islink(real) and os.readlink(real) == target:
            return False
        die("%s already exists — refusing to replace it with a symlink" % dest)
    seq = j.next_seq()
    j.ensure_parents(os.path.dirname(real))
    tmp = os.path.join(os.path.dirname(real), ".%s.mm-%s-%d.tmp" % (os.path.basename(real), j.rid, seq))
    j.log_pending("T", tmp)
    if os.path.lexists(tmp):
        os.unlink(tmp)
    os.symlink(target, tmp)
    j.publish(seq, {"seq": seq, "kind": "symlink", "dest": dest, "real": real, "prior": {"state": "absent"},
                    "installed": {"state": "symlink", "target": target}, "extra": {}})
    fault("pre-rename", seq)
    os.rename(tmp, real)
    fsync_dir(os.path.dirname(real))
    return True


# ---------------------------------------------------------------------------- state compare / revert
def current_state(entry):
    real = entry["real"]
    if entry["kind"] in ("symlink", "remove") and os.path.islink(real):
        return {"state": "symlink", "target": os.readlink(real)}
    if entry["kind"] == "file" and os.path.islink(real):
        # the journaled real path was a file; a symlink there now is someone else's change
        return {"state": "symlink", "target": os.readlink(real)}
    if not os.path.lexists(real):
        return {"state": "absent"}
    s = sha_file(real)
    if s is None:
        return {"state": "other"}
    return {"state": "file", "sha256": s}


def same_state(a, b):
    if a["state"] != b["state"]:
        return False
    if a["state"] == "file":
        return a.get("sha256") == b.get("sha256")
    if a["state"] == "symlink":
        return a.get("target") == b.get("target")
    return a["state"] == "absent"


def restore_prior(j, entry):
    real = entry["real"]
    prior = entry["prior"]
    if prior["state"] == "absent":
        if os.path.lexists(real):
            os.unlink(real)
            fsync_dir(os.path.dirname(real))
    elif prior["state"] == "file":
        data = j.saved_bytes(entry)
        if os.path.islink(real):
            os.unlink(real)
        os.makedirs(os.path.dirname(real), exist_ok=True)
        plain_atomic_write(real, data, prior.get("mode") or 0o644)
    elif prior["state"] == "symlink":
        d = os.path.dirname(real)
        tmp = os.path.join(d, ".%s.mm-restore-%s.tmp" % (os.path.basename(real), os.urandom(6).hex()))
        os.symlink(prior["target"], tmp)
        os.rename(tmp, real)
        fsync_dir(d)


def cleanup_pending(j, prune_dirs=True):
    items = j.pending()
    for kind, p in items:
        if kind == "T" and os.path.lexists(p) and ".mm-" in os.path.basename(p):
            try:
                os.unlink(p)
            except OSError:
                pass
    if prune_dirs:
        dirs = sorted({p for kind, p in items if kind == "D"}, key=lambda s: s.count(os.sep), reverse=True)
        for d in dirs:
            try:
                if os.path.isdir(d) and not os.path.islink(d) and not os.listdir(d):
                    os.rmdir(d)
                    fsync_dir(os.path.dirname(d))
            except OSError:
                pass


def revert_journal(j, quiet=False, skip_real=()):
    """Restore every journaled path whose current state is what the run installed; report the rest.
    skip_real: real paths already reverted another way (settings files, by cmd_revert_settings)."""
    changed = []
    restored = 0
    for e in reversed(j.entries()):
        if e.get("real") in skip_real:
            continue
        cur = current_state(e)
        if same_state(cur, e["installed"]):
            restore_prior(j, e)
            restored += 1
        elif same_state(cur, e["prior"]):
            continue  # never applied (crash between publish and rename) or already restored
        else:
            changed.append(e["dest"])
    cleanup_pending(j, prune_dirs=True)
    report = {"restored": restored, "left_changed": changed, "when": utc_stamp()}
    # a revert that left a changed path is REFUSED, not done: the journal stays open for a later retry (the owner
    # restores the bytes, then reverts again; paths already restored read as "prior" and are skipped)
    j.mark("revert-refused" if changed else "reverted", json.dumps(report, indent=1) + "\n")
    if not quiet:
        say("model-mesh: reverted journal %s — %d path(s) restored" % (j.path, restored))
    for c in changed:
        say("model-mesh: LEFT (changed since install, not reverted): %s" % c)
    return 3 if changed else 0


def cmd_revert_install(a):
    """Undo the newest unreverted install journal of one mode (and target): settings files first through the
    merge-aware revert-settings (hook entries out, `model` back only if unchanged since), then every other path
    whose current state is what the run installed. Returns 3 when a path changed since and was left."""
    target = os.path.realpath(a.target) if a.target else None
    pick = None
    for jp in reversed(journal_dirs(a.root)):
        j = Journal(jp)
        if j.meta.get("mode") != a.mode or j.has("reverted"):
            continue
        if target and os.path.realpath(j.meta.get("target") or "") != target:
            continue
        if not j.entries():
            continue
        pick = j
        break
    if pick is None:
        say("model-mesh: no unreverted %s install%s under %s — nothing to revert" % (
            a.mode, " for %s" % a.target if a.target else "", a.root))
        return 0
    settings_reals = []
    for e in pick.entries():
        if e.get("kind") == "file" and (e.get("extra") or {}).get("settings") and e["real"] not in settings_reals:
            settings_reals.append(e["real"])
    for real in settings_reals:
        ns = argparse.Namespace(root=a.root, dest=real, journal=a.journal)
        cmd_revert_settings(ns)
    rc = revert_journal(pick, skip_real=set(settings_reals))
    say("model-mesh: %s install %s reverted%s" % (a.mode, pick.rid,
        "; settings reverted through --revert-settings: %s" % ", ".join(settings_reals) if settings_reals else ""))
    return rc


def journal_dirs(root):
    if not os.path.isdir(root):
        return []
    out = []
    for name in sorted(os.listdir(root)):
        p = os.path.join(root, name)
        if os.path.isdir(p) and os.path.exists(os.path.join(p, "meta.json")):
            out.append(p)
    return out


# ---------------------------------------------------------------------------- settings JSON
def load_json_object(real):
    if not os.path.exists(real) or os.path.getsize(real) == 0:
        return {}, "", 2
    with open(real, "rb") as f:
        raw = f.read().decode("utf-8")
    try:
        data = json.loads(raw)
    except ValueError as e:
        die("%s is not valid JSON (%s) — refused, left untouched" % (real, e))
    if not isinstance(data, dict):
        die("%s is not a JSON object — refused, left untouched" % real)
    indent = 2
    for line in raw.splitlines()[1:]:
        stripped = line.lstrip(" ")
        if stripped and stripped != line:
            indent = len(line) - len(stripped)
            break
    return data, raw, indent


def dump_json(data, indent):
    return (json.dumps(data, indent=indent, ensure_ascii=False) + "\n").encode("utf-8")


def hook_present(groups, cmd):
    for g in groups:
        if isinstance(g, dict):
            for h in g.get("hooks") or []:
                if isinstance(h, dict) and h.get("command") == cmd:
                    return True
    return False


def fix_matcher(groups, cmd, matcher=PRETOOL_MATCHER):
    """An older install registered PreToolUse with another matcher (v2.4 pre-release: "Agent|Task"): move our
    command to a group with the current matcher. Returns the new group list, or None when already current."""
    if any(isinstance(g, dict) and g.get("matcher") == matcher and isinstance(g.get("hooks"), list)
           and any(isinstance(h, dict) and h.get("command") == cmd for h in g["hooks"]) for g in groups):
        return None
    out = []
    for g in groups:
        if isinstance(g, dict) and isinstance(g.get("hooks"), list):
            kept = [h for h in g["hooks"] if not (isinstance(h, dict) and h.get("command") == cmd)]
            if len(kept) != len(g["hooks"]):
                if not kept:
                    continue
                g = dict(g)
                g["hooks"] = kept
        out.append(g)
    out.append({"matcher": matcher, "hooks": [{"type": "command", "command": cmd}]})
    return out


FAIL_CLOSED_EVENTS = ("PreToolUse", "PreModelSwitch")


def set_on_failure(groups, cmd):
    """Fail-closed (Claude Code >= 2.1.295): our handler carries "onFailure": "block". True when anything changed."""
    changed = False
    for g in groups:
        if isinstance(g, dict) and isinstance(g.get("hooks"), list):
            for h in g["hooks"]:
                if isinstance(h, dict) and h.get("command") == cmd and h.get("onFailure") != "block":
                    h["onFailure"] = "block"
                    changed = True
    return changed


def add_hooks(data, cmd, events=HOOK_EVENTS, matcher=PRETOOL_MATCHER, fail_closed=False):
    # matcher: the PreToolUse group matcher — Claude Code spawn tools by default; "*" for the v2.7 Codex guard relay
    # fail_closed: the PreToolUse and PreModelSwitch handlers get "onFailure": "block" (an older entry is upgraded)
    added = []
    hooks = data.get("hooks")
    if hooks is None:
        hooks = {}
    if not isinstance(hooks, dict):
        die("settings `hooks` is not an object — refused, left untouched")
    for ev in events:
        groups = hooks.get(ev)
        if groups is None:
            groups = []
        if not isinstance(groups, list):
            die("settings `hooks.%s` is not a list — refused, left untouched" % ev)
        closed = fail_closed and ev in FAIL_CLOSED_EVENTS
        if hook_present(groups, cmd):
            changed = False
            if ev == "PreToolUse":
                fixed = fix_matcher(groups, cmd, matcher)
                if fixed:
                    groups = fixed
                    changed = True
            if closed and set_on_failure(groups, cmd):
                changed = True
            if changed:
                hooks[ev] = groups
                data["hooks"] = hooks
                added.append([ev, cmd])
            continue
        h = {"type": "command", "command": cmd}
        if closed:
            h["onFailure"] = "block"
        g = {"hooks": [h]}
        if ev == "PreToolUse":
            g = {"matcher": matcher, "hooks": [h]}
        groups.append(g)
        hooks[ev] = groups
        added.append([ev, cmd])
    if added:
        data["hooks"] = hooks
    return added


def remove_hooks(data, cmds):
    """Remove hook commands (by exact command string); drop groups/events/`hooks` this empties."""
    removed = []
    hooks = data.get("hooks")
    if not isinstance(hooks, dict):
        return removed
    for ev in list(hooks.keys()):
        groups = hooks[ev]
        if not isinstance(groups, list):
            continue
        new_groups = []
        touched = False
        for g in groups:
            if isinstance(g, dict) and isinstance(g.get("hooks"), list):
                kept = [h for h in g["hooks"] if not (isinstance(h, dict) and h.get("command") in cmds)]
                if len(kept) != len(g["hooks"]):
                    touched = True
                    for h in g["hooks"]:
                        if isinstance(h, dict) and h.get("command") in cmds:
                            removed.append([ev, h.get("command")])
                    if not kept:
                        continue
                    g = dict(g)
                    g["hooks"] = kept
            new_groups.append(g)
        if touched:
            if new_groups:
                hooks[ev] = new_groups
            else:
                del hooks[ev]
    if removed and not hooks:
        del data["hooks"]
    return removed


FAMILY_RE = re.compile(r"^(?:claude-)?(sonnet|opus|haiku|fable)(?![a-z])")


def family(v):
    if not isinstance(v, str):
        return None
    m = FAMILY_RE.match(v.strip().strip("\"'").lower())
    return m.group(1) if m else None


def seat_value(current, seat):
    if (isinstance(current, str) and current.strip().lower().endswith("[1m]")
            and family(current) == family(seat) and not seat.endswith("[1m]")):
        return seat + "[1m]"
    return seat


ABSENT = {"__model_mesh_absent__": True}


def cmd_settings(a):
    j = Journal(a.journal)
    real = os.path.realpath(a.dest)
    data, raw, indent = load_json_object(real)
    new = copy.deepcopy(data)
    extra = {"settings": True, "added_hooks": [], "removed_hooks": []}
    if a.hook_cmd:
        events = HOOK_EVENTS
        if a.events:
            events = tuple(e for e in a.events.split(",") if e)
            if not events or any(not re.fullmatch(r"[A-Za-z]+", e) for e in events):
                die("--events must be a comma-separated list of event names")
        extra["added_hooks"] = add_hooks(new, a.hook_cmd, events, a.pretool_matcher or PRETOOL_MATCHER, a.fail_closed)
    if a.remove_hook_cmd:
        extra["removed_hooks"] = remove_hooks(new, set(a.remove_hook_cmd))
    if a.model:
        if family(a.model) == "fable":
            die("refusing to write a Fable seat model (%s) — model-mesh never writes fable" % a.model)
        want = seat_value(new.get("model"), a.model)
        if new.get("model") != want:
            extra["model_prior"] = new["model"] if "model" in new else ABSENT
            extra["model_installed"] = want
            new["model"] = want
    if a.drop_model_if is not None and new.get("model") == a.drop_model_if:
        # v2.4: repos never pin the seat model. Drop only the exact value an older installer wrote; any other
        # value is someone's own choice and is left (the caller warns). The journal keeps the prior bytes.
        del new["model"]
        extra["model_dropped"] = a.drop_model_if
    if a.effort_medium:
        new["effortLevel"] = "medium"
    if a.default_mode:
        perms = new.get("permissions")
        if not isinstance(perms, dict):
            perms = {}
        perms["defaultMode"] = a.default_mode
        new["permissions"] = perms
    if new == data:
        say("model-mesh: %s already current" % a.dest)
        return 0
    if a.bak and os.path.isfile(real):
        bak = "%s.model-mesh.%s.bak" % (real, utc_stamp())
        n = 1
        while os.path.lexists(bak):
            bak = "%s.model-mesh.%s-%d.bak" % (real, utc_stamp(), n)
            n += 1
        with open(real, "rb") as f:
            plain_atomic_write(bak, f.read(), file_mode(real) or 0o644)
        say("model-mesh: backup %s" % bak)
    journaled_write(j, a.dest, dump_json(new, indent), None, extra)
    what = []
    if extra["added_hooks"]:
        what.append("%d hook entr%s" % (len(extra["added_hooks"]), "y" if len(extra["added_hooks"]) == 1 else "ies"))
    if extra["removed_hooks"]:
        what.append("removed %d hook entr%s" % (len(extra["removed_hooks"]), "y" if len(extra["removed_hooks"]) == 1 else "ies"))
    if "model_installed" in extra:
        what.append("model=%s" % extra["model_installed"])
    if "model_dropped" in extra:
        what.append("removed the old model pin (%s)" % extra["model_dropped"])
    if a.effort_medium or a.default_mode:
        what.append("effortLevel/permissions")
    say("model-mesh: %s updated (%s)" % (a.dest, ", ".join(what) or "merged"))
    return 0


def cmd_revert_settings(a):
    real = os.path.realpath(a.dest)
    chain = []  # (journal, entry), newest first
    for jp in reversed(journal_dirs(a.root)):
        j = Journal(jp)
        if j.meta.get("mode") in ("uninstall", "revert"):
            continue
        done = set()
        rs = os.path.join(jp, "reverted-settings.json")
        if os.path.exists(rs):
            try:
                with open(rs) as f:
                    done = set(json.load(f))
            except (OSError, ValueError):
                pass
        if real in done or j.has("reverted"):
            continue
        for e in reversed(j.entries()):
            if e.get("kind") == "file" and e.get("real") == real and (e.get("extra") or {}).get("settings"):
                chain.append((j, e))
    if not chain:
        say("model-mesh: no journaled installer change to %s — nothing to revert" % a.dest)
        return 0
    rj = Journal(a.journal)
    cur = sha_file(real)
    expect = cur
    exact = True
    for _, e in chain:
        if e["installed"].get("sha256") != expect:
            exact = False
            break
        expect = e["prior"].get("sha256") if e["prior"]["state"] == "file" else None
    if exact:
        oj, oe = chain[-1]
        if oe["prior"]["state"] == "absent":
            journaled_remove(rj, a.dest)
        else:
            journaled_write(rj, a.dest, oj.saved_bytes(oe), oe["prior"].get("mode"), {"settings-revert": True})
        say("model-mesh: %s restored to its exact pre-install bytes" % a.dest)
    else:
        data, raw, indent = load_json_object(real)
        new = copy.deepcopy(data)
        cmds = set()
        for _, e in chain:
            for ev, c in e["extra"].get("added_hooks") or []:
                cmds.add(c)
        removed = remove_hooks(new, cmds) if cmds else []
        model_entries = [e for _, e in chain if "model_installed" in e["extra"]]
        model_note = "model left as is"
        if model_entries:
            newest, oldest = model_entries[0], model_entries[-1]
            if new.get("model") == newest["extra"]["model_installed"]:
                prior = oldest["extra"]["model_prior"]
                if prior == ABSENT:
                    new.pop("model", None)
                else:
                    new["model"] = prior
                model_note = "model restored"
            else:
                model_note = "model changed since install — left as is"
        if new != data:
            journaled_write(rj, a.dest, dump_json(new, indent), None, {"settings-revert": True})
        say("model-mesh: %s changed since install — removed %d journaled hook entr%s, %s; every other key kept"
            % (a.dest, len(removed), "y" if len(removed) == 1 else "ies", model_note))
    by_journal = {}
    for j, _ in chain:
        by_journal[j.path] = j
    for jp, j in by_journal.items():
        rs = os.path.join(jp, "reverted-settings.json")
        done = []
        if os.path.exists(rs):
            try:
                with open(rs) as f:
                    done = json.load(f)
            except (OSError, ValueError):
                done = []
        if real not in done:
            done.append(real)
        j.mark("reverted-settings.json", json.dumps(done) + "\n")
    return 0


# ---------------------------------------------------------------------------- CLAUDE.md block
BLOCK_RE = re.compile(r"<!-- model-mesh:begin.*?<!-- model-mesh:end -->", re.S)


def cmd_claude_md(a):
    j = Journal(a.journal)
    real = os.path.realpath(a.dest)
    text = ""
    if os.path.isfile(real):
        with open(real, "rb") as f:
            text = f.read().decode("utf-8")
    if a.strip:
        if not BLOCK_RE.search(text):
            return 0
        new = re.sub(r"\n*<!-- model-mesh:begin.*?<!-- model-mesh:end -->\n?", "\n", text, flags=re.S)
    else:
        with open(a.block, "rb") as f:
            block = f.read().decode("utf-8")
        block = re.sub(r"<!--.*?-->\s*", "", block, count=1, flags=re.S).strip()
        # the switch word: "Cross-family (GPT) review" (v2.6 Claude seat), "(Claude)" (v2.6 Codex seat)
        # v2.7 either-seat block: "Cross-family review" with no family named; the v2.6 forms still match.
        block = re.sub(r"\*\*Cross-family(?: \((GPT|Claude)\))? review: OFF\.\*\*",
                       lambda m: "**Cross-family%s review: %s.**" % (" (%s)" % m.group(1) if m.group(1) else "", a.switch),
                       block, count=1)
        span = ("<!-- model-mesh:begin (managed by model-mesh %s; edit the skill, not this) -->\n"
                "%s\n<!-- model-mesh:end -->" % (a.label, block))
        if BLOCK_RE.search(text):
            new = BLOCK_RE.sub(lambda m: span, text)
        else:
            new = (text.rstrip() + "\n\n" + span + "\n").lstrip()
    if new == text:
        return 0
    journaled_write(j, a.dest, new.encode("utf-8"), None, {"claude-md": True})
    return 0


# ---------------------------------------------------------------------------- misc commands
def cmd_check_contained(a):
    root = os.path.realpath(a.root)
    bad = 0
    for rel in a.rel:
        if rel.startswith("/") or ".." in rel.split("/"):
            sys.stderr.write("model-mesh: REFUSED — %s is not a plain relative path\n" % rel)
            bad = 1
            continue
        p = root
        for comp in [c for c in rel.split("/") if c]:
            p = os.path.join(p, comp)
            if os.path.islink(p):
                sys.stderr.write("model-mesh: REFUSED — %s has a symlink component (%s)\n" % (rel, p))
                bad = 1
                break
            if not os.path.lexists(p):
                break
        full = os.path.realpath(os.path.join(root, rel))
        if not (full == root or full.startswith(root + os.sep)):
            sys.stderr.write("model-mesh: REFUSED — %s resolves outside the repo root (%s)\n" % (rel, full))
            bad = 1
    return 2 if bad else 0


def cmd_json_check(a):
    for p in a.files:
        real = os.path.realpath(p)
        if os.path.exists(real) and os.path.getsize(real) > 0:
            load_json_object(real)
    return 0


def cmd_drift(a):
    real = os.path.realpath(a.dest)
    if not os.path.isfile(real):
        return 0
    try:
        with open(real) as f:
            data = json.load(f)
    except (OSError, ValueError):
        return 0
    if not isinstance(data, dict):
        return 0
    fam = family(a.seat)
    ms = data.get("modelSettings")
    if isinstance(ms, dict):
        for k, v in ms.items():
            if (k == a.seat or (fam and family(k) == fam)) and isinstance(v, dict) and "effortLevel" in v:
                lvl = v.get("effortLevel")
                state = "matches medium" if lvl == "medium" else "DRIFT — the seat's pinned effort is medium"
                say("model-mesh: drift report — %s has a saved modelSettings.%s.effortLevel=%s (%s). The installer never "
                    "writes modelSettings; change it in a session with /effort, or remove the key by hand."
                    % (a.dest, k, json.dumps(lvl), state))
    if "effortLevel" in data:
        say("model-mesh: note — %s has a user-scope effortLevel=%s (a v2.3 install wrote it). model-mesh never writes a "
            "user-scope effortLevel; repos pin effort. Left as is." % (a.dest, json.dumps(data["effortLevel"])))
    return 0


def main(argv):
    ap = argparse.ArgumentParser(prog="mm-atomic.py")
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("rid")
    p = sub.add_parser("repo-hash"); p.add_argument("path")
    p = sub.add_parser("sha256"); p.add_argument("file")
    p = sub.add_parser("init"); p.add_argument("--journal", required=True); p.add_argument("--mode", required=True)
    p.add_argument("--target", default="")
    p = sub.add_parser("write"); p.add_argument("--journal", required=True); p.add_argument("--dest", required=True)
    p.add_argument("--src"); p.add_argument("--text"); p.add_argument("--mode")
    p.add_argument("--no-follow", action="store_true")
    p = sub.add_parser("copy-tree"); p.add_argument("--journal", required=True); p.add_argument("--src", required=True)
    p.add_argument("--dest", required=True)
    p = sub.add_parser("remove"); p.add_argument("--journal", required=True); p.add_argument("--dest", required=True)
    p = sub.add_parser("symlink"); p.add_argument("--journal", required=True); p.add_argument("--dest", required=True)
    p.add_argument("--target", required=True)
    p = sub.add_parser("settings"); p.add_argument("--journal", required=True); p.add_argument("--dest", required=True)
    p.add_argument("--hook-cmd"); p.add_argument("--remove-hook-cmd", action="append")
    p.add_argument("--model"); p.add_argument("--effort-medium", action="store_true")
    p.add_argument("--default-mode"); p.add_argument("--bak", action="store_true")
    p.add_argument("--fail-closed", action="store_true"); p.add_argument("--drop-model-if"); p.add_argument("--events"); p.add_argument("--pretool-matcher")
    p = sub.add_parser("claude-md"); p.add_argument("--journal", required=True); p.add_argument("--dest", required=True)
    p.add_argument("--block"); p.add_argument("--switch", default="OFF"); p.add_argument("--strip", action="store_true")
    p.add_argument("--label", default="install.sh --user")
    p = sub.add_parser("complete"); p.add_argument("--journal", required=True)
    p = sub.add_parser("note-dir"); p.add_argument("--journal", required=True); p.add_argument("dir")
    p = sub.add_parser("revert"); p.add_argument("--journal", required=True)
    p = sub.add_parser("revert-latest"); p.add_argument("--root", required=True)
    p = sub.add_parser("revert-settings"); p.add_argument("--root", required=True); p.add_argument("--dest", required=True)
    p.add_argument("--journal", required=True)
    p = sub.add_parser("revert-install"); p.add_argument("--root", required=True); p.add_argument("--mode", required=True)
    p.add_argument("--target"); p.add_argument("--journal", required=True)
    p = sub.add_parser("recover"); p.add_argument("--root", required=True); p.add_argument("--full", action="store_true")
    p = sub.add_parser("json-check"); p.add_argument("files", nargs="*")
    p = sub.add_parser("check-contained"); p.add_argument("--root", required=True); p.add_argument("rel", nargs="+")
    p = sub.add_parser("drift"); p.add_argument("--dest", required=True); p.add_argument("--seat", required=True)
    a = ap.parse_args(argv)

    if a.cmd == "rid":
        say(new_rid()); return 0
    if a.cmd == "repo-hash":
        say(hashlib.sha256(os.path.realpath(a.path).encode()).hexdigest()[:16]); return 0
    if a.cmd == "sha256":
        s = sha_file(a.file)
        if s is None:
            die("%s is not a readable file" % a.file)
        say(s); return 0
    if a.cmd == "init":
        if os.path.lexists(a.journal):
            die("journal %s already exists" % a.journal)
        init_journal(a.journal, a.mode, a.target); return 0
    if a.cmd == "write":
        if (a.src is None) == (a.text is None):
            die("write needs exactly one of --src / --text")
        if a.src is not None:
            with open(a.src, "rb") as f:
                data = f.read()
        else:
            data = a.text.encode("utf-8")
        mode = int(a.mode, 8) if a.mode else None
        journaled_write(Journal(a.journal), a.dest, data, mode, no_follow=a.no_follow); return 0
    if a.cmd == "copy-tree":
        return cmd_copy_tree(a)
    if a.cmd == "remove":
        journaled_remove(Journal(a.journal), a.dest); return 0
    if a.cmd == "symlink":
        journaled_symlink(Journal(a.journal), a.dest, a.target); return 0
    if a.cmd == "settings":
        return cmd_settings(a)
    if a.cmd == "claude-md":
        if not a.strip and not a.block:
            die("claude-md needs --block or --strip")
        return cmd_claude_md(a)
    if a.cmd == "note-dir":
        Journal(a.journal).log_pending("D", os.path.realpath(a.dir)); return 0
    if a.cmd == "complete":
        j = Journal(a.journal)
        cleanup_pending(j, prune_dirs=False)
        j.mark("complete", utc_stamp() + "\n"); return 0
    if a.cmd == "revert":
        return revert_journal(Journal(a.journal))
    if a.cmd == "revert-latest":
        for jp in reversed(journal_dirs(a.root)):
            j = Journal(jp)
            if j.has("reverted") or j.meta.get("mode") in ("revert", "uninstall"):
                continue
            if not j.entries():
                continue
            return revert_journal(j)
        say("model-mesh: no journaled install under %s to revert" % a.root)
        return 0
    if a.cmd == "revert-settings":
        return cmd_revert_settings(a)
    if a.cmd == "revert-install":
        return cmd_revert_install(a)
    if a.cmd == "recover":
        rc = 0
        for jp in journal_dirs(a.root):
            j = Journal(jp)
            if any(j.has(m) for m in TERMINAL_MARKERS):
                continue
            if a.full:
                say("model-mesh: an earlier run did not finish (%s) — reverting it first" % jp)
                rc = max(rc, revert_journal(j))
            else:
                cleanup_pending(j, prune_dirs=False)
                j.mark("abandoned", utc_stamp() + "\n")
                say("model-mesh: an earlier run did not finish (%s) — its temp files removed; this run converges it" % jp)
        return rc
    if a.cmd == "json-check":
        return cmd_json_check(a)
    if a.cmd == "check-contained":
        return cmd_check_contained(a)
    if a.cmd == "drift":
        return cmd_drift(a)
    ap.print_usage(sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
