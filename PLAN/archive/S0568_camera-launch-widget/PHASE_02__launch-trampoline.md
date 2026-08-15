# Phase 02 - Launch logic + transparent trampoline

**Strategic spec:** [`../S0568_camera-launch-widget.md`](../S0568_camera-launch-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-20
**Completed:** 2026-06-20 (commit ab3f5d02)

---

## Objective

Add the widget's business logic (`CameraLaunchWidgetManager`) and a transparent, no-UI trampoline activity (`CameraLaunchActivity`) that opens the unified camera host in switchable mode and saves the result to public folders - no on-screen UI, the user stays on the home screen.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`SaveCapturedMediaUseCase` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchActivity.kt` | New | ≤ 100 |

---

## Steps

### Step 02.1 - Create CameraLaunchWidgetManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraLaunchWidgetManager` holding all launch/save logic (Rule 3 - none in the activity). Constructor: `activity: Activity`, `settingsRepository: SettingsRepository`, `mediaCapabilities: MediaCapabilities`, `saveCapturedMedia: SaveCapturedMediaUseCase`, `coroutineScope: CoroutineScope`, `requestPermission: () -> Unit`, `launchCapture: (Intent) -> Unit`, `finish: () -> Unit`. Flow mirroring `MainCameraCaptureManager` + `CameraQuickCaptureLaunchManager`:
> - `start()`: read settings via `settingsRepository.getSettings().first()`; compute `photoAvailable = !settings.disableCameraCapture && mediaCapabilities.supportsImages` and `videoAvailable = !settings.disableVideoCapture && mediaCapabilities.supportsVideo`. If `!FEATURE_CAMERA_ANY` or neither mode available, toast `R.string.camera_capture_error_no_camera_app` and finish. Else create an app-private scratch dir (`getExternalFilesDir(null)/Capture`) + extension-less base name `CAP_<yyyyMMdd_HHmmss>`; store them as fields; then ensure CAMERA permission (request via `requestPermission` if not granted) and dispatch.
> - On permission granted: build the intent via `CameraCaptureContract.createSwitchableIntent(activity, dir, baseName, initialMode = if (photoAvailable) PHOTO else VIDEO, allowModeSwitch = photoAvailable && videoAvailable)` and call `launchCapture(intent)`. On denied: toast `R.string.camera_permission_required` and finish.
> - `onCaptureResult(resultCode, data)`: if not `RESULT_OK`, clear pending + finish. Else resolve captured file via `CameraCaptureContract.readResultOutputPath(data)` (fallback to `dir/base.<ext>` by `readResultMediaKind`), guard existence (`camera_capture_error_session_expired`), then `coroutineScope.launch { saveCapturedMedia(captured, isVideo) }` and toast per `SaveResult` branch (`camera_capture_saved` / `camera_capture_error_io` / `camera_capture_error_save_generic`), then finish.
> Use `Timber` only; route expected fallbacks at `Timber.i`/`w`, real failures at `Timber.e`. No `Sxxxx:` tags in this phase (the device-test probe is inserted in the final phase before the closing build, per CLAUDE.md "Debug Verification Tags").

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManager.kt` exists.
- `Grep` - `class CameraLaunchWidgetManager` matches once.
- `Grep` - `CameraCaptureContract.createSwitchableIntent(` present.
- `Grep` - `saveCapturedMedia(` present.
- `Grep` - `allowModeSwitch = ` present (switch enabled only when both modes available).
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.2 - Create CameraLaunchActivity trampoline

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `CameraLaunchActivity : AppCompatActivity()` annotated `@AndroidEntryPoint`, mirroring `CameraQuickCaptureActivity` (transparent, no layout). `@Inject lateinit var settingsRepository`, `@Inject lateinit var mediaCapabilities: MediaCapabilities`, `@Inject lateinit var saveCapturedMedia: SaveCapturedMediaUseCase`. Register a `RequestPermission` launcher and a `StartActivityForResult` launcher; in `onCreate` construct `CameraLaunchWidgetManager` wiring `requestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }`, `launchCapture = { captureLauncher.launch(it) }`, `finish = { finish() }`, `coroutineScope = lifecycleScope`, then call `manager.start()`. Forward the capture launcher callback to `manager.onCaptureResult(result.resultCode, result.data)`. Expose `companion object { const val ACTION_LAUNCH = "com.sza.fastmediasorter.action.LAUNCH_CAMERA" }`. No business logic in the activity.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchActivity.kt` exists.
- `Grep` - `@AndroidEntryPoint` present and `class CameraLaunchActivity` matches once.
- `Grep` - `CameraLaunchWidgetManager(` constructed in the activity.
- `Grep` - `const val ACTION_LAUNCH` present.
- `/build` - `standard debug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - validated in commit ab3f5d02 (`standard debug`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

`CameraLaunchActivity` (with `ACTION_LAUNCH`) is the trampoline target. Phase 03's widget provider points its tap PendingIntent at it.

---

## Rollback Plan

Revert phase commit(s) - two new files, no manifest entry yet (Phase 04), so nothing is user-reachable until registered.
