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
- To decide whether a finding is yours, diff the report against `config/detekt/baseline-app_v2.xml`. A file with **zero** baseline entries dumps its whole backlog on whoever touches it next - see [[detekt-scoped-gate-surfaces-untouched-debt]] and [[detekt-scoped-gate-flags-shifted-preexisting-findings]]. Confirm by line number against what you actually wrote before either fixing or parking it.


## Not detekt-specific - hit again on a different gate (2026-08-21)

`assert-memory-budget.ps1` behaves the same way. Without `-Gate` it prints
`assert-memory-budget: PASS (index within budget)` and exits 0; with `-Gate` the same file gives
`FAIL - 13736 B exceeds the 12947 B ceiling by 789 B`. The plain run is measuring against a stretch
allowance, the gated run against the hard ceiling, and only the gated verdict is the one the
fast-gate batch will report.

Cost of not knowing: three separate "PASS" checks were recorded while the batch was failing on that
very gate, and the failure surfaced only when the whole batch was run.

**How to apply:** treat `-Gate` as the default for any `assert-*` script whose parameter block
declares it, not as an extra. When a gate passes standalone and the batch still fails on it, the
missing switch is the first thing to check - the two verdicts are different questions.
