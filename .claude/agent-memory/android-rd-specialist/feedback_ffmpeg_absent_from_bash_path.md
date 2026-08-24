---
name: ffmpeg-absent-from-bash-path
description: ffmpeg resolves only in the PowerShell tool, not the Bash tool - and an ffmpeg call piped through grep reports a clean PASS when the binary was never found
metadata:
  type: feedback
---

`ffmpeg` is on PATH for the PowerShell tool but **not** for the Bash tool. In Bash it dies with
`ffmpeg: command not found`.

**Why this matters more than a missing binary usually does:** the natural way to read ffmpeg's
analysis filters is to pipe stderr through `grep` - `ffmpeg .. -vf freezedetect .. 2>&1 | grep
freeze_start`. When ffmpeg is absent, that pipeline prints nothing and exits 0, which is
indistinguishable from "the filter ran and found nothing". Measured 2026-08-24 while auditing the
S1991 Play evidence video: two `freezedetect` runs at different thresholds both "passed", and the
conclusion reported to the owner - "no frozen frames" - was drawn from a check that never executed.
The owner had already told me the video hung; the real answer was 129 of 146 seconds static.

This is the [[a-pass-that-observed-nothing]] failure with a specific, repeatable cause, and the
[[piping-a-gate-through-tail-masks-its-exit-code]] mechanic is what hides it.

**How to apply:**
- Run every ffmpeg/ffprobe invocation through the PowerShell tool, filtering with `Select-String`,
  not through Bash + `grep`.
- When any analysis filter returns an empty result, prove the binary ran before reporting the empty
  result as a finding: check the exit code separately, or print a line the tool always emits
  (`Duration:` from a bare `-i`).
- `freezedetect=n=-45dB:d=2` is the useful threshold for screen recordings; the default `-60dB` is
  too strict to catch a held UI screen with a live status-bar clock.
