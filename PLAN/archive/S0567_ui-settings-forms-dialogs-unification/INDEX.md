# Tactical Plan: S0567 - ui-settings-forms-dialogs-unification

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Research inputs:** none (strategic §1.1 carries the codebase survey)
**Feature:** Settings, forms, and dialog components unification
**Tier:** 3 - Standard
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 7 done
**Last updated:** 2026-06-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-selection-row | - | ✅ Done | 4/4 | [PHASE_01__settings-selection-row.md](PHASE_01__settings-selection-row.md) |
| 02 | settings-dropdown-row | 01 | ✅ Done | 4/4 | [PHASE_02__settings-dropdown-row.md](PHASE_02__settings-dropdown-row.md) |
| 03 | settings-input-row | 01 | ✅ Done | 3/3 | [PHASE_03__settings-input-row.md](PHASE_03__settings-input-row.md) |
| 04 | list-selection-dialog | 01 | ✅ Done | 4/4 | [PHASE_04__list-selection-dialog.md](PHASE_04__list-selection-dialog.md) |
| 05 | resource-form-primitives | 01 | ⬜ Not started | 0/4 | [PHASE_05__resource-form-primitives.md](PHASE_05__resource-form-primitives.md) |
| 06 | action-help-row-audit | 01,02,03,04,05 | ⬜ Not started | 0/3 | [PHASE_06__action-help-row-audit.md](PHASE_06__action-help-row-audit.md) |
| 07 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> Phases 02-05 each depend only on Phase 01 (they reuse its compound-view conventions: prefixed `attrs.xml` styleable, `view_*.xml` `<merge>` layout, `TooltipDialog` ownership). They do not depend on each other and may be implemented in any order after 01. Phase 06 audits surviving cases and so depends on all widget phases.

---

## Pre-Implementation Blockers

- None. Strategic §6 carries no `Status: Open` research items; the §1.1 survey is resolved and concrete.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic spec carries no §8 FEATURES sentence (visual-debt refactor, no new user-facing capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public widget API added).
- [ ] `scripts/quality/assert-settings-doc-sync.ps1` passes (settings presence/position/naming unchanged - regen only if it flags).
- [ ] `/spec-check S0567` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/7 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0567`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-21 - Initial tactical plan authored by `/spec-tech`.
