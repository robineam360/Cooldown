#!/bin/sh
# model-mesh-hook.sh — model-mesh v2.5 hook (DESIGN §4). One script, six events; the event is read from the
# stdin field `hook_event_name`, the command takes no arguments.
#
# Registration (install.sh writes it into settings `hooks`, idempotent by command path; BUILD-SPEC "Hook contract"):
#   "SessionStart":     [{"hooks":[{"type":"command","command":"<abs path of this file>"}]}]
#   "UserPromptSubmit": [{"hooks":[{"type":"command","command":"<abs path of this file>"}]}]
#   "PreModelSwitch":   [{"hooks":[{"type":"command","command":"<abs path of this file>"}]}]
#   "PostModelSwitch":  [{"hooks":[{"type":"command","command":"<abs path of this file>"}]}]
#   "PreToolUse":       [{"matcher":"Agent|Task|Workflow","hooks":[{"type":"command","command":"<abs path of this file>"}]}]
#   "SubagentStop":     [{"hooks":[{"type":"command","command":"<abs path of this file>"}]}]          (v2.5, P9)
#   (repo copy: "$CLAUDE_PROJECT_DIR"/.claude/hooks/model-mesh-hook.sh)
#
# Hook I/O formats, verified 2026-09-29 against https://code.claude.com/docs/en/hooks :
#   - "Common input fields": session_id, prompt_id (absent before the first prompt), transcript_path, cwd,
#     hook_event_name — JSON on stdin.
#   - "PreToolUse input": tool_name, tool_input, tool_use_id. "PreToolUse decision control": stdout
#     {"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"…"}},
#     exit 0. Allow = print nothing, exit 0 (normal permission flow applies; we never print "allow", which would
#     bypass the user's permission prompts).
#   - "SessionStart input": source, model. "SessionStart decision control":
#     {"hookSpecificOutput":{"hookEventName":"SessionStart","additionalContext":"…"}}.
#   - "UserPromptSubmit input": prompt (+ common prompt_id). "UserPromptSubmit decision control":
#     {"hookSpecificOutput":{"hookEventName":"UserPromptSubmit","additionalContext":"…"}}.
#   - "PreModelSwitch input": from_model, to_model, source. "PreModelSwitch decision control": hookSpecificOutput
#     with permissionDecision allow|deny|ask + permissionDecisionReason (top-level decision:"block" also works).
#   - "PostModelSwitch input": from_model, to_model, source (user|client|restore|auto); runs async, cannot block.
#   - "SubagentStop input" (verified 2026-10-04): agent_id, agent_type, agent_transcript_path, stop_hook_active,
#     last_assistant_message. Exit 0 with no output lets the stop proceed; this hook never blocks it.
#     v2.5 P9: the sub-agent transcript's assistant `message.usage` is summed (input, cache creation, cache read,
#     output; one count per message.id, since a message split over several lines repeats its usage) and one
#     line is appended to $MODEL_MESH_HOME/journal/usage.log; absent/unreadable transcript, no python3 or no
#     usage in it -> a `usage unknown` line. The docs warn the transcript "may lag" (UNVERIFIED, checked live).
#   - Exit codes: 0 = success, stdout parsed as JSON when it is one {…} object. Any other non-2 exit is a
#     NON-blocking error (the tool would proceed), so this script never relies on its exit code to deny: every
#     PreToolUse path ends in an explicit deny object unless it deliberately allows.
#
# v2.8: SessionStart also carries the opt-in update notice (update_note below); the fetcher is bin/model-mesh-update-check.
# Fail closed: no JSON parser, malformed stdin, unreadable config dir, unusable cwd, an existing-but-unparsable
# settings file, or an internal error on PreToolUse → deny, with a one-line actionable reason.
# Writes: only ${TMPDIR:-/tmp}/model-mesh/<session_id>/ (session cache, print markers), $MODEL_MESH_HOOK_LOG and,
# on SubagentStop only, ${MODEL_MESH_HOME:-~/.local/share/model-mesh}/journal/usage.log (append).
# Test seams (honoured only when MODEL_MESH_TEST=1): MM_TEST_MANAGED_SETTINGS=<path>, MM_TEST_NO_PYTHON=1,
# MM_TEST_NO_JQ=1, MM_TEST_UPDATE_FETCH=<script run instead of the update fetcher>.
# POSIX sh (bash 3.2 posix mode, dash); no heredocs (bash writes their bodies to temp files).

LC_ALL=C
export LC_ALL
umask 077

MM_ALIAS_VARS='ANTHROPIC_DEFAULT_OPUS_MODEL ANTHROPIC_DEFAULT_SONNET_MODEL ANTHROPIC_DEFAULT_HAIKU_MODEL ANTHROPIC_SMALL_FAST_MODEL CLAUDE_CODE_SUBAGENT_MODEL'
MM_ALT_VARS='ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN CLAUDE_CODE_OAUTH_TOKEN CLAUDE_CODE_USE_BEDROCK CLAUDE_CODE_USE_VERTEX CLAUDE_CODE_USE_FOUNDRY'
nl='
'
testmode=0
[ "${MODEL_MESH_TEST:-}" = 1 ] && testmode=1
decided=0
ev=''
eff='-'

# ---------------------------------------------------------------- JSON readers (python3 first, jq fallback)
# Line protocol, identical for both readers: key=value, one per line, values sanitised to printable ASCII
# (anything else → '?') except the *_raw path fields, which are emitted verbatim and only when they contain no
# control character. The last line is always end=1; a reader whose output lacks it is treated as unavailable.
# shellcheck disable=SC2016
MM_PY='
import json, re, sys
ALIAS = ["ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL", "ANTHROPIC_DEFAULT_HAIKU_MODEL",
         "ANTHROPIC_SMALL_FAST_MODEL", "CLAUDE_CODE_SUBAGENT_MODEL"]
ALT = ["ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "CLAUDE_CODE_OAUTH_TOKEN", "CLAUDE_CODE_USE_BEDROCK",
       "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY"]
OUT = sys.stdout.buffer
def emit(k, v):
    OUT.write((k + "=" + v + "\n").encode("utf-8", "replace"))
def san(v):
    return re.sub(r"[^ -~]", "?", v.strip())
def main():
    if sys.argv[1] == "stdin":
        try:
            d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
        except Exception:
            d = None
        if not isinstance(d, dict):
            emit("ok", "0")
            return
        emit("ok", "1")
        for src, dst in (("hook_event_name", "ev"), ("session_id", "sid"), ("prompt_id", "pid"),
                         ("model", "smodel"), ("to_model", "to"), ("source", "src"), ("tool_name", "tool"),
                         ("prompt", "prompt"), ("agent_type", "atype")):
            v = d.get(src)
            if isinstance(v, str):
                emit(dst, san(v))
        for k in ("cwd", "transcript_path", "agent_transcript_path"):
            v = d.get(k)
            if isinstance(v, str):
                if re.search(r"[\x00-\x1f]", v):
                    emit(k + "_bad", "1")
                else:
                    emit(k + "_raw", v)
        if "tool_input" not in d:
            emit("ti", "absent")
        elif not isinstance(d["tool_input"], dict):
            emit("ti", "other")
        else:
            t = d["tool_input"]
            emit("ti", "object")
            if isinstance(t.get("subagent_type"), str):
                emit("sa_type", "string")
                emit("sa", san(t["subagent_type"]))
            else:
                emit("sa_type", "other" if "subagent_type" in t else "absent")
            if "model" not in t:
                emit("m_type", "absent")
            elif isinstance(t["model"], str):
                emit("m_type", "string")
                emit("m", san(t["model"]))
            elif t["model"] is None:
                emit("m_type", "null")
            else:
                emit("m_type", "other")
    else:
        for i, f in enumerate(sys.argv[2:], 1):
            try:
                with open(f, "rb") as fh:
                    d = json.loads(fh.read().decode("utf-8", "replace"))
            except Exception:
                d = None
            if not isinstance(d, dict) or ("env" in d and not isinstance(d["env"], dict)):
                emit("bad", str(i))
                continue
            if "apiKeyHelper" in d:
                emit("akh", str(i))
            e = d.get("env") or {}
            for k in ALIAS:
                if k in e:
                    emit("env", str(i) + ":" + k + "=" + (san(e[k]) if isinstance(e[k], str) else "?nonstring"))
            for k in ALT:
                if k in e:
                    emit("alt", str(i) + ":" + k)
main()
emit("end", "1")
'
# SubagentStop usage summer (python3 only): argv[1] = transcript path. Prints one line: either
# "ok <model> <input> <cache_creation> <cache_read> <output> <skipped-lines>" or "unknown <reason>".
# shellcheck disable=SC2016
MM_PY_USAGE='
import json, re, sys
KEYS = ("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens", "output_tokens")
try:
    fh = open(sys.argv[1], "rb")
except OSError:
    print("unknown transcript unreadable"); sys.exit()
seen, anon, models, skipped = {}, [], [], 0
with fh:
    for raw in fh:
        if not raw.strip():
            continue
        try:
            d = json.loads(raw.decode("utf-8", "replace"))
        except ValueError:
            skipped += 1
            continue
        m = d.get("message") if isinstance(d, dict) else None
        if not isinstance(m, dict) or m.get("role") != "assistant" or not isinstance(m.get("usage"), dict):
            continue
        u = m["usage"]
        row = tuple(u.get(k) if isinstance(u.get(k), int) and not isinstance(u.get(k), bool) else 0 for k in KEYS)
        if isinstance(m.get("model"), str) and m["model"] not in models and m["model"] != "<synthetic>":
            models.append(m["model"])
        if isinstance(m.get("id"), str):
            seen[m["id"]] = row
        else:
            anon.append(row)
rows = list(seen.values()) + anon
if not rows:
    print("unknown no assistant usage in the transcript" + (" (%d malformed lines)" % skipped if skipped else "")); sys.exit()
s = [sum(r[i] for r in rows) for i in range(4)]
model = re.sub(r"[^A-Za-z0-9._:\[\]-]", "?", ",".join(models)) or "?"
print("ok %s %d %d %d %d %d" % (model, s[0], s[1], s[2], s[3], skipped))
'
# shellcheck disable=SC2016
MM_JQ_DEFS='
def san: sub("^\\s+"; "") | sub("\\s+$"; "") | explode | map(if . < 32 or . > 126 then 63 else . end) | implode;
def ctl: explode | any(. < 32);
'
# shellcheck disable=SC2016
MM_JQ_STDIN='
if length == 1 and (.[0] | type) == "object" then
  .[0] as $d
  | "ok=1",
    ( [["hook_event_name","ev"],["session_id","sid"],["prompt_id","pid"],["model","smodel"],["to_model","to"],
       ["source","src"],["tool_name","tool"],["prompt","prompt"],["agent_type","atype"]][] as $p
      | select(($d[$p[0]] | type) == "string") | "\($p[1])=\($d[$p[0]] | san)" ),
    ( ["cwd","transcript_path","agent_transcript_path"][] as $k | select(($d[$k] | type) == "string")
      | if ($d[$k] | ctl) then "\($k)_bad=1" else "\($k)_raw=\($d[$k])" end ),
    ( if ($d | has("tool_input") | not) then "ti=absent"
      elif ($d.tool_input | type) != "object" then "ti=other"
      else $d.tool_input as $t
        | "ti=object",
          ( if ($t.subagent_type | type) == "string" then "sa_type=string", "sa=\($t.subagent_type | san)"
            elif ($t | has("subagent_type")) then "sa_type=other" else "sa_type=absent" end ),
          ( if ($t | has("model") | not) then "m_type=absent"
            elif ($t.model | type) == "string" then "m_type=string", "m=\($t.model | san)"
            elif $t.model == null then "m_type=null" else "m_type=other" end )
      end ),
    "end=1"
else "ok=0", "end=1" end
'
# shellcheck disable=SC2016
MM_JQ_SET='
if length == 1 and (.[0] | type) == "object" then
  .[0] as $d
  | if ($d | has("env")) and (($d.env | type) != "object") then "bad=\($i)"
    else
      ( if ($d | has("apiKeyHelper")) then "akh=\($i)" else empty end ),
      ( ($d.env // {}) as $e
        | ( ($alias | split(" "))[] as $k | select($e | has($k))
            | "env=\($i):\($k)=\($e[$k] | if type == "string" then san else "?nonstring" end)" ),
          ( ($alt | split(" "))[] as $k | select($e | has($k)) | "alt=\($i):\($k)" ) )
    end
else "bad=\($i)" end
'

have_py() {
  if [ "$testmode" = 1 ] && [ "${MM_TEST_NO_PYTHON:-}" = 1 ]; then return 1; fi
  command -v python3 >/dev/null 2>&1
}
have_jq() {
  if [ "$testmode" = 1 ] && [ "${MM_TEST_NO_JQ:-}" = 1 ]; then return 1; fi
  command -v jq >/dev/null 2>&1 || return 1
  jq -n 1 >/dev/null 2>&1
}
ends_ok() { case $1 in "end=1" | *"${nl}end=1") return 0 ;; esac; return 1; }

# ---------------------------------------------------------------- small helpers
san() { printf '%s' "$1" | tr -c ' -~' '?'; }
trim() {
  t=$1
  while :; do case $t in ' '* | '	'*) t=${t#?} ;; *) break ;; esac; done
  while :; do case $t in *' ' | *'	') t=${t%?} ;; *) break ;; esac; done
  printf '%s' "$t"
}
# norm: trim, strip one pair of surrounding quotes, trim, lowercase (BUILD-SPEC "Safe set").
norm() {
  n=$(trim "$1")
  case $n in
    \"*\") n=${n#\"}; n=${n%\"} ;;
    \'*\') n=${n#\'}; n=${n%\'} ;;
  esac
  n=$(trim "$n")
  printf '%s' "$n" | tr 'A-Z' 'a-z'
}
# is_safe <normalised>: labels sonnet|opus|haiku, or ^claude-(sonnet|opus|haiku)-[a-z0-9.-]+(\[1m\])?$ .
# Hardening beyond the spec: anything containing "fable" is never safe.
is_safe() {
  s=$1
  case $s in
    *fable*) return 1 ;;
    sonnet | opus | haiku) return 0 ;;
    claude-sonnet-* | claude-opus-* | claude-haiku-*) ;;
    *) return 1 ;;
  esac
  s=${s#claude-}
  s=${s#*-}
  case $s in *'[1m]') s=${s%????} ;; esac
  case $s in '' | *[!a-z0-9.-]*) return 1 ;; esac
  return 0
}
family() {
  f=${1#claude-}
  case $f in
    *fable*) printf fable ;;
    opus*) printf opus ;;
    sonnet*) printf sonnet ;;
    haiku*) printf haiku ;;
    *) printf other ;;
  esac
}
json_esc() { printf '%s' "$1" | tr '\001-\037' ' ' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
log_line() {
  # <event> effective=<v> via=<step> decision=<allow|deny> reason=<r>   (BUILD-SPEC "Hook contract")
  [ -n "${MODEL_MESH_HOOK_LOG:-}" ] || return 0
  printf '%s effective=%s via=%s decision=%s reason=%s\n' "${ev:-unknown}" "$eff" "$1" "$2" "$(printf '%s' "$3" | tr '\001-\037' ' ')" \
    >>"$MODEL_MESH_HOOK_LOG" 2>/dev/null
  return 0
}
deny_tool() { # <via> <reason>
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' \
    "$(json_esc "$2")"
  decided=1
  log_line "$1" deny "$2"
}
deny_switch() { # <via> <reason>
  printf '{"hookSpecificOutput":{"hookEventName":"PreModelSwitch","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' \
    "$(json_esc "$2")"
  decided=1
  log_line "$1" deny "$2"
}
allow() { decided=1; log_line "$1" allow "$2"; }
context() { # <event> <text>
  printf '{"hookSpecificOutput":{"hookEventName":"%s","additionalContext":"%s"}}\n' "$1" "$(json_esc "$2")"
}

usage_line() { # <text> — append one line to the usage journal; never fails the hook
  ul_dir="${MODEL_MESH_HOME:-${HOME:-}/.local/share/model-mesh}/journal"
  case $ul_dir in /*) ;; *) return 0 ;; esac
  [ -d "$ul_dir" ] || mkdir -p "$ul_dir" 2>/dev/null || return 0
  [ -L "$ul_dir/usage.log" ] && return 0
  printf '%s agent=%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" "${atype:-?}" "$1" >>"$ul_dir/usage.log" 2>/dev/null
  return 0
}

# ---------------------------------------------------------------- read stdin, pick a reader
input=$(cat; printf x)
input=${input%x}

parser=none
out=''
if have_py; then
  out=$(printf '%s' "$input" | python3 -I -c "$MM_PY" stdin 2>/dev/null)
  if ends_ok "$out"; then parser=py; else out=''; fi
fi
if [ "$parser" = none ] && have_jq; then
  parser=jq
  out=$(printf '%s' "$input" | jq -r -s "$MM_JQ_DEFS$MM_JQ_STDIN" 2>/dev/null) || out="ok=0${nl}end=1"
  ends_ok "$out" || out="ok=0${nl}end=1"
fi

# ---------------------------------------------------------------- account / tier (DESIGN §3, §4 "Account")
dir=${CLAUDE_CONFIG_DIR:-${HOME:-}/.claude}
dir_disp=$(san "$dir")
dir_ok=0
if [ -d "$dir" ] && [ -r "$dir" ] && [ -x "$dir" ]; then dir_ok=1; fi

tier=standard
tier_disp='undeclared→standard'
tf="$dir/model-mesh-tier"
if [ "$dir_ok" = 1 ] && [ -f "$tf" ] && [ -r "$tf" ]; then
  # exactly `fable` or `standard` after stripping ONE trailing newline (BUILD-SPEC "Shared constants");
  # the byte count guards against NULs, which the shell would silently drop.
  tc=$(cat -- "$tf" 2>/dev/null && printf x) || tc=''
  tc=${tc%x}
  tsz=$(wc -c <"$tf" 2>/dev/null | tr -d ' ')
  case $tsz in '' | *[!0-9]*) tsz=0 ;; esac
  case $tc in *"$nl") tc=${tc%"$nl"}; tsz=$((tsz - 1)) ;; esac
  if [ "$tc" = fable ] && [ "$tsz" = 5 ]; then tier=fable; tier_disp=fable
  elif [ "$tc" = standard ] && [ "$tsz" = 8 ]; then tier=standard; tier_disp=standard
  fi
fi

# ---------------------------------------------------------------- no parser at all (fail closed)
if [ "$parser" = none ]; then
  ev=$(printf '%s' "$input" | sed -n 's/.*"hook_event_name"[[:space:]]*:[[:space:]]*"\([A-Za-z]*\)".*/\1/p' | sed -n 1p)
  cnt=$(printf '%s\n' "$input" | awk '{ n += gsub(/"hook_event_name"/, "") } END { print n + 0 }')
  [ "$cnt" = 1 ] || ev=''
  fix='install python3 (macOS: xcode-select --install) or jq'
  case $ev in
    SessionStart | UserPromptSubmit)
      context "$ev" "model-mesh: tier=$tier_disp account=$dir_disp [NO JSON PARSER: every sub-agent spawn is denied until python3 or jq is available — $fix]"
      allow noparser 'state line (no parser)'
      ;;
    PostModelSwitch) allow noparser 'cannot cache to_model without a parser' ;;
    SubagentStop) usage_line "usage unknown (no JSON parser)"; allow noparser 'usage unknown' ;;
    PreModelSwitch) deny_switch noparser "model-mesh: no JSON parser (python3 or jq), so the switch target cannot be checked — switch denied; $fix" ;;
    *) ev=${ev:-PreToolUse}; deny_tool noparser "model-mesh: no JSON parser (python3 or jq) — spawn denied; $fix" ;;
  esac
  exit 0
fi

# ---------------------------------------------------------------- unpack the reader's lines
ok=0 sid='' pid='' smodel='' smodel_set=0 to='' to_set=0 src='' tool='' prompt=''
cwd_raw='' cwd_state=absent tp_raw='' ti=absent sa_type=absent sa='' m_type=absent m='' atype='' atp_raw='' atp_bad=0
oldifs=$IFS
IFS=$nl
set -f
for line in $out; do
  k=${line%%=*}
  v=${line#*=}
  case $k in
    ok) ok=$v ;;
    ev) ev=$v ;;
    sid) sid=$v ;;
    pid) pid=$v ;;
    smodel) smodel=$v; smodel_set=1 ;;
    to) to=$v; to_set=1 ;;
    src) src=$v ;;
    tool) tool=$v ;;
    prompt) prompt=$v ;;
    cwd_raw) cwd_raw=$v; cwd_state=ok ;;
    cwd_bad) cwd_state=bad ;;
    transcript_path_raw) tp_raw=$v ;;
    agent_transcript_path_raw) atp_raw=$v ;;
    agent_transcript_path_bad) atp_bad=1 ;;
    atype) atype=$v ;;
    ti) ti=$v ;;
    sa_type) sa_type=$v ;;
    sa) sa=$v ;;
    m_type) m_type=$v ;;
    m) m=$v ;;
  esac
done
IFS=$oldifs
set +f

if [ "$ok" != 1 ]; then
  ev=PreToolUse
  deny_tool step1 'model-mesh: hook input on stdin is not one JSON object — spawn denied; update Claude Code, and check the hook with MODEL_MESH_HOOK_LOG set'
  exit 0
fi

# ---------------------------------------------------------------- session cache (${TMPDIR:-/tmp}/model-mesh/<sid>/)
tmproot=${TMPDIR:-/tmp}
tmproot=${tmproot%/}
[ -n "$tmproot" ] || tmproot=/tmp
cache_base="$tmproot/model-mesh"
sd=''
sess_dir() { # sets sd; returns 1 when no usable cache dir (then: no cache, no dedupe)
  sd=''
  case $sid in '' | .* | *[!A-Za-z0-9._-]*) return 1 ;; esac
  [ -d "$tmproot" ] || return 1
  [ -L "$cache_base" ] && return 1
  [ -d "$cache_base" ] || mkdir "$cache_base" 2>/dev/null || [ -d "$cache_base" ] || return 1
  [ -L "$cache_base/$sid" ] && return 1
  [ -d "$cache_base/$sid" ] || mkdir "$cache_base/$sid" 2>/dev/null || [ -d "$cache_base/$sid" ] || return 1
  [ -w "$cache_base/$sid" ] || return 1
  sd="$cache_base/$sid"
  return 0
}
cache_model() { # <value|''>  ('' removes the cache → unknown)
  sess_dir || return 0
  if [ -n "$1" ]; then
    printf '%s\n' "$1" >"$sd/.session-model.$$" 2>/dev/null && mv -f "$sd/.session-model.$$" "$sd/session-model" 2>/dev/null
    rm -f "$sd/.session-model.$$" 2>/dev/null
  else
    rm -f "$sd/session-model" 2>/dev/null
  fi
  return 0
}
read_cached_model() {
  sess_dir || return 0
  [ -f "$sd/session-model" ] || return 0
  san "$(sed -n 1p "$sd/session-model" 2>/dev/null)"
}

# ---------------------------------------------------------------- SubagentStop: usage journal line, never blocks (P9)
if [ "$ev" = SubagentStop ]; then
  if [ "$atp_bad" = 1 ]; then usage_line "usage unknown (transcript path has control characters)"
  elif [ -z "$atp_raw" ]; then usage_line "usage unknown (no agent_transcript_path in the hook input)"
  elif [ ! -f "$atp_raw" ] || [ ! -r "$atp_raw" ]; then usage_line "usage unknown (transcript absent or unreadable)"
  elif ! have_py; then usage_line "usage unknown (no python3)"
  else
    us=$(python3 -I -c "$MM_PY_USAGE" "$atp_raw" 2>/dev/null | sed -n 1p)
    case $us in
      'ok '*)
        set -f
        # shellcheck disable=SC2086  # deliberate split of the summer's one-line answer into fields
        set -- $us
        set +f
        if [ $# = 7 ]; then usage_line "model=$2 input=$3 cache_creation=$4 cache_read=$5 output=$6$( [ "$7" = 0 ] || printf ' skipped_lines=%s' "$7")"
        else usage_line "usage unknown (summer returned an unexpected line)"; fi ;;
      'unknown '*) usage_line "usage unknown (${us#unknown })" ;;
      *) usage_line "usage unknown (transcript could not be summed)" ;;
    esac
  fi
  allow usage 'usage journal line'
  exit 0
fi

# ---------------------------------------------------------------- PostModelSwitch: cache to_model, nothing else
if [ "$ev" = PostModelSwitch ]; then
  eff=${to:--}
  if [ "$to_set" = 1 ]; then cache_model "$to"; fi
  allow cache 'to_model cached'
  exit 0
fi

# ---------------------------------------------------------------- settings files (env / apiKeyHelper)
if [ "$cwd_state" = absent ]; then cwd_raw=${PWD:-}; [ -n "$cwd_raw" ] && cwd_state=ok; fi
case $(uname -s 2>/dev/null) in
  Darwin) managed='/Library/Application Support/ClaudeCode/managed-settings.json' ;;
  *) managed='/etc/claude-code/managed-settings.json' ;;
esac
if [ "$testmode" = 1 ] && [ -n "${MM_TEST_MANAGED_SETTINGS:-}" ]; then managed=$MM_TEST_MANAGED_SETTINGS; fi

# The list lives in the positional parameters (no arrays in POSIX sh). Existing + readable → parsed (a
# directory or bad JSON there → "unparsable" → spawns denied); existing but unreadable → skipped, because Claude
# Code runs as the same user and cannot read it either; a broken symlink counts as absent.
set --
for cand in "$dir/settings.json" "$cwd_raw/.claude/settings.json" "$cwd_raw/.claude/settings.local.json" \
  "${CLAUDE_PROJECT_DIR:+$CLAUDE_PROJECT_DIR/.claude/settings.json}" \
  "${CLAUDE_PROJECT_DIR:+$CLAUDE_PROJECT_DIR/.claude/settings.local.json}" "$managed"; do
  [ -n "$cand" ] || continue
  case $cand in /.claude/*) [ "$cwd_state" = ok ] || continue ;; esac
  if [ -e "$cand" ] && [ -r "$cand" ]; then set -- "$@" "$cand"; fi
done
nfiles=$#
sout=''
if [ "$nfiles" -gt 0 ]; then
  if [ "$parser" = py ]; then
    sout=$(python3 -I -c "$MM_PY" settings "$@" 2>/dev/null)
    ends_ok "$sout" || { i=1; sout=''; while [ "$i" -le "$nfiles" ]; do sout="${sout}bad=$i$nl"; i=$((i + 1)); done; }
  else
    i=1
    for f in "$@"; do
      o=$(jq -r -s --arg i "$i" --arg alias "$MM_ALIAS_VARS" --arg alt "$MM_ALT_VARS" "$MM_JQ_DEFS$MM_JQ_SET" "$f" 2>/dev/null) || o="bad=$i"
      sout="$sout$o$nl"
      i=$((i + 1))
    done
  fi
fi
nth() { # nth <index> <list…>
  nn=$1; shift
  [ "$nn" -ge 1 ] 2>/dev/null || return 0
  while [ "$nn" -gt 1 ] && [ $# -gt 0 ]; do shift; nn=$((nn - 1)); done
  [ $# -gt 0 ] && printf '%s' "$1"
}

settings_bad=''
alt_why=''
unsafe_why=''
remap=''
# alias mappings used by effective(): first value found per label
map_opus='' map_sonnet='' map_haiku='' map_fast=''
note_alias() { # <var> <normalised value> <source>
  [ -n "$2" ] || return 0
  case $remap in *"[ALIAS REMAP: $1=$2]"*) ;; *) remap="$remap [ALIAS REMAP: $1=$2]" ;; esac
  if [ -z "$unsafe_why" ] && ! is_safe "$2"; then unsafe_why="$1=$2 (from $3)"; fi
  case $1 in
    ANTHROPIC_DEFAULT_OPUS_MODEL) [ -n "$map_opus" ] || map_opus=$2 ;;
    ANTHROPIC_DEFAULT_SONNET_MODEL) [ -n "$map_sonnet" ] || map_sonnet=$2 ;;
    ANTHROPIC_DEFAULT_HAIKU_MODEL) [ -n "$map_haiku" ] || map_haiku=$2 ;;
    ANTHROPIC_SMALL_FAST_MODEL) [ -n "$map_fast" ] || map_fast=$2 ;;
  esac
}
# process environment first
for var in $MM_ALT_VARS; do
  eval "val=\${$var:-}"
  if [ -n "$val" ] && [ -z "$alt_why" ]; then alt_why=$var; fi
done
for var in $MM_ALIAS_VARS; do
  eval "val=\${$var:-}"
  note_alias "$var" "$(norm "$(san "$val")")" environment
done
# then settings files
oldifs=$IFS
IFS=$nl
set -f
for line in $sout; do
  k=${line%%=*}
  v=${line#*=}
  case $k in
    bad) [ -n "$settings_bad" ] || settings_bad=$(san "$(nth "$v" "$@")") ;;
    akh) [ -n "$alt_why" ] || alt_why="apiKeyHelper in $(san "$(nth "$v" "$@")")" ;;
    alt) [ -n "$alt_why" ] || alt_why="${v#*:} in $(san "$(nth "${v%%:*}" "$@")") env" ;;
    env)
      ix=${v%%:*}; rest=${v#*:}; var=${rest%%=*}; val=${rest#*=}
      note_alias "$var" "$(norm "$val")" "$(san "$(nth "$ix" "$@")")"
      ;;
  esac
done
IFS=$oldifs
set +f

if [ -n "$alt_why" ]; then tier=standard; tier_disp="standard (alternate credentials: $alt_why)"; fi
if [ -n "$settings_bad" ]; then tier=standard; fi

# ---------------------------------------------------------------- expected seat (models.env MM_SEAT_*)
seat_key=MM_SEAT_STANDARD
[ "$tier" = fable ] && seat_key=MM_SEAT_FABLE
eval "seat=\${$seat_key:-}"
if [ -z "$seat" ]; then
  mmh=${MODEL_MESH_HOME:-${HOME:-}/.local/share/model-mesh}
  case $0 in */*) hd=${0%/*} ;; *) hd=. ;; esac
  for mf in "$mmh/models.env" "$hd/../models.env" "$hd/../skills/model-mesh/models.env"; do
    if [ -f "$mf" ] && [ -r "$mf" ]; then
      seat=$(sed -n "s/^$seat_key=//p" "$mf" 2>/dev/null | sed -n '$p')
      [ -n "$seat" ] && break
    fi
  done
fi
seat=$(norm "$(san "$seat")")
sc=$seat
case $sc in *'[1m]') sc=${sc%????} ;; esac
case $sc in '' | *[!a-z0-9.-]*) if [ "$tier" = fable ]; then seat=opus; else seat=sonnet; fi ;; esac

# fable_via — basename of the first account dir listed in $MODEL_MESH_HOME/claude-reviewer whose tier file is exactly
# `fable` (one trailing newline allowed); prints nothing when none qualifies (v2.7: where a standard seat can reach Fable).
fable_via() {
  local rf lf d t ts
  rf=${MODEL_MESH_HOME:-${HOME:-}/.local/share/model-mesh}/claude-reviewer
  [ -f "$rf" ] && [ -r "$rf" ] || return 0
  while IFS= read -r lf || [ -n "$lf" ]; do
    case $lf in /*) ;; *) continue ;; esac
    d=${lf%/}
    [ -f "$d/model-mesh-tier" ] && [ -r "$d/model-mesh-tier" ] || continue
    t=$(cat -- "$d/model-mesh-tier" 2>/dev/null && printf x) || continue
    t=${t%x}
    ts=$(wc -c <"$d/model-mesh-tier" 2>/dev/null | tr -d ' ')
    case $t in *"$nl") t=${t%"$nl"}; ts=$((ts - 1)) ;; esac
    if [ "$t" = fable ] && [ "$ts" = 5 ]; then san "${d##*/}"; return 0; fi
  done <"$rf"
  return 0
}
# ---------------------------------------------------------------- state line (DESIGN §3)
state_line() { # <session model or ''>
  sm=$(norm "$1")
  if [ -n "$sm" ] && [ "$sm" != unknown ]; then smd=$sm; else smd='unknown → (b) suspended, same-model rules apply'; sm=''; fi
  line="model-mesh: tier=$tier_disp account=$dir_disp expected-seat=$seat session-model=$smd"
  if [ -n "$sm" ] && [ "$(family "$sm")" != "$(family "$seat")" ]; then
    if [ -z "${CLAUDE_CONFIG_DIR:-}" ] || [ "$dir" = "${HOME:-}/.claude" ]; then
      perm="install.sh --user --tier $tier"
    else
      nm=${dir%/}; nm=${nm##*/}; nm=${nm#.claude-}; nm=$(san "$nm")
      perm="setup-vscode.sh $nm --model $seat"
    fi
    line="$line [MISMATCH: /model → $seat → press s; permanent: $perm]"
  fi
  [ "$tier" = fable ] && line="$line [FABLE-TIER: pass model: fable to mesh-expert and mesh-judge r3]"
  if [ "$tier" != fable ]; then
    fv=$(fable_via); line="$line fable-via=${fv:-none}"
  fi
  if [ -n "$sm" ] && [ "$tier" != fable ] && [ "$(family "$sm")" = fable ]; then
    line="$line [SESSION ON FABLE, tier ≠ fable: switch now]"
  fi
  line="$line$remap"
  [ "$ev" = SessionStart ] && line="$line$(update_note)"
  [ "$dir_ok" = 1 ] || line="$line [ACCOUNT DIR UNREADABLE: every spawn is denied — fix CLAUDE_CONFIG_DIR or the directory]"
  [ -z "$settings_bad" ] || line="$line [SETTINGS UNPARSABLE: $settings_bad — every spawn is denied until it is valid JSON]"
  printf '%s' "$line"
}
# update_note — v2.8, SessionStart only, opt-in: when $MM_HOME/update-check says `on` and no opt-out variable is set,
# read $MM_HOME/update-cache (line 1 epoch, line 2 tag); absent/stale → start the fetcher detached and never wait;
# a cached tag newer than $MM_HOME/VERSION → " [UPDATE: <tag> — install.sh --update]". Silent on every other path.
ver_gt() { # ver_gt <a> <b>: dotted numeric a > b (both validated by the caller)
  vg_a=$1; vg_b=$2
  while [ -n "$vg_a" ] || [ -n "$vg_b" ]; do
    vg_x=${vg_a%%.*}; vg_y=${vg_b%%.*}
    case $vg_a in *.*) vg_a=${vg_a#*.} ;; *) vg_a='' ;; esac
    case $vg_b in *.*) vg_b=${vg_b#*.} ;; *) vg_b='' ;; esac
    vg_x=${vg_x:-0}; vg_y=${vg_y:-0}
    [ "$vg_x" -gt "$vg_y" ] && return 0
    [ "$vg_x" -lt "$vg_y" ] && return 1
  done
  return 1
}
update_note() {
  un_h=${MODEL_MESH_HOME:-${HOME:-}/.local/share/model-mesh}
  case $un_h in /*) ;; *) return 0 ;; esac
  [ -n "${MODEL_MESH_NO_UPDATE_CHECK:-}${CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC:-}${DISABLE_AUTOUPDATER:-}" ] && return 0
  [ -f "$un_h/update-check" ] && [ -r "$un_h/update-check" ] || return 0
  [ "$(norm "$(sed -n 1p "$un_h/update-check" 2>/dev/null)")" = on ] || return 0
  un_now=$(date +%s 2>/dev/null)
  case $un_now in '' | *[!0-9]*) return 0 ;; esac
  un_ep=''; un_tag=''
  if [ -f "$un_h/update-cache" ] && [ -r "$un_h/update-cache" ]; then
    un_ep=$(sed -n 1p "$un_h/update-cache" 2>/dev/null)
    un_tag=$(sed -n 2p "$un_h/update-cache" 2>/dev/null)
  fi
  case $un_ep in '' | *[!0-9]*) un_ep=''; un_tag='' ;; esac
  if [ -z "$un_ep" ] || [ "$un_ep" -gt "$un_now" ] || [ $((un_now - un_ep)) -ge 86400 ]; then
    un_f=''
    if [ "$testmode" = 1 ] && [ -n "${MM_TEST_UPDATE_FETCH:-}" ]; then un_f=$MM_TEST_UPDATE_FETCH
    else
      case $0 in */*) un_hd=${0%/*} ;; *) un_hd=. ;; esac
      for un_c in "$un_hd/../bin/model-mesh-update-check" "${HOME:-}/.local/bin/model-mesh-update-check"; do
        if [ -f "$un_c" ] && [ -r "$un_c" ]; then un_f=$un_c; break; fi
      done
    fi
    if [ -n "$un_f" ]; then
      nohup sh "$un_f" </dev/null >/dev/null 2>&1 &
    fi
  fi
  case $un_tag in v[0-9]*) ;; *) return 0 ;; esac
  un_tv=${un_tag#v}
  case $un_tv in *[!0-9.]* | .* | *. | *..*) return 0 ;; esac
  [ -f "$un_h/VERSION" ] && [ -r "$un_h/VERSION" ] || return 0
  un_iv=$(trim "$(sed -n 1p "$un_h/VERSION" 2>/dev/null)")
  case $un_iv in '' | *[!0-9.]* | .* | *. | *..*) return 0 ;; esac
  if ver_gt "$un_tv" "$un_iv"; then printf ' [UPDATE: %s — install.sh --update]' "$un_tag"; fi
  return 0
}
print_state_once() { # <event> <dedupe key>
  if sess_dir; then
    if ! mkdir "$sd/printed-$2" 2>/dev/null; then allow state-dup 'already printed for this occurrence'; return 0; fi
  fi
  context "$1" "$(state_line "$3")"
  allow state 'state line printed'
}
tsize() {
  if [ -n "$tp_raw" ] && [ -f "$tp_raw" ] && [ -r "$tp_raw" ]; then
    wc -c 2>/dev/null <"$tp_raw" | tr -d ' '
  else
    printf 0
  fi
}

case $ev in
  SessionStart)
    if [ "$smodel_set" = 1 ]; then sm0=$smodel; else sm0=''; fi
    cache_model "$sm0"
    eff=${sm0:--}
    srck=$(printf '%s' "$src" | tr -cd 'A-Za-z')
    print_state_once SessionStart "SessionStart-${srck:-none}-$(tsize)" "$sm0"
    exit 0
    ;;
  UserPromptSubmit)
    sm0=$(read_cached_model)
    eff=${sm0:--}
    case $pid in
      '' | *[!A-Za-z0-9._-]*) key="c$(printf '%s|%s' "$prompt" "$(tsize)" | cksum | sed 's/[^0-9].*//')" ;;
      *) key=$pid ;;
    esac
    print_state_once UserPromptSubmit "UserPromptSubmit-$key" "$sm0"
    exit 0
    ;;
  PreModelSwitch)
    ton=$(norm "$to")
    eff=${ton:--}
    if [ "$tier" = fable ]; then allow tier-fable 'fable tier'; exit 0; fi
    why='Fable is never used on a standard-tier account'
    [ -n "$alt_why" ] && why="alternate credentials ($alt_why) force tier standard"
    [ -n "$settings_bad" ] && why="$settings_bad is unparsable, so tier is forced to standard"
    if [ "$to_set" != 1 ] || [ -z "$ton" ]; then
      deny_switch switch "model-mesh: model switch has no to_model to check — switch denied on tier standard; retry with /model sonnet or /model opus"
    else
      case $ton in
        *fable*) deny_switch switch "model-mesh: /model $ton denied — $why; pick /model opus or /model sonnet, or (Max/Team premium/Enterprise premium only) write fable to $dir_disp/model-mesh-tier" ;;
        *) allow switch 'not fable' ;;
      esac
    fi
    exit 0
    ;;
  PreToolUse | '') ;;
  *) allow other-event 'event not handled'; exit 0 ;;
esac

# ---------------------------------------------------------------- PreToolUse Agent|Task|Workflow (DESIGN §4 steps 0–2)
ev=PreToolUse
pretool() {
  case $tool in
    Agent | Task | Workflow) ;;
    '') deny_tool step1 'model-mesh: PreToolUse input has no tool_name — spawn denied; update Claude Code or check the hook matcher (Agent|Task|Workflow)'; return ;;
    *) allow not-agent "tool $tool is not Agent|Task|Workflow"; return ;;
  esac
  if [ "$dir_ok" != 1 ]; then
    deny_tool account "model-mesh: config dir $dir_disp is not a readable directory — spawn denied; fix CLAUDE_CONFIG_DIR or that directory's permissions"; return
  fi
  if [ "$cwd_state" != ok ]; then
    deny_tool step0 'model-mesh: hook input cwd is missing or contains control characters, so project settings cannot be checked — spawn denied'; return
  fi
  if [ -n "$settings_bad" ]; then
    deny_tool step0 "model-mesh: settings file $settings_bad exists but is not a valid JSON object (or its env is not an object) — spawn denied; fix or remove it"; return
  fi
  # step 0 — alias/model env vars (environment and settings env), tier standard only
  if [ "$tier" = standard ] && [ -n "$unsafe_why" ]; then
    deny_tool step0 "model-mesh: tier=standard and $unsafe_why maps outside the safe set — every spawn denied; unset it or set a claude-sonnet|opus|haiku id"; return
  fi
  # Workflow — each workflow agent takes the model its script names (code.claude.com/docs/en/workflows, "Cost":
  # "A model the script names for a stage counts as the per-invocation model"), which this hook cannot check
  # per agent (whether workflow agents raise PreToolUse Agent is UNVERIFIED, G2-9). Fable tier: allowed.
  # Standard tier: denied, fail-closed, until G2-9 shows the per-agent spawns are guarded.
  if [ "$tool" = Workflow ]; then
    if [ "$tier" = fable ]; then allow workflow 'tier fable'; return; fi
    deny_tool workflow 'model-mesh: tier=standard — the Workflow tool is denied: its agents pick their own models, which model-mesh cannot check per agent; spawn with the Agent tool and a named model (sonnet|opus|haiku) instead'; return
  fi
  # step 1 — tool_input must be an object. subagent_type is optional (Claude Code may omit it for the
  # default agent); the explicit-model rule in step 2 does not depend on it. A present but non-string
  # subagent_type is malformed input and is denied.
  if [ "$ti" != object ] || [ "$sa_type" = other ]; then
    deny_tool step1 'model-mesh: tool_input is not an object or its subagent_type is not a string — spawn denied'; return
  fi
  # step 2 — tier
  if [ "$tier" = fable ]; then
    [ "$m_type" = string ] && eff=$(norm "$m")
    allow step2 'tier fable'; return
  fi
  tw='tier=standard'
  [ -n "$alt_why" ] && tw="tier=standard (alternate credentials: $alt_why)"
  case $m_type in
    absent | null) deny_tool step2 "model-mesh: $tw — this spawn names no model; pass model: sonnet|opus|haiku (Fable is never used on this account)"; return ;;
    string) ;;
    *) deny_tool step2 "model-mesh: $tw — tool_input.model is not a string; pass model: sonnet|opus|haiku"; return ;;
  esac
  mn=$(norm "$m")
  if [ -z "$mn" ] || [ "$mn" = inherit ]; then
    deny_tool step2 "model-mesh: $tw — model '${mn:-empty}' would inherit the session model; pass model: sonnet|opus|haiku"; return
  fi
  # effective(v) = the alias mapping when v is a label with a mapping set, else v
  e=$mn
  case $mn in
    opus) [ -n "$map_opus" ] && e=$map_opus ;;
    sonnet) [ -n "$map_sonnet" ] && e=$map_sonnet ;;
    haiku) if [ -n "$map_haiku" ]; then e=$map_haiku; elif [ -n "$map_fast" ]; then e=$map_fast; fi ;;
  esac
  eff=$e
  if is_safe "$mn" && is_safe "$e"; then
    allow step2 "model $mn effective $e is safe"; return
  fi
  if [ "$e" != "$mn" ]; then
    deny_tool step2 "model-mesh: $tw — model '$mn' maps to '$e', outside the safe set; pass model: sonnet|opus|haiku and fix the alias variable"
  else
    deny_tool step2 "model-mesh: $tw — model '$mn' is outside the safe set; pass model: sonnet|opus|haiku (Fable is never used on this account)"
  fi
}
pretool
if [ "$decided" != 1 ]; then
  deny_tool internal 'model-mesh: internal hook error — spawn denied; run the hook tests (test-hook.sh) and reinstall'
fi
exit 0
