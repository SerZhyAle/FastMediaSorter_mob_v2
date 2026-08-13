# Tactical Plan: S0623 - app-launch-panel-dialog

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Research inputs:** none (decisions captured in strategic §6 + Quiz decisions 2026-06-23)
**Feature:** Действие «Открыть панель программ» (quick-launch panel dialog)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (device test pending)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Design decisions (resolved from architecture + strategic spec, no owner input needed)

- **Panel host:** dispatch runs in a Service context, so a `DialogFragment` cannot be shown directly. A dedicated transparent-themed `AppLaunchPanelActivity` is launched with `FLAG_ACTIVITY_NEW_TASK` (mirrors `ScreenshotGestureActionDispatcher.launchApp`) and hosts `AppLaunchPanelDialogFragment`.
- **Fixed 15-slot grid:** the adapter always submits exactly 15 cells. Filled cells render an app tile; empty cells render a visible "add" placeholder (strategic §6.5 "пустые слоты как приглашение настроить"). Grid is 3 cols x 5 rows portrait, 5 cols x 3 rows landscape.
- **Persistence:** Room entity `AppLaunchPanelTileEntity` + DAO + `Migration35To36` + `@Database(version = 36)`. Heterogeneous tile types survive as a `type` string column; missing packages survive removal (resolved at read-time). Chosen over DataStore because the data is an ordered heterogeneous list (strategic §3.2 durability requirement).
- **Icon/label loading:** resolved on `Dispatchers.IO` inside `ResolveAppLaunchPanelTilesUseCase`; the dialog observes a `StateFlow` and renders once ready. ~15 local package icons is cheap; no Glide pipeline.
- **Missing package policy:** availability checked before show; unavailable tiles are hidden (rendered as empty/add slots), no disabled state, no replace prompt in v1 (strategic §6.3 soft degrade).
- **Edit Panel entry points:** the panel dialog header "Edit" affordance, and tapping an empty slot. No settings-screen row in v1 (extension point).
- **OPEN_PANEL availability:** shown unconditionally in the gesture-action picker wherever the overlay is present (same as `OPEN_APP`); an empty panel still opens.
- **Backup:** panel tile config is excluded from v1 cloud backup (extension point, like `lastUsedResourceId`).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | domain-model-and-room | - | ✅ Done | 6/6 | [PHASE_01__domain-model-and-room.md](PHASE_01__domain-model-and-room.md) |
| 02 | repository-and-mapper | 01 | ✅ Done | 4/4 | [PHASE_02__repository-and-mapper.md](PHASE_02__repository-and-mapper.md) |
| 03 | package-resolution-and-usecases | 01, 02 | ✅ Done | 6/6 | [PHASE_03__package-resolution-and-usecases.md](PHASE_03__package-resolution-and-usecases.md) |
| 04 | edit-panel-screen | 02, 03 | ✅ Done | 6/6 | [PHASE_04__edit-panel-screen.md](PHASE_04__edit-panel-screen.md) |
| 05 | panel-dialog-ui | 03, 04 | ✅ Done | 6/6 | [PHASE_05__panel-dialog-ui.md](PHASE_05__panel-dialog-ui.md) |
| 06 | gesture-action-wiring | 05 | ✅ Done | 5/5 | [PHASE_06__gesture-action-wiring.md](PHASE_06__gesture-action-wiring.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are `Resolved` (Quiz decisions 2026-06-23). No blockers - Phase 01 may start.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - N/A per-spec; FEATURES populated by `/skill-release` from the ALL_FEATURES diff (record added: `screen-capture.edge-gesture-open-app-panel`).
- [x] `dev/CHANGELOG.md` has an entry for every logical change (Phases 01-06).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (2003 records).
- [x] `docs/settings/*` - Rule 22 gate `assert-settings-doc-sync.ps1` green; no manifest change needed (runtime dialog value, not a catalogued setting row).
- [ ] `/spec-check S0623` returns `Verified` (after device test).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` (after device test).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0623`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
- 2026-06-23 - Phases 04-06 implemented (Edit panel screen, quick-launch panel dialog, OPEN_PANEL gesture wiring); `assembleStandardDebug` PASS. Phase 07 sync done (catalog, ALL_FEATURES, dev-log, gates). Ticket -> `BlockNeedUserTest` for on-device gesture->panel->launch/Edit verification.
