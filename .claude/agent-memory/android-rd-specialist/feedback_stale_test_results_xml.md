---
name: stale-test-results-xml
description: test-results XML survives failed/killed gradle runs - read BUILD verdict + report mtime before trusting test counts
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
