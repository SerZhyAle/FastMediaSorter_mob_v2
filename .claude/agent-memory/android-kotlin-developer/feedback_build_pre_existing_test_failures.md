---
name: feedback_build_pre_existing_test_failures
description: When testStandardDebugUnitTest fails due to pre-existing failures unrelated to current spec, do not hard-stop - verify own changes via XML reports and use assembleStandardDebug for Phase Done compile checks
metadata:
  type: feedback
---

When working a spec where `testStandardDebugUnitTest` exits non-zero due to **pre-existing test failures** in classes unrelated to the spec's edits - do NOT treat that as a hard stop.

**Why:** the project carries known broken unit tests across many areas (e.g. `StructuredMediaSnifferTest`, `MouseEventHandlerTest`, `SupportIntentFactoryTest`, `CommandPanelLayoutPlannerTest`, `NetworkErrorMessageMapperTest`, `ProvisionDefaultResourcesUseCaseTest` and others) that are tech debt unrelated to most specs. Stopping the spec to fix them blocks unrelated work indefinitely. User confirmed this policy on 2026-05-15 during S0209 Phase 01 Step 01.2.

**How to apply:**
- After running `.\a.ps1 dq` or `testStandardDebugUnitTest` and getting failures, read `app_v2/build/test-results/testStandardDebugUnitTest/TEST-<fqcn>.xml` for the test classes that exercise the code I just touched. The `tests=N failures=0 errors=0` line on that testsuite element is the authoritative PASS signal for the work in scope - the global red verdict is not.
- For compile-only validation after a non-trivial `.kt` edit, prefer `.\scripts\builders\build-debug.PS1 -Task assembleStandardDebug` (or whichever flavor the change targets) over running the full test task. Exit 0 means compile is green.
- Do NOT silently mark a step done if [[debug-tag-invariant]] is broken or if a test class I just added has a failing case - those are real failures owned by this task.
- In the post-change dev-log description, record "Pre-existing failures in N classes unrelated to <feature>; own tests <fqcn> XML report: tests=X failures=0 errors=0" so the next session sees the rationale.
