# Worktree mode — sessions started with the app's "worktree" option

<!-- worktree-mode v1 (2026-09-25). One text for every repo; only the table below differs.
Trialled in CooldownMac (PR #1); plan Astra-reviewed (accept). Decision record: the owner's MacAdmin notes. -->

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
| Verify command | `./gradlew testDebugUnitTest` |
| Sequential IDs to check | `CCRM-<n>` IDs; numbered BUGS/ROADMAP entries |
| Disposable ignored files | `.gradle/`, `build/`, `app/build/`, `.kotlin/`, `.idea/`, `*.iml`, `.DS_Store` (`local.properties` and signing files are **not** disposable) |
| Exchange airlock | none |

## The session

1. Stay on the branch the app created. Never check out `main`, never touch the main checkout.
2. Commit as you go (after the verify command passes, if there is one) and `git push -u origin HEAD` each time. On the first push: `gh pr create --base main --fill` (the title follows the repo's naming rules), then bind the PR to this session with the app's PR tool.
3. Ask the owner everything you need **before** landing.
4. **Land when the task is done**, by fast-forward only:
   1. `git fetch origin` → `git rebase origin/main`. Conflict → `git rebase --abort`, stop, report.
   2. ID check: for every sequential ID this session added (table), grep the rebased tree. If one is defined twice, renumber **this session's own** entry to the next free number, fix its references, and say so in the report.
   3. Run the verify command on this head (sha *T*). Nothing changes the tree after it.
   4. `git push --force-with-lease origin HEAD` (this session's own branch only), then `git push origin T:main` (plain push). Rejected → repeat from 4.1 once. Rejected again → stop and report; the PR stays open.
   5. Verify: `git fetch origin`, `git merge-base --is-ancestor T origin/main`, and `gh pr view <n> --json state` shows `MERGED` (allow 60 s). Both hold → `git push origin --delete <branch>`. Otherwise keep the branch and PR, and report.
5. **The final message is a report with no questions.** Anything the owner must do is a plain instruction. End the turn; the app's auto-archive archives the session and removes the worktree. **Never archive the session yourself** (in the trial, a self-archive left the worktree behind).
6. **Never:**
   - merge a PR this session did not open;
   - plain `--force`, `branch -D`, `worktree remove --force` or `worktree unlock`;
   - `gh pr merge`, or the app's server-side auto-merge (it squashes and lands a combination nobody tested).

## Sweeper — every session start, either mode

For each linked worktree under `.claude/worktrees/` other than this session's own, remove it **only if all of these hold**:

- `git -C <wt> status --porcelain --ignored` shows no `??` or modified line, and every `!!` line is in the disposable list (table).
- Its HEAD is an ancestor of `origin/main` (after `git fetch`).
- `git worktree list --porcelain` shows it unlocked.
- The app's session list (`include_archived`, limit 50) contains a session whose `worktreePath` equals it (`realpath` on both sides), and that session is **archived**. Re-read the list just before each removal. If the call fails or returns exactly 50 rows, skip the whole sweep.

Then run a plain `git worktree remove <wt>`. Delete its branch with `git branch -d` only from a main checkout fast-forwarded to `origin/main`; otherwise leave the branch. List anything that fails a check in the report; never remove it. A tree with no matching session (e.g. a sub-agent's) is left to Claude Code's own retention sweep.
