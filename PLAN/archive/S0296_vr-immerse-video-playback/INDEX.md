# Tactical Plan: S0296 - vr-immerse-video-playback

**Strategic spec:** [`../S0296_vr-immerse-video-playback.md`](../S0296_vr-immerse-video-playback.md)
**Feature:** VR immerse video playback
**Tier:** 3
**Priority:** 80
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | return-snapshot-contract | - | ✅ Done | 4/4 | [PHASE_01__return-snapshot-contract.md](PHASE_01__return-snapshot-contract.md) |
| 02 | video-launch-gates | 01 | ✅ Done | 3/3 | [PHASE_02__video-launch-gates.md](PHASE_02__video-launch-gates.md) |
| 03 | xr-owned-playback | 02 | ✅ Done | 4/4 | [PHASE_03__xr-owned-playback.md](PHASE_03__xr-owned-playback.md) |
| 04 | flat-return-restore | 03 | ✅ Done | 3/3 | [PHASE_04__flat-return-restore.md](PHASE_04__flat-return-restore.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research:** S0291 lifecycle readiness - S0291 must be `Verified` with passthrough exit and HUD re-entry defects closed before Phase 01 starts. See strategic §4.1.
- [ ] **Research:** S0292 snapshot restoration - S0292 must leave `Partial` and its round-trip restoration warnings must be resolved, or the owner must explicitly check this blocker and let Phase 01 and Phase 04 own the shared return-path fix. See strategic §4.2.
- [ ] **Research:** Prepared URI policy - S0296 implementation must confirm that MVP video input is limited to local `file://` or raw local paths; unprepared `content://`, network, cloud and streaming inputs stay rejected with typed unavailable results. See strategic §4.3.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated with the cinema-video bullet.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] No persistent `Timber.i/w/e` line contains `S0296`.
- [x] noLegal debug build passes.
- [ ] `/spec-check S0296` returns `Verified` after Quest 3 on-device verification.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0296`.

---

## Blockers Log

- 2026-05-25 - Phase 01 blocked: S0291 is `Tactical`, S0292 is `Partial`, and prepared URI policy is not checked for VIDEO. Next: close upstream tickets or explicitly accept Phase 01/04 ownership of the return-path work, then run `/spec-dev S0296`.

## Device Verification Handoff

- **Build command:** `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` passed on 2026-05-30.
- **Quest 3 manual checks:** launch the noLegal debug build from the headset library, open a local or prepared video in the flat player, tap the VR badge, verify immersive cinema video + audio, exit back to the flat player, and confirm file, position, play/pause state, speed and volume restore.
- **Unsupported input checks:** GIF, unprepared `content://`, network and cloud inputs must return typed unavailable results without crashing the flat player.
- **Regression checks:** Diagnostic IMAGE / Test Immersive flow must still enter and exit the OpenXR host.
- **Cold-start measurement:** pending device verification.

---

## Change Log

- 2026-05-25 - Initial tactical plan authored by `/spec-tech`.
