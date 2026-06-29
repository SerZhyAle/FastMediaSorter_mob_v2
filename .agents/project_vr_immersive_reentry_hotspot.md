---
name: vr-immersive-reentry-hotspot
description: VR immersive 2nd/3rd-entry hang (Quest) - recognition signature, root-cause class, S0607 fix lever, and the logcat capture trap
metadata:
  type: project
---

The "2nd/3rd immersive entry hangs" bug on Quest 3 was a ~2-month recurring battle (owner, resolved 2026-06-22 by [[S0607]]). Lineage of the immersive VR engine: S0249 (engine) -> S0290/S0291 (HUD/zoom/input rounds) -> S0382 -> **S0607** (re-entry fix). Treat `app_v2/src/vr/cpp/xr_session.cpp` + `DiagnosticXrActivity` as a flaky hotspot - changes here need on-device re-entry testing, not just a build.

**Recognition (how the bug looks):** first immerse works; 2nd/3rd shows "вечный полёт" (immersive void, no image rendered, trigger/both-triggers still exit via Android key). Native session is stuck in `XR_SESSION_STATE_IDLE` (state -> 1, never -> 2). Log signature: `OpenXR_SessionImpl: xrCreateSession: Activity is not yet in the ready state` + `E VrRuntimeClient: Failed to get window type` + `NullPointerException ... VolumetricWindowInfo.getType() on null ... VrRuntimeClient.getDisplayID`.

**Root-cause class:** process-scoped OpenXR objects (XrInstance / loader) get bound to the Activity they were created with; on immersive exit that Activity is `finish()`-ed (player immerse uses `VrLaunchDeliveryMode.ACTIVITY_RESULT`, which MUST `setResult`+`finish()` to return its result, so a new Activity is created every entry). The reused instance then points at a dead Activity -> Meta runtime resolves a null VolumetricWindowInfo -> session never reaches READY.

**Why:** keeping the XrInstance alive across entries was a deliberate S0291 choice to dodge a CheckJNI loader abort - but that is exactly what binds it to the stale Activity. "Keep the Activity alive" does NOT work because ACTIVITY_RESULT requires finishing it.

**How to apply (the S0607 fix lever):** recreate the `XrInstance` every entry bound to the CURRENT Activity (destroy it in `xr_session_shutdown`); initialize the OpenXR loader exactly once with the process-stable **Application context** as a **GLOBAL ref** kept for the whole process. JNI gotcha that crashed a build mid-fix: the Quest loader lazily `NewGlobalRef`s that context during the first `xrEnumerate*`, so a local ref deleted right after `xrInitializeLoaderKHR` aborts under CheckJNI ("invalid local reference ... in call to NewGlobalRef"); never detach the long-lived render thread. Keep EGL context process-scoped. See [[vr-immersive-logcat-capture-trap]] for how to capture proof.
