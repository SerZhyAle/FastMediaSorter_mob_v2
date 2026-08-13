# Tactical Plan: S0384 - unify-scrollable-text-dialogs

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Feature:** One unified scrollable-text dialog component; migrate all callers; delete dead duplicates
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 40
**Status:** Implemented (awaiting device test)
**Phases:** 6 / 6 done
**Last updated:** 2026-06-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Target & decisions (from strategic §6)

- New component `ScrollableTextDialog` (object) in `ui/dialog/`, built on the S0378 layout `dialog_error_detail` (P+L) and a reusable bind helper (the single UI layer).
- Builder: `MaterialAlertDialogBuilder` (Material3) for all callers (§6.2).
- Dismiss semantics: only Close and the primary CTA dismiss; copy/share/save/extra keep the dialog open (§6.1).
- Migration: all callers move to the new API; old entry points deleted (§6.4). No wrappers.
- `ScheduledLogDialog` stays a `Dialog` subclass but reuses the bind helper + layout; "clear" is an extra action with in-place text update (§6.3).

## Call-site inventory (24 + 1)

- `ErrorDialog.show(..)` - 15 calls across 9 files (incl. Throwable overload). Params already match the new API (title/message/details/actionButtonText/onActionClick/inlineActionButtonText/onInlineActionClick).
- `DialogUtils.showScrollableDialog(..)` - 5 live calls (AddResourceConnectionManager x2, GeneralSettingsImportExportHelper x2, GeneralSettingsLogHelper x1) + 2 dead (inside ErrorDialogHelper).
- `ScheduledLogDialog(..)` - 1 call (OperationsSettingsFragment).

## Dead code to delete

- `ErrorDialogHelper.kt` (0 external callers).
- `dialog_log_view.xml` + `layout-land/dialog_log_view.xml` (0 references) + auto-strings `dialog_log_view_tvLogText_*` (EN/RU/UK).
- After migration: `DialogUtils.kt` (only had showScrollableDialog), `dialog_scrollable_text.xml` (+land).
- After Phase 04: `dialog_scheduled_log.xml` if fully replaced + its auto-strings (if any).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | unified-component | - | ✅ Done | 3/3 | [PHASE_01__unified-component.md](PHASE_01__unified-component.md) |
| 02 | migrate-errordialog | 01 | ✅ Done | 2/2 | [PHASE_02__migrate-errordialog.md](PHASE_02__migrate-errordialog.md) |
| 03 | migrate-dialogutils | 02 | ✅ Done | 2/2 | [PHASE_03__migrate-dialogutils.md](PHASE_03__migrate-dialogutils.md) |
| 04 | scheduled-log-reuse | 01 | ✅ Done | 2/2 | [PHASE_04__scheduled-log-reuse.md](PHASE_04__scheduled-log-reuse.md) |
| 05 | dead-code-removal | 03,04 | ✅ Done | 2/2 | [PHASE_05__dead-code-removal.md](PHASE_05__dead-code-removal.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

> Implementation note: §6.3 reuse was achieved via the public `show(..)` + `ExtraAction` (no separate bind helper); `ScheduledLogDialog` was deleted, the scheduled-ops log now calls `ScrollableTextDialog.show(..)` directly from `OperationsSettingsFragment` and clears in place via the returned dialog.

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. S0378 (base) is Verified+Archived; all §6 decisions Resolved.

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `docs/FEATURES.md` trilingual - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0384` returns `Verified`.

---

## How to Track Progress

1. Flip phase row to `🚧 In Progress` on start; `✅ Done` when all steps pass + build green.
2. Flip step `[x]` only when its Verification predicate passes.
3. Blocked → `⛔ Blocked` + Blockers Log bullet.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-08 - Initial tactical plan authored by `/spec-tech` (within `/spec-all`).
