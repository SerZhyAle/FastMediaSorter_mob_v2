# Tactical Plan: S0008 — VR Immersive Controls Panel

**Strategic spec:** [`../S0008_vr-immersive-controls-panel.md`](../S0008_vr-immersive-controls-panel.md)
**Feature:** VR Immersive Controls Panel — controller rays, interactive HUD, seek/volume/brightness/track/format
**Tier:** 4 — Strategic (8h+, high risk)
**Status:** Broken (regressed by feature flag — see Field-log 2026-05-02 below)
**Phases:** 6 / 6 done (code-level), 0 / 7 §11 criteria PASS on device
**Last updated:** 2026-05-02

> **Scope of this document:** tactical, English, developer handoff. Every step has an explicit verification predicate. Strategic rationale lives in `../spec_vr-immersive-controls-panel.md`.

---

## Existing Infrastructure (read before starting)

| File | Role | Lines |
|------|------|------:|
| `app_v2/src/vr/java/.../vr/ui/VrControlOverlayManager.kt` | 2D View-based control overlay (placeholder for GL panel) | 151 |
| `app_v2/src/vr/java/.../vr/ui/VrHandRayManager.kt` | Hand-tracking cursor (NDC → MotionEvents on decor) | 185 |
| `app_v2/src/vr/java/.../vr/render/VrHudRenderer.kt` | Owns HUD OpenXR swapchain (1024×256) | 109 |
| `app_v2/src/vr/java/.../vr/render/VrHudSceneComposer.kt` | Canvas painter for passive HUD | 283 |
| `app_v2/src/vr/java/.../vr/render/VrHudState.kt` | Immutable HUD state snapshot | 71 |
| `app_v2/src/vr/java/.../vr/openxr/XrInputCallback.kt` | Callback interface (onInputEvent, onPointerMove) | 43 |
| `app_v2/src/vr/java/.../vr/openxr/OpenXrNative.kt` | JNI bindings to libopenxr_native.so | 135 |
| `app_v2/src/vr/java/.../vr/openxr/XrInputEventType.kt` | Input type constants (SEEK_BACKWARD=4, VOLUME_UP=8, etc.) | 54 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Full C++ OpenXR implementation | 3030 |

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | overlay-controls-extension | — | ✅ Done | 7/7 | [PHASE_01__overlay-controls-extension.md](PHASE_01__overlay-controls-extension.md) |
| 02 | controller-ray-native | — | ✅ Done | 7/7 | [PHASE_02__controller-ray-native.md](PHASE_02__controller-ray-native.md) |
| 03 | interactive-panel-gl | 01, 02 | ✅ Done | 8/8 | [PHASE_03__interactive-panel-gl.md](PHASE_03__interactive-panel-gl.md) |
| 04 | ray-hud-hit-test | 02, 03 | ✅ Done | 6/6 | [PHASE_04__ray-hud-hit-test.md](PHASE_04__ray-hud-hit-test.md) |
| 05 | player-command-integration | 03, 04 | ✅ Done | 6/6 | [PHASE_05__player-command-integration.md](PHASE_05__player-command-integration.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`.

---

## Pre-Implementation Blockers

- [ ] **Research Q2 (OpenXR API for ray rendering):** Confirm whether `XR_EXT_hand_interaction` / `XR_FB_hand_tracking_aim` also covers Touch controller aim pose, or if a separate aim action is needed. Check Meta OpenXR Samples in `OpenXrNative.cpp` action-set setup. Required before Phase 02.
- [ ] **Research Q3 (Render order: ray over video):** Verify that Quad layers added after the video layer in `xrEndFrame` composition array render on top. Check `nativeRunFrame` composition order. Required before Phase 02.
- [ ] **Research Q4 (Seek slurring over SMB/SFTP):** Measure `PlaybackCommand.SeekTo` latency on SMB source. If > 500 ms, add debounce to seek drag in Phase 04. Required before Phase 05.
- [ ] **`PlaybackCommand` audit:** Confirm which of `SetVolume`, `SetBrightness`, `SetPlaybackSpeed`, `SetAudioTrack`, `SeekTo(positionMs)` exist in `PlaybackCommand`. Add missing ones before Phase 05. Required before Phase 05.

---

## Completion Gate

The feature is Done when **every** item below is ticked:

- [ ] All 6 phases show ✅ Done in the Phase Overview.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes: `VrControllerRayManager`, `VrInteractivePanelRenderer`, `VrInteractivePanelComposer`, `VrInteractivePanelDriver`, `VrRayPanelHitTester`, `VrPanelHitZoneResolver`).
- [ ] `/spec-check vr-immersive-controls-panel` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. **Before starting a phase:** flip its row to `🚧 In Progress`. Update `Phases: X/6 done` at the top.
2. **During a phase:** flip each step's `Status:` to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent — only on verified signal.
3. **On phase completion:** confirm every step is `[x]` and every Phase Done Criterion passes. Flip row to `✅ Done`, bump counter.
4. **If blocked:** flip to `⛔ Blocked`, append to Blockers Log.
5. **On all phases done:** flip this file's Status to `Done` and run `/spec-check vr-immersive-controls-panel`.

---

## Blockers Log

- **2026-05-02 — Feature flag kills the entire feature.** Quest 3 capture (`logs/fastmediasorter_20260502_035656.log`) shows that `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED=false` ([`app_v2/build.gradle.kts:261`](../../app_v2/build.gradle.kts#L261), [`:310`](../../app_v2/build.gradle.kts#L310)) makes `isImmersiveUiLocked()` return `true` whenever `vrRenderingActive` is on ([`VrPlayerActivity.kt:1024-1025`](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L1024)). Every `OpenControls`/`OpenFileOps` command no-ops and shows `vr_hud_guard_controls`/`_file_ops` banner instead of the interactive panel. The user only sees the «Exit immersive to open it» message. **Fix path:** flip the flag for VR debug flavor, or remove the guard once Phase 03 / 04 / 05 are validated end-to-end.
- **2026-05-02 — Visual ray indicator missing.** `VrControllerRayManager` ([`VrControllerRayManager.kt:18-21`](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt#L18-L21)) declares «No cursor dot — Touch controller users receive hardware LED + haptic feedback.» On Quest 3 with focused XR session no hardware LED is visible to the user. Goal §2.2 «Луч от контроллера виден» fails. **Fix path:** add a billboard quad / line strip rendered from aim-pose to hit point (`Столп A` of strategic §5.1). Phase 02 already wires hit-testing (1449 hover events captured 2026-05-02) — only the visual draw step is missing.

### Field-log — Quest 3 2026-05-02

| § | Goal | Code present | Behaviour on device | Verdict |
|---|------|:-----------:|---------------------|---------|
| §11.1 | X opens HUD with interactive elements | yes (Phase 03) | gated → banner | FAIL (flag) |
| §11.2 | Controller ray visible | partial (math) | no visible cursor | FAIL (no draw) |
| §11.3 | Seek slider movable by ray | yes (Phase 04/05) | unreachable | FAIL (depends on §11.1) |
| §11.4 | Volume / brightness / track / speed in immersive | yes | unreachable | FAIL |
| §11.5 | Stereo-format indicator + manual switch | yes | unreachable | FAIL |
| §11.6 | Auto-hide after 10 s | n/a | unreachable | FAIL |
| §11.7 | FPS ≥ 72 with panel open | n/a | cannot measure | MANUAL |

---

## Change Log

- 2026-04-26 — Initial tactical plan authored by `/spec-tech` (`claude-sonnet-4-6`).

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 0
  - REVIEW applied: 0
  - DISCUSS proposed: 0
  - Clean pass — no findings on INDEX.md.
