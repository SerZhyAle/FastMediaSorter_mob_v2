---
name: stale-test-results-xml
description: test-results XML survives failed/killed gradle runs, and a --tests run writes to a SEPARATE -filtered dir - check verdict, mtime and directory before trusting counts
metadata:
  type: feedback
---

`app_v2/build/test-results/**/TEST-*.xml` is **not** cleared when a run fails, is killed, or dies before the test
task executes. The previous run's file stays on disk with its old counts, so "the report says 2 tests, 0 failures"
can be an artifact of a run that happened hours earlier.

**Why:** hit twice on 2026-07-26/27 while proving an S1026 hotfix.
1. A `fu` run OOMed and died; the XML still showed `tests="2" failures="0"` from a 17:40 run. A newly added test
   was absent, but "not in the FAILED list" read like a pass. It had never executed.
2. A control run failed at `compileStandardDebugKotlin` (an incomplete revert left a dangling reference). The
   command reported the *previous* run's `tests=11 failures=1`, which looked like a valid measurement of the
   reverted code. It measured nothing.

**How to apply:** before quoting any test count, check all three:
- the gradle verdict line (`BUILD SUCCESSFUL` / `BUILD FAILED`) - absence of it means the run did not finish;
- the report file's `LastWriteTime` - it must be from *this* run;
- that the specific test name you care about appears in the XML, and that the total count moved as expected
  (adding one test must take `tests="N"` to `N+1`).

Corollary: "my test is not among the failures" is never evidence it passed. Only its presence in a fresh report is.
Same failure shape as [[post-change-detekt-stale-report]] - cached gate/report artifacts outliving their run.

**Stop deriving the directory - the runner now prints it (2026-08-22, S1946).** `check-standard-fast.ps1
-Mode Unit -Tests ..` ends with a `Reports (this filtered run): <path>` line naming the directory that
actually received the XML, chosen by newest write rather than by rule. Read that line and go there. The
rule below is still worth knowing, but it is **not universal**: measured the same day, the identical call
filtered into `testStandardDebugUnitTest-filtered` on `app_v2` and into the plain `testDebugUnitTest` on
`wear`. A first version of that runner line hard-coded the `-filtered` suffix and therefore printed a
path that does not exist on the watch module - the same class of confident-wrong answer this whole file
is about.

**A filtered run writes to a DIFFERENT directory (2026-08-18, S1797).** `gradlew :app_v2:testStandardDebugUnitTest
--tests "*Foo"` puts its XML in `app_v2/build/test-results/testStandardDebugUnitTest-filtered/`, not in
`testStandardDebugUnitTest/`. The unfiltered directory keeps whatever the last FULL run left there, so after a
targeted run it shows old classes, old counts and old mtimes - and the class you just ran is often absent from it
entirely. Read that and a green `BUILD SUCCESSFUL` looks like it never executed anything. Always read the
`-filtered` directory after a `--tests` run; the mtime check above still applies inside it. The sanctioned
way to run one class is `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*FooTest*"` (it takes
`BUILD.LOCK`; Rule 23 forbids calling `gradlew.bat` yourself, and `a.ps1 fu` has no filter argument). Re-read
this file BEFORE checking a filtered result - on 2026-08-19 I owned this memory, read the unfiltered directory
anyway, and spent two calls concluding a passing test had never run. **Third time on 2026-08-22 (S1920):** same
mistake, four wasted calls, and it escalated - I deleted `build/test-results/testStandardDebugUnitTest/` to force
a rerun that was never stale, destroying another session's full-suite evidence to chase a number that was sitting
in the neighbouring directory. The tell was there from the first read: the count was `N` when the class had `N+1`
tests, i.e. the report described a *different version of the file*, which cannot happen in a run that just
compiled it. When the count is off by exactly the number of tests you added, suspect the directory before
suspecting the run. The index line in `MEMORY.md` now names the `-filtered` directory so this fires before the
first read, not after the fourth.

**Quoting `-P` args (same session).** `-Pchaquopy.enabled=false` unquoted in the PowerShell tool is split by the
parser, and gradle then reads `.enabled=false` as a task name and fails with `Task '.enabled=false' not found`.
Write `"-Pchaquopy.enabled=false"`. The failure is loud, but it looks like a build break rather than a quoting
bug, and the tests never ran - another case of the rule above.
