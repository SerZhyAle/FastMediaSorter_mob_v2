# Phase 04 - Camera capture engine (photo + video)

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research:** [`research/02__host-capture-adaptation.md`](research/02__host-capture-adaptation.md)
**Status:** ✅ Done
**Depends on:** none - reuses the existing `CameraCaptureSaver`
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce a host-neutral `MainCameraCaptureManager` that captures a photo (in-app CameraX) into `DCIM/Camera` and a video (system camera) into `Movies`, both saved through the existing `CameraCaptureSaver`. No menu wiring yet (Phase 05).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt` | New | ≤ 240 |

---

## Steps

### Step 4.1 - Photo capture into the public camera folder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainCameraCaptureManager(activity: FragmentActivity, coroutineScope: CoroutineScope, cameraCaptureSaver: CameraCaptureSaver)`. Register one `ActivityResultLauncher<Intent>` in the constructor (StartActivityForResult). `capturePhoto()` checks `FEATURE_CAMERA_ANY`, creates a `CAP_<timestamp>.jpg` temp under `getExternalFilesDir(DIRECTORY_PICTURES)`, builds a `FileProvider` uri (`${packageName}.fileprovider`), and launches `CameraCaptureActivity.createIntent(activity, uri, temp.absolutePath)` (in-app CameraX owns CAMERA permission). The result handler, on `RESULT_OK`, calls `cameraCaptureSaver.save(temp, temp.name, CameraCaptureTarget.CameraFolder) { _,_,_ -> false }` and shows the existing `R.string.camera_capture_saved` / error snackbars. Mirror the temp-cleanup discipline of `BrowseCameraCaptureManager`.

**Verification:**

- `Glob` - `MainCameraCaptureManager.kt` exists.
- `Grep` - `class MainCameraCaptureManager` matches once.
- `Grep` - `fun capturePhoto()` present.
- `Grep` - `CameraCaptureTarget.CameraFolder` present.
- `Grep` - `CameraCaptureActivity.createIntent(` present.

**Status:** `[x]` done

---

### Step 4.2 - Video capture into the public Movies folder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Add `captureVideo()`: guard with `BrowseCameraCaptureManager.hasVideoCaptureHandler(activity)`, create a `CAP_<timestamp>.mp4` temp under `getExternalFilesDir(DIRECTORY_MOVIES)`, FileProvider uri, launch `Intent(MediaStore.ACTION_VIDEO_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)` through the same launcher. Track an `pendingIsVideo` flag so the shared result handler routes a video to `cameraCaptureSaver.save(temp, temp.name, <Movies target>) { _,_,_ -> false }`, where the Movies target is `CameraCaptureTarget.Resource(id=-1, name=Environment.DIRECTORY_MOVIES, path=CaptureDestinationPolicy.resolveVideoDestination(null).absolutePath, type=ResourceType.LOCAL)` (the proven `localVideoFallbackTarget` shape). Photo uses `CameraFolder`. No CAMERA permission is requested for the video path (system camera).

**Verification:**

- `Grep` - `fun captureVideo()` present.
- `Grep` - `MediaStore.ACTION_VIDEO_CAPTURE` present.
- `Grep` - `resolveVideoDestination(null)` present.
- `Grep` - `hasVideoCaptureHandler(` referenced.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` on `MainCameraCaptureManager.kt` returns zero hits.
- [ ] Dev log entry added for the new file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class) - may be deferred to Phase 07.

---

## Handoff Notes to Next Phase

`MainCameraCaptureManager` exposes `capturePhoto()` and `captureVideo()` writing to `DCIM/Camera` and `Movies`. Phase 05 constructs it in `MainActivity.setupViews` and triggers the two methods from the photo and video menu entries.

---

## Step Log

- 2026-06-19 - Step 4.1 Verification PASS. `MainCameraCaptureManager.kt` New: `capturePhoto()` -> CameraCaptureActivity -> `CameraCaptureSaver(CameraFolder)`.
- 2026-06-19 - Step 4.2 Verification PASS. `captureVideo()` -> ACTION_VIDEO_CAPTURE -> saver Movies target (`resolveVideoDestination(null)`); guarded by `hasVideoCaptureHandler`.
- 2026-06-19 - Compile: fixed missing `showSnackbar(String)` overload (type mismatch at L125); `a.ps1 fk` BUILD SUCCESSFUL (covers Phases 03+04). No Log.d.

---

## Rollback Plan

Delete `MainCameraCaptureManager.kt` - no callers until Phase 05.
