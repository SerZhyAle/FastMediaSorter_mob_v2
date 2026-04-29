# Phase 05 — Manual on-device acceptance

**Ticket:** S0026 / F05
**Goal:** verify on Quest 3 that the user-reported flicker is gone. Non-blocking for `Verified` — checked off as `[manual — deferred to human]`.

---

## Pre-conditions

- Quest 3 device with VR debug build installed.
- Settings → VR → "Auto-detect 3D format" = ON.
- Settings → VR → "Auto-enter immersive on stereo content" = OFF.
- Browse → resource «Все видео» containing at least one VR180 file (e.g. `18VR_*_180x180_3dh.mp4`).

## Scenarios

### S1 — Stereo file + auto-immersive OFF → standard player, no flicker

1. From Browse, click the VR180 file.
2. **Expected:** standard `PlayerActivity` opens directly. No XR overlay flash. No `VrPlayerActivity: onCreate` line in the device log.
3. **Verify in log** (`adb logcat | rg -i 'VrPlayerActivity|VrTaskTransition|RouteDecision|BrowseEventHandler'`):
   - `BrowseEventHandler: route ... autoImmersive=false -> standard=true` is present.
   - `VrTaskTransition.enterImmersive` is **absent**.
   - `VrPlayerActivity: onCreate ENTRY` is **absent**.
   - `forceStopVrPlayback reason=standard-player-fallback:player-state` is **absent**.

### S2 — Stereo file + auto-immersive ON → immersive holds

1. Settings → flip auto-immersive ON.
2. From Browse, click the same VR180 file.
3. **Expected:** VR cinema/immersive opens and stays open until user explicitly exits.
4. **Verify in log:**
   - `BrowseEventHandler: route ... autoImmersive=true -> standard=false` present.
   - `VrTaskTransition.enterImmersive: source=BrowseActivity target=VrPlayerActivity` present.
   - `VrPlayerActivity: route decision file=<vr180> ... requested=VR180_FISHEYE_SBS effective=VR180_FISHEYE_SBS ... route=IMMERSIVE_VIDEO` present (the F03 hint primed `requested` to `VR180_FISHEYE_SBS` instead of `MONO`).
   - `forceStopVrPlayback reason=standard-player-fallback:*` is **absent**.

### S3 — Plain 2D file + auto-immersive ON → CINEMA_IMMERSIVE (no flicker)

1. With auto-immersive still ON, click a plain 2D `.mp4` (e.g. a regular landscape video without `3dh`/`SBS`/`OU` markers).
2. **Expected:** VR cinema (CINEMA_IMMERSIVE quad) opens. No fallback. This validates that ordinary 2D content still goes through the immersive cinema path when the user explicitly opted in.
3. **Verify in log:**
   - `VrPlayerActivity: route decision ... requested=MONO effective=MONO ... route=CINEMA_IMMERSIVE reason=plain-2d-video` present.

### S4 — Plain 2D file + auto-immersive OFF → standard player

1. Flip auto-immersive OFF.
2. Click the same plain 2D file.
3. **Expected:** standard `PlayerActivity` opens immediately (this was already the working case; just confirming no regression).

## Outcome reporting

Mark this phase complete with `[manual — deferred to human]` once the user confirms scenarios S1-S4. Until then the spec status flips to `BlockNeedUserTest` after the build gate (F4) passes.

If any scenario fails, file a follow-up audit in the strategic spec's `## Last Audit` block (do not create a separate audit file per CLAUDE.md rule).

---

## Acceptance for F05

- All four scenarios documented; not yet executed by AI.
- After F03 lands, status `BlockNeedUserTest` set via `pwsh -File scripts/spec_catalog/update.ps1 -Id S0026 -Status BlockNeedUserTest`.
- Dev changelog: `.\scripts\add_to_dev_log.ps1 "PLAN/S0026_bugfix-vr-stereo-route-flicker/F05_manual-acceptance.md" "S0026/F05" "Manual acceptance scenarios documented; awaiting Quest 3 verification"`.
