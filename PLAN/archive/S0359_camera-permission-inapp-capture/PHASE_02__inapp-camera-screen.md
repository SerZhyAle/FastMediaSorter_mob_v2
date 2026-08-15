# Phase 02 - In-app camera capture screen (CameraX, drop-in for ACTION_IMAGE_CAPTURE)

**Strategic spec:** [`../S0359_camera-permission-inapp-capture.md`](../S0359_camera-permission-inapp-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Introduce `CameraCaptureActivity` - a CameraX preview + single shutter that writes the captured JPEG to the caller-supplied `EXTRA_OUTPUT` URI and returns `RESULT_OK`, behaving as a drop-in replacement for `MediaStore.ACTION_IMAGE_CAPTURE`. All capture orchestration lives in a helper manager; the Activity is a thin shell (Strict Rule 3).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (CameraX deps + CAMERA permission declared).
- [ ] Working tree clean or on `DEBUG-v013`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | New | ≤ 300 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | New | ≤ 120 |
| `app_v2/src/main/res/layout-land/activity_camera_capture.xml` | New | ≤ 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`) | Modified | ≤ +4 each |

> Landscape parity: this is a new screen - both `layout/` and `layout-land/` variants are created in this phase (Steps 02.3 / 02.4).

---

## Steps

### Step 02.1 - Capture session manager (CameraX binding + capture)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraCaptureSessionManager` (Manager naming, no Activity logic in it). Responsibilities: bind a CameraX `Preview` + `ImageCapture` use case to a `LifecycleOwner` and a `PreviewView`'s surface provider via `ProcessCameraProvider`; expose `bind(previewView)`, `capture(outputFile, onSaved, onError)` writing a JPEG to the given file off the main thread, and `unbind()`. No permission UI here (the Activity owns the request). Use back camera by default. Timber for logging (no `Log.d`).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` exists.
- `Grep` - `class CameraCaptureSessionManager` matches once.
- `Grep` - `ProcessCameraProvider` and `ImageCapture` present.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 02.2 - CameraCaptureActivity shell

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `CameraCaptureActivity` (extends the project `BaseActivity`, `@AndroidEntryPoint`). It reads `EXTRA_OUTPUT` (a `Uri`) from the launching intent, checks/requests CAMERA at start via `ActivityResultContracts.RequestPermission()` (copy the BrowseActivity RECORD_AUDIO launcher pattern), and on grant binds `CameraCaptureSessionManager` to a `PreviewView`. The shutter button triggers `capture(...)`; on success it writes to `EXTRA_OUTPUT` and `setResult(RESULT_OK); finish()`. On denial it shows the rationale string and `setResult(RESULT_CANCELED); finish()`. Provide `companion` `createIntent(context, outputUri)` and `EXTRA_OUTPUT` constant. Apply system bar insets; shutter button focusable + D-pad/Enter activatable (Rule 17).

**Verification:**

- `Glob` - `CameraCaptureActivity.kt` exists.
- `Grep` - `class CameraCaptureActivity` matches once.
- `Grep` - `fun createIntent(` and `RegisterForActivityResult|registerForActivityResult` present.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

---

### Step 02.3 - Portrait layout

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create the portrait layout: a `androidx.camera.view.PreviewView` filling the screen, a shutter `ImageButton` (focusable, clickable, contentDescription), and a close/cancel control. Keep controls inside `systemBars()` + `displayCutout()` safe bounds (Rule 18). Match the project's visual style (colors, corner radii, icon style).

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/activity_camera_capture.xml` exists.
- `Grep` - `androidx.camera.view.PreviewView` present.
- `Grep` - `focusable` and `contentDescription` present on the shutter control.

**Status:** `[x] done`

---

### Step 02.4 - Landscape layout (parity)

**Files:** `app_v2/src/main/res/layout-land/activity_camera_capture.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Create the landscape counterpart of `activity_camera_capture.xml` with the same ids, the shutter control repositioned for landscape ergonomics, same insets/focus rules. Strict Rule 12: ids must match the portrait variant exactly.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout-land/activity_camera_capture.xml` exists.
- `Grep` - `androidx.camera.view.PreviewView` present.
- Both layouts declare the same shutter button id (manual id-parity check; record expected id | actual id).

**Status:** `[x] done`

---

### Step 02.5 - Manifest declaration + rationale string

**Files:** `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`)
**Depends on:** Step 02.2

**Prompt for developer:**

> Declare `<activity android:name=".ui.cameracapture.CameraCaptureActivity" android:exported="false" android:configChanges="orientation|screenSize|keyboardHidden" android:theme="@style/Theme.FastMediaSorter" />` near the existing capture-related activities (~line 147). Add a `camera_permission_required` rationale string (EN/RU/UK lockstep via `set-android-string.ps1 -Action add`) shown when CAMERA is denied. Tone per COMMUNICATION_POLICY.

**Verification:**

- `Grep` - `.ui.cameracapture.CameraCaptureActivity` matches once in `AndroidManifest.xml`.
- `Grep` - `camera_permission_required` in all three `strings.xml` (3 hits).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_permission_required"` exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build` standardDebug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes) via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`CameraCaptureActivity.createIntent(context, outputUri)` returns `RESULT_OK` with the JPEG written to `outputUri` - a drop-in for `ACTION_IMAGE_CAPTURE`. Phases 03 and 06 swap their capture intent for this Activity. CAMERA permission is requested inside the Activity, so callers may also pre-gate for a smoother UX.

---

## Rollback Plan

Revert phase commit(s) - new files only; no existing flow calls the new Activity yet, so nothing else is affected.
