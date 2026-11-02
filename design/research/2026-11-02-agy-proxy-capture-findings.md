# agy-proxy-capture — Astra findings

review: calls=1 (1 full + ≤ 1 delta) effort=high size=7.3 KB · verdict file: `2026-11-02-agy-proxy-capture-astra-call1.txt`

| ID | Grade | Conf. | Where | What | Fix | Closed by | Status |
|---|---|---|---|---|---|---|---|
| C1-1 | blocking | 99 | Steps 1–3, 8, reversal | Cleanup could delete a pre-existing `~/.mitmproxy` or uninstall software that was there before | CA generated in a fresh session-only folder in the scratchpad; starting state recorded; cleanup removes only session-created items | Evidence: starting-state check 2026-11-02T04:22:58Z (`~/.mitmproxy` absent, cask not installed, no mitm binaries, no mitmproxy keychain cert, `HTTPS_PROXY` unset), recorded in the plan; T5 re-checks `~/.mitmproxy` still absent | fixed |
| C1-2 | concerns | 98 | Closed decisions, step 9 | Replay leaves the redacted project id unresolved | Replay script fetches the project live from `loadCodeAssist` and refuses to send while any placeholder remains | T6 | fixed |
| C1-3 | concerns | 99 | Failure modes | A failed replay does not prove the difference is outside the request | Reworded: record only that the replay did not reproduce the buckets; cause stays open | Text change | fixed |
