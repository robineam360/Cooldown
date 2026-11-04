# Beta App Review notes — DRAFT

**What the app is.** Cooldown is a read-only usage monitor. It shows how much of a person's own
Claude (Anthropic), ChatGPT (OpenAI) and Gemini (Google) usage windows is used and when each resets.
It never sends a prompt, never spends quota and has no server of its own. It is unofficial and says so
in Settings → About.

**Why it signs in to those services.** Each service has no public usage API for personal accounts,
so Cooldown signs in the way the services' own command-line tools do and reads the usage numbers the
same way: Claude with an OAuth code the user pastes back, ChatGPT with OpenAI's device-code flow,
Gemini with Google's own sign-in page in a system web session (never an embedded web view). The user
signs in on the provider's own page; Cooldown never sees a password. Tokens are kept in the iOS
Keychain, shared only with the app's own widget extension, and are sent only to the provider they
belong to. Removing an account deletes its token from the device.

**What leaves the device.** Nothing goes to the developer. There is no analytics, no tracking and no
third-party SDK (see the privacy manifest and the policy URL). Requests go over HTTPS to the hosts
listed in the privacy policy.

**To review the app without your own accounts.** TO FILL BEFORE SUBMITTING: the reviewer needs a path
that does not require their own Claude, ChatGPT or Gemini account. The planned path is a sample-data
mode (wireframe pending Robin's approval, `design/2026-11-05-ios-parity-*.html`); until it is built,
supply a test account's sign-in here instead. Do not put real credentials in the repo.

**Permissions asked.** Notifications (reset alerts), Live Activities (the lock-screen countdown),
background app refresh (keeping widgets current). No location, contacts, camera or photos.

**Contact.** robin@eam360.com
