---
name: unit-suite-oom-truncation
description: Unit-suite worker health - the S1244 OOM truncation, the S1253 commit-exhaustion death, and the S1463 forkEvery-stop death that wears the same exit-10 signature but has none of S1253's causes
metadata:
  type: project
---

**Fixed on 2026-07-28 by S1244.** Kept because the failure mode is worth recognising again, and because any run from before that date is untrustworthy.

`testStandardDebugUnitTest` used to run on Gradle's default **512 MB** worker heap. The test worker is a separate process and inherits neither `org.gradle.jvmargs` (-Xmx6g, the Gradle daemon) nor `kotlin.daemon.jvm.options` (-Xmx4g, the Kotlin daemon) - both of which are set generously in `gradle.properties`, which is why the gap looked like a well-tuned config.

The worker died around `com.sza.fastmediasorter.data.remote.ftp.*`, so `domain.*`, `ui.*` and `util.*` never ran - **and Gradle still printed `946 tests completed, 1 failed`**, indistinguishable from a finished run. After `maxHeapSize = "2g"`: 2960 tests, 409 class reports, zero OOM, and *faster* (4m21s vs 8m59s - the dying worker had been thrashing).

**Why it still matters:** the truncation hid 7 of 8 failures, including a regression introduced the same day by S1229 in `ui.player` (parked as S1249). Every step of that ticket looked done - new tests written, suite run, build red for an unrelated reason.

**Still not fixed: the worker dies intermittently anyway.** Re-audited 2026-07-28 - two back-to-back `fu` runs gave 381 reports (`exit value 10`, reset socket, **zero** OOM) and then 409 (`ratio 1`, PASS). Different mechanism from the heap starvation, parked as **S1253** (now Archived: diagnosed as host commit exhaustion, fixed with `forkEvery = 100`). So a single green full run is not proof the suite is reliable; the same eight failures printed in both, which is why the truncated one looked normal.

**The same `exit value 10` signature came back on 2026-08-07 with a DIFFERENT mechanism (S1463).** Do not reach for S1253's diagnosis just because the log line matches - check the three signs it rested on first. That day: **zero** `OutOfMemoryError`, **zero** daemon memory-manager lines, **zero** `hs_err_pid*.log`. All 8 deaths carried a `RestartEveryNTestDefinitionProcessor.endBatch` frame, i.e. the death happens at worker **stop**, on the `forkEvery` path S1253 itself introduced - which is why it also hits a `--tests` filter of one class, and why the test report stays green (`failures="0"`) while the task goes red.

**`gradlew.bat:39-41` points `TMP`/`TEMP` of every build JVM at one shared `temp/gradle-tmp`, and nothing empties it.** Measured 2026-08-07: 3001 entries - 746 sqlite-jdbc DLLs (328 MB), 407 conscrypt DLLs, 335 MockK boot jars (58 in one day). Each test-JVM start unpacks a fresh set and leaves it; `forkEvery` multiplies the starts. Sweep it with `scripts/utils/prune-gradle-tmp.ps1` (24h floor, so a live run can never match). Whether the pile causes the deaths is NOT established - treat it as hygiene, not a cure.

**A red unit run here is often a run that never happened.** `check-standard-fast.ps1` now separates the two: a dead worker is retried once and then reported as exit **2** ("could not verify"), never exit 1. Before believing any red gate that runs tests, read the JUnit XML - `skipped="1"` with `failures="0"` means nothing was judged, whatever the gate concluded (S1464 shipped exactly that confusion in the settings gate).

**How to apply:**
- `.\a.ps1 fu` now self-checks - `check-standard-fast.ps1` runs `assert-test-suite-complete.ps1` on every unfiltered unit run, so you no longer invoke it by hand. It sits *before* the exit-code bail-out on purpose: a truncated run always exits non-zero, so a check placed after it would never see the case it exists for.
- Read the `assert-test-suite-complete: .. (ratio N)` line before believing any `fu` result. `TRUNCATED` means the run is not a result at all, whatever the test counts say.
- Never read a Gradle "<N> tests completed" line as proof the suite ran. It is printed whatever happened.
- 2 GB is a floor with headroom, not a measurement. A growing suite can find it again - the gate above is what will say so.
- To verify specific classes: `check-standard-fast.ps1 -Mode Unit -Tests "*YourTest"` - ONE Gradle pattern per invocation, a colon-joined pair matches nothing and fails the run after a full compile - then read `tests=/failures=/errors=` off the XML. See [[stale-test-results-xml]] and [[build-pre-existing-test-failures]].
- **`ratio 0` is a compile failure until proven otherwise.** The gate's message names `OutOfMemoryError` and `maxHeapSize`, which sends you hunting for a memory problem - but ratio *zero* means not one class report was written, and the ordinary cause is that `compile*UnitTestKotlin` never produced a test binary. Scroll up for `e: file://..` lines first. Observed 2026-08-05 (S1378): a constructor gained a parameter, three existing handler tests stopped compiling, and `fu` reported the OOM hint with zero tests and no OOM in the log. `.\a.ps1 fk` cannot catch this - it compiles main only. See [[constructor-change-compile-tests]].
