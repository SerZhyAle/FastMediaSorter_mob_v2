---
name: build-output-pipe-truncation
description: Piping gradle to `tail -N` hides the FAILURE block and can hang a background task forever; redirect to a file instead
metadata:
  type: feedback
---

Never pipe a gradle-backed command into `tail`/`head` from the Bash tool. Redirect to a file and read it: `pwsh -NoProfile -File <script> *> temp/scratch/<name>.log`.

Two separate failures, both real:

**1. `tail` crops the diagnostic.** When a gradle build fails, the FAILURE / "What went wrong" block sits in the MIDDLE of the output (right after the failing task), not at the end. The end is just gradle's deprecation footer and "BUILD FAILED in Ns". So `| tail -30` shows "BUILD FAILED" with no reason.

**2. `| tail` can hang the background task indefinitely.** 2026-07-29, S1239: launched `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor Vr 2>&1 | tail -40` with `run_in_background`. After 40 minutes the task still reported `running` with a **completely empty** output file, while `temp/BUILD.LOCK` was already **absent** - and that script acquires the lock at line 19 and releases it at line 98, so gradle had long finished. The gradle work was done; the bash pipe never closed. Had to `TaskStop` it and re-run with a file redirect, which finished in 46 s.

**Why:** wasted a turn in S0250 hunting a noLegal failure that `tail -30` had cropped, and ~40 minutes in S1239 waiting on a pipe that was never going to return.

**How to apply:**
- Default: `*> temp/scratch/<ticket>-<what>.log` (pwsh) or `> file 2>&1` (bash), then read the file. Works for both PASS and FAIL, no cropping, no hang.
- Diagnosing a stuck background gradle task: check `temp/BUILD.LOCK` via `scripts/utils/lock-status.ps1 -Name Build`. Lock absent + task still "running" + empty output = the pipe hung, not the build. Stop it and re-run with a redirect; do not keep waiting.
- Never append `; echo "EXIT=$?"` to a redirected command - the echo becomes the task's exit code and masks the real one (see [[background-task-exit-code-is-echo]]). Use `"EXIT=$LASTEXITCODE"` inside pwsh instead, or read the script's own verdict line.
