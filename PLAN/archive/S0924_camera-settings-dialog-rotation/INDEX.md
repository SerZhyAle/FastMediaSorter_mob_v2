# Tactical Plan: S0924 - camera-settings-dialog-rotation

**Strategic spec:** [`../S0924_camera-settings-dialog-rotation.md`](../S0924_camera-settings-dialog-rotation.md)
**Research inputs:** [`research/01__rotation-mechanism.md`](research/01__rotation-mechanism.md)
**Feature:** Rotate the camera settings dialog with physical device rotation while the camera screen stays portrait-locked
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 2 / 3 done (Phase 03 device-verification pending)
**Last updated:** 2026-07-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | rotation-signal-api | - | ✅ Done | 2/2 | [PHASE_01__rotation-signal-api.md](PHASE_01__rotation-signal-api.md) |
| 02 | rotation-container-manager | 01 | ✅ Done | 3/3 | [PHASE_02__rotation-container-manager.md](PHASE_02__rotation-container-manager.md) |
| 03 | cleanup-acceptance | 01, 02 | 🚧 In Progress | 2/3 | [PHASE_03__cleanup-acceptance.md](PHASE_03__cleanup-acceptance.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- **DEVICE-GATED (strategic §6):** implementation must run with a device/emulator attached. Research rates a blind (no-device) implementation as **HIGH risk**: no codebase precedent exists for rotating a whole interactive panel (only per-icon rotation in `CameraOverlayRotationManager`); the rotate-and-swap-measure container needs pivot + swapped-`MeasureSpec` tuning against dynamic row visibility; `window.setLayout` behaviour varies across widths/DPIs; and the dropdown-popup / tooltip seams manifest only visually. Phases 01-03 are authored and ready, but Phase 02's core (and the device-verification steps of Phase 03) require on-device visual iteration - do not execute them blind. Phase 01 (signal API plumbing) is compile-verifiable but has no standalone value until Phase 02 consumes it, so it is not worth landing alone.
- All other strategic §6 research items are Resolved (mechanism chosen; see `research/01__rotation-mechanism.md`).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - not edited per-spec; capability recorded in `docs/ALL_FEATURES.jsonl` on `Implemented`, showcase owned by `/skill-release`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class = public API change).
- [ ] Known limitation (dropdown-popup / tooltip seams unrotated, strategic §11.4) recorded in Phase 03 acceptance.
- [ ] Dead `res/layout-land/dialog_camera_settings.xml` deleted (strategic §11.5, Rule 20).
- [ ] Device verification of strategic criteria 1-4 passed (`/spec-test-device S0924` -> `/spec-check S0924`).
- [ ] `/spec-check S0924` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log. If the whole spec is blocked, also set journal status to a `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0924`.

---

## Blockers Log

- 2026-07-04 - Implementation blocked on device availability (strategic §6). Tactical plan authored under `/spec-all`; F3 (`/spec-dev`) deferred until a device/emulator is attached for visual iteration. Not a plan defect - a hardware precondition.

---

## Change Log

- 2026-07-04 - Initial tactical plan authored by `/spec-all` (F2). Implementation deferred: device-gated per strategic §6.
