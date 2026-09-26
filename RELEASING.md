# Releasing an update

The routine for shipping a new version of Cooldown to GitHub. Colleagues install by
downloading the APK from the **Releases page** (the app's *Check for updates* button and
the README both link to `releases/latest`). The APK is **no longer committed to the repo**
— it ships only as a release asset. So every release is: bump, build, commit source, push,
publish release.

## 1. Bump the version

In `app/build.gradle.kts`, increase both:

```kotlin
versionCode = 8        // +1 every release, always
versionName = "0.8"    // what users see in Settings → About
```

Android only offers "update" over an installed app when `versionCode` is higher —
forgetting the bump means colleagues can't install over the old build.

## 2. Build and refresh the APK

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew assembleRelease
# Signed APK lands at app/build/outputs/apk/release/app-release.apk — upload it in step 5.
# (No longer copied into release/ or committed.)
```

**Signing (since v0.13).** The app is signed with a permanent release keystore, not the
throwaway debug key. The keystore + its password live in two gitignored files that are
**never committed**: `ccooldown-release.jks` and `keystore.properties` (both at the repo
root). `app/build.gradle.kts` reads them automatically.

- **Back these two files up somewhere safe and permanent.** If you lose them you can never
  publish an update again — every user would have to uninstall and reinstall.
- Because the keystore is stable, every release from v0.13 onward updates in place and keeps
  the user's history and settings. (The one-time exception was v0.12 → v0.13: v0.12 was
  debug-signed with a key that's gone, so that single upgrade required a reinstall.)
- On a fresh clone without the two files, `assembleRelease` still builds but produces an
  **unsigned** APK — restore the keystore files before a real release.

## 3. Update the docs

The PDFs are generated — never edit them. The sources are `release/docs/src/guide.html`
and `brochure.html`; edit those, then rebuild with one command:

```bash
./release/docs/build.sh
```

It reads `versionName` from `app/build.gradle.kts` and writes three files into
`release/docs/`:

| Output | What it's for |
|---|---|
| `Cooldown-User-Guide-v<ver>.pdf` | the 13-page guide the README and release notes link to |
| `Cooldown-Brochure.pdf` | the 2-page pitch |
| `Cooldown-whats-new-v<ver>.png` | **guide page 3 as a single image** — paste under a Slack post |

The one-pager is *sliced out of* `guide.html` at build time, not authored separately, so
it can't drift. That's deliberate: don't add a fourth "what's new" document — the guide
page already is one.

**Filenames changed at v1.5** (CCRM-58 (Repo Rename)): the outputs were `CCooldown-*` up to
and including v1.4. The older PDFs and PNGs keep their old names in `release/docs/` — they
are the historical record and anything already linking to them still resolves. Don't rename
them retroactively.

**⚠️ Read every page you changed in the built PDF.** Each `.page` is a fixed A4 box with
`overflow:hidden`, so content that doesn't fit is **silently clipped, not reflowed**. In
v1.1 four pages overflowed and one lost an entire callout before anyone noticed. Adding a
row to a table, or a card to a grid, is enough to do it.

Also update by hand when the UI or features change:

- `release/USER-GUIDE.md` — the markdown guide (its own version header + changelog)
- `README.md` — feature bullets, and **both PDF links** if the version in the guide's
  filename changed
- `release/docs/src/shots/` — screenshots. Retire any that show a removed feature; a
  figure that contradicts the text is worse than no figure. v1.1's blue-accent
  "themeable" figure is currently absent for this reason — reshoot it with a blue theme
  if you want it back.

## 4. Commit, build, tag, draft, verify, publish — in this order

Since v1.8 (CCRM-78 (Widgets Reborn), RUNBOOK.md Step 8) the release runs as six steps, strictly
in order, so the published APK is provably the build of the tagged commit and carries the
permanent key. Nothing is public until step 6.

**Trusted signer digest** — the SHA-256 of the permanent release certificate, taken on
2026-09-26 from the published v1.7 asset (`gh release download v1.7`, then
`apksigner verify --print-certs`). Step 5 compares every new asset against it:

```
8bc21a2aca81e5a09b239d1847822549f10775d76849f0e1948980ecd044f64f
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
BT=$ANDROID_HOME/build-tools/36.0.0          # apksigner, aapt
V=1.8                                        # this release's versionName

git add -A && git commit -m "v$V — <one line>"                                  # 1 release source
./gradlew assembleRelease                                                       # 2 built from that commit
git tag v$V && git push && git push origin v$V                                  # 3 tag on it, both pushed
gh release create v$V app/build/outputs/apk/release/app-release.apk \
   --draft --verify-tag --title "Cooldown v$V" --notes-file <notes>             # 4 draft bound to the pushed tag
gh release download v$V -p '*.apk' -D /tmp/v$V                                  # 5 the asset is the build:
   shasum -a 256 app/build/outputs/apk/release/app-release.apk /tmp/v$V/app-release.apk  #   hashes equal
   $BT/apksigner verify --print-certs /tmp/v$V/app-release.apk                  #   signer SHA-256 = the digest above
   $BT/aapt dump badging /tmp/v$V/app-release.apk | head -1                     #   package com.robin.claudeusage, the new versionCode/Name
   # any mismatch: stop, gh release delete v$V --yes (still a draft), nothing was published
gh release edit v$V --draft=false                                               # 6 publish, last
```

Never stage `ccooldown-release.jks`, `keystore.properties` or `local.properties` (all
gitignored). Get a fresh judge's verdict before step 6 — publishing is irreversible for anyone
who installs (an installed build cannot be downgraded). Release notes go in
`release/docs/release-notes-v<ver>.md`, passed as `--notes-file`.

**If it goes wrong:** after 1–3 nothing is public — fix forward, or move the tag only if it must
(`git tag -d v$V && git push --delete origin v$V`). After 4–5 a bad asset is deleted with the
draft. After 6: `gh release edit v$V --draft` withdraws it, so `releases/latest` falls back to
the previous release (installed phones are untouched); installed apps recover only through a
new, higher versionCode.

The APK is distributed **only** as this release asset — the README, USER-GUIDE, and the
app's *Check for updates* button all point at `releases/latest`, so publishing the release
is what actually ships the update to colleagues.
