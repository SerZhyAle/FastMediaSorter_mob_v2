---
name: build-output-pipe-truncation
description: Piping gradle to `tail -N` hides the FAILURE block and can hang a background task; redirect to a file from Bash, but run plain in the foreground from the PowerShell tool
metadata:
  type: feedback
---

Never pipe a gradle-backed command into `tail`/`head` from the Bash tool. Redirect to a file and read it: `pwsh -NoProfile -File <script> *> temp/scratch/<name>.log`.

Two separate failures, both real:

**1. `tail` crops the diagnostic.** When a gradle build fails, the FAILURE / "What went wrong" block sits in the MIDDLE of the output (right after the failing task), not at the end. The end is just gradle's deprecation footer and "BUILD FAILED in Ns". So `| tail -30` shows "BUILD FAILED" with no reason.

**2. `| tail` can hang the background task indefinitely.** 2026-07-29, S1239: launched `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor Vr 2>&1 | tail -40` with `run_in_background`. After 40 minutes the task still reported `running` with a **completely empty** output file, while `temp/BUILD.LOCK` was already **absent** - and that script acquires the lock at line 19 and releases it at line 98, so gradle had long finished. The gradle work was done; the bash pipe never closed. Had to `TaskStop` it and re-run with a file redirect, which finished in 46 s.

**3. The file redirect is a BASH-tool remedy, and it backfires in the PowerShell tool.** 2026-08-08, S1433: `pwsh -NoProfile -File ./a.ps1 fk *> temp/S1433/fk.log` from the PowerShell tool reported **exit 255** on one run and blew past the 600 s tool timeout on the next - while the log itself held `BUILD SUCCESSFUL in 2m 54s` / `Fast check passed`, written minutes earlier. The same target run plainly in the foreground finished in 45 s with a clean verdict on screen. Redirecting hides the verdict behind a false failure signal, and the stale log then invites citing it as proof for a *later* edit: that is exactly what happened - a compile log from 20:33 was cited for a file edited at 20:41, so the last source edit was never actually compiled until the discrepancy surfaced.

**Why:** wasted a turn in S0250 hunting a noLegal failure that `tail -30` had cropped, ~40 minutes in S1239 waiting on a pipe that was never going to return, and in S1433 a false FAIL plus a false evidence citation in a spec Step Log.

**How to apply:**
- **PowerShell tool:** run the gradle-backed script plainly, no redirect, with `timeout: 600000`, and read its own verdict line (`Fast check passed` / `BUILD SUCCESSFUL`). The tool captures the full output already - it is not the Bash pipe, so it needs no remedy.
- **Bash tool:** `> file 2>&1`, then read the file. Works for both PASS and FAIL, no cropping, no hang.
- A build log is evidence only for the tree as it stood when the log was written. After ANY further source edit - including a one-line detekt fix - the previous log certifies nothing; re-run before citing it.
- Diagnosing a stuck background gradle task: check `temp/BUILD.LOCK` via `scripts/utils/lock-status.ps1 -Name Build`. Lock absent + task still "running" + empty output = the pipe hung, not the build. Stop it and re-run with a redirect; do not keep waiting.
- Never append `; echo "EXIT=$?"` to a redirected command - the echo becomes the task's exit code and masks the real one (see [[background-task-exit-code-is-echo]]). Use `"EXIT=$LASTEXITCODE"` inside pwsh instead, or read the script's own verdict line.
