---
name: post-change-dev-log-first-file-only
description: post-change.ps1 -Files writes a dev-log row for only the FIRST file, so a batched closure silently leaves the rest unlogged
metadata:
  type: feedback
---

`post-change.ps1 -Files "a.kt,b.kt,c.kt"` runs every **gate** across the whole set but writes exactly
**one** `dev/CHANGELOG.md` row - for the first file in the list. The others end the closure with no
dev-log entry at all.

**Why:** caught by `/spec-check` on S1205 (2026-08-06). Phase 02 closed six files through one
`post-change.ps1 -Files ... -ScopeToFile` call, the closure printed `post-change: PASS`, and the phase's
"Dev log entry added for every file in Files Touched" criterion was ticked on that verdict. Only
`LauncherCellCommand.kt` had a row; `LauncherCellCommandTest.kt` had none. The green verdict is about the
gates, not about dev-log coverage - reading it as both is what let the gap through.

**How to apply:** after a batched `-Files` closure, either grep `dev/CHANGELOG.md` for every path in the
batch, or use `close-and-log.ps1 -DevLogs '<json array>'` (one `{file,target,desc}` object per file),
which does log every entry. Never tick a per-file dev-log criterion off a `post-change: PASS` line.
Related: [[feedback-verify-full-evidence]].
