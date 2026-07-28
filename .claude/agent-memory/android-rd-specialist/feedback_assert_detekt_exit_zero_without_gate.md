---
name: assert-detekt-exit-zero-without-gate
description: assert-detekt.ps1 exits 0 even when it reports NEW findings unless -Gate is passed - read the verdict line, never the exit code
metadata:
  type: feedback
---

`scripts/quality/assert-detekt.ps1` reports findings but **exits 0 unless `-Gate` is passed**. A run that prints `assert-detekt: NEW findings in changed file(s):` still returns `REAL_EXIT=0`.

**Why:** the script is advisory by default and only becomes a barrier under `-Gate` (line ~173: `if ($Gate) { ...; exit 1 }; exit 0`). Reading the exit code alone turns "found problems in your file" into "clean", which is the precise failure the no-completion-claim rule exists to prevent. Observed 2026-07-28 closing S1221.

**How to apply:**
- Judge this gate by its verdict line, not `$?`: `PASS [scoped]` is a pass; `NEW findings in changed file(s)` is not, whatever the exit code says.
- Pass `-Gate` when you want it to actually fail the step.
- Its output is **file-granular** - it names the file, never the individual findings. To see them, read `app_v2/build/reports/detekt/detekt.txt` (check its mtime is newer than your edit) and grep the file name.
- To decide whether a finding is yours, diff the report against `config/detekt/baseline-app_v2.xml`. A file with **zero** baseline entries dumps its whole backlog on whoever touches it next - see [[detekt-scoped-gate-surfaces-untouched-debt]] and [[detekt-scoped-gate-line-shift]]. Confirm by line number against what you actually wrote before either fixing or parking it.
