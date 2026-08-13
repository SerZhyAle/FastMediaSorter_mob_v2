# Phase 02 - Native Runtime

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 6 / 6 (Steps 02.1..02.5 scaffold + Step 02.6 session bring-up)
**Started:** 2026-05-19
**Completed:** 2026-05-19 (step 02.6 closed 2026-05-19 11:13)

---

## Objective

Add the VR-only OpenXR loader dependency and a minimal native runtime bridge for a static equirect diagnostic image.

---

## Prerequisites

- [ ] Phase 01 is Done.
- [ ] OpenXR loader version blocker in `INDEX.md` is closed.
- [ ] `XR_KHR_composition_layer_equirect2` decision blocker in `INDEX.md` is closed.
- [ ] Existing comments in `app_v2/build.gradle.kts`, `app_v2/src/vr/AndroidManifest.xml`, and `app_v2/src/vr/cpp/CMakeLists.txt` are read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | existing >500, backup required |
| `app_v2/src/vr/AndroidManifest.xml` | Modified | <= 160 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | <= 160 |
| `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` | New | <= 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt` | New | <= 180 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` | New | <= 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt` | Modified | <= 120 |

---

## Steps

### Step 02.1 - Add VR-scoped OpenXR dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** start of phase

**Prompt for developer:**

> Create a timestamped backup of `app_v2/build.gradle.kts` in `temp/`, then add the selected official Khronos `openxr_loader_for_android` dependency with `vrImplementation` only. Do not add it to `implementation`, `standardImplementation`, or `noLegalImplementation` directly; `noLegal` inherits VR sources but dependency policy must be verified by the build.

**Verification:**

- `Grep` - `vrImplementation("org.khronos.openxr:openxr_loader_for_android:` appears exactly once in `app_v2/build.gradle.kts`.
- `Grep` - `implementation("org.khronos.openxr:openxr_loader_for_android` returns zero hits.
- `Glob` - `temp/*build.gradle.kts*` contains the timestamped backup.

**Status:** `[x]` done (2026-05-19)

---

### Step 02.2 - Verify manifest merge requirements

**Files:** `app_v2/src/vr/AndroidManifest.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Ensure the VR manifest has only app-side declarations needed for Quest and Android XR. Rely on the Khronos AAR manifest for OpenXR runtime query declarations unless the selected loader version requires manual entries.

**Verification:**

- `Grep` - `com.oculus.intent.category.VR` appears only if already required by S0245 rules or a documented S0249 decision.
- `Grep` - `org.khronos.openxr.permission.OPENXR_SYSTEM` appears only if the selected AAR version does not merge it automatically.
- `Grep` - `android.hardware.vr.headtracking` still appears if required by S0245 manifest policy.

**Status:** `[x]` done (2026-05-19)

---

### Step 02.3 - Add native runtime source

**Files:** `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp`, `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the native source file and CMake target wiring for a minimal diagnostic runtime bridge. The native layer must expose JNI methods for `probeExtensions`, `startSession`, `presentStaticImage`, and `requestExit`, and it must query extensions before enabling optional equirect composition.

**Verification:**

- `Glob` - `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` exists.
- `Grep` - `xrEnumerateInstanceExtensionProperties` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `XR_KHR_composition_layer_equirect2` appears in `diagnostic_xr_runtime.cpp`.
- `Grep` - `diagnostic_xr_runtime.cpp` appears in `CMakeLists.txt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 02.4 - Add Kotlin runtime facade

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a Kotlin facade around the native bridge. Keep lifecycle operations suspend-friendly and return structured results; load the native library from the VR source set only.

**Verification:**

- `Glob` - both runtime Kotlin files exist.
- `Grep` - `interface DiagnosticXrRuntime` appears in `DiagnosticXrRuntime.kt`.
- `Grep` - `System.loadLibrary` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `Timber` appears in `NativeDiagnosticXrRuntime.kt`.
- `Grep` - `Log.d(` returns zero hits in both runtime Kotlin files.

**Status:** `[x]` done (2026-05-19)

---

### Step 02.5 - Bind runtime facade

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Bind `DiagnosticXrRuntime` in the existing VR Hilt module and inject it into `XrEntryGatewayImpl`. Map runtime failures to `XrEntryResult.InitializationFailed`.

**Verification:**

- `Grep` - `DiagnosticXrRuntime` appears in `XrModule.kt`.
- `Grep` - `NativeDiagnosticXrRuntime` appears in `XrModule.kt`.
- `Grep` - `DiagnosticXrRuntime` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `InitializationFailed` appears in `XrEntryGatewayImpl.kt`.

**Status:** `[x]` done (2026-05-19)

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x]` done.
- [x] Project compiles - `assembleStandardDebug` PASS (v2.60.5190.118), `assembleNoLegalDebug` PASS (native lib `libfms_diagnostic_xr.so` built for arm64-v8a + x86_64, OpenXR linker resolution OK, package merged).
- [x] Dev log entries added for all 7 files in "Files Touched".
- [x] Catalog scan/render done.

## Implementation notes (2026-05-19)

- OpenXR Android loader version: **1.1.57** (latest stable from Khronos Maven).
- CMake gating: `FMS_BUILD_XR_RUNTIME=ON` is passed only by `vr` and `noLegal` flavors via `cmake.arguments`. Other flavors hit the `else()` branch and emit no native target — no OpenXR linkage occurs.
- `buildFeatures.prefab = true` enabled so `find_package(OpenXR REQUIRED CONFIG)` resolves the AAR's CMake config.
- Native include order fix: `EGL/egl.h` + `GLES3/gl3.h` must precede `openxr/openxr_platform.h` because the platform header references `EGLenum` / `GLenum` in `XrSwapchainImageOpenGLESKHR`.
- `XR_KHR_composition_layer_equirect2` probe call is present in `diagnostic_xr_runtime.cpp`; the boolean result is cached in `g_state.equirect2Supported` and surfaced through `nativeHasEquirect2()` → `DiagnosticXrRuntime.hasEquirect2Layer()`. The actual layer choice (native equirect2 vs sphere mesh) is decided by Phase 03 when the asset upload path lands.
- Session creation in `nativeStartSession` is **scaffolded** — instance + system are acquired but session creation is deferred to Phase 03 (needs GLES context + asset). The intermediate state currently returns `SessionCreationFailed`, which `XrEntryGatewayImpl` maps to `XrEntryResult.InitializationFailed`. This is intentional — Phase 02 verifies wiring only, Phase 03 completes the runtime. **Closed 2026-05-19 by Step 02.6 below — `xrCreateSession` now runs in a dedicated `DiagnosticXrActivity` on a real render thread.**

---

## Step 02.6 - Session bring-up (added 2026-05-19 02:57; landed 2026-05-19 11:13)

**Files:** `app_v2/src/vr/cpp/xr_session.cpp` (new, ~672 LOC), `app_v2/src/vr/cpp/xr_session.h` (new, ~81 LOC), `app_v2/src/vr/cpp/diagnostic_xr_runtime.cpp` (modified — JNI surface trimmed to ~100 LOC), `app_v2/src/vr/cpp/CMakeLists.txt` (modified — new source), `app_v2/src/vr/AndroidManifest.xml` (modified — `DiagnosticXrActivity` declared with `com.oculus.intent.category.VR`), `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` (new, ~199 LOC), `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrRenderThread.kt` (new, ~78 LOC), `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt` (refactor — startActivity instead of JNI), `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/DiagnosticXrRuntime.kt` (re-shaped interface), `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/runtime/NativeDiagnosticXrRuntime.kt` (re-shaped JNI surface).
**Depends on:** Step 02.5

**Prompt for developer:** historical record — owner correction 2026-05-19 02:57 reopened the phase after S0245 + Phase 02.5 scaffolded the button without a working session. Brief for the implementation agent lived in the chat transcript that triggered this step. Done conditions below.

**Verification (all passed 2026-05-19 11:13):**

- `assembleNoLegalDebug` exit 0 (1m 9s); APK `FastMediaSorter_noLegal_debug_v2.60.5191.111-NoLegal-DEBUG.apk` shipped to `DOWNLOADS/`.
- `assembleStandardDebug` exit 0 (31s) — phone flavors still compile, no `src/main/` references leaked to vr-only types.
- Manifest merge: `DiagnosticXrActivity` present in `app_v2/build/intermediates/merged_manifests/noLegalDebug/processNoLegalDebugManifest/AndroidManifest.xml` with `android.intent.category.DEFAULT` + `com.oculus.intent.category.VR`. OpenXR loader AAR auto-merges `org.khronos.openxr.permission.OPENXR` + `OPENXR_SYSTEM`.
- `grep -rn "BuildConfig.SUPPORT_VR_PLAYER\|BuildConfig.IS_NO_LEGAL_FLAVOR" app_v2/src/vr/` → 0 hits (Rule 15 clean).
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exit 0.

**Status:** `[x]` done (2026-05-19 11:13)

---

## Handoff Notes to Next Phase

VR-only runtime wiring exists and phone flavors remain free of OpenXR classes and dependencies.

---

## Rollback Plan

Revert Phase 02 commit(s), remove the OpenXR dependency, and restore `app_v2/build.gradle.kts` from the timestamped backup if needed.
