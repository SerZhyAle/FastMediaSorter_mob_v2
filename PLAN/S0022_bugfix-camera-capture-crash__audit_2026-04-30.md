# Audit Report — S0022 bugfix-camera-capture-crash

**Date:** 2026-04-30
**Mode:** full
**Overall score:** Broken

## Summary

- PASS: 21
- WARN: 3
- FAIL: 2
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
| WARN | Research item §6.2 remains open | Strategic spec still says `Status: Open` for “Hide command vs show error on click”. | Resolve owner decision and fold it back into the strategic spec. |
| WARN | Research item §6.4 remains open | Strategic spec still says `Status: Open` for the process-death UX copy / form. | Resolve final UX copy and close the open item. |
| FAIL | User-facing feature text was not updated in FEATURES mirrors | `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` still contain only the old generic camera-capture bullet; grep for the new behavior keywords returned no hits. | Add the S0022 behavior to all three `docs/FEATURES*.md` files. |
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
| PASS | Phase 03: hardcoded English error strings were removed from UI paths | Error UI calls use string resources such as `camera_capture_error_no_camera_app`, `_permission_denied`, `_session_expired`, and `_save_generic`. | |
| PASS | Phase 03: `SecurityException` is handled separately | `BrowseCameraCaptureManager.launch()` has dedicated `catch (e: SecurityException)` branches for both `FileProvider.getUriForFile(...)` and `launcher.launch(intent)`. | |
| FAIL | Phase 03: `IOException` does not surface its dedicated localized string | `camera_capture_error_io` exists only in resources; grep found no code reference under `app_v2/src/main/java/**`. `save()` catches `IOException` but still falls through to `camera_capture_error_save_generic`. | Wire `IOException` to `camera_capture_error_io` in the user-facing Snackbar path. |
| PASS | Phase 04: manager persists capture context | `BrowseCameraCaptureManager.kt` defines `saveState(outState: Bundle)` and `restoreState(savedState: Bundle, getResourceById: ...)`. | |
| PASS | Phase 04: `BrowseActivity.onSaveInstanceState()` calls `saveState` | `BrowseActivity.kt` calls `cameraCaptureManager.saveState(outState)`. | |
| PASS | Phase 04: `BrowseActivity.onCreate()` calls `restoreState` | `BrowseActivity.kt` restores pending camera-capture state after manager initialization. | |
| PASS | Phase 04: unrecoverable context shows session-expired Snackbar | `BrowseCameraCaptureManager.restoreState()` and `handleResult()` call `showSnackbar(R.string.camera_capture_error_session_expired)` when context is missing. | |
| PASS | Build gate: `assembleStandardDebug` succeeded | `temp/build_s0022.txt` ends with `BUILD SUCCESSFUL`. | |
| PASS | Lint gate: no touched-file findings were captured in the S0022 lint artifact | `temp/lint_s0022.txt` fails on `BeamAnimationDialog.kt:99`, and grep for touched S0022 files returned zero hits. | |
| WARN | Tactical INDEX status is stale | `PLAN/S0022_bugfix-camera-capture-crash/INDEX.md` still says `**Status:** In Progress` even though all phase checklists are marked done and code landed on 2026-04-29. | Update the tactical INDEX status after the failing checks are fixed and the audit is rerun. |

## Top Action Items

1. [FOLLOW-UP] Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` with the S0022 behavior: hide the command on devices without a camera handler and show localized failures instead of crashing.
2. [FOLLOW-UP] In `BrowseCameraCaptureManager.kt`, surface `camera_capture_error_io` on the user-facing path for `IOException` instead of collapsing it into the generic save error.
3. [FOLLOW-UP] After fixes, rerun the audit and advance the tactical INDEX status from `In Progress` to the correct audited state.
