# Phase 04 - Capture Result Manager

**Strategic spec:** [`../S0844_camera-capture-activity-detekt-baseline-drift.md`](../S0844_camera-capture-activity-detekt-baseline-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Extract multi-capture result persistence, gallery-thumbnail rendering, send-to visibility/menu, and "open last capture" into a new `CameraCaptureResultManager` that owns `lastSavedPath`/`lastSavedMediaType`.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureResultManager.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 850 (net shrink) |

---

## Steps

### Step 04.1 - Create CameraCaptureResultManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureResultManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraCaptureResultManager(activity: CameraCaptureActivity, lifecycleScope: CoroutineScope, sessionManager: CameraCaptureSessionManager, settingsRepository: SettingsRepository, saveCapturedMedia: SaveCapturedMediaUseCase, sendToMenuManager: SendToMenuManager, galleryThumbnail: ImageButton, sendToButton: View, onError: (Int) -> Unit)` in package `com.sza.fastmediasorter.ui.cameracapture.helpers` (adjust the gallery-thumbnail view's real type to match `binding.btnGalleryThumbnail`'s generated type - check the layout binding, do not guess). Move verbatim: `persistMultiCapture(file, isVideo)`, `showGalleryThumbnail(path)`, `updateSendToVisibility()`, `openSendToMenu()`, `openLastCapture()`, `inferMediaType(file)`. Move the `lastSavedPath: String?` and `lastSavedMediaType: MediaType?` fields into this class as `private var` with public read-only accessors (`val lastSavedPath: String?` / `val lastSavedMediaType: MediaType?`) since `resolveSaveDestinationName` reads `flowManager.currentOutputFile()` (unaffected) but callers outside this manager only ever check `lastSavedPath != null` for visibility - confirm no other Activity code reads these fields before finalizing the accessor shape (grep the current file first). Use `activity` as the `Context`/`Toast`/`FileProvider`/`startActivity` receiver and `activity.getString(...)`/`activity.packageName` where the moved code currently uses implicit `this`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureResultManager.kt` exists.
- `Grep` - `class CameraCaptureResultManager(` matches exactly once.
- `Grep` - `fun persistMultiCapture` and `fun openLastCapture` each match exactly once in the new file.

**Status:** `[x]` done

---

### Step 04.2 - Wire the Activity to the new manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Remove `persistMultiCapture`, `showGalleryThumbnail`, `updateSendToVisibility`, `openSendToMenu`, `openLastCapture`, `inferMediaType`, and the `lastSavedPath`/`lastSavedMediaType` fields from `CameraCaptureActivity`. Add `private lateinit var resultManager: CameraCaptureResultManager`, constructed in `setupViews()`. Update call sites: the `btnCameraSendTo`/`btnGalleryThumbnail` click listeners call `resultManager.openSendToMenu()` / `resultManager.openLastCapture()`; `capturePhoto()`'s `onSaved` callback and `startRecording()`'s finalize callback call `resultManager.persistMultiCapture(file, isVideo = ..)` instead of the removed local method; `updateShutterRecordingState()` calls `resultManager.updateSendToVisibility()` and reads `resultManager.lastSavedPath` where it previously read the removed field directly.

**Verification:**

- `Grep` - `private fun persistMultiCapture` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `private var lastSavedPath` returns zero hits in `CameraCaptureActivity.kt`.
- `Grep` - `resultManager = CameraCaptureResultManager(` matches exactly once.
- `Grep` - `resultManager.persistMultiCapture(` matches exactly 2 times (photo path + video path).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).

---

## Handoff Notes to Next Phase

`CameraCaptureResultManager` owns `lastSavedPath`/`lastSavedMediaType`. Phase 05's save-destination-label manager does NOT depend on this state (it reads `flowManager.currentOutputFile()` / settings, not the saved-result path) - no cross-dependency, but keep this note visible in case a future phase needs the saved path for the destination label.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - pure code relocation, identical persist/send-to/gallery behavior, no data migration or user-facing surface changed.
