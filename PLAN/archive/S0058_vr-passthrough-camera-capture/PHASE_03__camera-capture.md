# Phase 03 — Camera2 Capture Flow

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-05

---

## Objective

Implement the full Camera2 passthrough capture flow in `VrBrowsePassthroughCaptureManager.launch()`: runtime permission request → open first available passthrough `CameraDevice` → create `CameraCaptureSession` → single `TEMPLATE_STILL_CAPTURE` request → JPEG byte array returned via callback. Phase ends when a valid JPEG byte array is obtained in-memory; file save is Phase 04.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `VrBrowsePassthroughCaptureManager.kt` has the stub `launch()` from Phase 02.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt` | Modified | ≤ 250 |

> File may approach 250 lines after this phase — monitor. If it exceeds 250, extract helpers into `VrPassthroughCameraSession.kt` in the same package.

---

## Steps

### Step 03.1 — Implement runtime permission check + request flow

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the stub `launch()` with a real implementation. The entry point must:
>
> 1. Check `ActivityCompat.checkSelfPermission(activity, HEADSET_CAMERA_PERMISSION) == PERMISSION_GRANTED`.
> 2. If granted — call `capturePassthrough(activity, resource, onFileSaved)` directly.
> 3. If not granted — attach a headless `VrPermissionBridgeFragment` to the activity's `supportFragmentManager`, which calls `requestPermissions(arrayOf(HEADSET_CAMERA_PERMISSION), REQUEST_CODE)`. In `onRequestPermissionsResult`, if granted call `capturePassthrough()`; if denied (and `shouldShowRequestPermissionRationale` is false) show a `Snackbar` pointing to app settings; if denied but rationale still showable, show a short `Toast` explaining why the permission is needed.
>
> Add constants at the top of the file:
> ```kotlin
> private const val HEADSET_CAMERA_PERMISSION = "horizonos.permission.HEADSET_CAMERA"
> private const val PERM_REQUEST_CODE = 0x5800  // S0058
> ```
>
> `VrPermissionBridgeFragment` is a private inner class (or top-level in the same file) extending `Fragment`. It does NOT inflate any layout (`onCreateView` returns null). It removes itself from the fragment manager after receiving the result.
>
> Imports to add: `android.Manifest`, `androidx.core.app.ActivityCompat`, `androidx.core.content.ContextCompat`, `android.content.pm.PackageManager`, `android.widget.Toast`, `com.google.android.material.snackbar.Snackbar`, `android.provider.Settings`, `android.content.Intent`.

**Verification:**

- `Grep` — `HEADSET_CAMERA_PERMISSION` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `VrPermissionBridgeFragment` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `onRequestPermissionsResult` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `Log\.d\(` — zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified, ~128 LOC). Dev log recorded.

---

### Step 03.2 — Implement `capturePassthrough()` Camera2 flow

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement `capturePassthrough(activity: FragmentActivity, resource: MediaResource, onFileSaved: (String) -> Unit)` as a private `suspend fun` (or coroutine-launching fun). It must:
>
> 1. **Resolve passthrough camera ID**: iterate `CameraManager.cameraIdList`, pick the first id where `CameraCharacteristics.get(sourceKey) == 0` (same key as `isAvailable()`). If none found, show a `Toast(R.string.passthrough_capture_unavailable)` and return.
>
> 2. **Open `CameraDevice`** via `CameraManager.openCamera(id, stateCallback, handler)`. The `stateCallback` must handle `onOpened`, `onDisconnected`, `onError` — on any failure, show `Toast(R.string.passthrough_capture_error)` and close if non-null.
>
> 3. **Create `ImageReader`**: `ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 2)`. Set `OnImageAvailableListener` that acquires the image, reads `planes[0].buffer` into a `ByteArray`, closes the image, and delivers the bytes via a `CompletableDeferred<ByteArray>`.
>
> 4. **Create `CameraCaptureSession`** with `listOf(imageReader.surface)` as outputs. In `onConfigured`, build a `CaptureRequest` using `CameraDevice.TEMPLATE_STILL_CAPTURE`, add `imageReader.surface` as target, set `CONTROL_AE_MODE_ON`. Call `session.capture(request, null, null)`.
>
> 5. **Await** the `CompletableDeferred<ByteArray>` with a 3-second timeout. On timeout: show `Toast(R.string.passthrough_capture_timeout)` and close camera + ImageReader.
>
> 6. **Close** `CameraCaptureSession`, `CameraDevice`, and `ImageReader` immediately after bytes are received.
>
> 7. Deliver bytes to `onJpegCaptured(bytes, resource, onFileSaved)` (Phase 04 implements this method; stub it as `TODO("Phase 04")` here).
>
> Run the Camera2 callbacks on a `HandlerThread` (create + start + destroy per-capture). Keep all UI feedback (toasts) dispatched to Main.
>
> Imports to add: `android.hardware.camera2.*`, `android.media.ImageReader`, `android.graphics.ImageFormat`, `android.os.Handler`, `android.os.HandlerThread`, `kotlinx.coroutines.*`.

**Verification:**

- `Grep` — `ImageFormat.JPEG` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `TEMPLATE_STILL_CAPTURE` present in that file.
- `Grep` — `CompletableDeferred` present in that file.
- `Grep` — `HandlerThread` present in that file.
- `Grep` — `TODO("Phase 04")` present in that file (marks the handoff boundary).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified). Dev log recorded.

---

### Step 03.3 — Add haptic feedback on capture trigger

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `capturePassthrough()`, immediately after the `session.capture()` call (before awaiting the deferred), fire haptic feedback:
>
> ```kotlin
> val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
>     vibrator?.vibrate(android.os.VibrationEffect.createOneShot(80L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
> } else {
>     @Suppress("DEPRECATION")
>     vibrator?.vibrate(80L)
> }
> ```
>
> Add `android.os.Build` and `android.os.Vibrator` imports.

**Verification:**

- `Grep` — `VibrationEffect.createOneShot` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `vibrate(80L)` present (fallback path).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified). Dev log recorded.

---

### Step 03.4 — Add shutter flash overlay

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `capturePassthrough()`, immediately after the `session.capture()` call, dispatch on Main:
>
> ```kotlin
> withContext(Dispatchers.Main) {
>     val root = activity.window.decorView.rootView
>     val flash = android.view.View(activity).apply {
>         setBackgroundColor(android.graphics.Color.WHITE)
>         alpha = 0.85f
>     }
>     (root as? android.view.ViewGroup)?.addView(
>         flash,
>         android.view.ViewGroup.LayoutParams(
>             android.view.ViewGroup.LayoutParams.MATCH_PARENT,
>             android.view.ViewGroup.LayoutParams.MATCH_PARENT,
>         )
>     )
>     flash.animate().alpha(0f).setDuration(220L).withEndAction {
>         (root as? android.view.ViewGroup)?.removeView(flash)
>     }.start()
> }
> ```

**Verification:**

- `Grep` — `flash.animate().alpha` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `removeView(flash)` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep` — `TODO("Phase 04")` still present (intentional handoff marker — will be removed in Phase 04).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `capturePassthrough()` obtains a valid JPEG `ByteArray` and delivers it to `onJpegCaptured()` stub.
- Camera2 session lifecycle: open → capture → close (no resource held between captures).
- Haptic + flash fire at capture trigger (before JPEG is ready, feels immediate).
- Phase 04 replaces `TODO("Phase 04")` with actual file-save + toast feedback.

---

## Rollback Plan

Revert phase commit(s). Camera2 code is VR-only; non-VR builds unaffected.
