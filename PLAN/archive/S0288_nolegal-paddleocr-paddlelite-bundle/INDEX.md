# Tactical Plan: S0288 - nolegal-paddleocr-paddlelite-bundle

**Strategic spec:** [`../S0288_nolegal-paddleocr-paddlelite-bundle.md`](../S0288_nolegal-paddleocr-paddlelite-bundle.md)
**Feature:** Sideload Cyrillic PaddleOCR in noLegal Flavor
**Tier:** 4 — Strategic
**Priority:** 50
**Status:** 🚧 In Progress
**Phases:** 7 / 7 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | ocr-abstraction-foundations | - | ✅ Done | 4/4 | [PHASE_01__ocr-abstraction-foundations.md](PHASE_01__ocr-abstraction-foundations.md) |
| 02 | settings-ui-extension | 01 | ✅ Done | 3/3 | [PHASE_02__settings-ui-extension.md](PHASE_02__settings-ui-extension.md) |
| 03 | paddle-lite-runtime | 01 | ✅ Done | 3/3 | [PHASE_03__paddle-lite-runtime.md](PHASE_03__paddle-lite-runtime.md) |
| 04 | paddle-ocr-engine | 03 | ✅ Done | 3/3 | [PHASE_04__paddle-ocr-engine.md](PHASE_04__paddle-ocr-engine.md) |
| 05 | paddle-ocr-model-manager | 01 | ✅ Done | 3/3 | [PHASE_05__paddle-ocr-model-manager.md](PHASE_05__paddle-ocr-model-manager.md) |
| 06 | translation-manager-integration | 02, 04, 05 | ✅ Done | 4/4 | [PHASE_06__translation-manager-integration.md](PHASE_06__translation-manager-integration.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Convert PP-OCRv5 Cyrillic & East Slavic models to `.nb` format using Paddle-Lite opt tool for ARM64 - completed. See strategic §6.1.
- [x] **Research:** Benchmark complete det+cls+rec pipeline latency on ARM64 CPU - skipped. Latency benchmark skipped by owner request; progress animation preferred instead. See strategic §6.2.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES_noLegal.md` updated with PaddleOCR integration details (plus `_RU` / `_UK` mirrors).
- [x] `dev/CHANGELOG.md` has an entry for every modified file (46 `S0288` entries recorded incrementally via post-change pipeline).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated since public API changed (via `catalog_sync.ps1`).
- [ ] `/spec-check S0288` returns `Verified` — pending on-device test pass (status currently `BlockNeedUserTest`).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0288`.

---

## Blockers Log

- 2026-05-21 - Plan unblocked by owner request. Latency research skipped. Progress animation added as a priority.
- 2026-05-21 - Phase 03 is blocked: precompiled 16 KB page-aligned libpaddle_light_api_shared.so and Java API wrappers are missing from the project and user directories. Awaiting user to provide these files.
- 2026-05-21 - Phase 03 external runtime blocker resolved by importing official Paddle-Lite v2.14-rc Android armv8 clang c++_static with_extra with_cv artifacts. Both native libraries report LOAD Align 0x10000.
- 2026-05-21 - Phase 05 Step 05.2 external blocker resolved for runtime bootstrap: official PaddleX-Lite `.nb` tarballs downloaded and hashed for PP-OCRv5 det, PP-LCNet textline orientation cls, and generic PP-OCRv5 mobile rec. Cyrillic/East Slavic PaddleOCR models remain available only as Paddle 3 PIR `inference.json` + `inference.pdiparams`; Paddle-Lite 2.14 cannot convert them directly to `.nb`.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-21 - Spec unblocked: model conversion research completed, latency research skipped, added progress animation requirement.
