#!/usr/bin/env python3
"""Check a built Cooldown artefact (APK or AAB) for its channel's rules.

CCRM-87 (Update Channel) and CCRM-86 (Play Readiness): the Play build must hold no
update path outside Play, so its dex and resources may contain no GitHub Releases
endpoint and none of the update classes. RUNBOOK Step 3 wrote this; Step 8 reruns it on
the final artefacts.

    tools/check_artefact.py --channel play   app/build/outputs/bundle/playRelease/app-play-release.aab
    tools/check_artefact.py --channel github app/build/outputs/apk/github/release/app-github-release.apk
    tools/check_artefact.py --channel play   /tmp/splits.apks   # bundletool build-apks output

An .apks split set is checked APK by APK, which is what a device actually installs.

Both channels: every native library sits at a path on NATIVE_ALLOWLIST and is ready
        for 16 KB pages. The plan's first rule was "no .so at all", which rested on a false premise:
        Compose UI brings libandroidx.graphics.path.so (androidx.graphics:graphics-path,
        PathIterator below API 34; minSdk is 31), and v1.8 already shipped it. A fresh
        judge accepted this rule instead on 2026-09-30 (RUNBOOK Step 3's Log):
        - any container: each ELF PT_LOAD segment has p_align >= 0x4000;
        - an APK (alone or inside an .apks set) also: the library is stored uncompressed
          at a 16 KB-aligned offset, which is what `zipalign -c -P 16` checks;
        - an AAB deflates it, which is normal, so instead its BundleConfig must ask Play
          to store native libraries uncompressed with PAGE_ALIGNMENT_16K (read with
          `bundletool dump config`, so bundletool must be on PATH for an AAB).
        Sol's cross-check the same day pinned the allowlist to full ABI paths and added
        the split-set and config checks. Any other .so fails, so a new native
        dependency is a decision, not a surprise.
play:   none of FORBIDDEN in any entry, searched as UTF-8 and UTF-16LE (resource string
        pools can be either). The privacy policy's github.com blob URL is allowed; no
        pattern below matches it.
github: every FORBIDDEN pattern is present. This proves the scan can see what it looks
        for, so a clean play result means absence, not a blind scanner.

Exit 0 when the artefact passes, 1 when it fails, 2 on bad usage.
"""
import argparse
import io
import json
import struct
import subprocess
import sys
import zipfile

# androidx.graphics:graphics-path, via Compose UI. Paths as an APK spells them; an AAB
# puts the module name ("base/") in front.
NATIVE_ALLOWLIST = {
    f"lib/{abi}/libandroidx.graphics.path.so"
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
PAGE_16K = 0x4000

FORBIDDEN = [
    "api.github.com",
    "releases/latest",
    # Class descriptors, as the dex type table spells them.
    "Lcom/robin/claudeusage/data/UpdateCheck;",
    "Lcom/robin/claudeusage/data/UpdateGate;",
    "Lcom/robin/claudeusage/notify/UpdateNotification;",
    "Lcom/robin/claudeusage/data/UpdateInfo;",
    "Lcom/robin/claudeusage/channel/UpdatesCardKt;",
    # The Settings button and the notice action; any copy of it in the Play build is a
    # leftover update path.
    "Check for updates",
]


def load_aligns(elf):
    """p_align of every PT_LOAD segment of an ELF image; raises ValueError if not ELF."""
    if elf[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    wide = elf[4] == 2
    e = "<" if elf[5] == 1 else ">"
    if wide:
        (phoff,) = struct.unpack_from(e + "Q", elf, 32)
        phentsize, phnum = struct.unpack_from(e + "HH", elf, 54)
        align_at, fmt = 48, "Q"
    else:
        (phoff,) = struct.unpack_from(e + "I", elf, 28)
        phentsize, phnum = struct.unpack_from(e + "HH", elf, 42)
        align_at, fmt = 28, "I"
    aligns = []
    for k in range(phnum):
        base = phoff + k * phentsize
        (p_type,) = struct.unpack_from(e + "I", elf, base)
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from(e + fmt, elf, base + align_at)[0])
    return aligns


def data_offset(raw, info):
    """Where an entry's bytes start in the archive [raw], past its local file header."""
    name_len, extra_len = struct.unpack_from("<HH", raw, info.header_offset + 26)
    return info.header_offset + 30 + name_len + extra_len


def native_problems(raw, z, info, kind):
    """Everything wrong with one .so entry; empty when it passes."""
    problems = []
    path = info.filename.removeprefix("base/") if kind == "aab" else info.filename
    if path not in NATIVE_ALLOWLIST:
        problems.append("not on the allowlist")
    try:
        aligns = load_aligns(z.read(info))
        if not aligns or min(aligns) < PAGE_16K:
            problems.append(f"PT_LOAD p_align {[hex(a) for a in aligns]} below 0x4000")
    except (ValueError, struct.error) as err:
        problems.append(f"unreadable ELF ({err})")
    if kind == "apk":
        if info.compress_type != zipfile.ZIP_STORED:
            problems.append("compressed in the APK")
        elif data_offset(raw, info) % PAGE_16K:
            problems.append("not 16 KB zip-aligned in the APK")
    return problems


def scan_zip(raw, kind, prefix, natives, hits):
    """Scans one archive held in memory; an .apks set recurses into each APK."""
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for info in z.infolist():
            if info.is_dir():
                continue
            name = prefix + info.filename
            if kind == "apks":
                if info.filename.endswith(".apk"):
                    scan_zip(z.read(info), "apk", name + "!", natives, hits)
                continue
            if info.filename.endswith(".so"):
                natives[name] = native_problems(raw, z, info, kind)
            data = z.read(info)
            for p in FORBIDDEN:
                if p.encode("utf-8") in data or p.encode("utf-16-le") in data:
                    hits[p].append(name)


def bundle_config_problems(path):
    """The AAB must ask Play for uncompressed, 16 KB-aligned native libraries."""
    try:
        out = subprocess.run(
            ["bundletool", "dump", "config", f"--bundle={path}"],
            capture_output=True, text=True, check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as err:
        return [f"bundletool dump config failed ({err})"]
    native = json.loads(out).get("optimizations", {}).get("uncompressNativeLibraries", {})
    problems = []
    if native.get("enabled") is not True:
        problems.append("uncompressNativeLibraries is not enabled")
    if native.get("alignment") != "PAGE_ALIGNMENT_16K":
        problems.append(f"native alignment is {native.get('alignment')!r}, not PAGE_ALIGNMENT_16K")
    return problems


def scan(path):
    """Returns ({.so entry: [problems]}, {pattern: [entries holding it]})."""
    kind = path.rsplit(".", 1)[-1].lower()
    if kind not in ("apk", "aab", "apks"):
        sys.exit(f"not an .apk, .aab or .apks: {path}")
    hits = {p: [] for p in FORBIDDEN}
    natives = {}
    with open(path, "rb") as f:
        scan_zip(f.read(), kind, "", natives, hits)
    if kind == "aab":
        natives["BundleConfig.pb (native libraries)"] = bundle_config_problems(path)
    return natives, hits


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--channel", choices=["play", "github"], required=True)
    ap.add_argument("artefact")
    args = ap.parse_args()

    natives, hits = scan(args.artefact)
    ok = True
    print(f"{args.artefact} ({args.channel})")
    if not any(n.endswith(".so") for n in natives):
        print("  ok    no .so")
    for name, problems in natives.items():
        if problems:
            ok = False
            print(f"  FAIL  {name}: {'; '.join(problems)}")
        else:
            print(f"  ok    {name}: 16 KB-ready")
    for p, where in hits.items():
        if args.channel == "play":
            if where:
                ok = False
                print(f"  FAIL  {p!r} in {', '.join(where)}")
            else:
                print(f"  ok    absent: {p!r}")
        else:
            if where:
                print(f"  ok    present: {p!r}")
            else:
                ok = False
                print(f"  FAIL  expected in the github build, not found: {p!r}")
    print("PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
