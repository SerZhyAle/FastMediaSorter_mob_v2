# Tactical Plan: S0538 - unify-dialog-action-buttons

**Strategic spec:** [`../S0538_unify-dialog-action-buttons.md`](../S0538_unify-dialog-action-buttons.md)
**Research inputs:** [`research/01__confirm-cancel-presentation-convention.md`](research/01__confirm-cancel-presentation-convention.md), [`research/02__dialog-enforcement-seam-inventory.md`](research/02__dialog-enforcement-seam-inventory.md), [`research/03__compact-elements-mechanism.md`](research/03__compact-elements-mechanism.md)
**Feature:** Унификация кнопок подтверждения и отмены в диалогах
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 6 / 6 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Resolved design parameters (owner sign-off 2026-06-19)

- Confirm (OK / Save / Apply): Filled, `@color/success_color` (#2E7D32 light / #81C784 night) backgroundTint.
- Cancel: Outlined neutral.
- Destructive confirm (delete): Filled, `@color/delete_button` (#D32F2F light / #EF5350 night), same slot as confirm.
- Min button height: 56dp (large/default) / 28dp compact; gap 16dp / 8dp compact. Sized via `?attr/dialogActionButtonMinHeight`, swapped by a theme overlay applied in `BaseActivity` when "Compact elements (global)" is on (exact 50% per the documented contract).
- Scope: standard dialogs + custom-layout dialogs + bottom sheets with an action pair + non-standard layouts re-laid to a bottom action row.
- Non-color differentiation mandatory (emphasis filled-vs-outlined + position + label) for colorblind / TalkBack.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | action-button-styles | - | ✅ Done | 4/4 | [PHASE_01__action-button-styles.md](PHASE_01__action-button-styles.md) |
| 02 | compact-hook + dialog-theme-seam | 01 | ✅ Done | 5/5 | [PHASE_02__dialog-theme-seam.md](PHASE_02__dialog-theme-seam.md) |
| 03 | builder-migration | 02 | ✅ Done | 2/2 | [PHASE_03__builder-migration.md](PHASE_03__builder-migration.md) |
| 04 | custom-dialog-layouts | 01 | ✅ Done | 4/4 | [PHASE_04__custom-dialog-layouts.md](PHASE_04__custom-dialog-layouts.md) |
| 05 | bottom-sheets | 01 | ✅ Done | 2/2 | [PHASE_05__bottom-sheets.md](PHASE_05__bottom-sheets.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved; palette and scope signed off by owner 2026-06-19.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` has an entry for every logical change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (no public API delta - 1898 records unchanged).
- [ ] `/spec-check S0538` returns `Verified` (after on-device test confirms the visual + D-pad acceptance).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status.
5. All done: flip `Status:` to `Done`, run `/spec-check S0538`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.
