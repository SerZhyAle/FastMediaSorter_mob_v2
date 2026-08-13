# Tactical Plan: S0361 - result-screen-ocr-language-reocr

**Strategic spec:** [`../S0361_result-screen-ocr-language-reocr.md`](../S0361_result-screen-ocr-language-reocr.md)
**Feature:** Возврат «Языка OCR» на экран результата с повторным распознаванием
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | reocr-orchestration | - | ✅ Done | 3/3 | [PHASE_01__reocr-orchestration.md](PHASE_01__reocr-orchestration.md) |
| 02 | dialog-ocr-language | 01 | ✅ Done | 3/3 | [PHASE_02__dialog-ocr-language.md](PHASE_02__dialog-ocr-language.md) |
| 03 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 question 1 is resolved: the OCR'd image is already retained for the screen lifetime as the `orientedBitmap` field of `CameraOcrFlowManager` (set to the cropped copy after crop confirm, recycled only in `cleanup()`). The fallback when it is unavailable (process death) is a defensive disabled state, owner-approved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates one sentence in section "Offline OCR & Translation".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0361` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0361`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-05 - Initial tactical plan authored by `/spec-tech`.
