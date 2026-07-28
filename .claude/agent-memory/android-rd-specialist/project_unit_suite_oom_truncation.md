---
name: unit-suite-oom-truncation
description: .\a.ps1 fu dies of OutOfMemoryError mid-run and still prints a complete-looking "N tests completed" line - the ui/domain/util packages never execute
metadata:
  type: project
---

As of 2026-07-28, `.\a.ps1 fu` (`testStandardDebugUnitTest`) cannot complete. Its worker JVM exhausts the heap around `com.sza.fastmediasorter.data.remote.ftp.*` and loses the daemon connection, so everything alphabetically after that - the whole `domain.*`, `ui.*`, `util.*` space - never runs. Ticketed as **S1244**.

**Why this is dangerous rather than merely annoying:** the run still prints `946 tests completed, 1 failed, 7 skipped` and fails the build on whatever unrelated assertion it happened to reach. That summary reads exactly like a complete run with one known-red test - the signal everyone is trained to shrug at. The OOM is visible only as a stack trace mid-log.

**How to apply:**
- Never treat `fu` exit 0 (or its summary line) as evidence about a test in the second half of the alphabet. Confirm coverage: `ls app_v2/build/test-results/testStandardDebugUnitTest/ | grep "<your.package>"` - absent means it never ran, not that it passed.
- To verify specific classes, run them filtered: `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*YourTest"`, then read `tests=/failures=/errors=` off the JUnit XML.
- All reports in that directory carry the current run's timestamp, so a mixed-age directory is not the explanation - check the *set* of classes, not their mtimes. Compare with [[stale-test-results-xml]], which is the different trap.
- Known unrelated reds meanwhile: `CameraCaptureSaverTest` (S1246), `StreamLogoAtlasSlicerTest` (S1245, asserts a retired grid). See [[build-pre-existing-test-failures]] for the standing policy on those.
