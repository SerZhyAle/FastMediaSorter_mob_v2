# VR Edition — Master Spec

**Status:** Active · **Last updated:** 2026-04-19 · **Target device:** Meta Quest 3

> Thin master document. Background research, ambiguity answers, full format tables, and per-feature design rationale live in the archive under [`temp/plan_archive_vr/`](../temp/plan_archive_vr/). Only remaining work is tracked here and in [`PLAN/tasks/`](./tasks/).

---

## 1. Vision

Ship a dedicated **vr flavor** of FastMediaSorter for Meta Quest 3 that renders the user's existing media library (local + SMB/SFTP/FTP + cloud) as native OpenXR content: flat cinema, stereoscopic 3D (SBS/OU), 360° spherical, and VR180 panoramic.

- Standard/Lite/Photos/Legacy flavors remain unchanged on phones/tablets.
- VR flavor is a thin host wrapping the existing player stack — no duplicate media pipeline.
- On detecting 3D/360° content in the standard flavor, show a CTA to install the VR edition.

## 2. Scope

**In-scope** (remaining work — see `PLAN/tasks/` for per-phase breakdown):

- VR composition layer factory (Projection / Equirect2 / Cylinder).
- 360° photo rendering (equirect bitmap → sphere).
- VR settings screen extension (forced 360° format).
- Stereo snapshot capture (SBS PNG frame export).
- VR-specific command overrides (Fullscreen, Save Frame).
- Store submission infrastructure (Meta Horizon + sideload docs).

**Out-of-scope:** controller passthrough UI, hand tracking, spatial audio, room-scale locomotion, OpenXR actions beyond simple D-pad mapping.

## 3. Current State (verified 2026-04-19)

### Implemented

| Area | Location |
| ---- | -------- |
| VR flavor + BuildConfig gate | `app_v2/build.gradle.kts` (`SUPPORT_VR_PLAYER`) |
| OpenXR JNI bridge + native session bootstrap | [vr/openxr/OpenXrSessionManager.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt), `app_v2/src/vr/cpp/OpenXrNative.cpp`, `app_v2/src/vr/cpp/CMakeLists.txt`, [vr/openxr/OpenXrNative.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt) |
| Extended `StereoMode` enum for flat + spherical content (`EQUIRECT_*`, `VR180_FISHEYE_SBS`, `CYLINDER_180`) | [domain/model/StereoMode.kt](../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt) |
| Filename + MP4 Spatial Media + Matroska + AR stereo detection for flat and 360°/VR180/cylinder content | [ui/player/StereoDetector.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt), [ui/player/Mp4SpatialMetadataReader.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt) |
| Standard-flavor VR CTA routing for stereoscopic + spherical content | [ui/player/contracts/StereoDetectionFacade.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt), [ui/player/entry/PlayerEntryCoordinator.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt) |
| Image stereo crop via Glide transformation | `ui/player/render/StereoImageCropTransformation.kt` |
| Dual-surface static image renderer (SBS/OU/MONO) | [ui/player/render/DualSurfaceStaticImageRenderer.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt) |
| VR flavor host + runtime check | [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) |
| Phone fallback for VR flavor on non-headset | `vr/VrPhoneFallbackActivity.kt` |
| Entry routing (PHONE/TABLET/HEADSET × flavor × stereo) | [ui/player/entry/PlayerEntryCoordinator.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt) |
| PlaybackControlDialog STEREO tab + IPD + rendering-mode chips | [ui/player/PlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt) |
| PlaybackControlDialog dual flat/spherical RadioGroups + override switch + per-file remember-format cache | [ui/player/PlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt), [ui/player/PlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt), `data/local/db/StereoFormatOverride*.kt` |
| Settings keys (`vrForcedPlatFormat`, `vrForcedSphericalFormat`, `vrRenderingMode`, `vrRememberFileFormat`, `vrAutoDetectFormat`) | [domain/model/AppSettings.kt](../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt) |
| VR settings block in Video settings fragment | [ui/settings/fragments/VideoSettingsFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt) |
| ExoPlayer → OpenXR bridge (`VrVideoSurfaceTextureBridge`) | `vr/VrVideoSurfaceTextureBridge.kt` |
| Layer-aware stereo renderer dispatch for projection, cinema quad, equirect, and cylinder targets | [vr/render/VrStereoRenderer.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt), [vr/render/VrRenderContext.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrRenderContext.kt) |
| VR layer factory + descriptor-driven OpenXR composition layer routing | [vr/render/VrLayerFactory.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerFactory.kt), [vr/render/VrLayerDescriptor.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerDescriptor.kt), [vr/render/VrLayerType.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrLayerType.kt), [vr/openxr/OpenXrSessionManager.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt), `app_v2/src/vr/cpp/OpenXrNative.cpp` |
| Hilt module for vr-only bindings | `vr/di/VrModule.kt` |

### Known Broken / Incomplete

| Area | Location | Problem |
| ---- | -------- | ------- |
| Quest hardware validation | OpenXR render path end-to-end on device | Phase 1 is build-validated, but the latest fixes still need explicit Quest runtime verification (`onResume/onPause`, swapchain submission, per-eye render stability) |
| Quest hardware validation | OpenXR render path end-to-end on device | Phase 4 compiles and unit-tests, but Quest runtime validation is still required for `Equirect2KHR` / `CylinderKHR` submission logs and visual correctness |

### Missing (no code yet)

- `VrStereoRenderer` dispatcher for sphere/cylinder geometry.
- 360° photo support (bitmap → equirect layer or sphere mesh).
- VR settings forced flat and spherical format options.
- Store submission: Meta Horizon manifest, icon branding, sideload docs.
- Doc updates: `docs/FEATURES.md` EN/RU/UK, `docs/VR_EDITION.md`, `docs/VR_SIDELOAD.md`.

## 4. Architecture (as-is, to be preserved)

```text
┌─────────────────────────────────────────────────────────────┐
│ ui/main  (standard flavor — unchanged)                       │
│  PlayerEntryCoordinator → ShowVrInstallCta / OpenStandard    │
└─────────────────────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│ vr/  (vr flavor only — thin host)                            │
│  VrPlayerActivity (extends PlayerActivity)                   │
│   ├─ OpenXrSessionManager  (JNI bridge — build validated)    │
│   ├─ VrPlaybackEngine  (wraps ExoPlayer)                     │
│   ├─ VrVideoSurfaceTextureBridge  (Exo frames → XR swapchain)│
│   ├─ VrStereoRenderer  (per-eye GL — SBS/OU today;            │
│   │                     360°/cylinder to add)                │
│   ├─ VrLayerFactory  (NEW — Projection/Equirect2/Cylinder)   │
│   └─ VrControlOverlayManager (QuadLayer UI)                  │
└─────────────────────────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│ shared (main)                                                │
│  StereoDetector · StereoMode · StaticImageRenderer ·         │
│  PlaybackControlDialogFragment · AppSettings (vr* fields)    │
└─────────────────────────────────────────────────────────────┘
```

**Data flow:** `ExoPlayer frame → SurfaceTexture → OES texture → VrStereoRenderer.renderEye(view, layerType) → OpenXR swapchain image → xrEndFrame`.

## 5. Key Decisions (frozen — from ambiguity questionnaire)

- MP4 `st3d`/`sv3d` boxes override filename heuristics when both disagree.
- PlaybackControlDialog shows **both** Plat (SBS/OU/MONO) and Spherical (360°/VR180/Cylinder) RadioGroups simultaneously; the inactive group is disabled but visible.
- Auto-detect never shows a toast — it's silent. Manual format changes toast briefly.
- All stereo format options are shown regardless of current detection state (user can always override).
- Target device is Meta Quest 3; Quest 2 is best-effort.
- Cinema mode = flat Quad layer facing user at fixed distance (default 4m, configurable).
- Union AndroidManifest: one APK works for Meta Horizon Store and sideload/Google Play.
- Per-file format cache persists to the existing Room DB (not SharedPreferences).

## 6. Phases

Each phase has its own tactical doc in [`PLAN/tasks/`](./tasks/) with acceptance criteria and file-level touchpoints.

| # | Phase | Depends on | Doc |
| - | ----- | ---------- | --- |
| 1 | Native OpenXR JNI layer ✅ | — | [phase_01_openxr_jni.md](./tasks/phase_01_openxr_jni.md) |
| 2 | StereoMode 360° extension + detection ✅ | — | [phase_02_stereomode_360.md](./tasks/phase_02_stereomode_360.md) |
| 3 | MP4 `st3d`/`sv3d` parser ✅ | 2 | [phase_03_mp4_spatial_boxes.md](./tasks/phase_03_mp4_spatial_boxes.md) |
| 4 | VrLayerFactory + equirect/cylinder layers ✅ | 1, 2 | [phase_04_layer_factory.md](./tasks/phase_04_layer_factory.md) |
| 5 | Sphere/cylinder renderer dispatch ✅ | 4 | [phase_05_sphere_cylinder_render.md](./tasks/phase_05_sphere_cylinder_render.md) |
| 6 | 360° photo support | 2, 4 | [phase_06_photo_sphere.md](./tasks/phase_06_photo_sphere.md) |
| 7 | PlaybackControlDialog spherical RadioGroup ✅ | 2 | [phase_07_dialog_spherical.md](./tasks/phase_07_dialog_spherical.md) |
| 8 | VR settings — forced 360° format options ✅ | 2, 7 | [phase_08_settings_360.md](./tasks/phase_08_settings_360.md) |
| 9 | Stereo snapshot (SBS PNG) ✅ | 1 | [phase_09_stereo_snapshot.md](./tasks/phase_09_stereo_snapshot.md) |
| 10 | VR command overrides (Fullscreen, Save Frame) ✅ | 9 | [phase_10_vr_commands.md](./tasks/phase_10_vr_commands.md) |
| 11 | Store submission + sideload infra | 1–10 | [phase_11_store_submission.md](./tasks/phase_11_store_submission.md) |
| 12 | Docs sync (FEATURES EN/RU/UK + VR_EDITION + VR_SIDELOAD) | 1–11 | [phase_12_docs.md](./tasks/phase_12_docs.md) |

Phases 1, 2, 3, 4, 5, 7, 8, 9, and 10 are complete in code. Phase 6 photo-sphere closeout is the next VR implementation milestone; phase 11 is the release gate and phase 12 ships with it.

## 7. Acceptance Criteria (master)

The VR edition is shipable when:

1. `./gradlew.bat :app_v2:assembleVrRelease` produces a signed APK that installs on Quest 3 via ADB or Meta Horizon Store.
2. Launching any video/image from the standard flavor's CTA or from within the VR flavor renders correctly:
   - 2D content → Cinema Quad layer.
   - SBS/OU content → Projection layer with per-eye UV crop.
   - 360° equirect → Equirect2KHR layer.
   - VR180 → Cylinder or cropped Equirect2 (half-sphere).
3. Auto-detection covers: filename patterns, MP4 `st3d`/`sv3d`, Matroska `StereoMode`, aspect-ratio heuristics.
4. User can override detected format from PlaybackControlDialog; override persists per-file when `vrRememberFileFormat=true`.
5. SBS PNG snapshot capture produces a 2×width image with both eyes composited.
6. Settings screen on VR flavor exposes separate forced-format defaults for flat + spherical playback.
7. Phone fallback screen shows on any non-headset device running vr flavor.
8. `docs/FEATURES.md` EN/RU/UK mirror the shipped feature set.

## 8. Non-Goals (deferred)

- Hand tracking, controller models, advanced input.
- Spatial audio (HRTF, ambisonic).
- Room-scale locomotion or passthrough AR UI.
- Video editing, re-encoding, or format conversion on device.
- Standalone app store listing outside Meta Horizon + sideload/Google Play.
- Support for Pico, HTC Vive Focus, or non-OpenXR runtimes.

## 9. Risks

| Risk | Mitigation |
| ---- | ---------- |
| Native OpenXR loader version drift with Meta runtime | Pin to Khronos 1.0.34+; fall back to `OpenXrInitException` with phone fallback UI |
| Quest 3 thermal throttling on 8K 360° video | Document minimum resolution; ExoPlayer codec selection prefers HW decoder |
| Meta Horizon Store rejection for UX issues | Build against Meta's 2026 VR submission checklist; test on-device before submission |
| MP4 box parsing adds complexity or dependency bloat | Keep parsing dependency-free and limit support to the authoritative boxes required by Phase 3 |

## 10. References

- [`temp/plan_archive_vr/vr-doc.md`](../temp/plan_archive_vr/vr-doc.md) — format research, OpenXR extension table, decoder matrix
- [`temp/plan_archive_vr/spec_openxr_3d_player.md`](../temp/plan_archive_vr/spec_openxr_3d_player.md) — original architecture spec (Phases 1–5 as first drafted)
- [`temp/plan_archive_vr/spec_vr-3d-image-viewing.md`](../temp/plan_archive_vr/spec_vr-3d-image-viewing.md) — image stereo (now implemented)
- [`temp/plan_archive_vr/spec_vr-3d-video-viewing.md`](../temp/plan_archive_vr/spec_vr-3d-video-viewing.md) — video bridge (partial)
- [`temp/plan_archive_vr/spec_vr-360-spherical-video.md`](../temp/plan_archive_vr/spec_vr-360-spherical-video.md) — 360°/VR180 spec
- [`temp/plan_archive_vr/vr-360-ambiguity-questionnaire.md`](../temp/plan_archive_vr/vr-360-ambiguity-questionnaire.md) — user decisions
