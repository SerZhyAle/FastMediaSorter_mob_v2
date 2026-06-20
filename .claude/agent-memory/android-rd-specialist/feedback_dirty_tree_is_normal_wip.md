---
name: dirty-tree-is-normal-wip
description: The working tree IS the dev state; committed-vs-uncommitted is not a meaningful signal during development - only at release. Never alarm about a dirty tree.
type: feedback
---

The working tree is the single source of development truth. There are no "dirty" or "clean" files during development - only **recently-used** files. Commits matter **only before releases**; the rest of the time committed-vs-uncommitted carries no meaning. Do NOT reason about "is it committed", "stable committed baseline", "unverified/uncommitted foundation", or "collision with uncommitted WIP" when deciding whether to edit/build/defer. Just work on the current files.

When `git status` shows uncommitted/untracked changes, report them calmly as the user's **intact WIP** - never frame a dirty tree as alarming, as "another session clobbered" something, or in any way that implies loss. Keep git inspection limited to what the workflow strictly needs (e.g. `/spec-all` build-gate `git diff --name-only HEAD`, drift-check), and don't editorialize the result.

**Why (2026-06-20):** owner pushed back - "не знаю чего ты такой внимательный к тому что закоммичено .. разработка происходит только здесь над настоящими файлами, коммиты нужны перед релизами, в остальное время нет грязных или чистых файлов, есть недавно использованные." During a batch I had deferred tickets and narrated decisions around "committed baseline", "uncommitted WIP overlap", and "unverified foundation" - over-weighting git state. A mid-session timestamp WIP commit (`72b3fd2c "2606201743"`) is just the owner's routine save, not a meaningful state transition.

**Why:** during a `/spec-next` run I described ~30 uncommitted files as a "dirty tree from a concurrent session" and a "safety hazard." The owner read that as "someone reverted git / my code is lost" and got alarmed - then had to go hunt for their code. Nothing was lost (reflog showed only commits + a branch checkout, zero reset/revert; `git diff --stat` showed all +2121/-1755 intact). The scare was caused purely by my framing.

**How to apply:**
- This repo legitimately runs **multiple git worktrees** (`P:/ANDROID/FastMediaSorter_mob_v2` -> `DEBUG-vNNN`, `P:/ANDROID/FastMediaSorter_release` -> `main`) plus stashes across branches. An uncommitted/dirty tree is the **normal working state**, not a red flag.
- Do NOT defer/gate work on git state. "Spec X's code isn't committed yet" is NOT a reason to defer a dependent ticket - just build on the current files. A legitimate deferral signal is **device-unverified** (a BlockNeedUserTest foundation whose behaviour is genuinely unproven), NOT "uncommitted".
- The only real edit hazard is **truly concurrent** writers (e.g. parallel subagents mutating the same files in one run) - that is about simultaneity, not about commit state. A single dev session over the live files is normal, never a hazard.
- Verify before claiming anything destructive happened: `git reflog` is authoritative for reset/revert/checkout; presence of modified files = nothing was reset (reset would empty the tree).
- Commit only when the owner asks (typically pre-release). Otherwise leave the tree as working state.
