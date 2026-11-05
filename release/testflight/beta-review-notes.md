# Beta App Review notes — ready to paste

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

**To review the app without your own accounts.** No sign-in is required. On the first screen tap **Try with sample data**
(also available in Add account). It shows three made-up accounts with sample usage, so every screen can be reviewed: the
main screens, History, Settings and the share card. Nothing is saved or sent, no service is contacted, and **Exit** (the
purple banner at the top) returns to the real app. A real Claude, ChatGPT or Gemini account is not needed and none is
offered; in App Store Connect leave "Sign-in required" off. (Reset alerts and the Lock Screen section are hidden in the sample because they need notification permission and
real widgets; they behave as described above with a real account.)

**Permissions asked.** Notifications (reset alerts), Live Activities (the lock-screen countdown),
background app refresh (keeping widgets current). No location, contacts, camera or photos.

**Contact.** robin@eam360.com
