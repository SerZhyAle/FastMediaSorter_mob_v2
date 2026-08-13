# Tactical Plan: S0646 - settings-simple-list-dialog-unification

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Research inputs:** [`research/01__value-selection-dialog-inventory.md`](research/01__value-selection-dialog-inventory.md)
**Feature:** Unify simple value-selection dialogs in settings
**Tier:** UI consistency
**Priority:** 60
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shared-choice-dialog | - | ✅ Done | 2/2 | [PHASE_01__shared-choice-dialog.md](PHASE_01__shared-choice-dialog.md) |
| 02 | action-resource-dialogs | 01 | ✅ Done | 6/6 | [PHASE_02__action-resource-dialogs.md](PHASE_02__action-resource-dialogs.md) |
| 03 | ocr-selector-rows | 01 | ✅ Done | 4/4 | [PHASE_03__ocr-selector-rows.md](PHASE_03__ocr-selector-rows.md) |
| 04 | audio-visualizer-row | 01 | ✅ Done | 3/3 | [PHASE_04__audio-visualizer-row.md](PHASE_04__audio-visualizer-row.md) |
| 05 | docs-catalog-cleanup | 01,02,03,04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. All strategic §6 items are Resolved (quiz 2026-06-23 + architecture). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic spec has no §8 FEATURES sentence (UI-consistency refactor, no public showcase line).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (batched per phase).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `SimpleValueChoiceDialog` class).
- [ ] `/spec-check S0646` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0646`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
