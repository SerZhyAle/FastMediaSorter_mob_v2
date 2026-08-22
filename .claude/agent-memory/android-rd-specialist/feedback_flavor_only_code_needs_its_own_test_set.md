---
name: flavor-only-code-needs-its-own-test-set
description: A unit test for a class in a flavor-mounted source set (launcherEnabled, networkMonitor, vr, ocrEnabled..) must live in the matching test source set, never in the shared src/test.
metadata:
  type: feedback
---

A test that references a class from a **flavor-mounted** source set goes in that flavor's own test set, not in
`app_v2/src/test`. Measured pairs in `app_v2/build.gradle.kts`: `src/launcherEnabled` ↔
`src/testLauncherEnabled` (mounted by `testStandard` and `testNoLegal` only), `src/networkMonitor` ↔
`src/testNetworkMonitor`, `src/vr` ↔ `src/testVr`, and the cloud/streaming pairs above them.

**Why:** `src/test` compiles for **every** flavor, so a reference to a class only two flavors mount breaks
unit-test compilation on the other four - the S1450 shape. S1498 added `testLauncherEnabled` for exactly this
reason, and records that the arithmetic had previously been pushed into `src/main` just to stay testable,
duplicating a constant to get there.

**How to apply:** before writing a test, check which source set the class under test lives in. Under
`src/<flavor>/java` -> write the test under `src/test<Flavor>/java` with the same package path, and if that
directory is not yet mounted in `build.gradle.kts`, mount it in the same change. Only code under
`src/main/java` may be tested from `src/test`. The same rule decides where a test *fixture* goes.

**Related:** [[write-detekt-clean-first-time]] - the other thing to settle before writing the first line of a
new Kotlin file.
