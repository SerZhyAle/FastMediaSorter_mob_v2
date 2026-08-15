# Tactical Plan: S0338 - camera-ocr-crop-region

**Strategic spec:** [`../S0338_camera-ocr-crop-region.md`](../S0338_camera-ocr-crop-region.md)
**Feature:** Обрезка области кадра перед OCR в «Быстром переводе с камеры»
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented (awaiting on-device test)
**Phases:** 5 / 5 done
**Last updated:** 2026-06-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | crop-strings-geometry | - | ✅ Done | 3/3 | [PHASE_01__crop-strings-geometry.md](PHASE_01__crop-strings-geometry.md) |
| 02 | crop-overlay-view | 01 | ✅ Done | 3/3 | [PHASE_02__crop-overlay-view.md](PHASE_02__crop-overlay-view.md) |
| 03 | layout-crop-state | 02 | ✅ Done | 2/2 | [PHASE_03__layout-crop-state.md](PHASE_03__layout-crop-state.md) |
| 04 | flow-integration | 01, 02, 03 | ✅ Done | 4/4 | [PHASE_04__flow-integration.md](PHASE_04__flow-integration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (owner decisions captured 2026-06-03).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes added).
- [ ] `/spec-check S0338` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0338`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-03 - Initial tactical plan authored by `/spec-tech`.
