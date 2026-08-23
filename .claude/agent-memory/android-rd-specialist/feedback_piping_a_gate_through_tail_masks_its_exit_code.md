---
name: piping-a-gate-through-tail-masks-its-exit-code
description: In a background Bash task, `pwsh -File ./a.ps1 fu | tail -40` reports tail's exit code, so a failed suite arrives as "completed (exit code 0)"
metadata:
  type: feedback
---

Never read the exit code of a command you piped. `pwsh -NoProfile -File ./a.ps1 fu 2>&1 | tail -40`
run as a background task ends with `[exited with code 0]` and a notification saying
"completed (exit code 0)" **even when the build failed** - the code belongs to `tail`, the last stage
of the pipe, not to the gate. Bash has no `pipefail` here by default.

**Why:** measured 2026-08-23 during the release-34 campaign. The full unit suite failed with six red
tests (`BUILD FAILED in 5m 37s`, digest `verdict: failure  exitCode: 10`) and the harness announced it
as exit 0. Reading the announcement instead of the body would have carried a red tree into a merge to
`main` and a tag - the one step that cannot be taken back. The same shape hides any gate's verdict:
`assert-*`, `post-change`, a catalog mutator.

**How to apply:** in a background task, redirect instead of piping (`... > file 2>&1`) and read the file,
or run the command bare and let the harness report the real code. When a pipe is unavoidable, print the
real code yourself - in PowerShell `"EXIT=$LASTEXITCODE"` after the call, which is what every foreground
check in this repo already does. And read the body regardless: this repo's runners print their own
verdict line (`Fast check passed/failed`, `post-change: PASS`, a `--- Build Failure Digest ---` block),
and that line is the truth. Related: [[gate-fail-may-mean-never-ran]], [[stale-test-results-xml]].
