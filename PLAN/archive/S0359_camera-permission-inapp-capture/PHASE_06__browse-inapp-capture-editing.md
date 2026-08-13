# Phase 06 - Browse capture: in-app path + open-for-editing routing

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 04
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Switch the Browse camera-to-resource capture from `ACTION_IMAGE_CAPTURE` to the in-app `CameraCaptureActivity` (with a CAMERA permission gate), and route the saved photo into the existing drawing editor when `cameraCaptureOpenForEditing` is on.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`CameraCaptureActivity`).
- [ ] Phase 04 ✅ Done (`cameraCaptureOpenForEditing`).
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ +30 |

> `BrowseActivity` is large - keep additions minimal (a permission launcher + gate); put capture/editing logic in `BrowseCameraCaptureManager`, not the Activity (Strict Rule 3 / 1500-LOC guard).

---

## Steps

### Step 06.1 - In-app capture intent in BrowseCameraCaptureManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `launch(resource)` replace the `Intent(MediaStore.ACTION_IMAGE_CAPTURE)` (photo case) with `CameraCaptureActivity.createIntent(activity, uri)`, keeping the existing `FileProvider` temp-file + `EXTRA_OUTPUT` URI preparation and the `handlers`/`hasCameraHandler` probe unchanged (the probe still applies to the video case). Video capture (`ACTION_VIDEO_CAPTURE`) stays on the system intent (strategic non-goal). `handleResult` is unchanged - `CameraCaptureActivity` returns `RESULT_OK`. Remove unused photo-path `ACTION_IMAGE_CAPTURE` reference. WHY-comment: in-app capture removes the OEM confirmation step (S0359).

**Verification:**

- `Grep` - `CameraCaptureActivity` referenced in `BrowseCameraCaptureManager.kt`.
- `Grep -n "ACTION_IMAGE_CAPTURE"` returns zero hits in `BrowseCameraCaptureManager.kt` (video path uses `ACTION_VIDEO_CAPTURE` only).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 06.2 - CAMERA permission gate at the click site

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a `cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission())` mirroring the existing `recordAudioPermissionLauncher` (~line 71). In `onCameraCaptureClicked()` (~line 564), before `cameraCaptureManager.launch(resource)`, gate with `ContextCompat.checkSelfPermission(CAMERA)`: granted -> launch; else -> `cameraPermissionLauncher.launch(Manifest.permission.CAMERA)` and on grant proceed, on deny show the `camera_permission_required` Toast. Keep the VR passthrough branch first, as today.

**Verification:**

- `Grep` - `cameraPermissionLauncher` matches in `BrowseActivity.kt`.
- `Grep` - `Manifest.permission.CAMERA` referenced in `BrowseActivity.kt`.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 06.3 - Route saved photo to drawing editor when enabled

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> On a successful save, when `settings.cameraCaptureOpenForEditing` is true, route the saved file into the drawing editor via `BrowseEvent.NavigateToDrawingEditor(savedPath, resource.id)`. The current `onFileSaved(name)` callback passes only the name - extend the success path to also surface the saved absolute path (local) or the resolvable resource path (network) so the editor can open it. In `BrowseCameraCaptureManager.save()` capture the destination path and pass it through a new `onCapturedForEditing(path, resourceId)` callback (wired in `BrowseActivity` to emit the event through `BrowseFileOpenManager.openDrawingInEditor`). For network resources the editor opens the file via the existing player resource loading by `resourceId` + path. WHY-comment references S0359 open-for-editing.

**Verification:**

- `Grep` - `NavigateToDrawingEditor` reachable from the camera capture success path (referenced in `BrowseActivity.kt` capture wiring or via `openDrawingInEditor`).
- `Grep` - `cameraCaptureOpenForEditing` referenced in the capture flow.
- `/build` standardDebug compiles.

**Status:** `[x] done`

---

### Step 06.4 - Manual flow note for device test

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> On the `BlockNeedUserTest` transition, insert one `Timber.d("S0359: in-app camera capture to resource")` at the `launch()` entry, and remove it again when the spec leaves that status.

**Verification:**

- `Grep` - `S0359: in-app camera capture to resource` present once in `BrowseCameraCaptureManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `Grep -n "ACTION_IMAGE_CAPTURE"` returns zero hits in `BrowseCameraCaptureManager.kt`.
- [ ] Dev log entry for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if callback signatures changed.

---

## Handoff Notes to Next Phase

Both capture flows (OCR + Browse) are in-app. The open-for-editing routing reuses the existing drawing editor (S0360 adds a delete action to that editor's menu separately). Phase 07 finishes the permissions screen grouping.

---

## Rollback Plan

Revert phase commit(s) - restoring the `ACTION_IMAGE_CAPTURE` photo intent and dropping the permission gate returns Browse capture to the system camera. No persisted state changed.
