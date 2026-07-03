---
name: build-pre-existing-test-failures
description: When testStandardDebugUnitTest fails due to pre-existing failures unrelated to current spec, do not hard-stop - verify own changes via XML reports and use assembleStandardDebug for Phase Done compile checks
metadata:
  type: feedback
---

When working a spec where `testStandardDebugUnitTest` exits non-zero due to **pre-existing test failures** in classes unrelated to the spec's edits - do NOT treat that as a hard stop.

**Why:** the project carries known broken unit tests across many areas (e.g. `StructuredMediaSnifferTest`, `MouseEventHandlerTest`, `SupportIntentFactoryTest`, `CommandPanelLayoutPlannerTest`, `NetworkErrorMessageMapperTest`, `ProvisionDefaultResourcesUseCaseTest` and others) that are tech debt unrelated to most specs. Stopping the spec to fix them blocks unrelated work indefinitely. User confirmed this policy on 2026-05-15 during S0209 Phase 01 Step 01.2.

**Worse case - a pre-existing test that does not COMPILE blocks the whole unit-test source set:** as of 2026-06-11, `domain/translation/TranslationLanguageCodeMapperTest.kt` references `com.google.mlkit.nl.translate.TranslateLanguage`, which is not on any flavor's unit-test classpath (ML Kit lives in the `:translate_feature` module). So `compile<Flavor>DebugUnitTestKotlin` FAILS for **every** flavor (standard/legacy/etc), and NO XML reports are generated - you cannot run even an isolated `--tests "*MyNewTest"`. When this happens, verify new tests by reading the test source + confirming the production code builds green via `assemble<Flavor>Debug`; do not treat the inability to run as your own failure.

**How to apply:**
- For per-step test verification: read the JUnit XML report under `app_v2/build/test-results/testStandardDebugUnitTest/TEST-<fqcn>.xml`. The `tests=N failures=0 errors=0` line on that report's testsuite element is the authoritative PASS signal for the spec's own tests.
- For Phase Done Criteria "Project compiles" check: prefer `.\scripts\builders\build-debug.PS1 -Task assembleStandardDebug` (compile only, no tests) over running the full test task. Exit 0 means compile is green.
- Do NOT silently mark a step done while [[debug-tag-invariant]] is broken or while my own test class has a failing case - those are real failures.
- Document each occurrence in the Step Log: "Pre-existing failures in N classes unrelated to S<id>; own tests <fqcn> XML report: tests=X failures=0 errors=0".
