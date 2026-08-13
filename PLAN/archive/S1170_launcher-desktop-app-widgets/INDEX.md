# Tactical Plan: S1170 - launcher-desktop-app-widgets

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Research inputs:** [`research/01__widget-tap-targets-vs-launcher-commands.md`](research/01__widget-tap-targets-vs-launcher-commands.md)
**Feature:** The app's own home-screen widgets, placeable on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 5 / 7 done (05 and 07 partial - see their files)
**Last updated:** 2026-07-30

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | free-slot-placement | - | ✅ Done | 2/2 | [PHASE_01__free-slot-placement.md](PHASE_01__free-slot-placement.md) |
| 02 | internal-routes | - | ✅ Done | 2/2 | [PHASE_02__internal-routes.md](PHASE_02__internal-routes.md) |
| 03 | widget-gadget-bridge | 02 | ✅ Done | 4/4 | [PHASE_03__widget-gadget-bridge.md](PHASE_03__widget-gadget-bridge.md) |
| 04 | list-gadgets | 03 | ✅ Done | 2/2 | [PHASE_04__list-gadgets.md](PHASE_04__list-gadgets.md) |
| 05 | stateful-gadgets | 03 | 🚧 In Progress | 1/3 | [PHASE_05__stateful-gadgets.md](PHASE_05__stateful-gadgets.md) |
| 06 | settings-entry-point | 01, 03 | ✅ Done | 3/3 | [PHASE_06__settings-entry-point.md](PHASE_06__settings-entry-point.md) |
| 07 | docs-catalog-cleanup | all | 🚧 In Progress | - | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Contract shared by every phase

- **Where code lives.** Every gadget class and its layouts go in `app_v2/src/launcherEnabled/` (mounted by `standard` and `noLegal`, no gradle property of its own). Nothing launcher-specific may appear in `src/main` behind a `BuildConfig` guard. The only `src/main` changes this plan allows are the widget-catalog key field, the new internal routes and the desktop repository - all flavor-neutral.
- **Gadget key.** `HomeWidgetEntry` has no string id; identity is `providerClass`. Phase 03 adds an explicit `gadgetKey` field rather than deriving one from the class name, which R8 and renames would silently break.
- **One class, many registrations.** The nine mechanical widgets are one parameterised gadget registered nine times, not nine near-identical classes. The split across phases is 9 mechanical (Phase 03) + 2 list (Phase 04) + 3 behavioural (Phase 05) = 14.
- **Registry composition.** `LauncherGadgetRegistry` takes five constructor gadgets today. Phase 03 must add the home-widget set as a single injected collection - growing the constructor to nineteen parameters would trip detekt's `constructorThreshold` of 10.
- **Span parity.** A cell's default span comes from the corresponding widget's declared size so the desktop matches the Android home screen (strategic §3).

---

## Pre-Implementation Blockers

None. Both strategic §4 research items were closed by the owner on 2026-07-27, and the technical unknown they left (how each widget's tap maps onto the launcher command model) is answered in `research/01`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; the public showcase is `/skill-release`-owned. The capability goes to `docs/ALL_FEATURES.jsonl` in Phase 07.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this plan adds public types.
- [ ] New strings exist in EN/RU/UK and `scripts/check_strings_localized.ps1` exits 0.
- [ ] `/spec-check S1170` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S1170`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`, from `research/01`.
- 2026-07-30 - Phases 01-03 implemented and verified. Three corrections the plan needed, each recorded in its own phase file:
  - Phase 01 needed a bound for the free-slot scan, added as the DAO query `firstRowBelowAll` - a `@Query` only, no schema change.
  - Phase 02 was missing `ResolvePanelRouteAvailabilityUseCase` from its file list. That omission was not cosmetic: its `when` ends in `else -> unavailable` and `ExecuteLauncherCommandUseCase` refuses to launch anything not launchable, so all nine Phase 03 gadgets would have compiled, appeared, and silently done nothing. Phase 02 also needed no new strings at all - every one of the five destinations already ships a trilingual widget label - and forced a regeneration of `docs/icons/icon-inventory.json`, which is scanned from `InternalRouteCatalog`.
  - Phase 03's "none of the five excluded keys appears in the module" predicate was too literal; it is now "none is registered", so the KDoc may keep explaining the 9 + 2 + 3 = 14 split.
- 2026-07-30 - Phases 04 and 06 implemented and verified; 05 and 07 partial. Further corrections, each in its own phase file:
  - Phase 04 needed a command kind that did not exist (`FavoriteFile`) for a row tap to open the tapped file, and it exposed a live bug: the scheduled-op confirmation sat in `onCellTapped` instead of `run`, so the same operation pinned to the taskbar or opened from the Start menu answered "cannot open". Moved to `run`.
  - Phase 05's real cost is the CONFIGURATION ACTIVITY, which the plan never mentions. The store re-key was the easy half; `RandomPhotoFrameConfigActivity` and `CameraQuickCaptureConfigActivity` are keyed on `EXTRA_APPWIDGET_ID` end to end. Their two gadgets are therefore not registered - 12 of 14 keys resolve.
  - Phase 06 had to move the gadget spans into `HomeWidgetEntry`, because the placing code is in `src/main` and the gadget registry ships only in `src/launcherEnabled`. The declared `targetCellWidth`/`targetCellHeight` are the source, not the `minWidth`/`minHeight` dp pair.
  - `a.ps1 fk`/`fkn` do NOT validate the Dagger graph - they stop at `compileStandardDebugKotlin`. Every phase that adds a binding must verify with a target that runs kapt+hilt to completion.
