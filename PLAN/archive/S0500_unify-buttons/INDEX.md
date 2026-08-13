# Tactical Plan: S0500 - unify-buttons

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Research inputs:** [`research/01__button-inventory.md`](research/01__button-inventory.md)
**Feature:** Unify button widgets and styles across app_v2 layouts
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-06-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | button-style-family | - | ✅ Done | 2/2 | [PHASE_01__button-style-family.md](PHASE_01__button-style-family.md) |
| 02 | settings-button-consolidation | 01 | ✅ Done | 4/4 | [PHASE_02__settings-button-consolidation.md](PHASE_02__settings-button-consolidation.md) |
| 03 | dialog-sheet-migration | 01 | ✅ Done | 4/4 | [PHASE_03__dialog-sheet-migration.md](PHASE_03__dialog-sheet-migration.md) |
| 04 | misc-surface-migration | 01 | ✅ Done | 4/4 | [PHASE_04__misc-surface-migration.md](PHASE_04__misc-surface-migration.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (owner-confirmed 2026-06-18) - see [`research/01__button-inventory.md`](research/01__button-inventory.md) §6.

---

## Out-of-scope (explicit - do not migrate)

- `app_v2/src/main/res/layout/widget_scheduled_tasks.xml` plain `<Button>` (lines 54, 67) - RemoteViews app-widget (`ScheduledTasksWidgetProvider`); `MaterialButton` is not a supported RemoteViews type.
- Camera viewfinder layouts `activity_camera_ocr_translate.xml`, `activity_camera_capture.xml` - intentional dark-on-dark (strategic §6 fork 1; separate theming parked as S0501).
- ~295 player/media `ImageButton` on `?attr/selectableItemBackgroundBorderless` - including the noLegal overlays `item_media_file*.xml`, `bottom_sheet_binary_file.xml` (strategic §6 fork 2).
- ExoPlayer reserved-id controls `custom_player_controls*.xml`, `@id/exo_*` (strategic §6 fork 3).
- `player_draw_overlay_toolbar_content.xml` hex swatches - functional colour values for the draw-tool palette, not role buttons.
- `Widget.FastMediaSorter.Calculator.*` styles in `themes.xml` - distinct calculator-keypad taxonomy with custom insets; not the settings/dialog button "zoo" this spec targets.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8: no user-visible feature).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file batch.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no public Kotlin API change expected; regen to confirm).
- [ ] `/spec-check S0500` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0500`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-18 - Initial tactical plan authored by `/spec-tech`.
