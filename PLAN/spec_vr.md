# Specification: VR Edition (consolidated)

**Status:** Active
**Date:** 2026-04-21
**Target device:** Meta Quest 3 (Quest 2 / Quest Pro best-effort)
**Tier:** 4 — Strategic (multi-phase, high risk)
**Roadmap entry:** VR flavor track (subsumes the prior `spec_vr-master` and `phase_01..12` tactical docs).
**Supersedes:** `spec_vr-master.md`, `spec_vr-3d-rendering-research.md`, `spec_vr-3d-image-immersive-handoff.md`, `spec_vr-mvp-day1.md`, `PLAN/tasks/phase_01..phase_12` — all archived under [`temp/plan_archive_vr/`](../temp/plan_archive_vr/).

---

## 1. Problem Statement

FastMediaSorter has a dedicated `vr` product flavor for Meta Quest 3 that wraps the existing player stack with OpenXR composition layers. Phases 1–5, 7–10 (native OpenXR JNI, 360° detection, MP4 `st3d`/`sv3d` parsing, layer factory, sphere/cylinder render dispatch, spherical RadioGroup, forced-format settings, SBS snapshot, and VR command overrides) are implemented and build-validated. What remains is: finish the 360° **photo** path that the video path already covers, wire up Meta Horizon Store / sideload infrastructure, mirror the shipped feature set to EN/RU/UK docs, and complete on-device Quest validation for the stack that ships today.

This spec also captures the 2026-04-21 routing correction (`VrRouteDecisionHelper` — any stereoscopic OR spherical content must enter immersive because Quest panel mode is monoscopic) and the accompanying 3DVR integration-test sweep, so the remaining work is not blocked by stale decision records.

---

## 2. Goals

1. Render 360°/VR180 **photos** (equirect JPEG/PNG, VR180 fisheye pairs) on the sphere/cylinder renderer that the video path already uses.
2. Produce a signed, submission-ready VR release APK and the sideload/distribution artefacts Meta Horizon Store expects.
3. Mirror the shipped VR feature set into `docs/FEATURES.md` EN/RU/UK plus new `docs/VR_EDITION.md` and `docs/VR_SIDELOAD.md` guides.
4. Close out on-device Quest validation for the routing, XR swapchain, and sphere/cylinder submission paths that were code-complete before runtime testing finished.
5. Keep the 3DVR integration-test sweep green across each delta so routing regressions fail the test dialog, not end users.

**Non-goals (deferred):** hand tracking, controller action-set redesign, spatial audio, passthrough AR UX, room-scale locomotion, video re-encoding on device, Pico / HTC Vive Focus support, VR markets beyond Meta Horizon + sideload/Google Play, HDR equirect photos, panorama stitching, live-wallpaper 360° ambient backgrounds.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ❌ | Install-CTA only when stereoscopic/spherical content is detected. |
| `lite` | ❌ | No VR entry. |
| `photos` | ❌ | No VR entry. |
| `legacy` | ❌ | No VR entry (minSdk 23, OpenXR not available). |
| `vr` | ✅ | Primary target. `BuildConfig.SUPPORT_VR_PLAYER = true`, `PLAYER_ACTIVITY_CLASS = com.sza.fastmediasorter.vr.VrPlayerActivity`. |

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26 (vr minSdk) | Lowest supported; matches Quest 3 HorizonOS baseline. |
| 32+ (Quest 3 actual) | All modern APIs available; no legacy workarounds. |
| 34 / 35 (compileSdk) | Verify activity launch + lifecycle ordering during XR init/teardown on target SDK 35. |

### 3.3 Wear OS Impact

No Wear OS changes.

---

## 4. Current Architecture (preserved)

```text
standard flavor (unchanged)
 └─ PlayerEntryCoordinator → ShowVrInstallCta / OpenStandardPlayer

vr flavor (thin host — no duplicate media pipeline)
 └─ VrPlayerActivity (extends PlayerActivity)
      ├─ VrRouteDecisionHelper   ← plain 2D → panel; any stereo/spherical → immersive
      ├─ OpenXrSessionManager    ← JNI bridge, per-eye swapchains, xrGetGraphicsRequirements pre-call
      ├─ VrPlaybackEngine        ← wraps ExoPlayer
      ├─ VrVideoSurfaceTextureBridge  ← Exo frames → OES → XR swapchain
      ├─ VrStereoRenderer        ← per-eye GL (projection / cinema quad / equirect / cylinder)
      ├─ VrLayerFactory          ← descriptor-driven composition layer selection
      ├─ VrPhotoSphereRenderer   ← TODO (§6 — Phase 6)
      └─ VrControlOverlayManager ← QuadLayer UI

shared (main)
 └─ StereoDetector · StereoMode · DualSurfaceStaticImageRenderer ·
    Mp4SpatialMetadataReader · PlaybackControlDialogFragment · AppSettings (vr*, disable3dVr)
```

**Data flow:** `ExoPlayer frame → SurfaceTexture → OES texture → VrStereoRenderer.renderEye(view, layerType) → OpenXR swapchain → xrEndFrame`.

### 4.1 Route Policy (frozen — see §12 ADR-1 + ADR-4)

`VrRouteDecisionHelper` returns one of:

| Route | When |
|-------|------|
| `STANDARD_PANEL_FALLBACK` | plain 2D content OR `disable3dVr=true` |
| `IMMERSIVE_VIDEO` | any stereoscopic or spherical `MediaType.VIDEO` |
| `IMMERSIVE_STATIC_IMAGE` | any stereoscopic or spherical `MediaType.IMAGE` |
| `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE` | stereoscopic/spherical but media type is not VIDEO/IMAGE (e.g., PDF) |

Panel mode on Quest is **monoscopic** by platform contract — flat SBS/OU cannot produce a stereo effect there, so flat stereo must enter immersive just like spherical content. The unsupported-immersive branch shows a user-visible message via `PlayerEventHandler` before falling back.

---

## 5. Shipped State (verified 2026-04-21)

Build status: `./gradlew.bat :app_v2:assembleVrDebug` → BUILD SUCCESSFUL. Route unit tests pass (`VrRouteDecisionHelperTest`, four scenarios covering stereo image, plain image, unsupported media, and `disable3dVr` kill-switch).

| Area | Key Files |
| ---- | --------- |
| VR flavor + BuildConfig gates | [app_v2/build.gradle.kts](../app_v2/build.gradle.kts) (`SUPPORT_VR_PLAYER`, `PLAYER_ACTIVITY_CLASS`) |
| OpenXR JNI + native session (`xrGetOpenGLESGraphicsRequirementsKHR` pre-call included) | [vr/openxr/OpenXrSessionManager.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt), `app_v2/src/vr/cpp/OpenXrNative.cpp`, [vr/openxr/OpenXrNative.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt) |
| `StereoMode` extended with `EQUIRECT_*`, `VR180_FISHEYE_SBS`, `CYLINDER_180` | [domain/model/StereoMode.kt](../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt) |
| Detection — filename + MP4 Spatial Media + Matroska + aspect ratio | [ui/player/StereoDetector.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt), [ui/player/Mp4SpatialMetadataReader.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt) |
| Standard-flavor install-CTA for stereo/spherical content | [ui/player/contracts/StereoDetectionFacade.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt), [ui/player/entry/PlayerEntryCoordinator.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt) |
| Image stereo crop transformation (Glide) + dual-surface static image renderer | `ui/player/render/StereoImageCropTransformation.kt`, [ui/player/render/DualSurfaceStaticImageRenderer.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt) |
| VR host Activity + phone fallback | [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt), `vr/VrPhoneFallbackActivity.kt` |
| Route policy helper + launch-route model | [vr/helpers/VrRouteDecisionHelper.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt), [vr/VrLaunchRoute.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrLaunchRoute.kt) |
| PlaybackControlDialog STEREO tab — dual Plat/Spherical RadioGroups, override switch, per-file remember cache | [ui/player/PlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt), [ui/player/PlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt), `data/local/db/StereoFormatOverride*.kt` |
| Settings: `vrForcedPlatFormat`, `vrForcedSphericalFormat`, `vrRenderingMode`, `vrRememberFileFormat`, `vrAutoDetectFormat`, global `disable3dVr` | [domain/model/AppSettings.kt](../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt), [data/repository/SettingsRepositoryImpl.kt](../app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt) |
| VR settings UI block + kill-switch placement | [ui/settings/fragments/VideoSettingsFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt), [ui/settings/fragments/PlaybackSettingsFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt) |
| ExoPlayer → OpenXR bridge | `vr/VrVideoSurfaceTextureBridge.kt` |
| Layer factory + descriptor routing (projection / cinema quad / Equirect2KHR / CylinderKHR) | [vr/render/VrLayerFactory.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerFactory.kt), [vr/render/VrLayerDescriptor.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt), [vr/render/VrLayerType.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerType.kt) |
| Layer-aware stereo renderer dispatch | [vr/render/VrStereoRenderer.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt), [vr/render/VrRenderContext.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrRenderContext.kt) |
| Stereo snapshot (SBS PNG) + VR command overrides | `vr/capture/VrStereoSnapshotManager.kt`, `vr/commands/VrFullscreenCommandOverride.kt`, `vr/commands/VrSaveFrameCommandOverride.kt`, `vr/commands/VrSystemUiCommandOverride.kt` |
| Controller buttons (X / B / Menu / Back) via `dispatchKeyEvent` | [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) |
| `disable3dVr` hides 3D dialog page + bypasses browse routing | [ui/player/PlayerDialogHelper.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt), [ui/browse/managers/BrowseEventHandler.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt) |
| Hilt module for vr-only bindings | `vr/di/VrModule.kt` |
| Manifest: VR category on MainActivity only; `com.oculus.supportedDevices` meta-tag | `app_v2/src/vr/AndroidManifest.xml` |

### 5.1 Test Infrastructure (2026-04-21)

- [scripts/utils/synth_vr_test_media.py](../scripts/utils/synth_vr_test_media.py) — PIL-based synthesizer that produces 11 colour-coded test patterns (red L / green R fields, grid, crosshair, big labels) covering every renderable `StereoMode` under `c:/Common/test_media/3dvr/<variant>/`. No FFmpeg dependency.
- [scripts/utils/setup_test_media_vr.ps1](../scripts/utils/setup_test_media_vr.ps1) — VR analogue of `setup_test_media.ps1`: pushes each variant folder to `/sdcard/Download/FastMediaSorter_Test/3DVR/<variant>/` on a connected Quest, writes `3dvr_manifest.json` (path, name, variant, stereoMode, sizeBytes per file), and triggers per-file `MEDIA_SCANNER_SCAN_FILE` broadcasts.
- In-app **3DVR Sweep** section in the integration-test dialog ([chipThreeDVr](../app_v2/src/main/res/layout/dialog_integration_test.xml) gated on `BuildConfig.SUPPORT_VR_PLAYER`). Runs five checks against the manifest: manifest presence, file readability, route-per-StereoMode equivalence with `VrRouteDecisionHelper`, filename-hint match (token required by `StereoDetector`), and variant coverage (every renderable mode has at least one sample). Implementation in [IntegrationTestRunner.kt](../app_v2/src/debug/java/com/sza/fastmediasorter/domain/usecase/IntegrationTestRunner.kt) (section `3DVR SWEEP`).

---

## 6. Remaining Work

### 6.1 Phase A — 360° Photo Support

**Goal:** show equirect and VR180 photos on the sphere/cylinder renderer that the video path already uses. Decoding differs from video: single `Bitmap` → GL upload → Equirect2KHR / CylinderKHR layer.

**Current state:** image path uses `DualSurfaceStaticImageRenderer` (flat SBS/OU only). No sphere image path. EXIF / XMP photo-sphere tags (`GPano:ProjectionType=equirectangular`) are not read.

**Work:**

1. New `vr/render/VrPhotoSphereRenderer.kt` implementing the `StaticImageRenderer` contract.
2. Decoding:
   - Glide `asBitmap()` with no crop transformation.
   - For `EQUIRECT_360_SBS`, `EQUIRECT_360_OU`, `EQUIRECT_180_SBS` — pass the full bitmap; the layer handles per-eye UV crop (already implemented).
   - For `EQUIRECT_360_MONO` / `EQUIRECT_180_MONO` — single bitmap feeds both eyes.
   - For `VR180_FISHEYE_SBS` — dewarp fisheye to equirect on upload OR feed directly as an `Equirect2KHR` layer per platform support probe.
3. New `vr/detect/ExifPhotoSphereReader.kt` that parses XMP `GPano:ProjectionType`, `CroppedAreaImageWidthPixels`, `CroppedAreaImageHeightPixels`, `FullPanoWidthPixels`, `FullPanoHeightPixels` and feeds `StereoDetector`'s image branch.
4. Renderer selection in `VrPlayerActivity` after route resolution: if mode is spherical image → `VrPhotoSphereRenderer`; else flat → `DualSurfaceStaticImageRenderer`.
5. Texture upload guard: downsample to `GL_MAX_TEXTURE_SIZE` when the source exceeds it (common at 8K equirect).

**Acceptance criteria:**

- 8000×4000 equirect JPEG displays as a full sphere on Quest 3 without OOM.
- VR180 stereo JPEG displays with correct per-eye depth.
- Flat SBS/OU image path is unchanged (existing 3DVR Sweep continues to pass).
- Sequential navigation across 360° photos does not leak texture memory.

**Files touched:**

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrPhotoSphereRenderer.kt` (new, ≤400 lines)
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/detect/ExifPhotoSphereReader.kt` (new, ≤200 lines)
- [ui/player/StereoDetector.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) — XMP reader hook
- [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) — renderer selection branch (add route-side switch, keep thin)

### 6.2 Phase B — Store Submission + Sideload Infrastructure

**Goal:** ship a signed VR release APK plus the manifest attributes, icons, splash, build scripts, and signing plumbing Meta Horizon Store and sideloaded distribution expect.

**Current state:** vr flavor is defined in `build.gradle.kts`, but there is no Meta Horizon manifest metadata, no VR-branded icon/splash, and no dedicated release build script. `scripts/builders/` covers standard/lite/photos/legacy only.

**Work:**

1. **Manifest** (`app_v2/src/vr/AndroidManifest.xml`):
   - `<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" android:version="1"/>`
   - `<uses-feature android:name="oculus.software.handtracking" android:required="false"/>` (forward-compatible)
   - Confirm `<meta-data android:name="com.oculus.supportedDevices" android:value="quest2|quest3|questpro"/>` is present.
   - Keep `com.oculus.intent.category.VR` on `MainActivity` only (prevents panel-mode passthrough flicker on `VrPlayerActivity`).
2. **Branding assets:**
   - 512×512 app icon and 2560×1440 cover art → `app_v2/src/vr/res/drawable-nodpi/`.
   - 2048×2048 equirect splash → `app_v2/src/vr/res/raw/`.
3. **Build scripts:**
   - `scripts/builders/build-vr-release.ps1` — signs with release keystore, runs `:app_v2:assembleVrRelease`, copies to `artifacts/vr/`.
   - Extend `scripts/builders/build-and-push-all.ps1` to include the vr variant.
4. **Signing:** reuse the existing release keystore; document Meta-specific requirements in `VR_SIDELOAD.md`.
5. **Versioning:** keep the project's `Y.YM.MDDH.Hmm` format; Meta Horizon requires monotonic `versionCode`.
6. **Dry-run submission:** produce the Meta Developer Hub upload bundle; validate with `ovr-platform-util` and on-device `adb install`.

**Acceptance criteria:**

- `./scripts/builders/build-vr-release.ps1` produces a signed, aligned APK.
- `adb install` launches a valid XR session on Quest 3.
- Meta Horizon Developer Hub pre-submission validation passes without blocking warnings.
- Phone install of the same APK opens `VrPhoneFallbackActivity` (graceful non-headset fallback).

**Files touched:**

- `app_v2/src/vr/AndroidManifest.xml`
- `app_v2/src/vr/res/drawable-nodpi/ic_launcher.png`, `cover_art.png`
- `app_v2/src/vr/res/raw/vr_splash.jpg`
- `scripts/builders/build-vr-release.ps1` (new)
- `scripts/builders/build-and-push-all.ps1`
- `app_v2/build.gradle.kts` (versionCode logic for vr, if needed)

### 6.3 Phase C — Documentation Sync

**Goal:** bring user-facing and developer docs in line with the shipped VR edition. Follows the mandatory `/doc-update` skill rule for `docs/FEATURES*.md` mirrors.

**Current state:** `docs/FEATURES.md` EN/RU/UK have no VR section; `docs/VR_EDITION.md` and `docs/VR_SIDELOAD.md` do not exist; `docs/TECH_STACK.md` omits OpenXR, isoparser (vr-only), and the native layer.

**Work:**

1. **`docs/FEATURES.md` + `_RU` + `_UK`** — add a "VR Edition" section listing: stereoscopic 3D (SBS/OU) video and images, 360° mono/SBS/OU spherical video, VR180 panoramic video, 180° cylinder panoramic, auto-detection + manual override, SBS PNG snapshot capture, cinema mode for flat content.
2. **`docs/VR_EDITION.md`** (new, EN first) — end-user overview: supported devices (Quest 2/3/Pro), supported formats (with detection + override matrix), settings (forced plat/spherical, rendering mode, remember-per-file, kill-switch), known limitations (cubemap unsupported, HDR unsupported).
3. **`docs/VR_SIDELOAD.md`** (new) — enabling developer mode on Quest, `adb install` command, launching from Unknown Sources, troubleshooting (fallback activity, missing runtime).
4. **`docs/TECH_STACK.md`** — add OpenXR loader AAR, isoparser (vr-flavor-only), native layer bindings.
5. **`dev/CHANGELOG.md`** — via `scripts/add_to_dev_log.ps1` per post-change rule.
6. **Style conformance** — `..` not `...`; use `ё`/`Ё` in Russian where grammatically correct (per CLAUDE.md author rules).

**Acceptance criteria:**

- All three FEATURES mirrors include an equivalent-scope "VR Edition" section.
- `VR_EDITION.md` + `VR_SIDELOAD.md` exist and cross-link from README.
- `TECH_STACK.md` documents the new dependencies.
- `/doc-update` skill pass reports no sync warnings.

**Files touched:**

- `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
- `docs/VR_EDITION.md` (new)
- `docs/VR_SIDELOAD.md` (new)
- `docs/TECH_STACK.md`
- README cross-links
- `dev/CHANGELOG.md` (via script)

### 6.4 Phase D — On-Device Quest Validation

**Goal:** close out runtime verification for the stack that landed in Phases 1–5 / 7–10 but was never end-to-end tested on Quest hardware.

**Scope of checks:**

- `onResume → initialize → nativeInitialize → onSessionReady → render loop` and its teardown path without dead-thread warnings in logs.
- Per-eye render stability across `VrLaunchRoute.IMMERSIVE_VIDEO` and `IMMERSIVE_STATIC_IMAGE` for every `StereoMode` that has a sample on disk.
- `Equirect2KHR` + `CylinderKHR` swapchain submissions log no validation layer warnings.
- `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE` shows a user-visible message via `PlayerEventHandler` before returning to panel or Browse.
- `disable3dVr` kill-switch fully bypasses XR startup from Browse and Player paths.

**Validation artefacts to produce:**

- Logcat capture for a clean immersive-video session (record swapchain indices, frame timings).
- Logcat capture for a forced XR-init failure showing the fallback message appears before finish/teardown.
- 3DVR Sweep dialog screenshot with all five sub-tests green, plus its manifest on device.

---

## 7. Data Flow (post-Phase A)

```text
User selects file in Browse or Player CTA
 ↓
PlayerEntryCoordinator routes to BuildConfig.PLAYER_ACTIVITY_CLASS
 ↓
VrPlayerActivity.resolvePlaybackRoute()
 ↓
VrRouteDecisionHelper.decide(currentFile, effectiveStereoMode, settings)
 ├── STANDARD_PANEL_FALLBACK
 │     → PlayerActivity (panel)
 ├── IMMERSIVE_VIDEO
 │     → OpenXrSessionManager.initialize()
 │     → VrPlaybackEngine ⇢ VrVideoSurfaceTextureBridge ⇢ VrStereoRenderer ⇢ VrLayerFactory
 ├── IMMERSIVE_STATIC_IMAGE
 │     → OpenXrSessionManager.initialize()
 │     → (spherical) VrPhotoSphereRenderer  ← NEW in Phase A
 │     → (flat)     DualSurfaceStaticImageRenderer
 └── UNSUPPORTED_IMMERSIVE_WITH_MESSAGE
       → PlayerEventHandler.showError(userMessageResId)
       → controlled fallback
```

---

## 8. Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Route logic lives in `VrRouteDecisionHelper`; renderer selection will be a thin switch in the Activity. |
| Naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`, `NounReader`) | ✅ | `VrPhotoSphereRenderer`, `ExifPhotoSphereReader` follow the pattern. |
| Data flow `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Photo-sphere EXIF reads happen in `StereoDetector` (already DataSource-adjacent). |
| Timber only (no `Log.d()`) | ✅ | Keep the single `Log.e("VR_BOOT", ..)` for boot diagnostics; everything else routes through Timber. |
| Room schema version bumped on DB changes | N/A | No DB changes in remaining phases. |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | Reuse `PlayerViewModel` flows. |
| Hilt bindings declared in module file | ✅ | New classes bind via `vr/di/VrModule.kt` (or direct construction until promoted). |
| File size ≤1000 lines | ⚠️ | `VrPlayerActivity` is already large (~760 lines). Keep Phase A additions out of the Activity — route them through the helper/renderer pair. |

---

## 9. Files to Modify (all remaining phases combined)

| File | Change | Notes |
|------|--------|-------|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrPhotoSphereRenderer.kt` | Create | Phase A |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/detect/ExifPhotoSphereReader.kt` | Create | Phase A |
| [ui/player/StereoDetector.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) | Add XMP reader hook | Phase A |
| [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) | Renderer selection branch | Phase A |
| `app_v2/src/vr/AndroidManifest.xml` | VR features, meta-data, category | Phase B |
| `app_v2/src/vr/res/drawable-nodpi/ic_launcher.png`, `cover_art.png` | Create | Phase B |
| `app_v2/src/vr/res/raw/vr_splash.jpg` | Create | Phase B |
| `scripts/builders/build-vr-release.ps1` | Create | Phase B |
| `scripts/builders/build-and-push-all.ps1` | Extend | Phase B |
| `app_v2/build.gradle.kts` | VersionCode for vr (if required) | Phase B |
| `docs/FEATURES.md` / `_RU.md` / `_UK.md` | Add VR Edition section | Phase C |
| `docs/VR_EDITION.md` | Create | Phase C |
| `docs/VR_SIDELOAD.md` | Create | Phase C |
| `docs/TECH_STACK.md` | Add OpenXR / isoparser / native bindings | Phase C |
| README cross-links | Add | Phase C |
| `dev/CHANGELOG.md` | Append per post-change rule (via script) | Every phase |

Any file ≥500 lines gets a timestamped backup in `temp/` before editing.

---

## 10. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| 8K equirect photo OOM on Quest 3 | Med | Downsample to `GL_MAX_TEXTURE_SIZE`; stream tiles if needed post-Phase A. |
| VR180 fisheye dewarp precision issues | Med | Prefer feeding the runtime via `Equirect2KHR` when the platform reports support; dewarp path is a fallback. |
| Quest thermal throttling on 8K 360° video | Med | Document minimum resolution; ExoPlayer codec selection prefers HW decoder. |
| Meta Horizon Store rejection for UX issues | Med | Build against Meta's 2026 VR submission checklist; test on-device before submission. |
| OpenXR loader version drift with HorizonOS | Low | Pin to Khronos 1.0.34+; fall back via `OpenXrInitException` to `VrPhoneFallbackActivity`. |
| Dead-thread warnings during route changes | Med | Stop playback → clear video surface → release XR → finish. Regression-check via logcat capture (§6.4). |
| Doc drift after Phase A/B land | Low | `/doc-update` is mandatory; Phase C is on the critical path before tagging a release. |

---

## 11. Testing Plan

### 11.1 Unit tests (extend existing)

- `VrRouteDecisionHelperTest` — already covers stereo image / plain image / unsupported media / `disable3dVr`. Add a Phase A case for a spherical image → `IMMERSIVE_STATIC_IMAGE`.
- `StereoDetectorTest` — add XMP photo-sphere detection cases (equirect/mono + equirect/SBS/OU).
- `Mp4SpatialMetadataReaderTest` — regression-only; no new cases unless Phase A touches it.

### 11.2 In-app integration tests (3DVR Sweep)

Every phase delta must keep the existing chip green. Manifest lives at `/sdcard/Download/FastMediaSorter_Test/3DVR/3dvr_manifest.json`. The sweep covers: manifest presence, file readability, route-per-StereoMode equivalence, filename-hint match, variant coverage. Add a Phase A sub-test ("3DVR Photo Sphere Decode") that confirms the renderer path for each spherical image variant produces a non-empty GL texture.

### 11.3 Manual on-device tests (Quest 3)

1. Stereoscopic 3D image from panel mode → immersive stereo renders with per-eye pixels.
2. Normal 2D image → stays in panel mode; XR does not initialise.
3. Stereoscopic 3D video → existing immersive video path still works.
4. Unsupported non-video 3D asset → visible error then controlled fallback.
5. Force XR startup failure → user sees a visible error, playback stops, no silent background audio.
6. Navigate a sequence of 360° photos → no leak, no OOM.
7. `disable3dVr = true` → PlaybackControlDialog 3D page hidden; browse routing bypassed; XR never starts.
8. Repeat 1–7 on both `vrDebug` and any `vrUnlicensed` equivalent if/when introduced.

### 11.4 Maestro E2E

Not applicable on Quest immersive transitions. Keep existing Maestro smoke/critical suites for the non-VR flavors.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1 — Panel mode is monoscopic; stereo content must enter immersive.**
- Decision: `VrRouteDecisionHelper` routes *any* stereoscopic or spherical `StereoMode` to immersive regardless of flat vs spherical; only plain 2D falls back to the panel player.
- Alternatives considered: Keep flat SBS/OU in panel (previous implementation).
- Reason: Quest panel mode shows the same pixels to both eyes by platform contract. Flat SBS/OU in panel produces no stereo effect — that was the user-visible bug reported on 2026-04-21. Applies even when users expect "panel stereo" (Instagram-style); that is Meta-private functionality we do not have access to.

**ADR-2 — Route by immersive capability, not by `MediaType` alone.**
- Decision: The route decision is driven by (stereo classification × media type × settings), not by `MediaType.VIDEO` vs anything else.
- Alternatives considered: Blanket non-video → panel fallback with a toast.
- Reason: A toast-only fix would not solve the functional bug for 3D images.

**ADR-3 — Reuse the standardized player error path for VR fallback messaging.**
- Decision: VR fallbacks use `PlayerEventHandler.showError()` rather than a VR-only toast utility.
- Alternatives considered: Direct `Toast.makeText()` inside `VrPlayerActivity`.
- Reason: Centralised error presentation respects user settings (dialog vs toast) and preserves TalkBack support.

**ADR-4 — MP4 box metadata overrides filename heuristics when they disagree.**
- Decision: MP4 `st3d`/`sv3d` boxes (and Matroska `StereoMode`) win over filename heuristics when both are present.
- Alternatives considered: Filename-first, metadata-second.
- Reason: Metadata is authoritative; filenames are lossy and frequently wrong after re-encodes.

**ADR-5 — Per-file format cache persists in Room, not SharedPreferences.**
- Decision: `StereoFormatOverride` entities live in Room.
- Alternatives considered: SharedPreferences map keyed by file path.
- Reason: Consistent with existing persistence layer; supports backup/export; avoids SharedPreferences path-collision issues.

**ADR-6 — Single union `AndroidManifest.xml` for Meta Horizon + sideload + Google Play.**
- Decision: One APK with a union manifest.
- Alternatives considered: Separate manifests per distribution channel.
- Reason: Avoids divergent build variants; VR category on `MainActivity` only, passthrough stays available.

**ADR-7 — Keep PlaybackControlDialog's Plat and Spherical RadioGroups visible simultaneously.**
- Decision: Both RadioGroups show at all times; the inactive one is disabled, not hidden.
- Alternatives considered: Swap between Plat/Spherical based on detection.
- Reason: Users can always override without re-detecting; avoids hidden-control confusion.

---

## 13. Accessibility

Failure messaging must be TalkBack-reachable when presented as a dialog, and fallback toast text must be clear enough to explain why immersive mode did not start. Renderer additions in Phase A do not introduce new small touch targets — existing PlaybackControlDialog + command bar retain current accessibility contract.

---

## 14. User-Facing Feature Update (pre-filled for Phase C)

- `docs/FEATURES.md` (EN): `- VR Edition: immersive stereo (SBS/OU) video and images, 360°/VR180 panoramic video, 180° cylinder, auto-detection with per-file override and SBS snapshot capture on Meta Quest 3.`
- `docs/FEATURES_RU.md` (RU): `- VR-версия: иммерсивное стерео (SBS/OU) для видео и изображений, 360°/VR180 панорамное видео, 180° цилиндр, автоопределение с переопределением для каждого файла и захват SBS-снимка на Meta Quest 3.`
- `docs/FEATURES_UK.md` (UK): `- VR-версія: імерсивне стерео (SBS/OU) для відео і зображень, 360°/VR180 панорамне відео, 180° циліндр, автовизначення з окремим перевизначенням для кожного файлу та захоплення SBS-знімка на Meta Quest 3.`

---

## 15. Implementation Steps

1. Phase A — 360° photo support: create `VrPhotoSphereRenderer`, `ExifPhotoSphereReader`, hook XMP into `StereoDetector`, add renderer selection branch in `VrPlayerActivity`, extend 3DVR Sweep with a photo-sphere decode sub-test.
2. Phase D — run the Quest validation pass and capture logs + a green 3DVR Sweep screenshot.
3. Phase B — land manifest attributes, icons/splash, release build script, validate with `ovr-platform-util` and `adb install` on Quest 3.
4. Phase C — update docs (EN/RU/UK FEATURES mirrors, VR_EDITION, VR_SIDELOAD, TECH_STACK, README cross-links) via `/doc-update`.
5. After each file change: `./scripts/add_to_dev_log.ps1 "<path>" "<target>" "<short description>"`.

Mandatory checklist at close:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`) where UI copy is added.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase C).
- [ ] Room DB migration added + version incremented (if DB schema changes — none expected).
- [ ] `./scripts/add_to_dev_log.ps1` run for every modified file.
- [ ] 3DVR Sweep green on both Quest hardware and build-machine unit tests.

---

## 16. References

- [`temp/plan_archive_vr/spec_vr-master.md`](../temp/plan_archive_vr/spec_vr-master.md) — previous master document this spec supersedes.
- [`temp/plan_archive_vr/spec_vr-3d-rendering-research.md`](../temp/plan_archive_vr/spec_vr-3d-rendering-research.md) — 816-line research doc on why 3D content was not rendering; §10 Hybrid D routing is the source of ADR-1.
- [`temp/plan_archive_vr/spec_vr-3d-image-immersive-handoff.md`](../temp/plan_archive_vr/spec_vr-3d-image-immersive-handoff.md) — route-family decision origin (now implemented in `VrRouteDecisionHelper`).
- [`temp/plan_archive_vr/spec_vr-mvp-day1.md`](../temp/plan_archive_vr/spec_vr-mvp-day1.md) — initial Quest-day-1 validation goals.
- [`temp/plan_archive_vr/tasks_2026-04-21/`](../temp/plan_archive_vr/tasks_2026-04-21/) — per-phase tactical docs (phases 1–12) merged into §5 and §6 of this spec.
- [`temp/plan_archive_vr/vr-doc.md`](../temp/plan_archive_vr/vr-doc.md) — format research, OpenXR extension table, decoder matrix.
- [`temp/plan_archive_vr/vr-360-ambiguity-questionnaire.md`](../temp/plan_archive_vr/vr-360-ambiguity-questionnaire.md) — frozen user decisions underpinning ADR-4..ADR-7.
