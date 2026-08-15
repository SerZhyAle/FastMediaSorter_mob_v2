---
name: select-first-detaches-running-script
description: Piping a long-running script through Select-Object -First N returns before it finishes, so any state you read next is mid-write - capture to a variable instead
metadata:
  type: feedback
---

Never pipe a still-running script through `Select-Object -First N` (or any pipeline-stopping cmdlet)
and then read the state it was writing. The `-First` satisfies itself early and hands control back
while the script keeps writing. Assign the whole run to a variable first - `$out = pwsh ... 2>&1` -
then filter `$out`; the assignment waits for the process to exit.

**Why:** measured 2026-08-13 on the S1420 locale import. The run was piped through
`Select-String ... | Select-Object -First 12`, and the coverage gate run immediately afterwards
reported 726 untranslated keys for `bn`. Re-running the same gate a minute later reported 93 - the
number was never wrong, the files were simply still being written. Worse than the false reading: the
`exit-code-lock.ps1` call in the same chain released `CODE.LOCK` while the writes were in flight, so
another agent could have edited the same resources mid-write.

**How to apply:** any invocation that writes files, holds a lock, or whose exit code you intend to
act on - imports, seeders, closure facades, gates. Filtering output is fine; filtering it *lazily* is
not. A verdict or a state read taken after a truncated pipeline is not evidence, per
[[feedback_gate_fail_may_mean_never_ran]] - re-run it before believing it.
