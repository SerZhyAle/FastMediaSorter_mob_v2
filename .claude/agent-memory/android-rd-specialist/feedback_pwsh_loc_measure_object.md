---
name: pwsh-loc-measure-object-undercount
description: Get-Content | Measure-Object -Line undercounts LOC; use .Count for line counts
type: feedback
---

`(Get-Content $f | Measure-Object -Line).Lines` UNDERCOUNTS a file's line count. On S0915 it returned 1330 for a file that is actually 1483 lines - a 153-line error that produced a completely wrong "the file shrank 157 lines, the ticket premise self-resolved" narrative. Use `(Get-Content $f).Count`, or newline count on raw text, instead.

**Why:** `Measure-Object -Line` is designed to count lines *within each input string*. When `Get-Content` has already split the file into an array of one-line strings, piping into `-Line` miscounts (it does not simply sum to the array length). A subagent's independent `wc -l` gave the true 1483 and exposed the discrepancy.

**How to apply:** For any LOC / line-count check in PowerShell use `(Get-Content $f).Count` (array length) or `([regex]::Matches((Get-Content $f -Raw), "\n")).Count`. Never trust `Measure-Object -Line` piped from `Get-Content`. If a LOC delta is surprising (e.g. a central file "shrank" a lot), cross-check with a second method and the file's `LastWriteTime` before acting on the premise. Related: [[feedback_dirty_tree_is_normal_wip]] (working tree is truth - verify live, not from a stale number).
