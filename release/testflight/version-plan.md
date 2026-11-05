# Version and build plan — iOS beta (CCRM-95 (Dual Release) phase B)

The iOS version follows the Android version (RELEASING.md §6, "Same version, one number"). The beta is **2.0**; the build
number counts uploads.

| Item | Value | Where |
|---|---|---|
| `MARKETING_VERSION` | `2.0` (today `0.1` in `ios/project.yml`; set in the release commit, never earlier) | `ios/project.yml` |
| `CURRENT_PROJECT_VERSION` (build) | `1` for the first upload, +1 for every later upload to App Store Connect, even a rejected one; back to 1 only with a new `MARKETING_VERSION` | `ios/project.yml` |
| Android `versionName` / `versionCode` | `2.0` / the next in the Android sequence; set by the v2.0 Android release, not by this arc | `app/build.gradle.kts` |
| Beta display | "2.0 (1)", "2.0 (2)", … in TestFlight and in Settings → About (read from the bundle) | — |

**Order for the early beta (everything marked ROBIN needs him at the controls)**
1. The PR #23 branch and the v2.0 branch merge as the v2.0 arc decides; nothing goes to `main` (Gate G0).
2. ROBIN: the iPad device checks (reset alert on Always; second lock-screen widget; CCBG-70 (Stale Ring Widget) removal).
3. After the v1.9 release is out: Robin (or a session he names) merges the docs-only commit that adds the iPhone and iPad
   section to `docs/privacy.md` (cherry-pick `docs(CCRM-95): privacy policy — iPhone and iPad section`). The in-app Privacy
   policy row and the App Store Connect field both point at that page on `main`.
4. In the release commit: `MARKETING_VERSION: "2.0"`, `CURRENT_PROJECT_VERSION: "1"` in `ios/project.yml`.
5. ROBIN: archive and upload (RELEASING.md §6 has the commands and the archive checks), create the app record, paste the fields
   from this folder, submit for Beta App Review, create the public link. Keep the link unshared until v2.0.
6. The GitHub release for v2.0 carries the APK as always, with the TestFlight link and the iOS build number in its notes.
