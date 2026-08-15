# Tactical Plan: S0258 - settings-toggle-row-template

**Strategic spec:** [`../S0258_settings-toggle-row-template.md`](../S0258_settings-toggle-row-template.md)
**Feature:** Unified toggle row template for settings and forms
**Tier:** 3 - Moderate
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | documents-pilot | 01 | ✅ Done | 4/4 | [PHASE_02__documents-pilot.md](PHASE_02__documents-pilot.md) |
| 03 | settings-rollout | 02 | ✅ Done (partial) | 4/4 | [PHASE_03__settings-rollout.md](PHASE_03__settings-rollout.md) |
| 04 | form-rollout | 03 | ✅ Done (no-op) | 3/3 | [PHASE_04__form-rollout.md](PHASE_04__form-rollout.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** helper placement moved from rightmost child to title-adjacent inline position. See strategic §6.2.
- [x] **Research:** first wave is foundation + pilot; forms migrate later in this spec. See strategic §6.1.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing - see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [ ] `/spec-check S0258` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0258`.

---

## Blockers Log

- 2026-05-19 - none.

---

## Change Log

- 2026-05-19 - Initial tactical plan authored by `/spec-tech`.
