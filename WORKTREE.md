# Worktree mode — sessions started with the app's "worktree" option

<!-- worktree-mode v2 (2026-10-17). One text for every repo; only the table below differs.
v1 (2026-09-25) landed by rebase + force-with-lease + remote branch delete; the model-mesh v2.7.2 project settings
deny all three, so v2 lands by merge and keeps the branch. Plan worktree-mode-v2, Astra-reviewed (accept, one delta).
Decision record: the owner's MacAdmin notes. -->

**Pick the mode at every session start:**

```sh
[ "$(git rev-parse --git-dir)" != "$(git rev-parse --git-common-dir)" ] && echo worktree || echo main
```

- **Main mode** (the main checkout): this repo's own git rules, unchanged.
- **Worktree mode**: this file replaces "work on `main`" and "never push a `claude/*` branch" **for this session only**. Every other rule in the contract binds as before: gates, approvals, commit-message rules, reserved decisions. The `claude/*` rule still applies to cloud sessions, which have no worktree.
- **Either mode:** run the sweeper (bottom of this file) after the pull/fetch.

Everything here is automatic. Never ask the owner to commit, push, open, merge or clean up.

| This repo | |
|---|---|
| Verify command | `./gradlew testGithubDebugUnitTest testPlayDebugUnitTest`, and when `ios/` changed: `swift test --package-path ios/Packages/CooldownCore` |
| Sequential IDs to check | `CCRM-<n>` IDs; numbered BUGS/ROADMAP entries |
| Disposable ignored files | `.gradle/`, `build/`, `app/build/`, `.kotlin/`, `.idea/`, `*.iml`, `.DS_Store` (`local.properties` and signing files are **not** disposable) |
| Exchange airlock | none |

## The session

1. Stay on the branch the app created. Never check out `main`, never touch the main checkout.
2. Commit as you go (after the verify command passes, if there is one) and `git push -u origin HEAD` each time, as its own Bash call in exactly that form (no added flags such as `-q`, no `cd` in front, no `&&`, `;`, pipes or redirects; only that form is pre-approved). Refused by the permission check → report the command and the refusal; never ask the owner to widen settings. On the first push: `gh pr create --base main --fill` (the title follows the repo's naming rules), then bind the PR to this session with the app's PR tool.
3. Ask the owner everything you need **before** landing.
4. **Land when the task is done**; main only ever fast-forwards to the tree that was tested:
   1. `git fetch origin` → `git merge --no-edit origin/main` (merges main into this session's branch; main itself is
      never edited here). The merge commit keeps git's default message (the repo's commit-message rules apply to this session's own
      commits). Conflict → `git merge --abort`, stop, report; the PR stays open.
   2. ID check: for every sequential ID this session added (table), grep the merged tree. If one is defined twice, renumber **this session's own** entry to the next free number, fix its references, and say so in the report.
   3. Run the verify command on this head (sha *T*). Nothing changes the tree after it.
   4. Two pushes, each as its **own Bash call with exactly this text** (only these exact forms are pre-approved; any other spelling goes to auto mode's check, which refuses it as a merge without review): `git push -u origin HEAD` (this session's own branch; plain, because a merge only adds to it), then `git push origin HEAD:main` (plain push; HEAD is *T* here, and *T* contains `origin/main`, so main fast-forwards to it). Never force, in any spelling: the repo's settings deny it. No added flags (such as `-q`), no `cd` in front (the session already runs in its worktree), no `&&`, `;`, pipes, redirects or loops around either push; run the step 4.5 checks as separate calls. Refused by the permission check → compare the command sent with the form above. It differed → resend once exactly as written here. It was identical, or the resend is refused too → stop and report the exact command and the refusal text, without guessing the cause; the PR stays open. Never ask the owner to allow the push in settings; both forms are already allowed. Rejected by git (not a fast-forward) → repeat from 4.1 once. Rejected again → stop and report; the PR stays open.
   5. Verify: `git fetch origin`, `git merge-base --is-ancestor T origin/main`, and `gh pr view <n> --json state` shows `MERGED` (allow 60 s). **Keep the branch** — never delete it (the repo's settings deny remote deletes); name it in the report as a plain instruction: the owner deletes merged `claude/*` branches when convenient. *T* is on main but the PR is still not `MERGED` after 60 s → report that (the owner closes the PR); the work is safely on main, but do not archive. The ancestor check fails → keep the PR open and report.
5. **Ending the session.**
   - **The owner says the session is done** ("done", "end the session", "close it", "that's all"): land first if not landed yet (step 4, every gate). Then, only once step 4.5's checks both hold, archive this session yourself with the app's archive tool (`self`). The owner's words in this session are the agreement. If landing stopped (conflict, failing test, second rejection, failed check), do **not** archive; report instead.
   - **You finished on your own** (no such words): land, then end the turn with a report that asks no questions. Anything the owner must do is a plain instruction. The app's auto-archive takes it from there and may wait up to a day. Don't archive yourself in this case.
   - A self-archive can leave the worktree on disk; the next session's sweeper removes it.
   - **Undo:** unarchive the session from the sidebar's Archived list. Its commits are on `main`, so a new worktree session carries on from there. Files that were never committed can't be restored once a tree is removed; that is why the sweeper only removes trees holding nothing but disposable files.
6. **Never:**
   - merge a PR this session did not open;
   - `--force` in any spelling (`--force-with-lease` included), `branch -D`, `worktree remove --force` or `worktree unlock`;
   - `gh pr merge`, or the app's server-side auto-merge (it squashes and lands a combination nobody tested).

## Sweeper — every session start, either mode

For each linked worktree under `.claude/worktrees/` other than this session's own, remove it **only if all of these hold**:

- `git -C <wt> status --porcelain --ignored` shows no `??` or modified line, and every `!!` line is in the disposable list (table).
- Its HEAD is an ancestor of `origin/main` (after `git fetch`).
- `git worktree list --porcelain` shows it unlocked.
- The app's session list (`include_archived`, limit 50) contains a session whose `worktreePath` equals it (`realpath` on both sides), and that session is **archived**. Re-read the list just before each removal. If the call fails or returns exactly 50 rows, skip the whole sweep.

Then run a plain `git worktree remove <wt>`. Delete its branch with `git branch -d` only from a main checkout fast-forwarded to `origin/main`; otherwise leave the branch. List anything that fails a check in the report; never remove it. A tree with no matching session (e.g. a sub-agent's) is left to Claude Code's own retention sweep.
