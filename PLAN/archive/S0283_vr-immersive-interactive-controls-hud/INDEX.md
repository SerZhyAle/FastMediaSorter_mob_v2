# Tactical Plan: S0283 - vr-immersive-interactive-controls-hud

**Strategic spec:** [`../S0283_vr-immersive-interactive-controls-hud.md`](../S0283_vr-immersive-interactive-controls-hud.md)
**Feature:** VR Immersive Interactive Controls & HUD
**Tier:** 3 — Moderate
**Priority:** 80
**Status:** BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-xr-input | - | ✅ Done | 6/6 | [PHASE_01__foundation-xr-input.md](PHASE_01__foundation-xr-input.md) |
| 02 | world-hud-raycast | 01 | ✅ Done | 5/5 | [PHASE_02__world-hud-raycast.md](PHASE_02__world-hud-raycast.md) |
| 03 | interactive-canvas-jni | 02 | ✅ Done | 5/5 | [PHASE_03__interactive-canvas-jni.md](PHASE_03__interactive-canvas-jni.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Canvas Performance under High-Frequency Hover Updates - required before Phase 03. Resolved via C++ cursor rendering (ADR-1) and sparse Kotlin Canvas redraws on state changes.
- [x] **Research:** AndroidManifest Permissions for Quest Hand Tracking - required before Phase 01. Defined Meta Quest specific permission and feature tags with android:required="false".
- [x] **Research:** OpenXR Haptic Feedback Action Paths - required before Phase 03. Mapped vibration actions to /user/hand/*/output/haptic.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0283` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0283`.

---

## Blockers Log

- 2026-05-21 - All pre-implementation blockers resolved: Canvas performance resolved via native cursor rendering; Manifest permissions identified; Haptics paths configured. Phase 01 unblocked.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-21 - Pre-implementation blockers resolved by agent during planning. Ready for implementation phase.
- 2026-05-21 - Spec aligned with audit requirements: haptic method, ADR-3, target sizing, hand extension queries, Kotlin architectural helpers, C++ TU decomposition, status synchronization, and Quest Manifest step. Status set to Tactical (Ready).
- 2026-05-21 - Audit follow-up: §2.3/§5.1.1 expanded to enumerate all three hand-extension roles; INDEX.md status aligned with journal (`In Progress`); Phase 01 bumped `xr_input.cpp` budget to ≤500 LOC and added Step 01.5 (extract action wiring + hand-tracking pointers from `xr_session.cpp` -> `xr_input.cpp` to eliminate duplicate global symbols); Phase 02 added CMakeLists.txt to Files Touched + Step 02.0 (create stub TUs and re-add `xr_raycast.cpp`/`xr_hud_world.cpp` to CMakeLists); Phase 03 Step 03.4 fixed cast to canonical `XrHapticBaseHeader*`; `diagnostic_xr_runtime.cpp` patched to `#include "xr_input.h"` for `xr_input_apply_haptic` resolution; CMakeLists.txt temporarily reverted to existing TUs only (Phase 02 will re-add the new TUs).
