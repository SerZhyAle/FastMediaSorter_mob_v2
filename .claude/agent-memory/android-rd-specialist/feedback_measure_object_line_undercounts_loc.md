---
name: measure-object-line-undercounts-loc
description: PowerShell Measure-Object -Line skips blank lines, so it silently undercounts LOC - use wc -l when a size threshold (2000, 600, 500) is the decision
metadata:
  type: feedback
---

`(Get-Content <file> | Measure-Object -Line).Lines` does **not** count blank lines, so it reports a
smaller file than the file is. Measured 2026-08-16 on S1541: the same two files gave 507 / 382 by
that idiom and **558 / 406** by `wc -l` - gaps of 51 and 24, exactly the blank-line counts.

**Why it matters here:** every size rule in this repo is a threshold on line count - Rule 2's 2000-LOC
split, the 600-line `LargeClass` detekt threshold, and Rule 5's 500-LOC backup trigger. Undercounting
by 10% is how a file crosses a threshold while the measurement still says it has room, and how a plan
records a number that no later measurement reproduces. On S1541 a phase file's Handoff Note carried
507 for a 558-line file; the disagreement read as an agent error until the idiom turned out to be the
cause.

**How to apply:** when the number is only informational, either is fine. When a threshold decision or
a written record depends on it, use `wc -l` from the Bash tool, or
`(Get-Content <file>).Count` in PowerShell - both count blank lines. Never mix idioms inside one
comparison (before/after a refactor), or the delta is meaningless.

Related: [[feedback_verify_subagent_build_failures]] - a subagent's number disagreeing with yours is
not automatically the subagent being wrong; reproduce with a second method before correcting it.
