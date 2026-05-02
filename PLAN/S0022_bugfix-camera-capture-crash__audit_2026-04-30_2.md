# Audit Report — S0022 bugfix-camera-capture-crash

**Date:** 2026-04-30
**Mode:** full
**Overall score:** Verified

## Summary

- PASS: 26
- WARN: 0
- FAIL: 0
- MANUAL: 7
- UNCHECKABLE: 0
- EXEMPT: 0

## Strategic Checks

| Status | Check | Evidence | Action |
|------|------|------|------|
| PASS | Goals are covered by tactical phases | `PLAN/S0022_bugfix-camera-capture-crash/INDEX.md` defines phases for strings, visibility guard, exception handling, and process-death recovery, matching strategic axes in `§5`. | |
| PASS | Hard constraint: all affected flavors share the same code path | All implementation files are under `app_v2/src/main/...`; no flavor-specific source set was introduced. | |
| PASS | Hard constraint: no new API dependencies | No Gradle or dependency files were touched; changes are limited to existing Kotlin and string resources. | |
| PASS | Hard constraint: Wear OS is not affected | No files under `wear/` were touched by S0022. | |
| PASS | Hard constraint: EN/RU/UK localizations exist | `app_v2/src/main/res/values*/strings.xml` contain `camera_capture_error_no_camera_app`, `_permission_denied`, `_io`, `_session_expired`. | |
| PASS | Research item §6.2 is resolved | Strategic spec marks the menu-visibility decision as answered via ADR-3 and implemented behavior. | |
| PASS | Research item §6.4 is resolved | Strategic spec marks the process-death UX as answered with `Snackbar` + `camera_capture_error_session_expired`. | |
| PASS | User-facing feature text is present in FEATURES mirrors | `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` describe auto-hiding the command on devices without a compatible camera app and localized in-app errors. | |
| MANUAL | Completion criterion 1: command absent on Quest 3 / no-camera device | Strategic criterion is device-only. | Run on-device validation on Quest 3 or equivalent no-handler device. |
| MANUAL | Completion criterion 2: phone capture still works | Strategic criterion is device-only. | Run on-device validation on a phone with a working default camera app. |
| MANUAL | Completion criterion 3: process death recovery | Requires lifecycle interruption on device. | Reproduce process death between launch and result, then verify recovery path. |
| MANUAL | Completion criterion 4: system errors stay in-app | Requires runtime fault injection / device scenarios. | Validate no-camera, permission denial, and IO-failure scenarios on device. |
| MANUAL | Completion criterion 5: distinct execution markers in logs | Requires runtime capture. | Capture a fresh runtime log for success and each failure branch. |
| MANUAL | Completion criterion 6: Quest 3 + phone checklist | Explicitly device-only. | Execute the full on-device checklist before closure. |
| MANUAL | Completion criterion 7: localization review by meaning | Requires human review. | Perform manual EN/RU/UK wording review. |

## Tactical Checks

| Status | Check | Evidence | Action |
|------|------|------|------|
| PASS | Phase 01: EN strings added | `app_v2/src/main/res/values/strings.xml` contains all 4 new keys. | |
| PASS | Phase 01: RU strings added | `app_v2/src/main/res/values-ru/strings.xml` contains all 4 new keys. | |
| PASS | Phase 01: UK strings added | `app_v2/src/main/res/values-uk/strings.xml` contains all 4 new keys. | |
| PASS | Phase 02: `hasCameraHandler` helper exists | `BrowseCameraCaptureManager.kt` defines `fun hasCameraHandler(context: Context, resource: MediaResource): Boolean`. | |
| PASS | Phase 02: popup-menu visibility uses state + handler predicate | `BrowseManagerInitializer.kt` computes `isCameraVisibleByState` and gates it through `BrowseCameraCaptureManager.hasCameraHandler(activity, res)`. | |
| PASS | Phase 02: launch checks handlers before temp file creation | `BrowseCameraCaptureManager.launch()` performs `queryIntentActivities` before `createTemp(...)`. | |
| PASS | Phase 02: warning log on `handlers=0` exists | `BrowseCameraCaptureManager.kt` logs `CameraCapture: no handlers, command hidden action=%s` in `hasCameraHandler()`. | |
| PASS | Phase 03: Snackbar is used instead of Toast | `BrowseCameraCaptureManager.kt` imports `com.google.android.material.snackbar.Snackbar` and routes error UI through `showSnackbar(...)`. | |
| PASS | Phase 03: hardcoded English error strings were removed from UI paths | Error UI calls use string resources such as `camera_capture_error_no_camera_app`, `_permission_denied`, `_session_expired`, `_io`, and `_save_generic`. | |
| PASS | Phase 03: `SecurityException` is handled separately | `BrowseCameraCaptureManager.launch()` has dedicated `catch (e: SecurityException)` branches for both `FileProvider.getUriForFile(...)` and `launcher.launch(intent)`. | |
| PASS | Phase 03: `IOException` surfaces its dedicated localized string | `BrowseCameraCaptureManager.save()` maps `IOException` to `failureMessageRes = R.string.camera_capture_error_io`. | |
| PASS | Phase 04: manager persists capture context | `BrowseCameraCaptureManager.kt` defines `saveState(outState: Bundle)` and `restoreState(savedState: Bundle, getResourceById: ...)`. | |
| PASS | Phase 04: `BrowseActivity.onSaveInstanceState()` calls `saveState` | `BrowseActivity.kt` calls `cameraCaptureManager.saveState(outState)`. | |
| PASS | Phase 04: `BrowseActivity.onCreate()` calls `restoreState` | `BrowseActivity.kt` restores pending camera-capture state after manager initialization. | |
| PASS | Phase 04: unrecoverable context shows session-expired Snackbar | `BrowseCameraCaptureManager.restoreState()` and `handleResult()` call `showSnackbar(R.string.camera_capture_error_session_expired)` when context is missing. | |
| PASS | Build gate: `assembleStandardDebug` succeeded | `temp/build_s0022.txt` ends with `BUILD SUCCESSFUL`. | |
| PASS | Lint gate: no touched-file findings were captured in the S0022 lint artifact | `temp/lint_s0022.txt` fails on `BeamAnimationDialog.kt:99`, and grep for touched S0022 files returned zero hits. | |
| PASS | Tactical INDEX status is aligned with the current state | `PLAN/S0022_bugfix-camera-capture-crash/INDEX.md` now says `**Status:** BlockNeedUserTest`, matching the remaining manual device gate. | |

## Top Action Items

1. Run the Quest 3 / no-camera-device validation to confirm the popup command is absent and the `handlers=0` marker is recorded.
2. Run the phone validation matrix: successful capture, permission denial, save IO failure, and process-death recovery.
3. Perform a manual EN/RU/UK wording review and attach the on-device results to the ticket closure.
