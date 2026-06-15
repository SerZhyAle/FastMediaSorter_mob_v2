---
name: dirty-tree-is-normal-wip
description: Don't alarm about uncommitted changes; this repo uses multiple worktrees so a dirty tree is normal intact WIP, not a hazard
type: feedback
---

When `git status` shows uncommitted/untracked changes, report them calmly as the user's **intact WIP** - never frame a dirty tree as alarming, as "another session clobbered" something, or in any way that implies loss. Keep git inspection limited to what the workflow strictly needs (e.g. `/spec-all` build-gate `git diff --name-only HEAD`, drift-check), and don't editorialize the result.

**Why:** during a `/spec-next` run I described ~30 uncommitted files as a "dirty tree from a concurrent session" and a "safety hazard." The owner read that as "someone reverted git / my code is lost" and got alarmed - then had to go hunt for their code. Nothing was lost (reflog showed only commits + a branch checkout, zero reset/revert; `git diff --stat` showed all +2121/-1755 intact). The scare was caused purely by my framing.

**How to apply:**
- This repo legitimately runs **multiple git worktrees** (`P:/ANDROID/FastMediaSorter_mob_v2` -> `DEBUG-vNNN`, `P:/ANDROID/FastMediaSorter_release` -> `main`) plus stashes across branches. An uncommitted/dirty tree is the **normal working state**, not a red flag.
- If uncommitted work genuinely blocks an automated loop (build/edit hazard), say so factually: "there is uncommitted WIP in these files, I left it untouched, building/editing risks interleaving - want me to proceed or wait?" Do NOT use words like "clobber", "another session", "revert", or "lost".
- Verify before claiming anything destructive happened: `git reflog` is authoritative for reset/revert/checkout; presence of modified files = nothing was reset (reset would empty the tree).
