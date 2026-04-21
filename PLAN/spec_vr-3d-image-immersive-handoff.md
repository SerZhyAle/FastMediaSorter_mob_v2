# Specification: VR.1 - 3D Image Immersive Handoff

**Status:** Draft
**Date:** 2026-04-21
**Tier:** 4 - Strategic (8h+, high risk)
**Roadmap entry:** Not yet listed in PLAN/IMPROVEMENT_ROADMAP.md. Ad-hoc runtime investigation from the 2026-04-21 VR failure report.

---

## 1. Problem Statement

The VR flavor currently routes every non-video file back to the standard player path from `VrPlayerActivity`, even when the selected asset is a stereoscopic 3D image that the user expects to open in immersive mode. This means the panel -> immersive handoff is missing for non-video VR media in [app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt](p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt). The fallback is also silent, so the user sees a return to the previous player flow without a toast or error dialog, and teardown can leave Media3 warnings in the log tail.

---

## 2. Goals

1. Add an explicit immersive entry path for supported stereoscopic non-video media, starting with 3D images and photos.
2. Replace silent fallback with a guaranteed user-visible error or status message whenever immersive mode cannot start.
3. Preserve the existing XR failure recovery path, but make unsupported-media and XR-init failures distinguishable to the user and logs.
4. Harden teardown ordering so fallback/finish does not leave the player in a dead-thread warning state during route changes.

Non-goals for this spec: full VR document immersive support, complete controller-action-set redesign, and broad VR UI redesign beyond the failure messaging and route handoff.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ❌ | Uses standard player flow and VR install CTA only. |
| `lite`     | ❌ | No VR player support. |
| `photos`   | ❌ | No VR player support. |
| `legacy`   | ❌ | No VR player support. |
| `vr`       | ✅ | Primary target. `BuildConfig.SUPPORT_VR_PLAYER` and `BuildConfig.PLAYER_ACTIVITY_CLASS` route playback into `VrPlayerActivity`. |
| `vrUnlicensed` | ✅ | Shares the same `src/vr` sources and must behave identically. |

Existing gating already applies through `BuildConfig.SUPPORT_VR_PLAYER` and `BuildConfig.PLAYER_ACTIVITY_CLASS` in [app_v2/build.gradle.kts](p:/ANDROID/FastMediaSorter_mob_v2/app_v2/build.gradle.kts). No new `BuildConfig` flag is required for the first implementation pass.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26+ (VR flavor minSdk) | Default path for OpenXR-capable VR builds. |
| 29 (Android 10) | No special scoped-storage change expected for the immersive handoff itself. |
| 30+ (Android 11) | SAF and MediaStore behavior remain unchanged; only playback routing changes. |
| 34+ (Android 14) | No known API-specific change for the XR handoff path; verify activity launch and lifecycle ordering on target SDK 35. |

### 3.3 Wear OS Impact

No Wear OS changes required.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `VrPlayerActivity` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | VR host activity that decides whether to stay in standard playback or initialize XR. |
| `OpenXrSessionManager` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Owns OpenXR session lifecycle and render thread startup/shutdown. |
| `VrPhotoSphereRenderer` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrPhotoSphereRenderer.kt` | VR image renderer used when a static image is active. |
| `PlayerActivity` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Base player host reused by VR flavor through inheritance. |
| `PlayerEventHandler` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt` | Standardized error and toast/dialog presentation path. |
| `AndroidManifest.xml` | `app_v2/src/vr/AndroidManifest.xml` | Defines panel-mode entry on `MainActivity` and immersive host on `VrPlayerActivity`. |
| `StereoDetector` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` | Detects stereoscopic layout from filename and metadata. |

The key limitation is that `VrPlayerActivity.shouldUseStandardPlayer()` currently treats every non-video media type as unsupported for immersive playback, even if the renderer stack is already structured to support static VR image rendering.

---

## 5. Proposed Architecture

### 5.1 Route-family decision instead of media-type-only fallback

Replace the current `MediaType.VIDEO` gate in `VrPlayerActivity.shouldUseStandardPlayer()` with a route-family decision that distinguishes:

- `STANDARD_PANEL_FALLBACK`
- `IMMERSIVE_VIDEO`
- `IMMERSIVE_STATIC_IMAGE`
- `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`

The decision should be based on stereo classification and renderer availability, not only on `MediaType`. Supported stereoscopic image types should be allowed to reach XR initialization and the static-image renderer path.

Illustrative Kotlin sketch:

```kotlin
internal enum class VrLaunchRoute {
    STANDARD_PANEL_FALLBACK,
    IMMERSIVE_VIDEO,
    IMMERSIVE_STATIC_IMAGE,
    UNSUPPORTED_IMMERSIVE_WITH_MESSAGE,
}
```

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `VrLaunchRoute.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/` | <= 80 |
| `VrRouteDecisionHelper.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/` | <= 220 |

If route selection expands beyond the initial image/video split, it must stay outside `VrPlayerActivity` to keep the Activity thin and below the practical complexity ceiling.

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ⚠️ | Existing logic is in `VrPlayerActivity`; new route logic should move into `VrRouteDecisionHelper`. |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | Helper naming is consistent with existing player helper pattern. |
| Data flow strictly `UI -> ViewModel -> UseCase -> Repository -> DataSource` | ⚠️ | Route decision remains UI-layer orchestration. Avoid new data logic in the Activity. |
| No `Log.d()` - Timber only | ✅ | Continue using `Timber`; keep existing `Log.e("VR_BOOT", ..)` only for high-priority boot diagnostics. |
| Room schema version incremented (if DB changes) | N/A | No database changes. |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | Reuse current `PlayerViewModel` event/state flows. |
| Hilt DI: new bindings declared in module file | N/A | Initial helper can be constructed directly unless later promoted to injectable dependency. |

---

## 6. Data Flow

```text
User selects 3D file in panel mode
  -> Player launch uses BuildConfig.PLAYER_ACTIVITY_CLASS
  -> VrPlayerActivity receives current file
  -> VrRouteDecisionHelper decides route family
      -> IMMERSIVE_VIDEO or IMMERSIVE_STATIC_IMAGE
          -> OpenXrSessionManager.initialize()
          -> onSessionReady
          -> video bridge or static image renderer activated
          <- user sees immersive content
      -> UNSUPPORTED_IMMERSIVE_WITH_MESSAGE
          -> PlayerEventHandler.showError()/toast
          -> controlled fallback to PlayerActivity or Browse
      -> STANDARD_PANEL_FALLBACK
          -> PlayerActivity launch
          <- user remains in panel mode
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Replace non-video blanket fallback with route-family decision, explicit user-visible failure path, and safer teardown ordering | ~760 lines |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Expose clearer initialization failure reason hooks if needed | ~360 lines |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrPhotoSphereRenderer.kt` | Confirm/extend static-image immersive path for stereoscopic images | <400 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt` | Reuse or extend standardized error presentation for immersive fallback reasons | ~240 lines |
| `app_v2/src/main/res/values/strings.xml` | Add user-facing immersive failure and unsupported-route strings | existing resource file |
| `app_v2/src/main/res/values-ru/strings.xml` | Add RU strings | existing resource file |
| `app_v2/src/main/res/values-uk/strings.xml` | Add UK strings | existing resource file |

Because `VrPlayerActivity.kt` is already above 500 lines, create a timestamped backup in `temp/` before implementation.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Static-image immersive route starts XR but image renderer is incomplete for some stereo families | Med | Limit first pass to explicitly supported 3D image layouts and reject unsupported ones with a visible message. |
| New fallback messaging appears too late because Activity is already finishing | Med | Post the error before finish and centralize fallback ordering in one method. |
| Media3 teardown warning persists after route changes | Med | Stop playback, clear video surface, then release XR, then finish. Add regression log validation. |
| Route logic grows further inside `VrPlayerActivity` and makes the file harder to maintain | High | Extract route decision helper on the first implementation pass. |
| User-visible behavior diverges between `vr` and `vrUnlicensed` | Low | Verify both flavors share `src/vr` sources and run one smoke pass per flavor. |

---

## 9. Testing Plan

### 9.1 Unit Tests

Add or extend tests for:

- `VrRouteDecisionHelperTest`
  - stereoscopic image -> immersive static image route
  - plain 2D image -> standard panel fallback
  - unsupported non-video 3D asset -> unsupported-with-message route
  - XR disabled setting -> standard panel fallback
- `VrPlayerActivity` route-policy tests if activity-independent helpers cannot cover all logic
- `StereoDetector` regression cases for filenames representing SBS/OU still images

### 9.2 Manual Test Cases

1. On Quest 3, open a supported stereoscopic 3D image from panel mode and verify immersive mode starts and the image renders in stereo.
2. Open a normal 2D image and verify the app stays in panel mode without XR startup.
3. Open a supported stereoscopic 3D video and verify the existing immersive video path still works.
4. Open an unsupported non-video 3D asset and verify a visible error appears before fallback.
5. Force XR startup failure and verify the user sees a visible error, playback stops, and no silent background audio remains.
6. Repeat the failure case while monitoring logs and confirm the dead-thread warning is reduced or eliminated.
7. Verify the same behavior on both `vr` and `vrUnlicensed` builds.

### 9.3 Maestro E2E (if applicable)

No Maestro tests needed for the first pass because Quest immersive transitions are not a realistic Maestro target in the current workspace. Manual device validation is required.

---

## 10. Accessibility

This change affects user-visible error feedback and route behavior. Any new failure message must be reachable by TalkBack if shown as a dialog, and any fallback toast text must be clear and specific enough to explain why immersive mode did not start. No new small touch targets are required if the initial implementation only adds messaging and route handling.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): `- VR edition can open supported stereoscopic 3D images in immersive mode and now shows an explicit error when immersive startup fails.`
- `docs/FEATURES_RU.md` (RU): `- VR-версия умеет открывать поддерживаемые стереоскопические 3D-изображения в immersive-режиме и теперь явно сообщает об ошибке, если immersive-запуск не удался.`
- `docs/FEATURES_UK.md` (UK): `- VR-версія вміє відкривати підтримувані стереоскопічні 3D-зображення в immersive-режимі та тепер явно повідомляє про помилку, якщо immersive-запуск не вдався.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Route by immersive capability, not by media type alone**
- **Decision:** Replace the current `MediaType.VIDEO` gate with a route decision based on stereo classification plus renderer capability.
- **Alternatives considered:** Keep the current blanket non-video fallback and only add a toast.
- **Reason:** A toast-only fix would improve UX but would not solve the functional bug reported by the user.

**ADR-2: Use the existing standardized player error path for VR fallback messaging**
- **Decision:** Reuse `showError()` / `PlayerEventHandler` instead of inventing a VR-only toast utility.
- **Alternatives considered:** Add direct `Toast.makeText()` calls inside `VrPlayerActivity`.
- **Reason:** Centralized error presentation respects user settings and already handles dialog-vs-toast behavior.

**ADR-3: Stabilize fallback teardown before expanding controller or overlay work**
- **Decision:** Treat route and failure ordering as part of the fix.
- **Alternatives considered:** Ship immersive static-image routing first and defer teardown cleanup.
- **Reason:** The current dead-thread warning indicates that route changes are already stressing lifecycle ordering.

---

## 13. Implementation Steps

1. Create a timestamped backup of `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` in `temp/` before any edits.
2. Introduce a small route-family model (`VrLaunchRoute`) and extract route selection into `VrRouteDecisionHelper`.
3. Update `VrPlayerActivity.resolvePlaybackRoute()` and `shouldUseStandardPlayer()` to use the new route family instead of the current blanket non-video fallback.
4. Allow supported stereoscopic images to reach the immersive static-image path and reject unsupported immersive media through an explicit reasoned failure branch.
5. Centralize fallback handling so user-visible error messaging happens before finish/teardown.
6. Re-check `forceStopVrPlayback()` and XR release ordering to minimize Media3 dead-thread warnings during fallback.
7. Add EN/RU/UK strings for immersive startup failure and unsupported immersive media.
8. Add unit tests for route decisions and stereo-image routing regressions.
9. Manually validate on Quest 3 with one supported 3D image, one normal 2D image, one 3D video, and one forced XR failure.
10. Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` after implementation.
11. Run `./scripts/add_to_dev_log.ps1` for every modified file.

Mandatory step checklist at the end:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (if user-facing)
- [ ] Room DB migration added + version incremented (if DB schema changes)
- [ ] `./scripts/add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- Full immersive support for PDF, EPUB, and generic document assets.
- Replacing Android key-event controller handling with a complete OpenXR action-set implementation.
- Passthrough-specific UX redesign or cooperative overlay windows inside immersive mode.
- Large-scale VR settings redesign beyond the messaging and route behavior required for this fix.