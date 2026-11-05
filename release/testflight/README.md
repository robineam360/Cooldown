# TestFlight copy — READY TO PASTE (CCRM-95 (Dual Release) phase B)

Written 2026-11-04, updated 2026-11-05 after Robin's decisions. **Nothing here is uploaded or entered anywhere; only Robin
opens App Store Connect.** Google Play and the public App Store stay out until after v2.0.

**Settled by Robin, 2026-11-05**
- Publisher: EAM360 may publish the app. Sedin Technologies and EAM360 are the same Apple team; the question is closed.
  It is a TestFlight-only app for colleagues, not on the public App Store.
- Robin can already sign in to App Store Connect with his own Apple ID on that team.
- Bundle ids stay: `com.robinrichardrajan.cooldown`, its `.widgets` extension and the App Group.
- Sharing: one public TestFlight link (external testers, Beta App Review once).
- Timing: an early beta before v2.0, so Apple's beta review is done before the announcement; **the link stays unshared
  until v2.0.** Upload only after the iPad device checks pass, and only with Robin at the controls for signing and upload.
- Reviewer access: the sample-data mode only, no test account (see `beta-review-notes.md`).
- Contact for TestFlight feedback and the Beta Review contact: robin@eam360.com.
- Gate G0 still holds: nothing from the v2.0 branch goes to `main`, and there is never an iOS-only GitHub release.

| File | Goes in |
|---|---|
| [beta-app-description.md](beta-app-description.md) | TestFlight → Test Information → *Beta App Description*, *Feedback Email*, *Marketing URL* (skip), *Privacy Policy URL* |
| [whats-to-test.md](whats-to-test.md) | TestFlight → the build → *What to Test* (4,000 characters at most) |
| [beta-review-notes.md](beta-review-notes.md) | TestFlight → Test Information → *Beta App Review Information* (the notes field; sign-in required: **off**) |
| [version-plan.md](version-plan.md) | The version and build numbers for every upload |
| [privacy-ios-draft.md](privacy-ios-draft.md) | Source of the iOS section now prepared in `docs/privacy.md` (commit on this branch; not on `main`) |

**Only Robin can do** (see `version-plan.md` for the order): the iPad device checks; the privacy text to `main` once the v1.9
release is out; the version/build bump in the release commit; the archive, signing and upload; creating the app record and
filling the fields above in App Store Connect; the first Beta App Review submission; creating the public link and keeping it
unshared until v2.0; the app icon decision.
