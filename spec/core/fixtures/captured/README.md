# Captured payloads

Raw responses captured on the phone, read by name by Android's unit tests (Gradle adds this
folder to the test resources). Not paired with `.expected.json` files: the tests assert on them
directly.

| File | Endpoint | Captured |
|---|---|---|
| `claude-usage-2026-09.json` | Claude `GET /api/oauth/usage` | 2026-09-16, a Team Standard account (CCRM-69 (Window Dollars) probe). Unknown and codenamed keys are kept on purpose: parsers must ignore them. |
| `chatgpt-usage-2026-09.json` | ChatGPT `GET /backend-api/wham/usage` | 2026-09-06, a Plus account (CCRM-54 (ChatGPT Account)). `user_id`, `account_id` and `email` replaced; everything else verbatim. |

Add a new capture beside these rather than editing one.
