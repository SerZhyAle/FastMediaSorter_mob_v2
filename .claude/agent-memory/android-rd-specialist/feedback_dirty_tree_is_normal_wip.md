---
name: dirty-tree-is-normal-wip
description: The working tree IS the source of truth during dev. Never consult git history (log/blame/diff/status/HEAD~N) to understand current state, what changed, or whether something is WIP - only the user-driven release/commit flows touch git.
type: feedback
---

**Do not consult git history to understand current state.** The working tree is the single source of development truth. Never run `git log` / `git blame` / `git diff` / `git status` / `git show` / `HEAD~N` comparisons to figure out "what changed recently", "what did this file look like yesterday", "is this committed", or "is this WIP". On this project that signal is actively misleading and just burns iterations. Read the live files instead.

There are no "dirty" or "clean" files during development - only **recently-used** files. Commits matter **only before releases**; the rest of the time committed-vs-uncommitted carries no meaning. Do NOT reason about "is it committed", "stable committed baseline", "unverified/uncommitted foundation", or "collision with uncommitted WIP" when deciding whether to edit/build/defer. Just work on the current files.

If the working tree is dirty/untracked, that is the **normal** state - report it calmly as the user's intact WIP if it ever comes up. Never frame a dirty tree as alarming, as "another session clobbered" something, or in any way that implies loss.

**Use git only when:** the user explicitly asks ("show me the old version", "what changed", `/git`), or inside a release/commit flow (`/skill-release`, `/skill-fix-release`, `/caveman-commit`, `.\a.ps1 c`). Those flows own git deliberately. Everything else: stay out of git.

**Why (2026-07-19):** owner pushed back a third time - "откуда столько внимания у тебя состоянию внутреннего гит репозитория? Вся разработка здесь на одном компьютере. Никакие бранчи не открываются кроме релизного и текущего для разработки, однако ты всю дорогу смотришь что где и когда закоммичено." Confirms this is a standing pattern to actively suppress, not a one-off correction: single machine, single dev branch + release branch only, no other branches ever opened. Any framing that leans on "committed/not committed", "what's in git", "branch state" is imported habit from generic SWE practice and does not fit this project - self-check before mentioning git state at all, even descriptively.

**Why (2026-06-22):** owner directive - "Не лезь в ГИТ историю если не просят - не пытайся понять как этот файл выглядел вчера и что там WIP или нет. Это всё лишние итерации." Single developer; commit decisions come sometimes less than once a day while many tickets touch the **same** files. So an uncommitted pile mixes dozens of unrelated tickets, and `HEAD~1..HEAD` / `git diff HEAD` tell you nothing about the task at hand. The release branch is the only "clean" thing; everything else is intentionally dirty - do not go there.

**Why (2026-06-20):** owner pushed back - "разработка происходит только здесь над настоящими файлами, коммиты нужны перед релизами, в остальное время нет грязных или чистых файлов, есть недавно использованные." I had over-weighted git state, narrating decisions around "committed baseline", "uncommitted WIP overlap", "unverified foundation". A mid-session timestamp WIP commit is just the owner's routine save, not a meaningful state transition.

**Why (earlier):** during a `/spec-next` run I described ~30 uncommitted files as a "dirty tree from a concurrent session" and a "safety hazard." The owner read that as "someone reverted git / my code is lost" and got alarmed - then had to go hunt for their code. Nothing was lost; the scare was caused purely by my framing.

**How to apply:**
- Need to know what a feature does or where it stands → **read the code** (Grep/Read/catalog), never git.
- Continuing an In-Progress ticket → reconcile against the live files (Grep the spec's symbols, check files exist), not against `git status`. See [[feedback_spec_dev_continue_verify_code_first]].
- Don't defer/gate work on git state. "Spec X's code isn't committed yet" is NOT a reason to defer a dependent ticket - build on the current files. A legitimate deferral signal is **device-unverified** (a `BlockNeedUserTest` foundation whose behaviour is genuinely unproven), NOT "uncommitted".
- The only real edit hazard is **truly concurrent** writers (parallel subagents mutating the same files in one run) - simultaneity, not commit state. See [[feedback-parallel-agents-no-git-build]].
- Commit only when the owner asks (typically pre-release). Otherwise leave the tree as working state.
