# S1244 - The unit suite dies on OOM half-way and still prints a normal-looking result

**Status:** Archived
**Priority:** 75

## 0. Raw capture

Found on 2026-07-28 while running `.\a.ps1 fu` as the Phase 2 verification of S1220. Out of scope of that ticket (an atlas-slicer crash guard), parked per CLAUDE.md 3.1.

The run ends like this:

```
946 tests completed, 1 failed, 7 skipped
> Task :app_v2:testStandardDebugUnitTest FAILED
BUILD FAILED in 8m 59s
```

That line reads as "the suite ran, one test is red". It is not what happened. Immediately above it:

```
Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "fms-log-io"
   .. x18, across fms-log-io / DefaultDispatcher-worker-25 / DefaultDispatcher-worker-59 / cloud-token-issuer ..
*** java.lang.instrument ASSERTION FAILED ***: "!errorOutstanding" with message can't create name string
    at s\src\java.instrument\share\native\libinstrument\JPLISAgent.c line: 838
Unexpected exception thrown.
org.gradle.internal.remote.internal.MessageIOException: Could not write '/127.0.0.1:57596'.
Caused by: java.io.IOException: Connection reset by peer
```

The test worker JVM exhausted its heap and lost its connection to the Gradle daemon.

## 1. What the truncation actually costs

The run wrote 136 class reports into `app_v2/build/test-results/testStandardDebugUnitTest/`, all stamped with this run's time - so the directory is not a stale mix, it is the whole output of the run.

Sorted, the reports stop at `com.sza.fastmediasorter.data.remote.ftp.*`. Everything alphabetically after that - the entire `domain.*`, `ui.*` and `util.*` space - never executed. `ls | grep -c "\.ui\."` returns 0.

So the reported "946 tests completed" is not the suite; it is however much of the suite fitted in the heap before the worker died. A green-looking `fu` run therefore proves nothing about any test in the second half of the alphabet, and a genuine regression there is invisible.

This is the part that makes it worth a ticket rather than a retry: the failure mode is silent. The build does go red, but it goes red *for the one unrelated assertion failure it happened to reach*, which is exactly the signal an agent or a human is trained to shrug at.

## 1a. The heap the worker actually gets

Answered 2026-07-28 by reading `gradle.properties` and `app_v2/build.gradle.kts`:

- `org.gradle.jvmargs=-Xmx6g` - the **Gradle daemon**.
- `kotlin.daemon.jvm.options=-Xmx4g` - the **Kotlin compile daemon**.
- `testOptions.unitTests` sets `isIncludeAndroidResources`, `isReturnDefaultValues` and two system properties. **No `maxHeapSize`, no `forkEvery`.**

Gradle forks the test worker as its own process and does not inherit `org.gradle.jvmargs`. With no `maxHeapSize` the worker takes Gradle's default of **512 MB** - for ~200 Robolectric classes sharing one JVM. The two generous heap settings in this repo both apply to processes that are not the one that died, which is why the gap survived: the file looks well-tuned.

That makes the first move an experiment, not a guess: raise the worker heap and see whether the suite completes. If it does, this was a config gap. If it still dies, there is a leak and the OOM was telling the truth.

## 2. Open questions for research

- Which test leaks? The OOM surfaces in `fms-log-io` and `DefaultDispatcher-worker-*` threads, i.e. app-owned background machinery still alive after its test finished. A test that starts app scopes and never tears them down would accumulate across ~136 Robolectric classes in one worker.
- Is the worker heap configured at all? `testOptions.unitTests` in `app_v2/build.gradle.kts` sets `isIncludeAndroidResources` / `isReturnDefaultValues` / two system properties, and no `maxHeapSize` - so the worker runs on the Gradle default (512 MB), which is small for a Robolectric suite of this size.
- Should `forkEvery` bound the damage? Recycling the worker every N classes caps accumulation without finding the leak, at a wall-clock cost. Worth measuring against the current ~9 min.
- Raising the heap alone would hide a real leak rather than fix it. Decide deliberately which of the two this ticket is.

## 3. Why this is not "just raise the heap"

Two distinct defects are tangled here and both should be named:

1. The suite cannot complete (resource exhaustion).
2. When it fails to complete, it says so only in a stack trace mid-log, while its summary line claims a complete-looking result.

Even after (1) is fixed, (2) stays dangerous: any future worker death produces the same misleading summary. A check that a run's class-report count matches the expected class count - or that the last executed package is not suspiciously early - would turn a silent truncation into a loud one.

## Goal

Полный прогон юнит-тестов должен доходить до конца - сейчас он умирает на трети и печатает при этом строчку, неотличимую от нормального результата. И если он всё же оборвётся, это должно быть видно сразу, а не только тому, кто вручную сверит список отчётов с составом исходников.

## Phase 1 - Give the test worker a heap sized for the suite

- [x] `app_v2/build.gradle.kts`, `testOptions.unitTests.all { }` - `it.maxHeapSize = "2g"`, with the comment naming both settings that do **not** reach this process.
- [x] No `forkEvery` added.
- **Verification:** PASS, and by a wide margin.

| | before | after |
| --- | --- | --- |
| tests completed | 946 | **2960** |
| class reports | 136 | **409** |
| reports in `ui.*` / `domain.*` / `util.*` | 0 / 0 / 0 | **82 / 125 / 19** |
| `OutOfMemoryError` occurrences | 18 | **0** |
| wall clock | 8m 59s | 4m 21s |

The suite was running at **32% of its own size** and reporting it as a result. It is also now *faster*: the dying worker spent minutes thrashing a full heap before losing its connection.

Phase 3 is therefore not needed - with a sane heap there is no OOM at all, so this was a configuration gap and not a leak.

## Phase 2 - Make a truncated run impossible to mistake for a finished one

- [x] `scripts/quality/assert-test-suite-complete.ps1` - compares the test classes in `src/test` against the JUnit XML reports the run actually wrote. It does not parse the log, because the misleading line comes from Gradle itself.
- [x] Names the packages that produced no reports, not just a count.
- **Verification:** both directions, which matters - a gate only ever seen green is not evidence.
  - Complete run: `409 report(s) for 407 *Test.kt file(s) (ratio 1)` -> `PASS`, exit 0.
  - Truncated run, simulated by pointing it at a report set containing only `data.*`: `160 report(s) .. (ratio 0.39)` -> `TRUNCATED`, exit 1, and it lists `core.init`, `core.input`, `core.launcher`, .. as the packages with sources but no reports.

Deliberately not an exact-equality check: abstract bases, helpers and `@Ignore`d classes legitimately produce no report, and a truncation loses whole *packages*. The floor is a ratio plus the missing-package list, which is what distinguishes "one helper file" from "the second half of the alphabet".

- [x] Wired into `scripts/builders/check-standard-fast.ps1`, which is what `.\a.ps1 fu` runs.

Not wired into `post-change.ps1`: that facade does not run the unit suite, so the check would have nothing fresh to read. It belongs next to a `.\a.ps1 fu` invocation, which is where it now sits.

The wiring has one non-obvious constraint, and getting it wrong would have made the gate decorative. A truncated run **always** ends non-zero, because the worker process dies - so the check must run *before* the script's exit-code bail-out. Placed after it, the single case the gate exists to catch would exit first and never reach it. It is also skipped when `-Tests` is passed: under a filter, a handful of reports is the correct outcome, not a truncation.

## Phase 3 - Only if Phase 1 does not finish

Not needed. Zero `OutOfMemoryError` at 2 GB, so there is no leak to hunt. Left in place as the record of what would have been done had the heap not been the answer.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1220, S1245, S1246
- **UI scope:** none - build configuration and a developer-facing check.
- **Flavor scope:** all - `testOptions` is not flavor-specific.

## Last Audit

2026-07-28. What the fix immediately exposed is the strongest argument that this ticket mattered.

The truncated run reported **1** failure. The complete run reports **8**, all of which were already broken and none of which anyone could see:

- `CameraCaptureSaverTest` - the only one previously visible. Parked as **S1246**.
- `StreamLogoAtlasSlicerTest` x3 - stale grid contract. Parked as **S1245**.
- `StereoDetectorUserInitiatedTest > userInitiated tall 1024x2048 with no tokens returns OU` - **a regression shipped by S1229 earlier in this same session**, in the `ui.player` package the truncated suite never reached. Parked as **S1249**.
- `IconInventoryExportTest`, `ExecuteScheduledOperationUseCaseTest`, `CameraRuntimeCapabilitiesTest` - three pre-existing failures never triaged because they were invisible. Parked together as **S1250**, scoped as one triage pass rather than split blind: which are stale expectations and which are real defects is exactly what has not been determined yet.

The S1229 case is the point of the whole ticket in miniature: a change was made, its own new tests passed, the full suite was run, it went red for an unrelated reason, and the regression it had just introduced was in the part of the alphabet the worker never reached. Nothing in that sequence looks like negligence, and the defect still shipped.

**Residual risk:** 2 GB is a floor chosen to clear the current suite with headroom, not a measurement of peak usage. A suite that keeps growing will find it again - and now the Phase 2 check will say so out loud instead of printing a plausible number.

### Re-audit 2026-07-28 (`/spec-next` round 2) - Verified

Phase 1 reproduces exactly. A fresh `.\a.ps1 fu` gives `2960 tests completed, 8 failed, 17 skipped` and `409 report(s) for 407 *Test.kt file(s) (ratio 1)` -> `PASS`, with **zero** `OutOfMemoryError` in the full log. Those are the same figures this spec recorded, produced seven hours later on a different session, so the heap fix is real and not a lucky run. The eight failures are the eight named above, unchanged and each already parked.

The gate earned both its verdicts on real data this session rather than on a simulation, which is better evidence than the spec originally had. An earlier run the same morning truncated at 381 reports and the gate returned `ratio 0.94` -> `TRUNCATED`, naming the seven packages that produced nothing; the later complete run returned `ratio 1` -> `PASS`.

Two changes came out of the re-audit.

- **The gate was wired in.** As written, Phase 2 delivered a script that nothing invoked - not `a.ps1`, not `post-change.ps1`, not CI. Its only caller was a sentence telling a human to remember it. That does not meet this ticket's own goal, since the failure mode being fixed is precisely that a plausible summary gets waved through by someone who is not looking. It now runs inside `check-standard-fast.ps1` on every unfiltered unit run. See Phase 2 for the ordering constraint that makes it work.
- **The truncating run was parked as S1253.** It died with `Process 'Gradle Test Executor 2' finished with non-zero exit value 10` and a reset socket, with no `OutOfMemoryError` anywhere - a different mechanism from the heap starvation this ticket diagnosed, at a different point in the suite. It is intermittent: one truncation in two consecutive runs. That belongs to its own research rather than reopening this one.

The S1253 case is also the clearest argument that this ticket paid off. The truncated run printed the *same eight failures* as the complete run, so its summary line was indistinguishable from a normal result - and the gate caught it anyway.

## 4. Related

- S1220 (atlas slicer crash guard) - the ticket whose verification hit this. Its own tests had to be run through `--tests "*AtlasSlicerTest"` because the full suite never reached the `ui` package.
