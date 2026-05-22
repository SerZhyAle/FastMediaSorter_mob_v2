---
name: build-pre-existing-test-failures
description: When reading testStandardDebugUnitTest results, expect ~26 broken pre-existing tests; never cite the aggregate count in a research report - point to per-class XML
metadata:
  type: feedback
---

When the research report consumes results of `testStandardDebugUnitTest`, expect a significant number (≈26 broken classes as of 2026-05-15) of **pre-existing test failures** in areas unrelated to current work. Do NOT cite the aggregate "X tests failed" count in the report as evidence of regression - point to per-class XML reports instead.

**Why:** the project carries known broken unit tests across many areas (e.g. `StructuredMediaSnifferTest`, `MouseEventHandlerTest`, `SupportIntentFactoryTest`, `CommandPanelLayoutPlannerTest`, `NetworkErrorMessageMapperTest`, `ProvisionDefaultResourcesUseCaseTest` and others) that are tech debt. A research report that cites "build has 26 failing tests" as a risk would be technically accurate but operationally misleading - the spec author would lose hours chasing unrelated debt instead of focusing on the spec's scope.

**How to apply:**
- For per-class evidence: read the JUnit XML report at `app_v2/build/test-results/testStandardDebugUnitTest/TEST-<fqcn>.xml`. The `tests=N failures=0 errors=0` line on the `testsuite` element is authoritative PASS for that single class. Cite the XML path and the exact counts in the report's "Test Coverage Summary" section.
- For Phase Done compile checks performed by a writer agent, the canonical signal is `.\scripts\builders\build-debug.PS1 -Task assembleStandardDebug` exit 0 (compile only, no tests). If a research report quotes this signal, label it "compile-only verification, unit-test status separate".
- When listing classes without test coverage in the report's Test Coverage Summary, distinguish "no test class on disk" from "test class exists but fails for a reason unrelated to this spec". The former is a real coverage gap; the latter is tech debt to flag at Low severity.
- Never claim "build is failing" based on aggregate test counts alone - that produces false alarms in the report.
