---
name: fc-after-a-resource-change-is-not-a-fast-check
description: The 14-21 s figure for fk/fc holds only for code-only edits; adding a layout or editing a menu regenerates R and forces a full module recompile, so run it in the background
metadata:
  type: feedback
---

`a.ps1 fc` (and `fk`) is listed as a foreground check because it measures 14-21 s on a warm daemon.
That figure is for a **code-only** edit. When the change adds or edits a resource - a new layout file, a
menu, a string - the R class is regenerated, every Kotlin file that reads `R` is invalidated, and the
run becomes a full-module recompile in the minutes range.

**Why:** 2026-08-19, S1823. A change touching `menu_streams.xml`, `activity_streams.xml`, a new layout
and three `strings.xml` files was closed with `a.ps1 fc` in the **foreground**. The compile itself took
**2m 26s** - well past the 600 s Bash timeout only because the invocation also piped through
`Select-Object -Last 12`, which buffers: the task's output file stayed 0 bytes, the harness never saw
the command exit, and the run sat there long after gradle had finished. The daemon log settled it -
`BUILD SUCCESSFUL in 2m 26s` at 23:48:41, then a client disconnect - while the task still showed as
running. Killing it and re-running with `*> file` returned `BUILD SUCCESSFUL in 1s, 21 tasks
up-to-date`: the work had been done all along.

Two separate lessons, and the second cost far more than the first: a resource-touching `fc` is minutes,
not the 14-21 s the docs quote for a code-only edit; and a piped long-running build can hang the
wrapper after the build itself is over, which looks exactly like a stuck compile.

**How to apply:**
- Resource-touching change -> background the check (`run_in_background`) and read the verdict from the
  log, the same as `d`/`nd`/`fu`. Code-only change -> foreground is still right.
- Never pipe a long build through `Select-Object -Last N`: the pipeline buffers, so the output file
  stays 0 bytes until the command exits and there is no progress to read. Redirect to a file with `*>`
  instead ([[build-output-pipe-truncation]] covers the exit-code half of this).
- Before diagnosing a "stuck" gradle run, check the daemon log for `BUILD SUCCESSFUL` first - the build may already be over and the wrapper the only thing hanging: `$env:USERPROFILE\.gradle\daemon\<ver>\daemon-<pid>.out.log`.
- Diagnosing a genuinely running build: `Get-CimInstance Win32_Process` on the java pids tells you which is
  the gradle daemon and which is the Kotlin daemon; rising CPU on the Kotlin one means progress.
