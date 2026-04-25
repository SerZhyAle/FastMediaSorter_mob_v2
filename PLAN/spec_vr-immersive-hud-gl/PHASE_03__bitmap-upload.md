# Phase 03 — Bitmap Upload Path (Canvas Bitmap → HUD swapchain)

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05, Phase 06, Phase 07
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Turn `nativeUploadHudBitmap` from a stub into a real upload: accept an Android `Bitmap` (ARGB_8888, premultiplied), lock pixels, push them into the acquired HUD swapchain image via `glTexSubImage2D`, release the image. On the Kotlin side introduce a thin `VrHudRenderer` that owns the HUD size and a reusable `Bitmap`, and calls into the native upload through `OpenXrSessionManager`.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Phase 02 is `✅ Done` — dark rectangle visible in immersive.
- [ ] Developer can run the `vrDebug` variant on a Quest 3 and view the logcat.
- [ ] `OpenXrNative.cpp` freshly backed up for this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 2900 |
| `app_v2/src/main/cpp/CMakeLists.txt` or `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | ≤ 100 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` | New | ≤ 250 |

> If the VR CMake file already links `android` (for `ALog`), the `jnigraphics` addition in Step 3.1 is a single line. Confirm by grepping the file first.

---

## Steps

### Step 3.1 — Link `jnigraphics` in the VR CMake target

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt` (or wherever the VR native target is declared)
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the CMake file that defines the VR native library target (`target_link_libraries(openxr_native ..)` is the anchor). Append `jnigraphics` to the link list — it ships with the NDK and provides `AndroidBitmap_lockPixels` / `AndroidBitmap_unlockPixels` / `AndroidBitmap_getInfo`. Also add `#include <android/bitmap.h>` to the top of `OpenXrNative.cpp` if it is not already included.

**Verification:**

- `Grep` — pattern `jnigraphics` in the VR CMake file returns exactly one hit.
- `Grep` — pattern `#include <android/bitmap.h>` in `app_v2/src/vr/cpp/OpenXrNative.cpp` returns exactly one hit.
- `/build` skill compiles `vrDebug` — linker resolves `AndroidBitmap_lockPixels`.

**Status:** `[ ]` not done

---

### Step 3.2 — Implement `nativeUploadHudBitmap` fully

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 3.1

**Prompt for developer:**

> Backup first: `Copy-Item app_v2/src/vr/cpp/OpenXrNative.cpp temp/OpenXrNative_phase03_$(Get-Date -Format yyyyMMdd_HHmm).cpp.bak`. Replace the Phase-01 stub body of `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeUploadHudBitmap` with the real implementation. Order of operations:
>
> 1. Acquire `g_ctxMutex`. Early-return `JNI_FALSE` if `g_ctx.hudSwapchain == XR_NULL_HANDLE` or `!g_ctx.sessionRunning`.
> 2. `AndroidBitmap_getInfo` on the `bitmap` jobject. Reject (return `JNI_FALSE`, log `LOGW`) if `format != ANDROID_BITMAP_FORMAT_RGBA_8888`, or dimensions do not match `g_ctx.hudSwapchainWidth/Height`.
> 3. `AndroidBitmap_lockPixels` — error return on non-zero.
> 4. `xrAcquireSwapchainImage` + `xrWaitSwapchainImage` on `hudSwapchain` to get the GL texture ID (the same image enumeration trick used in Phase 02).
> 5. Bind the image as `GL_TEXTURE_2D`, `glPixelStorei(GL_UNPACK_ALIGNMENT, 4)`, then `glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels)`.
> 6. `glFinish()` is NOT required — OpenXR's `xrReleaseSwapchainImage` provides the barrier.
> 7. `xrReleaseSwapchainImage`.
> 8. `AndroidBitmap_unlockPixels`.
> 9. Return `JNI_TRUE`.
>
> Every `xr*` failure logs `LOGE("HUD upload: xr<Name> failed: %d", r)`. Every Android bitmap failure logs `LOGW`. Throttle identical failure logs via a static `bool` to avoid spamming the logcat on every frame.

**Verification:**

- `Grep` — pattern `AndroidBitmap_lockPixels` in `OpenXrNative.cpp` returns at least one hit.
- `Grep` — pattern `xrAcquireSwapchainImage.*hudSwapchain` returns at least two hits (Phase 02 acquire-pump + Phase 03 upload) OR exactly one if Phase 02's pump was refactored to share this path.
- `Grep` — pattern `glTexSubImage2D` in `OpenXrNative.cpp` returns at least one hit.
- `/build` skill compiles `vrDebug` without errors.
- Device test: call `nativeUploadHudBitmap` from a throw-away Kotlin test with a solid red Bitmap; the HUD rectangle turns red. If it stays dark, check `AndroidBitmap_getInfo` result and swapchain image binding.

**Status:** `[ ]` not done

---

### Step 3.3 — Prevent double acquire: share the swapchain-pump between idle and upload paths

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 3.2

**Prompt for developer:**

> Phase 02 Step 2.4 added an acquire/wait/release "pump" for each frame so the swapchain index advances even without uploads. With Step 3.2 now doing its own acquire/wait/release, both paths fighting for the same swapchain image produces `XR_ERROR_CALL_ORDER_INVALID`. Refactor so only ONE acquire/wait/release happens per frame:
>
> - Introduce a per-frame flag `bool hudFrameUploaded` inside `renderFrame()`.
> - Expose (only inside the translation unit) a helper `bool consumeHudUpload()` that a successful `nativeUploadHudBitmap` can set. The flag clears when `renderFrame` starts.
> - In `renderFrame`, after the per-frame HUD compositing opportunity has passed: if `!hudFrameUploaded && hudLayerVisible`, run the no-op acquire/wait/release pump so the image index still advances.
> - If `hudFrameUploaded`, skip the pump — `nativeUploadHudBitmap` already did the acquire/release.
>
> Document in a comment why the flag exists: "Runtime demands exactly one acquire/release per swapchain per frame."

**Verification:**

- `Grep` — pattern `hudFrameUploaded` in `OpenXrNative.cpp` returns at least three hits (declaration, set on upload, check in renderFrame).
- Device test: sustained HUD uploads for ≥ 30 s (drive via test activity) produce no `XR_ERROR_CALL_ORDER_INVALID` lines in the logcat.

**Status:** `[ ]` not done

---

### Step 3.4 — Create `VrHudRenderer` Kotlin class

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt`
**Depends on:** Step 3.3

**Prompt for developer:**

> Create a new file `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` with class `VrHudRenderer` (note: not `Manager` — follows the existing `VrStereoRenderer` / `VrPhotoSphereRenderer` naming in the same package). Responsibilities:
>
> - Constructor args: `sessionManager: OpenXrSessionManager`, `width: Int = 1024`, `height: Int = 256`.
> - Holds a private `Bitmap` field of `Bitmap.Config.ARGB_8888`, `isPremultiplied = true`, created at first use (lazy).
> - `fun ensureSwapchainCreated(): Boolean` — idempotent; calls `sessionManager.createHudSwapchain(width, height)` once; stores the result.
> - `fun submit(producer: (Canvas) -> Unit): Boolean` — locks canvas on the internal bitmap, invokes `producer(canvas)`, uploads via `sessionManager.uploadHudBitmap(bitmap)`. Returns the upload result.
> - `fun setVisible(visible: Boolean)` — pass-through to `sessionManager.setHudLayerVisible`.
> - `fun release()` — calls `sessionManager.destroyHudSwapchain()` and recycles the bitmap.
> - No allocations inside `submit` — the Bitmap and the Canvas are reused.
> - No threading inside this class — callers (the activity / lifecycle scope) decide when to call.
> - Timber-only logging with tag derived by class name. Zero `Log.d(` calls.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` exists.
- `Grep` — pattern `class VrHudRenderer` returns exactly one hit in that file.
- `Grep` — pattern `fun submit(producer: (Canvas) -> Unit)` returns exactly one hit.
- `Grep` — pattern `fun ensureSwapchainCreated` returns exactly one hit.
- `Grep` — pattern `fun setVisible` returns exactly one hit.
- `Grep` — pattern `fun release` returns exactly one hit.
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` returns zero hits.
- File ≤ 250 LOC.
- `/build` skill compiles `vrDebug` without errors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — `/build` on `vrDebug`.
- [ ] Smoke: a one-line debug test from `VrPlayerActivity` (temporary — remove before Phase 05) that calls `VrHudRenderer.submit { it.drawColor(Color.RED) }` once at session ready paints the HUD quad red.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits in the repository.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] No public API added to `main` source set — catalog regen deferred to Phase 07.

---

## Handoff Notes to Next Phase

Phase 04 assumes:

- Any `(Canvas) -> Unit` drawing block submitted to `VrHudRenderer` paints into the HUD quad.
- The bitmap surface is ARGB_8888 premultiplied; Canvas is size `1024 × 256` unless overridden.
- The Kotlin caller does NOT worry about acquire/release — native handles it.
- Latency between `submit` call and compositor frame: ~1 render frame; safe to call from the main thread.

---

## Rollback Plan

Revert the phase commit(s). Phase 02 leaves a dark rectangle, which is safe. Native upload path is gone, Kotlin `VrHudRenderer` file is deleted. No catalog or docs changes so far.
