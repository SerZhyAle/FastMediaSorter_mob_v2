# Tactical Plan: S0363 - drawing-command-image-resources

**Strategic spec:** [`../S0363_drawing-command-image-resources.md`](../S0363_drawing-command-image-resources.md)
**Feature:** Команда «Создать рисунок» для изображенческих ресурсов
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | drawing-target-policy | - | ✅ Done | 2/2 | [PHASE_01__drawing-target-policy.md](PHASE_01__drawing-target-policy.md) |
| 02 | save-routing-indexing | 01 | ✅ Done | 2/2 | [PHASE_02__save-routing-indexing.md](PHASE_02__save-routing-indexing.md) |
| 03 | visibility-wiring | 01, 02 | ✅ Done | 3/3 | [PHASE_03__visibility-wiring.md](PHASE_03__visibility-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items - strategic §6 fully resolved. Phase 01 may start.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - existing drawing entry extended (strategic §8 mandates a one-line update).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public class `DrawingTargetPolicy`).
- [ ] `/spec-check S0363` returns `Verified` (pending on-device verification).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0363`.

---

## Blockers Log

- None.

---

## Change Log

- 2026-06-05 - Initial tactical plan authored by `/spec-tech`.
