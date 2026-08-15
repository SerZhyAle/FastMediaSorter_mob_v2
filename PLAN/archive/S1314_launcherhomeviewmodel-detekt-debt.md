# S1314 - LauncherHomeViewModel carries 14 constructor dependencies against a limit of 10

**Status:** Archived
**Priority:** 35
**Tactical plan:** `PLAN/S1314_launcherhomeviewmodel-detekt-debt/INDEX.md`

<!-- discovered by /spec-dev S1087 - 2026-07-30, parked per CLAUDE.md 3.1 -->

## 0. Raw capture

Surfaced while closing S1087 phase 02: the diff-scoped detekt gate flagged three findings in
`LauncherHomeViewModel.kt`, a file that ticket only added one `StateFlow` property to.

Measured on 2026-07-30 against `config/detekt/baseline-app_v2.xml`:

- `LongParameterList - 11/10 - [LauncherHomeViewModel] at LauncherHomeViewModel.kt:64:48`
- `NoUnusedImports - LauncherHomeViewModel.kt:41` - `kotlinx.coroutines.flow.onEach`, 0 usages
- `ImportOrdering - LauncherHomeViewModel.kt:3` - `domain.usecase.ExecuteScheduledOperationUseCase`
  sat between two `domain.usecase.launcher.*` imports
- The baseline contains **zero** entries for this file, so none of the three was ever frozen.

The last two were mechanical and were fixed inside S1087 (a touched file must leave the gate green).
`LongParameterList` is structural and is what this ticket is for.

## 1. Why it is parked rather than fixed

Cutting the constructor means grouping injected dependencies behind a facade or splitting the
ViewModel, which is a design change to a 405-line class that S1087 only reads. Doing it inside a
ticket whose device test was already queued would put an untested refactor in front of that test.

Re-freezing the baseline (`:app_v2:detektBaseline`) is not an option either: it re-freezes the whole
project from a dirty working tree, so it would silently swallow every other in-flight ticket's new
findings along with this one.

## 2. Scope sketch (to be settled at Approval)

- Which dependencies actually belong together: the four launcher repositories/use-cases that serve the
  desktop, versus the settings/streams/scheduled-operation trio that serve the taskbar and gadgets.
- Whether a `LauncherHomeDependencies`-style holder is the pattern this repo wants, or whether the
  cheaper answer is moving the gadget/scheduled-operation surface into its own ViewModel.
- Confirm the fix leaves the file green with no `@Suppress` (Rule 19: a suppression on a method that
  already has a baselined finding shifts the baseline signature and surfaces new ones).

## 3. Fix direction

Group ten of the fourteen constructor dependencies into three Hilt-constructible holder classes,
leaving seven direct parameters. The ViewModel's public API does not change, so none of its five
consumers is edited.

Holders rather than a ViewModel split. `run(command)` is the single dispatch entry for every
launcher surface - eight gadget classes via `LauncherGadgetHost`, plus `LauncherHomeActivity` and
`LauncherStartMenuFragment` - and it owns the `launchInFlight` re-entry guard together with the
`ScheduledOp` confirmation branch that S1170 deliberately centralised there. Splitting by surface
would duplicate that single-instance state, which is the one invariant the file exists to hold.
The holder shape already has a precedent in this repo:
`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerDependencies.kt`.

### 3.3 Owner inputs (Approval gate)

- **Scope:** ten of fourteen constructor dependencies move into three holders, leaving seven direct
  parameters against a threshold of ten.
- **Measurement correction:** the constructor carries **14** parameters, not the 11 recorded in §0 on
  2026-07-30 - S0427 and S1176 each added dependencies since. Verified 2026-07-31 by reading the
  constructor at `LauncherHomeViewModel.kt:70-84` and the threshold at `config/detekt/detekt.yml:19`.
- **Pattern:** dependency holders, not a ViewModel split - rationale in §3 above.
- **Flavors:** the new `LauncherHomeDependencies.kt` lands in `src/launcherEnabled/java/`, mounted by
  `standard` and `noLegal`; `lite`, `photos`, `legacy` and `vr` mount `src/launcherDisabled/` and
  never see it.
- **Constraint:** no `@Suppress` and no edit to `config/detekt/baseline-app_v2.xml` - the baseline holds
  zero entries for this file today (verified by grep) and must keep holding zero.
- **Related tickets:** S1087 (surfaced it; fixed the two mechanical findings in the same file);
  S1103, S1170, S1176 (all `BlockNeedUserTest` - their `Timber.d("Sxxxx: ..")` probes live in this file
  and must survive the refactor); S1198, S1247, S1311, S1328, S1329 (sibling detekt-debt tickets).

---

## 4. Related

- **S1087** - surfaced it; fixed the two mechanical findings in the same file.
- **S1198**, **S1247**, **S1311** - the other parked detekt-debt tickets, same shape. Correction to the
  original note: they are not all in package 34 - `PLAN/RELEASE_QUEUE.md` places S1314 in package 30,
  S1247 in 32, and S1198 and S1311 in 34.

---

## Last Audit

**2026-07-31 - Verified.** Implemented and audited inside `/spec-next`. The constructor went from 14
parameters to 7 by moving ten dependencies into `LauncherDesktopDependencies`,
`LauncherTaskbarDependencies` and `LauncherShortcutDependencies`, all three plain classes with an
`@Inject constructor` in `src/launcherEnabled/java/`.

Evidence, in the order it was gathered:

- Every call site moved: `desktopDependencies.` 10, `taskbarDependencies.` 6, `shortcutDependencies.` 4 -
  20 in total, matching the plan's measured cluster map line for line.
- `.\a.ps1 d` - BUILD SUCCESSFUL, and the log shows `hiltJavaCompileStandardDebug` ran. A Kotlin-only
  check would not have proved anything here: Dagger validates the graph in that task, not in
  `compileStandardDebugKotlin`.
- `.\a.ps1 fkn` - BUILD SUCCESSFUL. `noLegal` is the second flavor mounting `src/launcherEnabled/`, so a
  green `standard` does not cover it.
- Diff-scoped detekt over both touched files - `PASS [scoped]`, and `LauncherHomeViewModel` now returns
  zero findings in the fresh project report. The count of files carrying new findings project-wide fell
  from 168 to 167. `config/detekt/baseline-app_v2.xml` holds zero entries for this file before and after,
  and the file carries no `@Suppress`: the finding was cleared by restructuring, not absorbed.
- Phase-boundary audit, aimed at the one risk the plan named - a holder that compiles but fails to
  construct. Resolved with generated code rather than opinion: kapt emitted
  `LauncherDesktopDependencies_Factory`, `LauncherTaskbarDependencies_Factory` and
  `LauncherShortcutDependencies_Factory`, and `LauncherHomeViewModel_Factory` declares exactly seven
  `Provider<..>` fields in the new parameter order. Dagger resolved every binding.
- `LauncherHomeActivity` was **not** launched on the device, and deliberately so: it is declared
  `android:enabled="false"` and only switched on when the user enables launcher mode. Forcing it up with
  `pm enable` would have left the emulator in a half-configured launcher state and risked a crash from
  that misconfiguration being read as a defect of this refactor. Compile-time Dagger validation is the
  right rung of the evidence ladder for a change whose only failure mode is an unresolvable binding.

No P0/P1 findings. `LauncherHomeViewModel`'s public API is unchanged, so the five consumers listed in the
tactical INDEX were not touched and need no regression review. The file shrank from 450 lines to 436.
