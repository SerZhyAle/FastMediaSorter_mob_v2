---
name: piping-a-gate-through-tail-masks-its-exit-code
description: In a background Bash task, anything after the command owns the exit code - a pipe to tail, or a trailing `echo EXIT=$? >> log`, so a failed build arrives as "completed (exit code 0)"
metadata:
  type: feedback
---

Never read the exit code of a command that is not the last thing on its line. Two shapes cause this,
both measured in this repo, both announced as `completed (exit code 0)` over a genuine failure:

- **A pipe.** `pwsh -NoProfile -File ./a.ps1 fu 2>&1 | tail -40` reports `tail`'s code. Bash has no
  `pipefail` here by default.
- **A trailing echo.** `pwsh ... > log 2>&1; echo "EXIT=$?" >> log` reports the **echo**'s code. This
  one is nastier, because the idiom was adopted *as the fix for the pipe* - it does write the real code
  into the file, but the harness notification still reads 0.

**Why:** measured 2026-08-23 during the release-34 campaign, twice. First the full unit suite failed with
six red tests (`BUILD FAILED`, digest `verdict: failure exitCode: 10`) and was announced as exit 0.
Then, after switching to redirect-plus-echo, `build-release-spectrum.ps1` aborted on an unknown flavor
(`SPECTRUM_EXIT=1`) and was announced as exit 0 again. Reading either announcement instead of the body
would have carried a broken artifact into a publish - the step that cannot be taken back.

**How to apply:** make the command you care about the **last** thing on the line - redirect with
`> file 2>&1` and nothing after it, so the harness code is the real one. When a wrapper is unavoidable
(`pwsh -Command`), end it with `exit $LASTEXITCODE`. And read the body regardless of what the
notification says: this repo's runners print their own verdict line (`Fast check passed/failed`,
`post-change: PASS`, `BUILD SUCCESSFUL`, a `--- Build Failure Digest ---` block), and that line is the
truth. Related: [[gate-fail-may-mean-never-ran]], [[stale-test-results-xml]],
[[pwsh-file-does-not-split-a-comma-list-into-an-array]].
