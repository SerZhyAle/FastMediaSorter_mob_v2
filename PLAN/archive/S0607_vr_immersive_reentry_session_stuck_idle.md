# S0607 - VR immersive re-entry session stuck in IDLE

**Status:** Archived

## 0. Problem

- Second (and subsequent) immersive entry never renders - the headset shows an endless loading / floating state ("вечный полёт"). First entry works.
- Reproduced on Quest 3, NoLegal-DEBUG build `2.60.6211.605`, captured to `temp/vr_reentry.txt` (enter -> exit -> enter).

## 1. Evidence (captured logcat)

- Entry 1 (cold, 00:30:12) - `creating XrInstance (cold start)`, session state walks `1 -> 2 -> 3 -> 4 -> 5`, frames render, clean exit.
- Entry 2 (re-entry, 00:30:20) - `reusing existing XrInstance 0x1`, then during `xrCreateSession`:
  - `E VrRuntimeClient: Failed to get window type`
  - `java.lang.NullPointerException: ... VolumetricWindowInfo.getType() on a null object reference at com.oculus.vrapi.VrRuntimeClient.getDisplayID`
  - session state reaches only `1` (IDLE) and stays there; no `xrBeginSession`; 6 s of no frames until the user exits.
- `WorldManagerService: VW Content Listener: com.sza.fastmediasorter.debug` (volumetric window registration) arrives at 00:30:20.857 - 24 ms AFTER `xrCreateSession` already ran at 00:30:20.833.

## 2. Root cause

- The `XrInstance` and OpenXR loader are intentionally kept process-scoped (S0291 round 4, to avoid the CheckJNI loader abort). The instance is bound to the Activity it was created with on cold start (`XrInstanceCreateInfoAndroidKHR.applicationActivity` + `xrInitializeLoaderKHR`).
- On exit the Activity is `finish()`-ed. On re-entry a NEW Activity instance is created (logcat: `Activity instance changed across sessions`), but the reused instance/loader still hold the OLD, now-destroyed Activity.
- At `xrCreateSession` the Meta runtime resolves the volumetric window from that stale Activity (`VrRuntimeClient.getDisplayID -> VolumetricWindowInfo.getType()` on null) and logs `xrCreateSession: Activity is not yet in the ready state`. The session is created against no display and the runtime defers readiness against an Activity that never becomes ready, so it stays in `IDLE` forever.
- Cold start works because the instance is bound to the live first Activity.
- Confirmed via the Meta forum resolution for this exact error: it indicates an incorrect/stale Activity reference, not a timing issue. A first attempt (native self-heal session recreate) was disproven on device - recreating the session against the same stale instance still logs `Activity is not yet in the ready state` and stays IDLE.

## 3. Fix

- A keep-the-Activity-alive attempt was rejected on device: the real launch path (player immerse) uses `VrLaunchDeliveryMode.ACTIVITY_RESULT`, whose exit must `setResult` + `finish()` to return the result to the contract launcher, so the Activity is necessarily recreated per entry.
- Native fix instead: recreate the `XrInstance` on every entry, bound to the CURRENT Activity.
- `xr_session_shutdown` now destroys the `XrInstance` (was preserved). `xr_session_init` therefore recreates it each entry via `createInstance`, which passes the current Activity to `XrInstanceCreateInfoAndroidKHR.applicationActivity`.
- The S0291 CheckJNI abort that originally forced the keep-alive is removed at its source: the OpenXR loader is now initialized exactly once with the process-stable Application context (was the per-entry Activity), so its cached JNI ref never goes stale across instance recreate.
- The EGL context stays process-scoped (Activity-agnostic); only the per-Activity EGLSurface is recreated. The recreated instance also restores the ~430 ms `xrCreateInstance` delay per entry, which incidentally covers the volumetric-window registration window.

## 4. Touch points

- `app_v2/src/vr/cpp/xr_session.cpp` - loader init once with Application context (`getApplicationContextLocal` helper + `g_loaderInitialized` + `g_appContextGlobal` process-lifetime global ref); destroy `XrInstance` in `xr_session_shutdown`; recreate per entry in `xr_session_init`. Only file changed.

## 5. Validation

- Build: `.\a.ps1 nd` (noLegal debug) passes (v2.60.6220.128).
- Device: owner confirmed on Quest 3 - immersive renders on the 2nd and 3rd entry (was endless-floating before). Verified.

## 6. Gotcha (JNI)

- The loader's `applicationContext` must be a process-lifetime GLOBAL ref. The Quest OpenXR loader lazily `NewGlobalRef`s that context during the first `xrEnumerate*`, so passing a local ref and deleting it right after `xrInitializeLoaderKHR` aborts under CheckJNI ("invalid local reference ... in call to NewGlobalRef"). Do not detach the long-lived render thread either.
