---
name: vr-native-two-texture-channels
description: Native OpenXR runtime exposes only 2 texture channels (main quad + HUD quad) - no spatial panels without C++/JNI
type: project
---

The native OpenXR runtime (`NativeDiagnosticXrRuntime` / `libfms_diagnostic_xr.so`, src/vr) exposes exactly **two** texture channels: `queueFrame(rgba,w,h)` (main media quad - sphere/180/flat, driven by `setRenderConfig` projection+layout) and `queueHud(rgba,w,h)` (one flat HUD panel quad). Plus `applyHaptic`, `getVideoSurface`, ray callback `onNativeRayInteraction(uvX,uvY,isHover,isClick)`.

**Why:** All immersive UI must be composited onto one of those two Canvas surfaces as ARGB_8888 bytes. Multiple independently-positioned 3D panels ("spatial panels") are NOT possible without C++/JNI changes to the native slice - out of scope for app-layer specs. `onNativeRayInteraction` UV addresses the **HUD quad** specifically, so any interactive/hit-tested UI (S0963 browse grid, S0964 HUD track rows) must live on the HUD-quad channel; the main quad is view-only media.

**How to apply:** When planning any VR-epic pillar (S0773 family: S0962 done, S0963 immersive browser done, S0964 HUD tracks pending), design UI as a Canvas drawn to `queueHud` with `RectF` regions hit-tested via UV->pixel (mirror `HudCanvasRenderer`/`HudInteractionDispatcher` and the S0963 `ImmersiveBrowseGridRenderer`/`ImmersiveBrowseInteractionDispatcher`). Reject "spatial panels" render-form options in research/quiz - they need native work. In-session state transitions (browse->play) reuse the same session (~5s OpenXR re-init tax per relaunch, `DiagnosticXrRenderThread` KDoc) - never relaunch the Activity per selection. See [[vr-immersive-reentry-hotspot]], [[vr-hud-quirks]].
