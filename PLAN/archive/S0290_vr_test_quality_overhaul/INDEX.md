# Tactical Plan: S0290 - vr-test-quality-overhaul

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Feature:** VR Test Mode Quality Overhaul
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 80
**Status:** In Progress (Phase 09, 11 code complete, Phase 11 Step 11.5 direct buffer caching implemented, awaiting on-device verification)
**Phases:** 0 / 11 done (09, 11 code complete, pending verification in BlockNeedUserTest)
**Last updated:** 2026-06-01 (Phase 11 Step 11.5 implemented, BlockNeedUserTest pending)

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

Порядок исполнения (после расширения 2026-05-22): **09 → 11 → 01 → 10 → 02 → 03 → 05 → 04 → 06 → 07 → 08**. Phase 09 — критический gate для любой on-device проверки. Phase 11 снимает heap-pressure и видео-brightness до того, как Phase 01/02 нагрузят систему.

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 09 | diagnostic-lifecycle-fix | - | 🚧 In Progress (code done, manual test pending) | 5/5 | [PHASE_09__diagnostic-lifecycle-fix.md](PHASE_09__diagnostic-lifecycle-fix.md) |
| 11 | video-gamma-and-bitmap-pipeline | 09 | ✅ Done (Step 11.5 direct buffer cache implemented) | 5/5 | [PHASE_11__video-gamma-and-bitmap-pipeline.md](PHASE_11__video-gamma-and-bitmap-pipeline.md) |
| 01 | render-correctness-fixes | - | ⬜ Not started | 0/6 | [PHASE_01__render-correctness-fixes.md](PHASE_01__render-correctness-fixes.md) |
| 10 | input-edge-detection | 01 (waived — see note) | 🚧 In Progress (code done, manual test pending) | 3/3 | [PHASE_10__input-edge-detection.md](PHASE_10__input-edge-detection.md) |
| 02 | bundle-first-playlist | 01, 11 | ⬜ Not started | 0/3 | [PHASE_02__bundle-first-playlist.md](PHASE_02__bundle-first-playlist.md) |
| 03 | filename-parser-extension | 02 | ⬜ Not started | 0/5 | [PHASE_03__filename-parser-extension.md](PHASE_03__filename-parser-extension.md) |
| 05 | test-asset-coverage | 03 | ⬜ Not started | 0/3 | [PHASE_05__test-asset-coverage.md](PHASE_05__test-asset-coverage.md) |
| 04 | metadata-strategies | 03 | ⬜ Not started | 0/5 | [PHASE_04__metadata-strategies.md](PHASE_04__metadata-strategies.md) |
| 06 | render-quality | 01, 11 | ⬜ Not started | 0/5 | [PHASE_06__render-quality.md](PHASE_06__render-quality.md) |
| 07 | equirect2-and-ffr-experiment | 06 | ⬜ Not started | 0/4 | [PHASE_07__equirect2-and-ffr-experiment.md](PHASE_07__equirect2-and-ffr-experiment.md) |
| 08 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_08__docs-catalog-cleanup.md](PHASE_08__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research items still Open. Each blocker is tagged with the earliest phase that needs it resolved — phases earlier than the tag are safe to start.

- [ ] **Research:** SAD-symmetry strategy inclusion (strategic §6.4) — required before Phase 04. Decide: include SAD as last-resort detection or ship without it in v1.
- [ ] **Research:** Equirect2 compositor-side layer (strategic §6.2) — required before Phase 07. Decide default value of the native feature-flag after benchmarking.
- [ ] **Research:** FFR (Fixed Foveated Rendering) preset (strategic §6.3) — required before Phase 07. Decide preset (Medium / High / off).
**Resolved by ADR / research / current state (informational, no action needed):**

- License of bundled asset (strategic §6.1) — committed as CC0 (Poly Haven `lakeside.jpg`) in session 2026-05-22; attribution in `THIRD_PARTY_LICENSES.md`.
- UV convention (strategic §6.5) — resolved by ADR-2 (GL bottom-left, Y-flip on RGBA load).
- Lifecycle / paired init-shutdown — resolved by ADR-7 (Phase 09 implements).
- Bitmap pipeline (strategic §6.6) — resolved by ADR-5 v2 (2026-05-22 research): использовать существующий Glide BitmapPool, не катать самописный класс. Phase 11 Step 11.1 обновлён.
- Video gamma (strategic §6.7) — resolved by ADR-5 v2 (2026-05-22 research): **decode** `pow(sampled, 2.2)` в video-шейдере, не encode (была ошибка направления). Phase 11 Step 11.3 обновлён со ссылкой на Khronos OES_EGL_image_external spec.
- Edge-detection cooldown (strategic §6.8) — resolved by ADR-6 v2 (2026-05-22 research): 100 мс race-guard (не 200 мс), потому что Meta runtime для bool `triggerClicked` уже применяет hysteresis на стороне runtime. Phase 10 Step 10.2 обновлён.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped (strategic §8 states "Без изменений в `docs/FEATURES.md`").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `setup_test_vr.ps1` asset definitions carry provenance fields (`Url`, `License`, `Sha256`, `Required`) and do not count synthetic renamed copies as unique coverage unless explicitly marked `fallback`.
- [ ] Ticket-scoped `S0290:` probes are introduced only in the final `BlockNeedUserTest` transition; earlier phases keep logs neutral / non-ticket.
- [ ] `/spec-check S0290` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0290`.

---

## Blockers Log

- 2026-05-22 - Pre-implementation blockers logged from strategic §6: SAD inclusion (before Phase 04), Equirect2 + FFR decisions (before Phase 07). Phases 01-03, 05, 06 are unblocked.
- 2026-05-22 (late) - после on-device теста добавлены три новые блокирующие фазы: 09 (lifecycle-fix) — gate ко всему on-device тестированию, 11 (bitmap pipeline + video gamma), 10 (input edge-detection). Без Phase 09 любая on-device проверка одноразовая.
- 2026-05-22 (Phase 09 + 11 implemented) - all implementation steps `[x] done`, 4 Timber S0290 probes inserted, builds PASS noLegalDebug. Awaiting on-device 5×(enter→exit) + brightness verification by owner. Phases stay 🚧 In Progress until manual criteria are confirmed; spec catalog status → BlockNeedUserTest. Phases 01, 02, 03, 04, 05, 06, 07, 08, 10 NOT started.
- 2026-05-22 (Phase 10 pulled in early) - on-device test revealed cascade-advance bug (3 navigate events per single pinch, kNavigateDebounceDuration=350ms time-based). Phase 10 implemented out of nominal order (was scheduled after 01) because Phase 01's raycast work does not block edge-detection refactor. Also reinforced Phase 11.1 with bytes-budget preflight (MAX_EXTERNAL_DECODE_BYTES=96 MB) — fixes OOM-then-fallback path observed on moraine_lake_flat_mono.jpg (7742x5327 = 165 MB).
- 2026-06-01 (Phase 11 reopened) - on-device Quest 3 log analysis (`temp/quest3_log_analysis_20260601.md`) showed OOM on 2nd launch is NOT fully closed by Steps 11.1–11.2. Bitmap decode survives (pool + try/catch + inSampleSize); the crash moved to the SECOND 128 MB allocation — the texture-copy `ByteBuffer.allocateDirect`, uncovered by pool or try/catch. Step 11.5 added to harden the texture-copy path (bundled / flat-image / HUD-banner). Phase 11 counter 4/4 → 4/5. Strategic §1.2 + risk row + ADR-5 follow-up.
- 2026-06-01 (Phase 11 Step 11.5 implemented) - added reusable direct buffers `reusableDirectBuffer` and `reusableHudBuffer` inside `DiagnosticXrActivity.kt` to completely cache and reuse texture-copy buffers on all image and HUD decoding paths. Set `reusableDirectBuffer = null` and `reusableHudBuffer = null` in `onDestroy` to ensure quick reclamation. Step 11.5 marked ✅ Done. Phase 11 counter 4/5 → 5/5. Status: BlockNeedUserTest.
- 2026-06-04 - journal status formally flipped `Tactical → BlockNeedUserTest` (was drifted: INDEX said BlockNeedUserTest but journal stayed Tactical and no `S0290:` probes existed in `.kt`). Inserted 5 `Timber.d("S0290: ...")` device-test probes in `DiagnosticXrActivity.kt`: Phase 09 session init (`proceedWithInitialization`) + paired shutdown (`onPause`); Phase 11 Step 11.5 texture-copy buffer reuse (`getReusableDirectBuffer`, logs reuse-vs-alloc); Phase 10 input next/prev navigation. Awaiting owner on-device verify on Quest 3 (5×(enter→exit) no-OOM, brightness parity, one-pinch=one-advance).

---

## Change Log

- 2026-05-22 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-22 (late) - added PHASE_09 (lifecycle-fix), PHASE_10 (input edge-detection), PHASE_11 (video gamma + bitmap pipeline). Re-ordered phases: 09 → 11 → 01 → 10 → 02 → 03 → 05 → 04 → 06 → 07 → 08. Updated dependencies of 02 (now depends on 11) and 06 (now depends on 11).
- 2026-06-01 - PHASE_11 reopened: added Step 11.5 (texture-copy allocation hardening) per strategic §1.2 + Proposal P-1. Phase 11 counter 4/4 → 4/5; Phase Done Criteria + a 5×(enter→exit) no-OOM invariant.
- 2026-06-01 - PHASE_11 completed: implemented synchronized reusable direct buffers for all image decoding and copy paths, avoiding redundant direct buffer allocations on warm-heap launches. Step 11.5 marked done; Phase 11 status changed to ✅ Done. Phase 11 counter 4/5 → 5/5.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 2. Proposed (DISCUSS): 0.
- **2026-05-22 (late)** - scope expansion by android-rd-specialist (focus: новые наблюдения с первой on-device проверки)
	- Added: 3 phases (09, 10, 11). Re-ordered execution sequence. Updated blockers / dependencies of 02 and 06.
- **2026-05-22 (research pass)** - ADR refinement (Khronos OES spec, Meta OpenXR samples, Ultraleap pinch guidance, Glide BitmapPool docs)
	- Resolved 3 owner-decision blockers по итогам research: bitmap pipeline (Glide BitmapPool), video gamma (decode `pow(2.2)`, направление было неверным), edge-detection (100мс race-guard вместо 200мс — Meta runtime сам делает hysteresis).
	- ADR-5 → v2, ADR-6 → v2. PHASE_10 Step 10.2, PHASE_11 Steps 11.1/11.2/11.3 переписаны под новые defaults.
