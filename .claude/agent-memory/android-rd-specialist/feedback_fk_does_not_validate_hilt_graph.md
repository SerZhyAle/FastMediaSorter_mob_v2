---
name: fk-does-not-validate-hilt-graph
description: a.ps1 fk/fkn compile Kotlin only - a broken or variance-mismatched Dagger binding passes them and fails later at hiltJavaCompile
metadata:
  type: feedback
---

**A green `.\a.ps1 fk` / `fkn` is NOT evidence that a DI change works.** Those targets run `compileStandardDebugKotlin` (resp. noLegal) and stop. Dagger validates the graph in `hiltJavaCompile`, which only runs in a fuller build - `fc`, `d`, or any unit-test target. So a `MissingBinding` can sit behind two "BUILD SUCCESSFUL" verdicts.

**Why:** cost one wasted verification round on S1170. Both flavors compiled clean; the very next unit-test run failed with `@HomeWidgetGadgets List<? extends LauncherGadget> cannot be provided`. Claiming the phase verified on `fk` alone would have been a completion claim without proof (CLAUDE.md 12).

**How to apply:** whenever a change adds/edits an `@Provides`, `@Binds`, a qualifier, a multibinding, or an `@Inject constructor` parameter, escalate past `fk` to something that runs kapt+hilt to completion before calling it verified. A targeted `check-standard-fast.ps1 -Mode Unit -Tests "*Something*"` is usually the cheapest such proof and doubles as a behaviour check.

**The specific trap that bit:** Kotlin compiles a `List<Foo>` constructor parameter to Java `List<? extends Foo>`, which Dagger keys differently from the `List<Foo>` a module provides. Collection injection points need `List<@JvmSuppressWildcards Foo>` / `Set<@JvmSuppressWildcards Foo>` - the repo already does this in `ResolvePanelRouteAvailabilityUseCase` for `Set<@JvmSuppressWildcards ScreenVideoRecordingController>`. Copy that shape for any injected collection.

Related: [[verify-subagent-build-failures]], [[constructor-change-compile-tests]].
