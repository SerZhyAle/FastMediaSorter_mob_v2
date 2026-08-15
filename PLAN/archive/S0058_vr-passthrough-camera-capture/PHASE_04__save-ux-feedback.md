# Phase 04 — Save + UX Feedback

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-05

---

## Objective

Implement `onJpegCaptured()`: write the JPEG `ByteArray` to a timestamped file in the current `MediaResource` (local path or via `MediaStore` for virtual paths); show confirmation `Snackbar`; invoke `onFileSaved` to trigger the Browse list refresh. Remove the `TODO("Phase 04")` marker.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `TODO("Phase 04")` present in `VrBrowsePassthroughCaptureManager.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt` | Modified | ≤ 300 |

> If the file would exceed 300 lines after this phase, extract file-save logic into a private `VrPassthroughFileSaver` helper class or object in the same file or a sibling file.

---

## Steps

### Step 04.1 — Implement `onJpegCaptured()` file-save

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace `TODO("Phase 04")` stub call with a real `onJpegCaptured(bytes, activity, resource, onFileSaved)` implementation. The function must:
>
> 1. Build file name: `"passthrough_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}.jpg"`.
>
> 2. Determine save target:
>    - If `resource.type == ResourceType.LOCAL` and `!VirtualPathUtils.isVirtualPath(resource.path)`: write to `File(resource.path, fileName)` on `Dispatchers.IO`.
>    - Otherwise (virtual path / network resource type): write via `MediaStore` — `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with `RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/FastMediaSorter"`, `IS_PENDING = 1`, then `IS_PENDING = 0` after write. Use `ContentValues` + `contentResolver.openOutputStream(uri)`. This matches the pattern in `VrStereoSnapshotManager.saveBitmap()`.
>
> 3. On success: call `onFileSaved(fileName)`.
>
> 4. On `IOException` / any `Throwable`: log with Timber + show `Toast(R.string.passthrough_capture_error_save)`.
>
> Imports needed: `java.time.LocalDateTime`, `java.time.format.DateTimeFormatter`, `java.io.File`, `com.sza.fastmediasorter.domain.model.ResourceType`, `com.sza.fastmediasorter.util.VirtualPathUtils`, `android.content.ContentValues`, `android.provider.MediaStore`, `android.os.Environment`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

**Verification:**

- `Grep` — `onJpegCaptured` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `passthrough_` present (filename prefix).
- `Grep` — `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` present.
- `Grep` — `ResourceType.LOCAL` present.
- `Grep` — `TODO("Phase 04")` — zero hits (stub replaced).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 5/5 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified). Dev log recorded.

---

### Step 04.2 — Show confirmation Snackbar after successful save

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `onJpegCaptured()`, after `onFileSaved(fileName)` is called, dispatch on Main:
>
> ```kotlin
> withContext(Dispatchers.Main) {
>     val msg = activity.getString(R.string.passthrough_capture_saved, resource.name)
>     Snackbar.make(activity.window.decorView.rootView, msg, Snackbar.LENGTH_LONG).show()
> }
> ```
>
> Import `com.google.android.material.snackbar.Snackbar`.

**Verification:**

- `Grep` — `passthrough_capture_saved` present in `VrBrowsePassthroughCaptureManager.kt`.
- `Grep` — `Snackbar.make` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: VrBrowsePassthroughCaptureManager.kt (modified). Dev log recorded.

---

### Step 04.3 — Verify end-to-end flow compiles; remove handoff marker

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Confirm `TODO("Phase 04")` is absent. Run `/build` for VR build. Confirm file compiles without unresolved references for `R.string.passthrough_capture_saved`, `R.string.passthrough_capture_error_save`, `R.string.passthrough_capture_unavailable`, `R.string.passthrough_capture_error`, `R.string.passthrough_capture_timeout`.
>
> **Note:** These string resources do not exist yet — Phase 05 adds them. If the build fails for missing strings, add temporary placeholder strings manually in `values/strings.xml` now and replace them in Phase 05.

**Verification:**

- `Grep` — `TODO("Phase 04")` — zero hits in the VR source set.
- VR debug build compiles (run `/build`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification PASS (zero TODO("Phase 04") hits; build exit 0). Added 7 placeholder strings to vr/res/values/strings.xml for build compatibility. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Full capture → save → feedback loop is implemented in VR source set.
- Five string resource keys referenced but not yet localised (Phase 05 adds them with real strings).
- Phase 05 finalises all three locale files and ensures no missing-key build warnings.

---

## Rollback Plan

Revert phase commit(s). No database schema changes. Non-VR builds unaffected.
