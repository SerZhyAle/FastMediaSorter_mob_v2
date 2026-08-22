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

**4. `| tail` also MASKS THE EXIT CODE - a red run reads as green.** 2026-08-17, during the S1786 review: `pwsh -NoProfile -File ./a.ps1 fu 2>&1 | tail -60` in a background Bash task was reported as `[exited with code 0]`, while the captured output itself ended with `BUILD FAILED in 7m 49s` and `Fast check failed`. In Bash the pipeline's status is the LAST element's, i.e. `tail`'s. The completion notification therefore says "completed (exit code 0)" for a failed unit suite - and that notification is often the only thing read. Worse than cropping: cropping loses the reason, this loses the verdict.

**Why:** wasted a turn in S0250 hunting a noLegal failure that `tail -30` had cropped, ~40 minutes in S1239 waiting on a pipe that was never going to return, and in S1433 a false FAIL plus a false evidence citation in a spec Step Log.

**How to apply:**
- **PowerShell tool:** run the gradle-backed script plainly, no redirect, with `timeout: 600000`, and read its own verdict line (`Fast check passed` / `BUILD SUCCESSFUL`). The tool captures the full output already - it is not the Bash pipe, so it needs no remedy.
- **Bash tool:** `> file 2>&1`, then read the file. Works for both PASS and FAIL, no cropping, no hang.
- A build log is evidence only for the tree as it stood when the log was written. After ANY further source edit - including a one-line detekt fix - the previous log certifies nothing; re-run before citing it.
- Diagnosing a stuck background gradle task: check `temp/BUILD.LOCK` via `scripts/utils/lock-status.ps1 -Name Build`. Lock absent + task still "running" + empty output = the pipe hung, not the build. Stop it and re-run with a redirect; do not keep waiting.
- Never append `; echo "EXIT=$?"` to a redirected command - the echo becomes the task's exit code and masks the real one (see [[background-task-exit-code-is-echo]]). Use `"EXIT=$LASTEXITCODE"` inside pwsh instead, or read the script's own verdict line.

**5. The exit-code masking is NOT gradle-specific - it hides a lease/lock verdict too.** 2026-08-20, `/spec-code S1853`: the run opened with `pwsh .. ticket-lease.ps1 -Verb Claim -Id S1853 .. 2>&1 | tail -10; echo "EXIT=$?"`, which printed `EXIT=0` - `tail`'s code. The real code was **3: "claim lost, a live foreign session already holds this ticket"**. The message on screen (`already claimed by session <id> on MARK`) is printed on BOTH the idempotent-own-session path and the lost-claim path, so the text alone cannot tell them apart - only the exit code can. Believing the 0, the run began a full pipeline over a ticket a sibling `/spec-dev` session was writing at that moment (three source files re-written 80 s into the run, mid-inspection). Caught only because two greps of the same file, minutes apart, disagreed.

**Recurrence, 2026-08-21, `/spec-all S1864`:** the identical mistake on the run's FIRST tool call - `ticket-lease.ps1 -Verb Claim | tail -5; echo "EXIT=$?"` printed `EXIT=0` over a real **3**. A whole spec was written, Approved and three files edited before a preflight run happened to print `leased_ids: [{S1864, session pid-62424, reason //spec-code}]` - a foreign session id in a payload, not any gate, is what surfaced it. The guard is worthless applied late: the lease call is the first command of the pipeline, so this rule has to fire there or the collision is already paid for.

**How to apply (addition):** any script whose ANSWER is its exit code - `ticket-lease.ps1`, `enter-code-lock.ps1` (4 = queued), `post-change.ps1` (1 = gate failed, 2 = did not run), `assert-*.ps1` - must never be piped from the Bash tool. Redirect to a file and take `$?` off the bare command, or run it in the PowerShell tool. And when a file's content changes between two reads inside one turn, treat that as a live sibling writer and verify the lease before writing anything - do not rationalise it as a stale read.
