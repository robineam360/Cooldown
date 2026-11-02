REVIEW: full · plan=agy-proxy-capture

Scope and threat model: a one-off, single-session capture on the owner's own Mac of ONE request the Antigravity CLI
(`agy`, a Go binary) sends to Google (`retrieveUserQuotaSummary` on `daily-cloudcode-pa.googleapis.com` /
`cloudcode-pa.googleapis.com`), to learn which headers or body make Google return the Gemini weekly and Gemini 4
Argon weekly buckets that a plain HTTPS client with the same OAuth token does not get. Threats: (1) a trusted,
locally generated root CA whose private key sits on disk lets anything holding that key intercept the owner's TLS
for as long as it is trusted; (2) the owner's Google OAuth access token (in the Authorization header) must never be
written to disk, printed, or shown to the agent; (3) the machine must end in the state it started in.

## Goal
Learn the exact request shape agy uses, then replay it from a plain client with no proxy. Done = either a replay
that returns the Gemini weekly and Argon buckets, or a recorded reason the capture cannot work (pinning, proxy
ignored), with the machine restored in both cases.

## Closed decisions
- Interception tool: mitmproxy's `mitmdump`, installed from the Homebrew cask `mitmproxy` (no Python tooling on
  this Mac). Its configuration directory is set to a **fresh, session-only folder in the session scratchpad**, so
  the CA it generates there is new and nothing in `~/.mitmproxy` is created, read or deleted.
- Starting state, recorded 2026-11-02T04:22:58Z before step 1: `~/.mitmproxy` absent; cask `mitmproxy` not
  installed; no `mitmdump`/`mitmproxy`/`mitmweb` on PATH; no keychain certificate named mitmproxy; `HTTPS_PROXY`
  unset. Cleanup removes only what this session created, and uninstalls the cask only because it was absent.
- Trust goes into the owner's **login keychain** (user trust domain), never the System keychain; no sudo.
- Go on macOS verifies TLS through the Security framework and ignores `SSL_CERT_FILE`, so keychain trust is the
  only way agy will accept the proxy's certificate.
- Only agy's one process is pointed at the proxy, through the `HTTPS_PROXY` environment variable on that command.
  System proxy settings are not touched.
- The proxy listens on 127.0.0.1 only and intercepts TLS for the two cloudcode-pa hosts only; every other host is
  passed through as an untouched tunnel.
- The capture addon replaces the Authorization header value and the Cloud project id with placeholders in memory,
  before anything is written; response bodies are written with the account email redacted.
- The trust window is minutes: trust is added immediately before the capture and removed immediately after,
  before the replay.

## Steps (what changes · check · reversal)
1. **Install mitmproxy** (agent). Changes: a Homebrew cask. Check: `mitmdump --version` prints a version.
   Reversal: uninstall the cask.
2. **Dry-run the addon** (agent), no trust involved. Start mitmdump with the addon, send one request through it to
   a cloudcode-pa host with a fake bearer value, telling the client to skip certificate checks. Check: the capture
   file holds the request with the placeholder, and the fake value appears nowhere in it or in mitmdump's output.
   This run also generates the CA in the session folder. Reversal: delete the capture file.
3. **Record the CA fingerprint** (agent). Changes nothing. Check: the SHA-256 fingerprint of
   `mitmproxy-ca-cert.pem` in the session folder is written to the session log; the key file is owner-read-only.
4. **Trust the CA — owner's step, reserved** (security setting; the agent may not change it). The owner adds the
   certificate to the login keychain as a trusted root for SSL, after comparing the fingerprint with step 3's.
   Check: the keychain lists exactly one certificate named mitmproxy with that fingerprint. Reversal: step 7.
5. **Capture** (agent). Start mitmdump (127.0.0.1, the two hosts only, the addon), then run `agy -p /usage` once
   with `HTTPS_PROXY` pointing at it. Check: agy prints its usual five quota lines (proves the proxy did not break
   it); the capture file holds at least one `retrieveUserQuotaSummary` request with its headers and body and a
   response containing a "Gemini Models" group; a search of the capture file and mitmdump's output for the
   access-token prefix (`ya29.`) finds nothing. If agy errors on TLS (pinning) or the capture file stays empty
   (proxy ignored): record that and go straight to step 6.
6. **Stop the proxy** (agent). Check: nothing listens on the chosen port.
7. **Remove the trust — owner's step, reserved, always runs once step 4 ran, whatever happened in step 5.** The
   owner removes the certificate's trust setting and deletes the certificate, matched by step 3's fingerprint.
   Check: the keychain lists no certificate with that fingerprint, and `agy -p /usage` without the proxy still
   prints its quota lines.
8. **Delete the CA and uninstall** (agent). Delete the session CA folder (holds the CA private key) and uninstall
   the cask, which the recorded starting state shows this session installed. Check: the folder is gone;
   `mitmdump` is not on PATH; `~/.mitmproxy` is still absent.
9. **Replay** (owner runs a script the agent writes; the agent cannot read the Keychain token). Plain HTTPS, no
   proxy, the captured headers and body. Inside the script the token is read from the Keychain and the project id
   is fetched live from `loadCodeAssist` (the same source agy uses), neither printed; the script refuses to send
   while any placeholder remains in the headers or body. Check: whether the reply carries the Gemini weekly and
   Argon buckets.
10. **Record** (agent). Redacted capture summary and replay result into the 2026-11-02 respike note and the
    CCRM-55 entry; the capture file itself is deleted (it lives in the session scratchpad, not the repo).

## Whole-plan reversal from any partial state
After 1–3: uninstall the cask, delete the session CA folder. After 4 or 5: stop mitmdump, then the owner removes
trust and deletes the certificate by fingerprint, then delete the session CA folder and uninstall. After 7+: nothing trusted remains;
remaining cleanup is steps 8 and 10. The capture file never holds a token, so deleting it is housekeeping, not
remediation. No step touches system proxy settings, the System keychain, agy's own files or its token.

## Reserved gates
- Steps 4 and 7 are the owner's (security settings). The agent stops before step 4 and before step 7 and waits.
- The agent never reads, prints or stores the OAuth token; step 9 is run by the owner.

## Failure modes
- agy ignores `HTTPS_PROXY`: empty capture → steps 6–8, record "proxy ignored".
- Certificate pinning or a non-HTTP transport for the quota call: TLS error or no matching flow → steps 6–8,
  record it.
- The owner forgets step 7: the agent re-checks the keychain for the fingerprint at step 8 and does not delete
  the session CA folder until trust is gone, then says so plainly.
- The session dies between 4 and 7: the trusted CA and its key stay on disk. The fingerprint and the teardown
  steps are in this plan in the repo, so the next session or the owner can finish step 7–8.
- The replay still lacks the buckets: record only that the replay did not reproduce them, and leave the cause
  open unless other evidence settles it.

## UNVERIFIED
- agy honours `HTTPS_PROXY` for its cloudcode-pa calls (Go's default does; agy's own transport might not).
- Go's macOS verifier accepts a root trusted only in the user's login keychain.
- agy does not pin Google's certificates.
- The quota call is plain HTTPS/JSON, not gRPC (agy's logs show HTTPS URLs for it).

## Tests and gates
- T1 addon dry run: fake token redacted, appears nowhere (step 2).
- T2 fingerprint match before trust (step 4) and before deletion (step 7).
- T3 token-leak search on the capture file and proxy output: zero hits for `ya29.` (step 5).
- T4 agy still works through the proxy (step 5) and without it after teardown (step 7).
- T5 teardown: no certificate with the fingerprint in the keychain; session CA folder absent; `~/.mitmproxy`
  still absent; `mitmdump` absent (steps 7–8).
- T6 replay script refuses to send while a placeholder remains (step 9).

## Execution record
- Step 1 done 04:23Z: cask mitmproxy 12.3.1 installed (absent before).
- Step 2 done: T1 passed — fake token, project and email redacted; zero leak hits in capture file and proxy log.
- Step 3: session CA `CN=mitmproxy, O=mitmproxy`, SHA-256
  `71:4F:3E:F0:9A:22:B5:A3:4E:81:6A:D7:D5:87:E1:78:D1:34:68:69:26:9B:88:D8:3E:16:1F:5B:92:C9:7D:7A`, SHA-1
  `48:4C:DA:66:58:BF:22:D1:0C:8F:39:ED:10:A5:F8:00:60:D4:CD:06`; key files 0600; `~/.mitmproxy` still absent.
  If this session dies before step 7, remove trust from and delete the login-keychain certificate with this SHA-1.
