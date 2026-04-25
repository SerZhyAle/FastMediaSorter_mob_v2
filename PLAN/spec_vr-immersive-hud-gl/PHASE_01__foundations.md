# Phase 01 — Foundations

**Strategic spec:** [`../spec_vr-immersive-hud-gl.md`](../spec_vr-immersive-hud-gl.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — this is the foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06, Phase 07
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Introduce the `VR_UI_COMPOSITION_LAYER_ENABLED` BuildConfig flag on the `vr` flavor, add the native bridge scaffolding (empty HUD swapchain fields in `g_ctx`, JNI entry points as stubs that return success without doing XR work), and declare the matching Kotlin facade calls. No swapchain is created yet; no rendering yet.

---

## Prerequisites

Check each before starting Step 1:

- [ ] Working tree is clean or changes are on a feature branch for this phase.
- [ ] `app_v2/build.gradle.kts` `vr` flavor block is located (search anchor: `create("vr")`).
- [ ] `app_v2/src/vr/cpp/OpenXrNative.cpp` is backed up before edits (it is > 2500 LOC — see Step 1.2 backup action).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 400 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 2800 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt` | Modified | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 550 |

> `OpenXrNative.cpp` already exceeds 1000 LOC and is a legacy native bridge — backups before every phase edit are mandatory until a follow-up refactor splits it.
> `OpenXrSessionManager.kt` is currently ~485 LOC and will cross 500 LOC after Step 1.5 — backup is mandatory per CLAUDE.md rule 5.

---

## Steps

### Step 1.1 — Add `VR_UI_COMPOSITION_LAYER_ENABLED` BuildConfig flag to the vr flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, locate the `productFlavors { .. create("vr") { .. } }` block. Inside the `vr` flavor body, add a new `buildConfigField` that declares a boolean flag named `VR_UI_COMPOSITION_LAYER_ENABLED` with default value `false`. Place the new field alphabetically close to the existing `SUPPORT_VR_PLAYER` field to keep the block tidy. Do not add this field to any other flavor — the HUD native pipeline builds only under `vr`.

**Verification:**

- `Grep` — pattern `VR_UI_COMPOSITION_LAYER_ENABLED` in `app_v2/build.gradle.kts` returns exactly one hit inside the `vr` flavor block.
- `Grep` — pattern `VR_UI_COMPOSITION_LAYER_ENABLED` anywhere else in `app_v2/build.gradle.kts` returns zero hits.
- `/build` skill compiles the `vrDebug` variant without errors.

**Status:** `[ ]` not done

---

### Step 1.2 — Add HUD swapchain fields to native `g_ctx` + backup `OpenXrNative.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 1.1

**Prompt for developer:**

> Backup first: `Copy-Item app_v2/src/vr/cpp/OpenXrNative.cpp temp/OpenXrNative_$(Get-Date -Format yyyyMMdd_HHmm).cpp.bak`. Then in `OpenXrNative.cpp`, locate the `g_ctx` structure definition (search anchor: `struct Context`). Add the following fields (grouped under a new comment `// HUD composition layer (spec_vr-immersive-hud-gl)`):
>
> - `XrSwapchain hudSwapchain = XR_NULL_HANDLE;`
> - `uint32_t hudSwapchainWidth = 0;`
> - `uint32_t hudSwapchainHeight = 0;`
> - `std::atomic<bool> hudLayerVisible{false};`
> - `std::vector<uint32_t> hudSwapchainImageHandles;` (match the existing image-handle storage pattern used for eye swapchains).
>
> Do NOT call `xrCreateSwapchain` yet — that happens in Phase 02. Do NOT touch `renderFrame` yet — that happens in Phase 02. Do NOT add destroy logic yet — add a TODO line `// TODO(phase-02): xrDestroySwapchain(hudSwapchain)` next to `destroyAll()` so Phase 02 knows where to wire it.

**Verification:**

- `Glob` — `temp/OpenXrNative_*.cpp.bak` exists (backup artifact).
- `Grep` — pattern `hudSwapchain` in `OpenXrNative.cpp` returns at least 4 hits (declarations + TODO).
- `Grep` — pattern `xrCreateSwapchain.*hudSwapchain` returns zero hits (invariant: no XR resource created yet).
- `/build` skill compiles the `vrDebug` variant without errors.

**Status:** `[ ]` not done

---

### Step 1.3 — Add JNI stubs for HUD lifecycle + bitmap upload + visibility

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 1.2

**Prompt for developer:**

> In `OpenXrNative.cpp`, append four new `extern "C" JNIEXPORT` entry points at the bottom of the file (after the existing `nativeTriggerHaptic` block), each following the naming pattern `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_native<Name>`:
>
> 1. `nativeCreateHudSwapchain(jint width, jint height) → jboolean` — for now just stores `width`/`height` into `g_ctx.hudSwapchainWidth/Height` under `g_ctxMutex` and returns `JNI_TRUE`. Logs via `LOGI("nativeCreateHudSwapchain: requested %dx%d (stub)", w, h)`.
> 2. `nativeDestroyHudSwapchain() → void` — for now just clears the stored dimensions and logs `LOGI("nativeDestroyHudSwapchain: stub")`.
> 3. `nativeSetHudLayerVisible(jboolean visible) → void` — stores `visible` into `g_ctx.hudLayerVisible` atomic. Logs at `LOGD` level.
> 4. `nativeUploadHudBitmap(jobject bitmap) → jboolean` — for now just checks `bitmap != nullptr`, returns `JNI_TRUE` or `JNI_FALSE` without locking Android pixels. Logs `LOGD("nativeUploadHudBitmap: stub (bitmap=%p)", bitmap)`.
>
> All four must acquire `g_ctxMutex` before touching `g_ctx`. No `xr*` calls in any of these stubs — real work is added in Phase 02 (swapchain) and Phase 03 (bitmap upload).

**Verification:**

- `Grep` — pattern `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeCreateHudSwapchain` returns exactly one hit in `OpenXrNative.cpp`.
- `Grep` — pattern `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeDestroyHudSwapchain` returns exactly one hit.
- `Grep` — pattern `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeSetHudLayerVisible` returns exactly one hit.
- `Grep` — pattern `Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeUploadHudBitmap` returns exactly one hit.
- `Grep` — pattern `xrCreateSwapchain` anywhere inside the four new entry points returns zero hits.
- `/build` skill compiles `vrDebug` — native library links without unresolved JNI symbols.

**Status:** `[ ]` not done

---

### Step 1.4 — Declare matching `external` Kotlin bindings in `OpenXrNative.kt`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt`
**Depends on:** Step 1.3

**Prompt for developer:**

> In `OpenXrNative.kt`, add four `@JvmStatic external` function declarations at the end of the `object OpenXrNative` block (after `nativeTriggerHaptic`). Signatures:
>
> - `fun nativeCreateHudSwapchain(width: Int, height: Int): Boolean`
> - `fun nativeDestroyHudSwapchain()`
> - `fun nativeSetHudLayerVisible(visible: Boolean)`
> - `fun nativeUploadHudBitmap(bitmap: android.graphics.Bitmap): Boolean`
>
> Each declaration gets a short KDoc (one line) describing the call. The file is the only entry point to native; no other Kotlin file may `external fun` into libopenxr_native.

**Verification:**

- `Grep` — pattern `fun nativeCreateHudSwapchain` in `OpenXrNative.kt` returns exactly one hit.
- `Grep` — pattern `fun nativeDestroyHudSwapchain` returns exactly one hit.
- `Grep` — pattern `fun nativeSetHudLayerVisible` returns exactly one hit.
- `Grep` — pattern `fun nativeUploadHudBitmap` returns exactly one hit.
- `Grep` — pattern `external fun native` (case sensitive) returns the same number of hits as native methods declared — no orphan declarations.
- `/build` skill compiles `vrDebug` — Kotlin compilation passes (JNI link resolves at runtime against Step 1.3 stubs).

**Status:** `[ ]` not done

---

### Step 1.5 — Expose HUD lifecycle through `OpenXrSessionManager`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`
**Depends on:** Step 1.4

**Prompt for developer:**

> Backup first if the file is still at 485+ LOC: `Copy-Item app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt temp/OpenXrSessionManager_$(Get-Date -Format yyyyMMdd_HHmm).kt.bak`. Then in `OpenXrSessionManager.kt`, add four pass-through methods to the `OpenXrSessionManager` class (placed right below `fun eyeHeight` / above the `companion object`):
>
> - `fun createHudSwapchain(width: Int, height: Int): Boolean` — if `!running.get()` return `false`; else `return OpenXrNative.nativeCreateHudSwapchain(width, height)`.
> - `fun destroyHudSwapchain()` — if `running.get()` then `OpenXrNative.nativeDestroyHudSwapchain()`.
> - `fun setHudLayerVisible(visible: Boolean)` — no running check; safe to call when session is idle (native stub handles it): `OpenXrNative.nativeSetHudLayerVisible(visible)`.
> - `fun uploadHudBitmap(bitmap: android.graphics.Bitmap): Boolean` — if `!running.get()` return `false`; else `return OpenXrNative.nativeUploadHudBitmap(bitmap)`.
>
> Every method is a thin pass-through. No `drainNativeLog()` calls — native log collection is unchanged. Use `Timber.v` (verbose) to log entries at a debug build only.

**Verification:**

- `Grep` — pattern `fun createHudSwapchain` in `OpenXrSessionManager.kt` returns exactly one hit.
- `Grep` — pattern `fun destroyHudSwapchain` returns exactly one hit.
- `Grep` — pattern `fun setHudLayerVisible` returns exactly one hit.
- `Grep` — pattern `fun uploadHudBitmap` returns exactly one hit.
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` returns zero hits (Timber-only rule).
- File length ≤ 550 LOC (projected +40 LOC net).
- `/build` skill compiles `vrDebug` without errors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

All of the following must hold for this phase to flip to `✅ Done`:

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run the `/build` skill on the `vrDebug` variant.
- [ ] `Grep` for `VR_UI_COMPOSITION_LAYER_ENABLED` in Kotlin source returns zero hits (not yet consumed — that is Phase 06).
- [ ] `Grep` for `xrCreateSwapchain.*hud` in `OpenXrNative.cpp` returns zero hits (invariant: still Phase-01 scaffolding).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1 "<path>" "phase-01" "<description>"`.
- [ ] No public API added outside of native-bridge files — catalog regen is deferred to Phase 07.

---

## Handoff Notes to Next Phase

Phase 02 assumes:

- Kotlin side can call four HUD lifecycle methods on `OpenXrSessionManager` — methods return success but do nothing visible.
- Native `g_ctx` has `hudSwapchain`, `hudSwapchainImageHandles`, `hudLayerVisible`, `hudSwapchainWidth/Height` ready to be filled.
- The TODO marker `TODO(phase-02): xrDestroySwapchain(hudSwapchain)` sits next to `destroyAll()`.
- `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED` exists as `false`; nothing reads it yet.

---

## Rollback Plan

Revert the phase commit(s) — no user-facing surface changed, no data migration, no runtime behavior difference. Native library recompiles cleanly on the prior revision.
