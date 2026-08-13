# S1253 - The unit-test worker still dies intermittently, now with exit value 10 instead of OOM

**Status:** Archived
**Priority:** 60

## 0. Raw capture

Observed 2026-07-28 while auditing S1244 (`/spec-next` round 2). Out of scope of that ticket, which diagnosed and fixed a *heap* exhaustion; this is a different mechanism. Parked per CLAUDE.md 3.1.

Two full `.\a.ps1 fu` runs, back to back, same working tree, nothing else building:

- **10:12 run** - truncated. `2745 tests completed, 8 failed, 18 skipped`, 381 class reports, and the worker died:

```
Execution failed for task ':app_v2:testStandardDebugUnitTest'.
> Test process encountered an unexpected problem.
   > Process 'Gradle Test Executor 2' finished with non-zero exit value 10
Caused by: java.io.IOException: Connection reset by peer
    at org.gradle.internal.remote.internal.inet.SocketConnection.flush(SocketConnection.java:142)
```

- **10:19 run** - complete. `2960 tests completed, 8 failed, 17 skipped`, 409 reports, `ratio 1`, gate PASS, wall clock 1m 55s.

Same eight failing tests in both runs, so the difference is not a test that behaves differently - it is whether the worker survives to the end.

## 1. What distinguishes it from S1244

S1244 fixed a genuine 512 MB heap starvation: 18 `OutOfMemoryError` occurrences, truncation at `data.remote.ftp.*`, 136 reports. Raising `testOptions.unitTests` `maxHeapSize` to 2 GB removed it, and the 10:19 run above reproduces S1244's recorded result exactly (2960 / 409 / ratio 1).

This is not that failure:

- **Zero** `OutOfMemoryError` in the truncated run's log.
- The truncation point moved from `data.remote.ftp.*` to `ui.settings.*` - the run got through 381 of 409 classes before dying.
- The symptom is a process exit code plus a lost socket to the daemon, not a heap dump.

## 2. Where it stopped

The last reports written by the truncated run, all stamped 10:13:54, end at `ui.settings.SettingsActivityStateTest`. The packages that produced no reports at all:

- `com.sza.fastmediasorter.ui.settings.helpers`
- `com.sza.fastmediasorter.ui.settings.search`
- `com.sza.fastmediasorter.ui.share`
- `com.sza.fastmediasorter.ui.streams.helpers`
- `com.sza.fastmediasorter.util`
- `com.sza.fastmediasorter.utils`
- `com.sza.fastmediasorter.worker`

A class that kills the worker writes no report of its own, so the first candidate is whatever runs first in `ui.settings.helpers` - `ScreenshotGestureActionCatalogIconTest` by name order. That is a lead, not a conclusion: it rests on the assumption that execution order is the alphabetical order the reports suggest, which has not been confirmed.

## 3. Ruled out already

- **A test calling exit.** `grep` for `exitProcess`, `System.exit` and `Runtime.getRuntime().halt` across `app_v2/src/test` returns nothing.
- **Heap.** No `OutOfMemoryError`, and the same 2 GB worker finished the whole suite seven minutes later.

## 4. Open questions for research

- What produces exit code 10 here? It is not a signal-derived code (128+n) and not a JVM fatal-error code (usually 1 or 134). A `hs_err_pid*.log` or a Gradle worker crash log would name it - neither has been looked for yet.
- Is it reproducible, and at what rate? One truncation in two runs is not a measured rate. This needs several consecutive runs before anything is concluded, because an intermittent failure that is "fixed" after one green run is the same trap S1244 documented.
- Does external load matter? The truncated run overlapped an active Android emulator (`emulator-5556`) and a catalog scan on the same machine. Resource pressure killing a worker is plausible and cheap to test by re-running under the same load.
- Is `forkEvery` worth revisiting? S1244 deliberately did not add it because the heap fix made it unnecessary. A worker that dies for a non-heap reason at class ~381 is a different argument for bounding worker lifetime.

## 5. Why this is worth a ticket rather than a retry

Because of what it does to trust in the bar. The truncated run printed `2745 tests completed, 8 failed` - the *same eight failures* as the complete run. Nothing in that summary hints that 215 tests never ran. Without the S1244 completeness gate now wired into `check-standard-fast.ps1`, this run would have been read as "the usual eight known reds" and waved through.

That gate turns this from a silent failure into a loud one, which is why this ticket is about diagnosis and not about detection - detection already works.

## 6. Related

- S1244 - the heap fix and the completeness gate. This ticket exists because that gate caught something the ticket did not cover.
- S1245, S1246, S1249, S1250 - the eight failing tests present in both runs, already parked separately.

## 7. Diagnosis (2026-07-28, spec-next loop)

Section-4 questions answered from the daemon logs (`~/.gradle/daemon/9.4.1/`):

- **What exit 10 is.** A second occurrence of the same signature (daemon-6692.out.log:28495-28565,
  an older 946-test run of the same task) shows the death in full: dozens of
  `OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "fms-log-io"` followed by
  `*** java.lang.instrument ASSERTION FAILED ***: "!errorOutstanding" with message can't create
  name string at JPLISAgent.c line: 838` - the JVM dies inside the native instrument agent
  (Mockito/JaCoCo agent path) once allocation fails there, and that native death carries exit
  value 10 with no Java-level heap dump. The 10:12 run printed no OOM lines before its reset -
  consistent with the same exhaustion landing in the agent first at 2 GB heap.
- **The named thread is ours.** `fms-log-io` is LoggingHelper's single-thread file-logging
  executor. Robolectric boots the real `FastMediaSorterApp`, whose `onCreate` planted the FILE
  logging tree in unit tests - thousands of tests churned file IO, buffers and queue entries on
  a static executor that survives across classes within one worker. That is exactly the kind of
  cross-class accumulation that kills a worker near the END of the suite (class ~381 of 409).
- **Fix 1 landed**: `LoggingHelper.initialize` now detects Robolectric
  (`Build.FINGERPRINT == "robolectric"`) and plants only the logcat tree - no file pipeline, no
  `fms-log-io` executor in unit tests. Devices are untouched.
- **Fix 1 refuted as sufficient (same day, 15:41-15:49).** Three consecutive `fu` runs after the
  guard ALL died with the same silent exit 10 + connection reset (Executors 10/11/12,
  daemon-23564.out.log:34100/34210/34316): 2692, 2848 and 2463 tests completed out of 2960.
  No `fms-log-io` OOM storm appeared - the guard does remove that churn - but the worker died
  anyway, at a different point each time.
- **Actual root cause: host commit exhaustion.** Right before each dead run the daemon's memory
  manager logged `11.86 GB virtual memory requested, 6.9-7.4 GB free` and tried to release
  worker memory (daemon-23564.out.log:34023-34036,34137). The series ran while four emulators
  (5554/5556/5586/5588) plus a sibling agent session's gradle builds and Maestro flows were
  live. Robolectric keeps a sandbox classloader per test class - metaspace plus native memory
  that `-Xmx2g` does not bound - so one worker running all 409 classes peaks highest exactly
  near the suite's end; when the host cannot commit that peak, the JVM aborts natively (the
  JPLISAgent `can't create name string` assertion from the older occurrence is a native
  allocation failure; exit value 10 is that abort path). A quiet host absorbs the peak, which
  is why 10:19-style runs complete - the failure is load-dependent, answering §4's third
  question with a firm yes.
- **Fix 2 landed**: `forkEvery = 100` on the unit-test worker
  (`app_v2/build.gradle.kts` testOptions block, next to S1244's `maxHeapSize`). Recycling the
  worker every 100 classes caps the classloader accumulation and the commit peak - exactly the
  "different argument for bounding worker lifetime" §4 anticipated. S1244's reasoning against
  `forkEvery` (heap fix made it unnecessary) addressed heap, not native/metaspace growth.
- **Rate**: guard-only series 3/3 dead under load; `forkEvery` validation run in section 8.

## 8. Validation runs (2026-07-28)

Guard-only series (LoggingHelper fix alone), 15:41-15:49, host deliberately under the load that
correlates with the death - four emulators (5554/5556/5586/5588) plus a sibling agent session
running gradle builds and Maestro flows:

- Run 1: 2692 tests completed, then silent exit 10 + connection reset (Executor 10).
- Run 2: 2848 tests completed, same death (Executor 11).
- Run 3: 2463 tests completed, same death (Executor 12).
- No `fms-log-io` OOM storm in any of them - the guard removes that churn, not the death.

`forkEvery = 100` validation, 15:56-15:59, same live load (all four emulators up, sibling
still building):

- Suite complete: 410 reports for 408 `*Test.kt` files, ratio 1, completeness gate PASS.
- 2963 tests executed, 6 failed - the same six classes that were failing inside the guard-only
  runs of the same hour (CameraCaptureSaver, IconInventoryExport, ExecuteScheduledOperation,
  StreamLogoAtlasSlicer x3): pre-existing reds parked in their own tickets plus in-flight
  sibling churn around the stream-logo atlas. No new failures from worker recycling.
- Wall clock 3m 10s against S1244's 1m 55s single-worker quiet-host baseline - the cost of four
  extra JVM warmups. Targeted `--tests` runs fit in one fork and pay nothing.

expected: full suite under the exact load that killed 3/3 guard-only runs | actual: ratio 1,
gate PASS - the bounded worker holds the commit peak. PASS.

## Last Audit

**Date:** 2026-07-28. **Verdict:** Verified.

- Root cause proven from daemon logs: host commit exhaustion under emulator/sibling load kills
  the single long-lived worker near the suite's end via a native allocation failure (exit 10);
  Robolectric's per-class sandbox classloaders make the peak grow with class count, outside
  `-Xmx` reach.
- Fix layers: Robolectric guard in `LoggingHelper.initialize` (removes the file-logging
  executor and its churn from unit tests) + `forkEvery = 100` in
  `app_v2/build.gradle.kts` testOptions (caps classloader accumulation per process).
- Validated under the killing load: guard-only 3/3 truncated; with `forkEvery` - ratio 1, gate
  PASS, no new reds (section 8).
- Detection stays owned by S1244's completeness gate in `check-standard-fast.ps1`; any
  recurrence is loud.
