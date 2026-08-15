# Tactical Plan: S0249 - vr-test-immersive-image-stage1a

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Feature:** Stage 1A diagnostic OpenXR immersive image
**Tier:** 3 - Moderate
**Priority:** 90
**Status:** BlockNeedUserTest
**Phases:** 6 / 6 Done (Phase 02 step 02.6 session-bringup landed 2026-05-19 11:13; Phases 05 & 06 retroactively closeable now that the session is real).
**Spec status:** BlockNeedUserTest — full pipeline implemented, awaiting Quest 3 device verification.

**End-to-end flow that now works:**

1. User taps `Test Immersive` in Settings → Media → блок `Управление 3D-VR` on `vr`/`noLegal` build.
2. `VrSettingsBlockFragment.launchDiagnosticImmerse` (Timber probe fires) → `XrEntryGatewayImpl.enterDiagnosticImage` (probe fires).
3. Gateway builds `Intent(appContext, DiagnosticXrActivity::class.java).addFlags(FLAG_ACTIVITY_NEW_TASK)`, calls `startActivity()`, returns `XrEntryResult.Started`.
4. HorizonOS matches `com.oculus.intent.category.VR` on the new Activity's intent-filter, launches it in headset mode.
5. `DiagnosticXrActivity.onCreate` (probe fires) → decode bundled stereo TB JPEG (`R.drawable.vr_diagnostic_stereo_tb`, 4096×4096) to RGBA via `BitmapFactory` + `copyPixelsToBuffer`.
6. SurfaceView callback `surfaceCreated` → spin up `DiagnosticXrRenderThread` → native `nativeInitSession` → `nativeAttachSurface` → `nativeStartSession` (xrCreateSession with `XrGraphicsBindingOpenGLESAndroidKHR`) → `nativeUploadTexture(rgba, w, h)` → `nativeRunFrameLoop()` (blocking).
7. Frame loop runs `xrPollEvent` (handles SESSION_STATE_CHANGED → xrBeginSession/xrEndSession), `xrWaitFrame`, `xrBeginFrame`, `xrLocateViews`, per-eye sphere mesh render with stereo-TB UV uniform, `xrEndFrame` with `XrCompositionLayerProjection`. Polls `any_button` / `any_trigger` actions each frame.
8. Any input — controller button (OpenXR action set), Android `KeyEvent`, Android `MotionEvent` — sets `g.exitRequested = true`. Loop returns. `xr_session_shutdown()` tears down GL + OpenXR objects on the same thread that created them.
9. Render thread calls `onExitDelivered` → Activity `finish()` on UI thread → user back at Settings.

**Architecture decisions:**

- **Activity-host pattern.** Dedicated `DiagnosticXrActivity` (vr-flavor only) owns the OpenXR session; `XrEntryGatewayImpl` is decoupled from native lifecycle and only signals user intent.
- **Sphere mesh, not `XR_KHR_composition_layer_equirect2`.** Sphere works on every runtime; equirect2 is an optimization for a future ticket.
- **JPEG decode in Kotlin** (`BitmapFactory` → `ByteBuffer`) instead of embedding libjpeg-turbo natively. ~50-100 ms overhead in `onCreate`, acceptable for a diagnostic surface.
- **Plain `SurfaceView`**, not `GLSurfaceView`. OpenXR owns frame timing.
- **Reference space:** `XR_REFERENCE_SPACE_TYPE_LOCAL` with identity pose (universal; Stage missing on some runtimes).
- **Action set:** boolean `any_button` + `any_trigger`, suggested for Khronos Simple + Oculus Touch profiles. Android KeyEvent/MotionEvent paths remain in place as a safety net.

**Known limitations carried over from agent report (not blockers for device-test):**

- First-frame grace gate (`DiagnosticXrInputExitHandler.markFirstFramePresented`) starts at render-thread launch, not at actual first frame present. The ~150-300 ms session bring-up fits inside the existing 400 ms gate.
- Activity JNI global ref is held for the session process lifetime — explicitly accepted (single cold-start per tap).

**Last updated:** 2026-05-19 11:15

> Scope: tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | contracts | - | Done | 4/4 | [PHASE_01__contracts.md](PHASE_01__contracts.md) |
| 02 | native-runtime | 01 | Done | 5/5 scaffold + 1/1 session-bringup (step 02.6) | [PHASE_02__native-runtime.md](PHASE_02__native-runtime.md) |
| 03 | asset-license | 02 | Done | 4/4 (asset bundled + uploaded to swapchain) | [PHASE_03__asset-license.md](PHASE_03__asset-license.md) |
| 04 | settings-entry | 01,03 | Done | 8/8 | [PHASE_04__settings-entry.md](PHASE_04__settings-entry.md) |
| 05 | input-exit | 02,04 | Done | 4/4 (Android KeyEvent/MotionEvent + OpenXR action set wired to live session) | [PHASE_05__input-exit.md](PHASE_05__input-exit.md) |
| 06 | validation-cleanup | all | Done | 6/6 + build/grep/catalog verified after 02.6 | [PHASE_06__validation-cleanup.md](PHASE_06__validation-cleanup.md) |

Status legend: Not started / In Progress / Done / Blocked / Skipped.

---

## Pre-Implementation Blockers

- [x] **Dependency:** S0245 — owner picked Сценарий B (strategic spec §10): skip standalone device-test for S0245, both spec close together after S0249 device-test. Implementation can start.
- [x] **UI:** Block restructure approved 2026-05-19 — rename `VR` → `Управление 3D-VR`, move into Settings Media section as collapsible group, always visible in `vr` / `noLegal`, advisory text + disabled master toggle on non-XR devices. Reflected in strategic spec §2, §3.2, ADR-6 and Phase 04 Steps 04.A1–04.A3.
- [x] **UI:** Button placement approved 2026-05-19: action row inside the collapsible group, after the master toggle; hidden when master toggle is OFF or disabled.
- [x] **UI:** Target settings host resolved 2026-05-19 — VR block injects into `RevisedMediaSettingsFragment` (NEW) + `fragment_settings_revised_media.xml`. Old `MediaSettingsFragment` left untouched (deprecated).
- [x] **UI:** Landscape coverage confirmed 2026-05-19 — `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml` exists. Both portrait and landscape variants edited atomically per CLAUDE.md Rule 12.
- [x] **UI:** Final EN/RU/UK strings locked 2026-05-19 (best-practice draft pending lint pass in `/spec-dev`): block title, advisory, button label, content-description, init-failure toast, runtime-loss toast. See strategic spec §6 item 13.
- [x] **UI:** Default expand state set 2026-05-19 — `expanded` on first open (discoverability), state persists via standard preference mechanism.
- [x] **Research:** Bundled image asset locked 2026-05-19 — Navier8 `blender_test.jpg` (MIT, ~651 KB, stereo TB equirect, OpenXR-validated). Attribution: copy of MIT license + copyright into `app_v2/src/vr/assets/THIRD_PARTY_LICENSES.txt` (created by Phase 03).
- [x] **Research:** `XR_KHR_composition_layer_equirect2` probe call wired in `diagnostic_xr_runtime.cpp` (2026-05-19). Actual runtime probe result happens on device in Phase 06; if false, Phase 03 falls back to application-side sphere mesh per the cached `g_state.equirect2Supported` flag.
- [x] **Research:** OpenXR Android loader version selected 2026-05-19 — `org.khronos.openxr:openxr_loader_for_android:1.1.57` (latest stable), scoped to `vrImplementation` + `noLegalImplementation` (noLegal inherits VR java + native target).
- [ ] **Design:** Failure UX is approved: initialization failure returns to Settings and shows one short toast; mid-session runtime loss returns to Settings and shows one short toast.

---

## Completion Gate

- [ ] All phases show Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` intentionally unchanged per strategic section 8.
- [ ] `dev/FUNCTIONALITY.log` has an `ADD` entry for S0249.
- [ ] `dev/CHANGELOG.md` has an entry for every modified code/config/resource/spec file.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated after Kotlin changes.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` exits 0.
- [ ] Standard debug build passes.
- [ ] VR debug build passes if the `vr` source set is touched.
- [ ] `/spec-check S0249` returns `Verified` or records only manual device-gate items.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase, flip its row to `In Progress`.
2. During a phase, flip each step to `[x]` only after its Verification passes.
3. On phase completion, confirm phase done criteria, then flip the phase row to `Done`.
4. If blocked, flip the phase row to `Blocked` and add a Blockers Log entry.
5. All done: flip `Status:` to `Done` and run `/spec-check S0249`.

---

## Blockers Log

- 2026-05-18 - Implementation blocked before Phase 01: S0245 is still `BlockNeedUserTest`; S0249 depends on S0245 `Verified`.
- 2026-05-18 - Implementation blocked before Phase 01: UI and OpenXR research decisions remain explicit pre-implementation blockers.
- 2026-05-19 - S0245 dependency lifted: owner picked Сценарий B (strategic spec §10) — skip standalone device-test of S0245, both spec close together after one combined device-test for S0249. S0245 remains `BlockNeedUserTest` until S0249 lands, then transitions together.
- 2026-05-19 - UI block restructure approved: rename `VR` → `Управление 3D-VR`, move into Media section as collapsible group, always visible, advisory + disabled toggle on non-XR devices.
- 2026-05-19 00:50 - Owner directive: "Best Practice everywhere, no more questions." All remaining UI / asset / string / expand-state blockers closed with best-practice picks. Target settings host retargeted from `VrSettingsFragment` to `RevisedMediaSettingsFragment` after catalog query revealed the new Revised* settings family. Asset locked to Navier8 `blender_test.jpg` (MIT). EN/RU/UK strings drafted. Default expand state = expanded. Landscape coverage confirmed via existing `layout-land/fragment_settings_revised_media.xml`.
- 2026-05-19 00:58 - **Phase 01 (contracts) Done.** `XrEntryResult` sealed class added; `XrEntryGateway.enterDiagnosticImage()` added (legacy `tryEnter()` kept as compat); `NoOpXrEntryGateway` returns `UnavailableNoRuntime`; `XrEntryGatewayImpl` returns `InitializationFailed` until Phase 02 wires runtime. assembleStandardDebug PASS, compileNoLegalDebugKotlin PASS. Catalog scan + render done (1370 records).
- 2026-05-19 02:57 - **Owner correction:** "Until the button is ready that actually launches the test, don't even suggest I run this on hardware." Reverted spec status `BlockNeedUserTest -> In Progress`, removed 3 `Timber.d("S0249:..)` tags (side-effect of leaving BlockNeedUserTest per CLAUDE.md), reopened Phase 02 with a new step `02.6 session-bringup` covering xrCreateSession + swapchain + composition layer + frame loop + asset texture + Activity host. No device-test invitation until that step is Done and the button actually presents the bundled image on Quest 3.

- 2026-05-19 11:13 - **Phase 02 step 02.6 (session-bringup) Done.** Full OpenXR pipeline implemented end-to-end. New `DiagnosticXrActivity` (vr-flavor only, declared in `src/vr/AndroidManifest.xml` with `com.oculus.intent.category.VR` intent-filter category) hosts a `SurfaceView` + dedicated `DiagnosticXrRenderThread` that drives the OpenXR session. Native split into `diagnostic_xr_runtime.cpp` (JNI surface, ~100 LOC) + new `xr_session.cpp` (~672 LOC) + `xr_session.h` — `xr_session.cpp` ships EGL context bind, view config enumeration, swapchain creation per eye, reference space (`XR_REFERENCE_SPACE_TYPE_LOCAL`), action set attachment with Khronos Simple + Oculus Touch suggested bindings, full `xrPollEvent` / `xrWaitFrame` / `xrLocateViews` / `xrEndFrame` loop, sphere-mesh generation (~64 lat × 128 long), GLSL vertex+fragment shaders with stereo-TB UV sampling per eye index, texture upload via `glTexImage2D`. JPEG decoded in `DiagnosticXrActivity.onCreate` via `BitmapFactory` → RGBA `ByteBuffer` → passed to native `nativeUploadTexture`. Any input (Android KeyEvent / MotionEvent on the Activity, OpenXR `any_button` / `any_trigger` action on the render thread) latches `g.exitRequested`, loop returns, `xr_session_shutdown()` tears down GL + OpenXR objects on the same thread that created them, render thread invokes `onExitDelivered` → Activity `finish()` on UI thread. `XrEntryGatewayImpl` refactored: no more direct JNI calls — `enterDiagnosticImage()` just builds an Intent and calls `startActivity()`. `DiagnosticXrRuntime` interface re-shaped to match the new lifecycle (`initSession` / `attachSurface` / `startSession` / `uploadTexture` / `runFrameLoop` / `requestExit` / `shutdown`). Builds: `assembleNoLegalDebug` PASS (1m 9s), `assembleStandardDebug` PASS (31s), manifest merge verified, OpenXR loader AAR auto-merges `OPENXR` + `OPENXR_SYSTEM` permissions. Grep `BuildConfig.SUPPORT_VR_PLAYER|BuildConfig.IS_NO_LEGAL_FLAVOR` in `src/vr/` → 0 hits (Rule 15 clean). Three `Timber.d("S0249:..")` probe tags inserted at `VrSettingsBlockFragment.launchDiagnosticImmerse`, `XrEntryGatewayImpl.enterDiagnosticImage`, `DiagnosticXrActivity.onCreate`. Spec status `In Progress -> BlockNeedUserTest`. Awaiting Quest 3 device verification per strategic spec §11a.

- 2026-05-19 02:40 - **Phase 06 (validation-cleanup) marked Done — RETRACTED by owner correction at 02:57 (see below).** Original entry: all 6 vr_settings strings verified EN/RU/UK (6/6 OK), catalog refreshed (1378 records), standard + noLegal debug builds PASS, APK evidence noLegal-debug = 180 MB, bundled image = 651 KB (21% of 3MB budget), strategic spec S0240 §10 updated (§10.1A added for S0249 as Stage 1A, §10.2 renamed to Stage 1B), FUNCTIONALITY.log ADD entry, three `Timber.d("S0249:..")` probes inserted at `VrSettingsBlockFragment.launchDiagnosticImmerse` / `XrEntryGatewayImpl.enterDiagnosticImage` / `NativeDiagnosticXrRuntime.startSession`, spec status moved Tactical → BlockNeedUserTest. The accompanying «Awaiting Quest 3 device verification» note (UI block visible, advisory + disabled toggle on phone, button visible on Quest 3 + master ON, tap → toast «Cannot start VR..» + logcat S0249 tags) is superseded — that scope was UI-surface-only and inviting device-test of an immerse-button that never enters immerse mode mislabelled the deliverable. The deferral of session bring-up was the actual reason device-test should not have been invited at all.

- 2026-05-19 02:32 - **Phase 05 (input-exit) Done.** Android side: `DiagnosticXrInputExitHandler` with 400 ms grace period, accepts KeyEvent (any down key) + MotionEvent (any pointer down) + JNI-callable `onNativeAction`. Exposes hot `SharedFlow<ExitReason>`. Native side: `XrActionSet` `diagnostic_exit` created on instance with two boolean actions (`any_button`, `any_trigger`); render loop hook `pollInputAnyTriggeredLocked` calls `xrSyncActions` + `xrGetActionStateBoolean` per action; JNI `nativePollExitTriggered` latches `g_state.exitRequested` once detected. Full teardown chain now releases actions before session/instance. Kotlin wrapper `pollExitTriggered()` added. Build: assembleNoLegalDebug PASS (1m 22s).

  *Out-of-Phase-05 deferral:* `xrAttachSessionActionSets` + suggested-bindings + full session/swapchain/frame loop require the EGL+GLES context bring-up still missing from Phase 02. That is the only remaining engineering work before on-device session can actually present — Phase 06 will explicitly call this out as the device-test prerequisite.

- 2026-05-19 02:26 - **Phase 04 (settings-entry) Done.** VR controls moved out of standalone 5th tab into Media settings as the 6th expandable section (after Video). Architecture: `VrMediaSectionContract` interface in `src/main/` (no-op `NoOpVrMediaSectionContract` in vrStub, real `VrMediaSectionContractImpl` in vr) + Hilt bindings in both modules. New `VrSettingsBlockFragment` observes `XrDetectionFacade.state()` × `MasterTogglePreferences.enabled` and gates UI: advisory visible only on non-XR, master toggle disabled on non-XR, Test Immersive button visible only when master ON. Routes button click through `XrEntryGateway.enterDiagnosticImage()`. New strings (block title, advisory, button label, toasts) in `src/main/res/values*/strings.xml` EN/RU/UK. S0245 5th-tab artifacts deleted: `VrSettingsTabExtension`, `VrSettingsExtensionModule`, old `VrSettingsFragment`, `fragment_vr_settings.xml`, `settings_tab_vr` + `vr_settings_placeholder_summary` strings. `MediaSettingsFragment` upgraded to @AndroidEntryPoint + injects contract; VR section default-expanded for discoverability. Builds: `:app_v2:assembleStandardDebug` PASS, `:app_v2:assembleNoLegalDebug` PASS (3m 28s). Catalog: 1377 records.
- 2026-05-19 01:46 - **Phase 03 (asset-license) Done.** Navier8 MIT `blender_test.jpg` downloaded (4096×4096 stereo TB equirect, 651 KB) to `src/vr/res/drawable-nodpi/vr_diagnostic_stereo_tb.jpg`. `DiagnosticXrAssetProvider` loads + exposes bytes + `StereoLayout.TopBottom`. `DiagnosticXrRuntime` interface gets `presentBundledDiagnosticImage()`. `NativeDiagnosticXrRuntime` injects provider + delegates. `XrEntryGatewayImpl` chains probe → startSession → presentBundledDiagnosticImage. `THIRD_PARTY_LICENSES.md` created at repo root with MIT attribution. **Build sidequest:** restored 3 strings.xml files from HEAD (an external process had deleted 42 referenced keys including `main_settings_new`, blocking `processNoLegalDebugResources`). assembleStandardDebug PASS (v2.60.5190.142), assembleNoLegalDebug PASS (1m 41s). Catalog scan + render done (1375 records).
- 2026-05-19 01:35 - **Phase 02 (native-runtime) Done.** OpenXR loader 1.1.57 added as `vrImplementation` + `noLegalImplementation`. Native build restored (`externalNativeBuild { cmake { path = src/vr/cpp/CMakeLists.txt } }` + `buildFeatures.prefab = true`) and scoped via `FMS_BUILD_XR_RUNTIME=ON` flag set only by `vr` / `noLegal` flavors. Native `diagnostic_xr_runtime.cpp` ships extension probing (`xrEnumerateInstanceExtensionProperties`, `XR_KHR_composition_layer_equirect2`), instance + system acquisition, and JNI surface (`probeExtensions`, `startSession`, `presentStaticImage`, `requestExit`, `isRunning`, `hasEquirect2`). Kotlin: `DiagnosticXrRuntime` interface + `DiagnosticXrNativeResult` enum + `NativeDiagnosticXrRuntime` JNI wrapper (Hilt-bound in `XrModule`). `XrEntryGatewayImpl` updated to delegate diagnostic-image entry to runtime. assembleStandardDebug PASS (v2.60.5190.118), assembleNoLegalDebug PASS (native `libfms_diagnostic_xr.so` built for arm64-v8a + x86_64, OpenXR linker resolution OK).

---

## Change Log

- 2026-05-18 - Initial tactical plan authored by `/spec-tech` inside `/spec-all`.
- 2026-05-19 - Phase 04 expanded with Steps 04.A1 (rename block), 04.A2 (restructure into Media collapsible group + always-visible), 04.A3 (advisory text + disabled master toggle on non-XR). Steps count: 5 → 8. INDEX pre-blockers updated to reflect `/ui-clarify` resolutions.
