# Tactical Plan: S0545 - camera-capabilities-expansion

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Feature:** Unified in-app camera capture with capability-driven photo controls and in-app video
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 60
**Status:** BlockNeedUserTest (real-device video verification)
**Phases:** 6 / 6 done
**Last updated:** 2026-06-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | capture-host-foundation | - | ✅ Done | 3/3 | [PHASE_01__capture-host-foundation.md](PHASE_01__capture-host-foundation.md) |
| 02 | capability-matrix | 01 | ✅ Done | 3/3 | [PHASE_02__capability-matrix.md](PHASE_02__capability-matrix.md) |
| 03 | photo-controls-ui | 02 | ✅ Done | 2/2 | [PHASE_03__photo-controls-ui.md](PHASE_03__photo-controls-ui.md) |
| 04 | in-app-video | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__in-app-video.md](PHASE_04__in-app-video.md) |
| 05 | host-integration | 01, 03, 04 | ✅ Done | 3/3 | [PHASE_05__host-integration.md](PHASE_05__host-integration.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Owner UI gate (Rule 10):** RESOLVED 2026-06-20 by direct owner Q&A (in lieu of `/ui-clarify`). Full placement contract recorded in strategic §3.4. Key outcomes: zoom = preset chips + slider; overflow = "more" menu for secondary controls; mic = top bar, video-mode only; focus = tap focus-ring; unsupported controls hidden (capability-driven). **No in-screen `PHOTO|VIDEO` switch in S0545** - capture mode is fixed by the launching entry point; in-screen switching deferred to `S0563`.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - DEFERRED to `/skill-release` per CLAUDE.md §11 (showcase is never edited per-spec; populated from the ALL_FEATURES diff at release time).
- [x] `docs/ALL_FEATURES.jsonl` has the new unified camera capability record (`quick capture` area, spec S0545).
- [x] `dev/CHANGELOG.md` has the ticket entries (spec + impl batch).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - new camera helper/model classes recorded.
- [x] `Timber.d("S0545: ...")` debug tags present (host entry + video recording start) while in `BlockNeedUserTest`.
- [ ] `/spec-test-device S0545` captures REAL-DEVICE evidence for the in-app video path - AVD is explicitly insufficient (emulator online but cannot verify CameraX video). Pending real device.
- [ ] `/spec-check S0545` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0545`.

---

## Blockers Log

- 2026-06-20 - RESOLVED: owner-approved control placement contract now exists (strategic §3.4). Implementation unblocked for all phases. Mode-switch UI removed from Phase 04 scope (fixed-mode-per-entry decision); in-screen switch handed to `S0563`.

---

## Change Log

- 2026-06-20 - Initial tactical plan authored by `/spec-tech`.
