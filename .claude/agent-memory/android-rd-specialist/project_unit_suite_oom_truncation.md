---
name: unit-suite-oom-truncation
description: Fixed 2026-07-28 (S1244) - the unit suite used to die of OOM at 32% and print a complete-looking summary; a.ps1 fu now self-checks coverage, but the worker still dies intermittently (S1253)
metadata:
  type: project
---

**Fixed on 2026-07-28 by S1244.** Kept because the failure mode is worth recognising again, and because any run from before that date is untrustworthy.

`testStandardDebugUnitTest` used to run on Gradle's default **512 MB** worker heap. The test worker is a separate process and inherits neither `org.gradle.jvmargs` (-Xmx6g, the Gradle daemon) nor `kotlin.daemon.jvm.options` (-Xmx4g, the Kotlin daemon) - both of which are set generously in `gradle.properties`, which is why the gap looked like a well-tuned config.

The worker died around `com.sza.fastmediasorter.data.remote.ftp.*`, so `domain.*`, `ui.*` and `util.*` never ran - **and Gradle still printed `946 tests completed, 1 failed`**, indistinguishable from a finished run. After `maxHeapSize = "2g"`: 2960 tests, 409 class reports, zero OOM, and *faster* (4m21s vs 8m59s - the dying worker had been thrashing).

**Why it still matters:** the truncation hid 7 of 8 failures, including a regression introduced the same day by S1229 in `ui.player` (parked as S1249). Every step of that ticket looked done - new tests written, suite run, build red for an unrelated reason.

**Still not fixed: the worker dies intermittently anyway.** Re-audited 2026-07-28 - two back-to-back `fu` runs gave 381 reports (`exit value 10`, reset socket, **zero** OOM) and then 409 (`ratio 1`, PASS). Different mechanism from the heap starvation, parked as **S1253**. So a single green full run is not proof the suite is reliable; the same eight failures printed in both, which is why the truncated one looked normal.

**How to apply:**
- `.\a.ps1 fu` now self-checks - `check-standard-fast.ps1` runs `assert-test-suite-complete.ps1` on every unfiltered unit run, so you no longer invoke it by hand. It sits *before* the exit-code bail-out on purpose: a truncated run always exits non-zero, so a check placed after it would never see the case it exists for.
- Read the `assert-test-suite-complete: .. (ratio N)` line before believing any `fu` result. `TRUNCATED` means the run is not a result at all, whatever the test counts say.
- Never read a Gradle "<N> tests completed" line as proof the suite ran. It is printed whatever happened.
- 2 GB is a floor with headroom, not a measurement. A growing suite can find it again - the gate above is what will say so.
- To verify specific classes: `check-standard-fast.ps1 -Mode Unit -Tests "*YourTest"` - ONE Gradle pattern per invocation, a colon-joined pair matches nothing and fails the run after a full compile - then read `tests=/failures=/errors=` off the XML. See [[stale-test-results-xml]] and [[build-pre-existing-test-failures]].
- **`ratio 0` is a compile failure until proven otherwise.** The gate's message names `OutOfMemoryError` and `maxHeapSize`, which sends you hunting for a memory problem - but ratio *zero* means not one class report was written, and the ordinary cause is that `compile*UnitTestKotlin` never produced a test binary. Scroll up for `e: file://..` lines first. Observed 2026-08-05 (S1378): a constructor gained a parameter, three existing handler tests stopped compiling, and `fu` reported the OOM hint with zero tests and no OOM in the log. `.\a.ps1 fk` cannot catch this - it compiles main only. See [[constructor-change-compile-tests]].
