# Tactical Plan: S1314 - launcherhomeviewmodel-detekt-debt

**Strategic spec:** [`../S1314_launcherhomeviewmodel-detekt-debt.md`](../S1314_launcherhomeviewmodel-detekt-debt.md)
**Research inputs:** none
**Feature:** LauncherHomeViewModel constructor restructuring (detekt `LongParameterList`)
**Tier:** Tech debt
**Priority:** 35
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Measured baseline (2026-07-31)

Strategic §0 recorded `11/10` on 2026-07-30. The working tree has moved since: the constructor now
carries **14** parameters against the same threshold of 10.

- `config/detekt/detekt.yml` - `complexity.LongParameterList.constructorThreshold: 10`, `functionThreshold: 8`.
- `config/detekt/baseline-app_v2.xml` - zero entries for `LauncherHomeViewModel` (strategic §0 claim confirmed).
- `app_v2/build/reports/detekt/detekt.xml` - one finding at `LauncherHomeViewModel.kt:71:48`, listing all 14 parameters.
- No `.editorconfig` at repo root and no `MaxLineLength` override, so detekt's default ceiling of 120 chars applies.

Target after this plan: **7** constructor parameters.

---

## Design decision (resolved by measurement, ratified in strategic §3.3)

Strategic §2 left two options open: a dependency-holder class, or splitting the ViewModel by surface.
The measured usage clustering decides it in favour of holders:

- `run(command)` is the single dispatch entry for every launcher surface - 8 gadget classes reach it
  through `LauncherGadgetHost.run`, plus `LauncherHomeActivity` (2 sites) and `LauncherStartMenuFragment`
  (3 sites). It owns two pieces of single-instance state: the `launchInFlight` re-entry guard and the
  `LauncherCellCommand.ScheduledOp` confirmation branch that S1170 deliberately moved here from
  `onCellTapped`. Splitting the ViewModel by surface would duplicate that guard or force cross-ViewModel
  coordination - a regression in exactly the invariant the file documents as "one guard for all".
- The holder pattern has repo precedent: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerDependencies.kt`
  declares `VideoPlayerHostDependencies` / `VideoPlayerNetworkDependencies` / `VideoPlayerStoreDependencies`,
  consumed by `VideoPlayerManager` and built in `PlayerViewerFactory`. This plan mirrors that shape and naming.
- The holder pattern needs no new Hilt `@Module`: a class with `@Inject constructor` is constructible
  by the graph on its own.
- Holders keep the ViewModel's public API byte-identical, so none of the five consumers change.

Difference from the precedent: the precedent's holders are `data class` because they are built by hand and
destructured in tests. These are built by Hilt and never compared or copied, so they are declared as plain
`class`. `data class` would additionally silence future growth inside a holder - detekt's default
`LongParameterList.ignoreDataClasses` is `true` - and this ticket exists because a finding was hidden, not
because it was noisy.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | dependency-bundles | - | ✅ Done | 4/4 | [PHASE_01__dependency-bundles.md](PHASE_01__dependency-bundles.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner ratification:** strategic §2 defers the pattern choice to Approval. Phase 01 must not start
      until strategic §3.3 records the owner's decision on the holder pattern and the three cluster boundaries.
      Cleared 2026-07-31 without an owner prompt. Section 3.3 records the pattern, the flavor
      placement, the cluster count and the no-Suppress constraint, and `check-owner-inputs.ps1 -Id S1314`
      returns exit 0: "All 6 Owner Input field(s) in 3.3 are filled". The spec is already past Approval -
      it sits at `Tactical`, a status reachable only through that same gate. The pattern is not a new
      invention needing a ruling either: `ui/player/VideoPlayerDependencies.kt` is the shipped house
      precedent this phase mirrors, so the architecture already answers the question.

---

## Consumers - none require edits

The refactor is constructor-internal. These five files reference `LauncherHomeViewModel` and its public API
stays unchanged, so they are listed as a regression surface, not as files to touch:

- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` (`by viewModels()`)
- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` (`by activityViewModels()`)
- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt` (constructor parameter)
- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResizeManager.kt` (constructor parameter)
- `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt` (constructor parameter)

No unit or instrumentation test references `LauncherHome*`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped: the strategic spec carries no FEATURES sentence and
      this change ships no user-visible capability.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - the three holder classes are new public types.
- [x] `/spec-check S1314` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1314`.

---

## Blockers Log

- 2026-07-31 - Phase 01 blocker cleared: section 3.3 filled and `check-owner-inputs.ps1` green; the holder pattern follows the existing `VideoPlayerDependencies` precedent. Phase 01 started.

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.