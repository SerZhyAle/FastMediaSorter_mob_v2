# Tactical Plan: S0378 - system-info-dialog-compact-actions

**Strategic spec:** [`../S0378_system-info-dialog-compact-actions.md`](../S0378_system-info-dialog-compact-actions.md)
**Feature:** Compact icon actions + highlighted scroll area in the shared text dialog (ErrorDialog)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device test)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-07

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Target

- Shared dialog: `ui/dialog/ErrorDialog.kt` + `res/layout/dialog_error_detail.xml` + `res/layout-land/dialog_error_detail.xml`.
- All `ErrorDialog.show(..)` callers inherit the change (system info, errors, player, cloud auth, file ops). No per-caller edits.
- Out of scope: `DialogUtils.showScrollableDialog` (separate utility, separate layout) - not touched.

## Action mapping (after redesign)

- Single horizontal icon row under the text area; the AlertDialog button panel (negative/neutral/positive) is removed.
- Row order left to right: primary, inline, copy, close.
- `btnPrimary`: default = Share icon-only (`ic_share`); when caller passes `actionButtonText`/`onActionClick` = labeled icon+text button (custom CTA hybrid, §6 item 4). Dismisses on CTA.
- `btnInlineAction`: default = Save to file icon-only (`ic_create_text_file`); when caller passes `inlineActionButtonText`/`onInlineActionClick` = Copy full report icon-only (`ic_copy_full_report`, new).
- `btnCopy`: Copy to clipboard icon-only (`ic_copy`).
- `btnClose`: Close icon-only (`ic_clear`). Dismisses.
- Each icon-only button carries `contentDescription` + `TooltipCompat` from an existing string; min touch target via Material3 IconButton style.
- Collapsible details toggle (`tvDetailsToggle` + `scrollDetails`) unchanged.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | action-assets | - | ✅ Done | 2/2 | [PHASE_01__action-assets.md](PHASE_01__action-assets.md) |
| 02 | dialog-layout | 01 | ✅ Done | 2/2 | [PHASE_02__dialog-layout.md](PHASE_02__dialog-layout.md) |
| 03 | dialog-wiring | 02 | ✅ Done | 3/3 | [PHASE_03__dialog-wiring.md](PHASE_03__dialog-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All §6 research items are Resolved (items 1-4).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (ErrorDialog.kt touched).
- [ ] `/spec-check S0378` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0378`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-07 - Initial tactical plan authored by `/spec-tech`.
