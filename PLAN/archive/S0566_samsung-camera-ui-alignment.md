# Strategic Specification: S0566 - Samsung-style camera UI alignment and features

**Ticket:** S0566
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-20
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - spun off from Samsung camera UI alignment requests (2026-06-20)

---

## 0. Raw capture (verbatim owner input)

> В программе реализована активити камеры для фото и видео. Изучи это. Как можно юзер-интерфейс приблизить к типовому решению от самсунг (без потери функционала? Что можно добавить в реализацию?

### 0.1 Owner clarifications (2026-06-20, via /ui-clarify questions)

- Capture flow is dual:
  - Fixed callers (camera-OCR-translate, the explicit "take photo" / "take video" commands) take one frame/recording and close, continuing their own flow. Unchanged.
  - The general entry (main-activity overflow "Camera", plus the in-development home widget) keeps the camera open: the user switches photo/video and takes several photos/recordings until they close it manually. This entry fully replaces the device camera app.
- Gallery thumbnail shows the just-captured frame (a live-session affordance).
- Gesture model: swipe switches the lens; single tap = focus at point; double tap = auto-zoom to the maximum, double tap again = back to 1x.

---

## 1. Problem

The in-app camera ([CameraCaptureActivity.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt)) is functional but feels detached from a native One UI camera.

- The mode switch is a `MaterialButtonToggleGroup` of outlined buttons, not text tabs.
- Zoom is hidden behind a "More" toggle that reveals a slider.
- The shutter is a flat button; it does not change shape per mode or recording state.
- There is no gallery thumbnail of the last capture.
- The only gesture is tap-to-focus; no pinch-zoom, no swipe, no double-tap.
- There is no recording timer and no pause/resume during recording.
- The general "Camera" entry takes a single frame then closes, unlike a real camera app that stays open for a series of captures.

---

## 2. Goals

1. Re-align the bottom action bar to One UI: gallery thumbnail on the left, centered shutter, lens switch on the right.
2. Replace the outlined mode buttons with a custom text mode selector (PHOTO / VIDEO).
3. Expose zoom presets permanently on screen (drop the "More" toggle and the slider).
4. Make the shutter change appearance by mode and recording state.
5. Add gestures on the viewfinder: pinch-zoom, double-tap zoom toggle, swipe lens switch, horizontal swipe mode switch.
6. Add a recording timer overlay and a pause/resume control during recording.
7. Add a gallery thumbnail of the just-captured media; tapping it opens that file in the in-app player.
8. Make the general "Camera" entry a stay-open, multi-capture session that saves each capture without closing.

### Non-goals

- Do not change where files are saved (photo -> DCIM/Camera, video -> Movies, or the configured Browse destination).
- Do not give fixed-mode callers (OCR-translate, single photo/video commands) the stay-open behavior, the mode selector, or the thumbnail - their one-shot result contract is preserved.
- Do not rewrite the CameraX session/probe layer ([CameraCaptureSessionManager.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt)) beyond adding pause/resume.

---

## 3. Constraints & Guidelines

- Apply every layout change to both `res/layout/activity_camera_capture.xml` and `res/layout-land/activity_camera_capture.xml`.
- Keep the microphone toggle and the explicit RECORD_AUDIO grant (ADR-5 of S0545) intact.
- Rely on CameraX and standard SDK APIs at minSdk 26.
- All new labels/tooltips localized in EN, RU (with Ё/ё) and UK.
- No business logic in the Activity (CLAUDE.md Rule 3): new behavior lives in helper managers.
- Keep the Activity and each new helper under 1500 LOC.
- Support touch, D-pad/TV and mouse: gesture targets keep their focusable button equivalents.
- Keep UI inside the systemBars + displayCutout safe area in both orientations.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0545 (unified in-app capture host), S0563 (in-screen photo/video switch), S0523 (main "Camera" entry), S0369 (shared `CameraCaptureSaver`).
- **Capture flow:** dual - fixed callers stay one-shot-then-close; the general entry stays open for a multi-capture session.
- **Placement (portrait):** gallery thumbnail bottom-left, shutter bottom-center, lens switch bottom-right; zoom pills above the mode selector; mode selector above the action bar; recording timer top-center.
- **Placement (landscape):** same logical order rotated to the right edge, mirroring the existing landscape action bar.
- **Visibility predicates:** mode selector only when both photo and video are available; zoom pills only when the lens supports zoom; thumbnail only in multi-capture and only after the first capture; pause/resume only while recording.
- **Gestures:** single tap focus, double tap zoom toggle, pinch zoom, vertical swipe lens, horizontal swipe mode (multi-capture only).
- **Fallback:** when a control is unsupported by the lens its button is hidden (existing capability gating); single-shot mode hides the multi-capture-only affordances.
- **Accessibility:** all gesture actions keep a focusable button/tab equivalent for D-pad/TV and mouse.

---

## 4. Current Architecture Context

- [CameraCaptureActivity.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt) is a thin host; decisions live in [CameraCaptureFlowManager.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt), camera I/O in [CameraCaptureSessionManager.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt).
- Intent/result contract is owned by [CameraCaptureContract.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt); `EXTRA_ALLOW_MODE_SWITCH` already flags the general entry.
- Saving is Activity-free in [CameraCaptureSaver.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaver.kt) (a Hilt `@Singleton`); Browse and the main entry route through it.
- The general entry is [MainCameraCaptureManager.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt): it hands the host a scratch dir, then moves the returned file to DCIM/Camera or Movies on result.
- Capabilities are a per-bind snapshot in [CameraRuntimeCapabilities.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt); `buildZoomPresets` already produces Samsung-familiar steps.

---

## 5. Architectural Decisions (ADR)

**ADR-1: Stay-open multi-capture is a new lifecycle flag, not `allowModeSwitch`.**
- Add `EXTRA_MULTI_CAPTURE` (default false) to the contract. The general entry sets it true; mode-switch availability stays a separate concern (only when both photo and video are available).
- Fixed callers leave it false and keep the one-shot-then-finish contract verbatim.
- Reason: a general entry with only photo available must still stay open for a series of photos, so stay-open cannot be derived from `allowModeSwitch`.

**ADR-2: The host saves each capture itself only in multi-capture mode.**
- The Activity (already `@AndroidEntryPoint`) injects the existing `CameraCaptureSaver` and, after each in-session capture, saves to the public folder (photo -> `CameraFolder` = DCIM/Camera, video -> Movies) without finishing.
- Single-shot mode is untouched: it still writes the scratch file and returns it for the caller to move.
- Reason: the caller's result callback fires once on finish, which cannot exist in a stay-open session; moving the save into the host is the smallest change that keeps single-shot callers byte-for-byte identical.

**ADR-3: Gestures forward to manager actions; buttons stay for non-touch.**
- A `CameraCaptureGestureManager` wraps `GestureDetector` + `ScaleGestureDetector` on `previewViewCamera`.
- Single tap -> tap-to-focus; double tap -> zoom toggle; pinch -> continuous zoom; vertical fling -> lens switch; horizontal fling -> mode switch (multi-capture only).
- The lens-switch and shutter buttons remain visible and focusable so D-pad/TV/mouse keep parity.
- Reason: One UI feel without losing the project's mandated non-touch input support (Rule 16).

**ADR-4: Double-tap zoom toggles 1x and the lens maximum preset.**
- CameraX exposes only a single zoom-ratio range, not an optical/digital boundary, so "maximum optical" is approximated by the largest entry of `CameraRuntimeCapabilities.zoomPresets` (already clamped to the lens range), falling back to `maxZoomRatio`.
- Double tap stores the pre-zoom ratio so a second double tap restores 1x.
- Reason: honor the owner's intent within what CameraX can report.

**ADR-5: Zoom presets are always visible; the slider and "More" button are removed.**
- When `supportsZoom`, the preset pills are shown above the mode selector with no toggle; pinch covers continuous zoom; pills cover discrete/D-pad zoom.
- Reason: One UI parity and one fewer control; the slider is redundant with pinch + pills.

**ADR-6: The gallery thumbnail is multi-capture only.**
- After each in-session capture the thumbnail loads the just-saved file via Glide into a circular ImageView left of the shutter; tapping opens it in the in-app player.
- Hidden entirely in single-shot mode (the host closes immediately, so there is no session to preview).
- Reason: matches the owner's "just-captured frame" answer and the stay-open lifecycle.

---

## 6. Proposed Design

### 6.1 Mode lifecycle

- `CameraCaptureFlowManager` reads `EXTRA_MULTI_CAPTURE` into `multiCapture: Boolean`.
- Multi-capture photo: `onCaptureSucceeded()` saves via the host, refreshes the thumbnail, re-enables the shutter, and does NOT finish.
- Multi-capture video: `onRecordingFinalized()` saves via the host, refreshes the thumbnail, and does NOT finish.
- Single-shot: both still call `finishWithResult(..)` exactly as today.

### 6.2 Host-side saving (multi-capture)

- The Activity injects `CameraCaptureSaver` and a `CoroutineScope` (lifecycle).
- Photo -> `CameraCaptureTarget.CameraFolder`; video -> a Movies `Resource` target (mirror of `MainCameraCaptureManager.moviesTarget`).
- The captured scratch file is saved then deleted by the saver (existing behavior); the saved public path feeds the thumbnail and the player tap.
- `MainCameraCaptureManager` sets `EXTRA_MULTI_CAPTURE = true` and, for multi-capture results, no longer moves the file (the host already saved it); it only clears pending state.

### 6.3 Gestures

- `CameraCaptureGestureManager(previewView, callbacks)` owns one `GestureDetector` (tap, double-tap, fling) and one `ScaleGestureDetector` (pinch), routed through a single `setOnTouchListener`.
- Fling axis decides intent: |dx| > |dy| -> horizontal (mode), else vertical (lens), above a velocity/translation threshold.
- Pinch multiplies the current zoom ratio, clamped to the lens range, forwarded to `flowManager.onZoomRatioSelected`.

### 6.4 Dynamic shutter

- Three drawable states backed by new vector/shape drawables: photo (filled white disc), video-idle (white ring + red dot), video-recording (white ring + red rounded square).
- The Activity swaps the shutter foreground per mode and recording state (extends the existing `updateShutterRecordingState`).

### 6.5 Recording timer + pause/resume

- A timer overlay (red dot + `mm:ss`) at the top-center of the viewfinder, shown only while recording, driven by a lifecycle-bound ticker in a `CameraRecordingTimer` helper.
- `CameraCaptureSessionManager` gains `pauseRecording()` / `resumeRecording()` over CameraX `Recording.pause()/resume()`; a pause/resume button appears next to the shutter while recording and pauses the timer in step.

### 6.6 Mode selector

- A lightweight horizontal text selector (`HorizontalScrollView` + focusable `TextView` tabs) replaces the toggle group; selected tab is bold/white, others dim; same `switchMode` wiring.

### 6.7 Gallery thumbnail

- A circular `ImageView` (`btnGalleryThumbnail`) left of the shutter, hidden until the first in-session capture, then Glide-loaded from the saved file; click opens it through the existing in-app player entry.

---

## 7. Tactical Plan (phases)

### Phase 1 - Visual redesign (no lifecycle change)
- [x] New shutter state drawables + colors.
- [x] Always-visible zoom pills; remove `btnCameraMore`, `cameraZoomBar` slider from both layouts.
- [x] Custom text mode selector replacing the toggle group in both layouts.
- [x] Gallery thumbnail view added to both layouts (hidden by default).
- [x] Activity wires the new shutter states + zoom pills. (`fk` verified 2026-06-21)

### Phase 2 - Gestures
- [x] `CameraCaptureGestureManager` with pinch / double-tap / single-tap / fling.
- [x] Wire into the Activity touch listener; keep tap-to-focus + buttons.
- [x] Double-tap zoom toggle in `CameraCaptureFlowManager`. (`fk` verified 2026-06-21)

### Phase 3 - Recording timer + pause/resume
- [x] `pauseRecording` / `resumeRecording` in the session manager.
- [x] `CameraRecordingTimer` helper + overlay views in both layouts.
- [x] Pause/resume button + timer wiring in the Activity. (`fk` verified 2026-06-21)

### Phase 4 - Stay-open multi-capture + thumbnail + host saving
- [x] `EXTRA_MULTI_CAPTURE` in the contract + `multiCapture` in the flow manager.
- [x] Host injects `SaveCapturedMediaUseCase`; per-capture save without finishing. (uses new `SaveCapturedMediaUseCase`, not `CameraCaptureSaver` directly)
- [x] Thumbnail tap opens the just-captured file in the in-app player via `StandalonePlayerDispatcherActivity` (§6.7), not the system viewer.
- [x] `MainCameraCaptureManager` carries an explicit `multiCapture` flag (persisted across process death) and skips the move on result instead of inferring it from `RESULT_CANCELED`.
- [x] Localized EN/RU/UK. (`fk` verified 2026-06-21)

---

## 8. Verification Plan

### Automated
- `./a.ps1 fr` (resources/manifest), `./a.ps1 fk` (Kotlin), `./a.ps1 fc` (code + resources) after the relevant phase.

### Manual (device, S0566 tags)
- Single-shot callers (OCR-translate, Browse photo/video) still take one shot and close.
- General "Camera" entry stays open across several photos and recordings, saving each.
- Horizontal swipe switches PHOTO <-> VIDEO; vertical swipe flips the lens.
- Pinch zooms; double tap toggles 1x <-> max and back.
- Recording shows the timer and a working pause/resume.
- The thumbnail updates to the last capture and opens it in the player.
- Portrait and landscape both correct.

---

## Last Audit

**Date:** 2026-06-21 (spec-all, NO BUILD directive - static review only)

### Resolved since 2026-06-20 audit

- **[GAP, Med] Thumbnail tap now in-app.** `CameraCaptureActivity.openLastCapture()` routes the FileProvider uri through `StandalonePlayerDispatcherActivity` (resolves the media family, forwards to the matching standalone host) instead of an implicit `ACTION_VIEW`. Matches §6.7 and mirrors the blessed in-app open path (`LinkDownloadWorker.buildOpenInPlayerPendingIntent`).
- **[GAP, Low] Rule 19 fixed.** `res/drawable/ic_shutter_photo.xml` `fillColor` is now `@android:color/transparent` (was `#00000000`). The other two shutter drawables already used `@android:color/white`.
- **[PARTIAL, Low] Explicit multi-capture guard.** `MainCameraCaptureManager` now holds a `multiCapture` flag (set on launch, persisted via `KEY_PENDING_MULTI` across process death) and short-circuits `handleResult` to skip the move - the "host already saved" contract (§6.2 / ADR-2) no longer depends on the host returning `RESULT_CANCELED`.
- **[FOLLOW-UP, reduced] Scratch orphans.** The host's multi-capture failed-save branch (`persistMultiCapture`) now deletes the failed `CAP_<stamp>_<seq>` scratch file. Residual: a save still in flight when the session is closed (lifecycleScope cancelled) can leave one scratch file; a full session-dir sweep is out of scope here (the scratch `Capture` dir is shared with other capture entries).

### Implementation state

- All four phases are present in the working tree (uncommitted). Static review (read + grep, no build) confirms:
  - Phase 1: shutter drawables (`ic_shutter_photo/_video_idle/_video_recording`), zoom-preset `ChipGroup` (old `btnCameraMore`/`cameraZoomBar` removed from both layouts), text mode selector (`cameraModeSelector` + `tabModePhoto`/`tabModeVideo`), `btnGalleryThumbnail` (gone by default) - all in both `layout/` and `layout-land/`.
  - Phase 2: `CameraCaptureGestureManager` (pinch / single / double / fling) wired via `previewViewCamera`; double-tap toggle in `CameraCaptureFlowManager.onDoubleTapZoom()`; buttons kept focusable.
  - Phase 3: `pauseRecording()` / `resumeRecording()` in `CameraCaptureSessionManager`; `CameraRecordingTimer` helper; timer overlay + `btnCameraPauseResume` in both layouts.
  - Phase 4: `EXTRA_MULTI_CAPTURE` + `readMultiCapture`; new `SaveCapturedMediaUseCase` (`@Inject`, wraps `@Singleton CameraCaptureSaver`); host saves each capture without finishing; EN/RU/UK strings present.
- Hilt graph is valid (generated `SaveCapturedMediaUseCase_Factory`, `CameraCaptureActivity_MembersInjector`, `Hilt_CameraCaptureActivity`).

### Action items (remaining)

- All four 2026-06-20 code action items are resolved (see "Resolved since 2026-06-20 audit"). No open code action items.
- Build verification still pending (see Blocker) and on-device verification (§8 manual checks) not yet run.

### Blocker (RESOLVED 2026-06-21)

- The kapt NPE that previously blocked `:app_v2:kaptStandardDebugKotlin` no longer reproduces. `compileStandardDebugKotlin` (which chains kaptGenerateStubs + kapt) is green for the whole co-mingled tree (`.\a.ps1 fk`, BUILD SUCCESSFUL).
- Static re-investigation found no "two companion objects in one class" in any modified or untracked `.kt` (the only file with two companions, `SearchableOptionPickerDialog.kt`, declares them in two *different* classes - legal). The earlier duplicate-companion hypothesis did not hold; the broader WIP settled and the masked failure cleared.
- Verification done here: Kotlin + kapt compile only (`fk`). A full `assembleStandardDebug` / device install was not run during this `/spec-dev` pass (the tree is co-mingled with active S0568/streams WIP); on-device acceptance (§8) is now pending via the `BlockNeedUserTest` debug-tag run.
