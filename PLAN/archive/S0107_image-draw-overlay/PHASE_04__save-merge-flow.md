# Phase 04 — Save and Merge Flow

**Strategic spec:** [`../S0107_image-draw-overlay.md`](../S0107_image-draw-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Implement `MergeDrawOverlayUseCase` (domain layer), wire it through `PlayerViewModel` state, show the filename dialog, perform the in-memory merge, write the file via `DataSource`, and navigate to the new file on success.

---

## Prerequisites

- [ ] Phases 02 and 03 are ✅ Done.
- [ ] Blocker "Verify ImageLoadingManager exposes loaded Bitmap" is resolved: confirm `ImageLoadingManager` (or `PlayerViewModel.state`) provides the currently displayed Bitmap without re-download. If not, add a `currentDisplayedBitmap: Bitmap?` property to `PlayerViewModel` in Step 4.1.
- [ ] Working tree is clean or on a feature branch.
- [ ] Pre-edit backup: `PlayerViewModel.kt` is >500 LOC — create `temp/PlayerViewModel_S0107_<timestamp>.kt.bak`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MergeDrawOverlayUseCase.kt` | **New** | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 810 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 500 |

> Landscape layout parity: no layout changes in this phase.

---

## Steps

### Step 4.1 — Create `MergeDrawOverlayUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MergeDrawOverlayUseCase.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `MergeDrawOverlayUseCase` (annotated `@Inject constructor`) with a single `suspend fun execute` signature:
>
> ```kotlin
> suspend fun execute(
>     baseBitmap: Bitmap,
>     overlayBitmap: Bitmap,
>     outputFormat: Bitmap.CompressFormat,   // JPEG or PNG
>     quality: Int = 95
> ): Result<ByteArray>
> ```
>
> Implementation (on `Dispatchers.IO`):
> 1. Create a mutable copy of `baseBitmap` (ARGB_8888).
> 2. Draw `overlayBitmap` onto it via `Canvas(mutable).drawBitmap(overlayBitmap, 0f, 0f, null)`.
> 3. Compress to `ByteArrayOutputStream` using `outputFormat` and `quality`.
> 4. Return `Result.success(byteArray)`.
> 5. On any exception: return `Result.failure(e)`.
>
> No file I/O here — the caller owns writing the bytes to the correct DataSource. The use case is pure in-memory transformation.

**Verification:**

- `Glob` — `MergeDrawOverlayUseCase.kt` exists at the expected path.
- `Grep` — `class MergeDrawOverlayUseCase` matches exactly once.
- `Grep` — `suspend fun execute` present.
- `Grep` — `Dispatchers.IO` present (coroutine context).
- `Grep` — `Log\.d\(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS. Files: MergeDrawOverlayUseCase.kt (new, 53 LOC). Dev log recorded.

---

### Step 4.2 — Expose `currentDisplayedBitmap` from `PlayerViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Determine how the player currently holds the displayed Bitmap (check `ImageLoadingManager`, `PlayerImageLoadingCallbackImpl`, `PlayerState`). If a non-downscaled Bitmap is already accessible, document the accessor here and skip adding a new property. Otherwise:
>
> Add to `PlayerViewModel`:
>
> ```kotlin
> /** Bitmap currently shown in the image viewer; set by ImageLoadingManager on load. Null for video/audio. */
> var currentDisplayedBitmap: Bitmap? = null
>     internal set
> ```
>
> In `PlayerImageLoadingCallbackImpl` (or `ImageLoadingManager.onImageLoaded`), set `viewModel.currentDisplayedBitmap = bitmap` when an image is successfully loaded. Clear it (`= null`) on file change.
>
> Add `Timber.d("S0107: currentDisplayedBitmap set, size=${bitmap.byteCount}")` when the value is assigned.

**Verification:**

- `Grep` — `currentDisplayedBitmap` present in `PlayerViewModel.kt`.
- `Grep` — `currentDisplayedBitmap` assigned in `PlayerImageLoadingCallbackImpl.kt` or `ImageLoadingManager.kt`.
- `Grep` — `Timber.d("S0107: currentDisplayedBitmap set` present in the assigning file.
- `Grep` — `Log\.d\(` returns zero hits in modified files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: PlayerViewModel.kt (+6 LOC), ImageLoadingManager.kt (+5 LOC interface+call), PlayerImageLoadingCallbackImpl.kt (+8 LOC). Dev logs recorded.

---

### Step 4.3 — Implement filename dialog and merge trigger in `ImageDrawOverlayManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt`
**Depends on:** Steps 4.1, 4.2

**Prompt for developer:**

> Override the `exitDrawMode(save = true)` path to:
>
> 1. Retrieve `overlayBitmap = drawCanvasView.getBitmap()` — return early if null.
> 2. Build the default filename: take `currentFile.name` without extension, append `_draw_` + `YYMMdd_HHmm` (format from `LocalDateTime.now()` or `SimpleDateFormat`), then the original extension. Example: `photo_draw_260506_1430.jpg`.
> 3. Show an `AlertDialog` with an `EditText` pre-filled with the default filename and hint `@string/draw_overlay_filename_hint`.
> 4. On confirm: invoke `saveCallback?.onSaveRequested(overlayBitmap, confirmedFilename)` (extend the callback interface to include `filename: String`).
> 5. On dismiss/cancel: do nothing (Draw Mode stays active — user can try again or press Cancel).
>
> The `currentFile: MediaFile?` reference must be injected or passed from `PlayerActivity` before `enterDrawMode()`.

**Verification:**

- `Grep` — `_draw_` string literal present in `ImageDrawOverlayManager.kt` (filename template).
- `Grep` — `draw_overlay_filename_hint` referenced in `ImageDrawOverlayManager.kt`.
- `Grep` — `onSaveRequested` present (callback invocation).
- `Grep` — `Log\.d\(` returns zero hits in `ImageDrawOverlayManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: ImageDrawOverlayManager.kt (+25 LOC). Dev log recorded.

---

### Step 4.4 — Implement `DrawOverlaySaveCallback` in the player (coroutine-backed save)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` (callback interface update), `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` (callback impl)
**Depends on:** Steps 4.2, 4.3

**Prompt for developer:**

> In `PlayerActivity` (or `PlayerManagerInitializer`), set `imageDrawOverlayManager.saveCallback` to an anonymous object implementing `DrawOverlaySaveCallback.onSaveRequested(overlayBitmap, filename)`:
>
> 1. Retrieve `baseBitmap = viewModel.currentDisplayedBitmap ?: return` with a toast error if null.
> 2. Determine `outputFormat`: `Bitmap.CompressFormat.JPEG` for `.jpg`/`.jpeg`; `PNG` otherwise.
> 3. Determine `isReadOnly` from `viewModel.state.value.currentResource`.
> 4. Launch a coroutine on `lifecycleScope` + `Dispatchers.IO`:
>    a. Call `mergeDrawOverlayUseCase.execute(baseBitmap, overlayBitmap, outputFormat)` — inject via `@Inject lateinit var` in `PlayerActivity`.
>    b. On failure: switch to Main, toast the error.
>    c. On success: write `bytes` to the target path. If `!isReadOnly`: use the existing file-write mechanism (`FileOperationUseCase` or direct `DataSource` write) to save in the same directory as `currentFile`, with `filename`. If `isReadOnly`: write to local `Downloads` folder via `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`.
>    d. On write success (Main thread): call `imageDrawOverlayManager.exitDrawMode(save = false)` to clean the canvas, then if the new file is in the same resource, navigate to it via `viewModel` / `PlayerNavigationManager`; otherwise show `Toast` with `R.string.draw_overlay_saved_to_downloads` formatted with the path.
>    e. `Timber.d("S0107: overlay merged and saved to $targetPath")`.

**Verification:**

- `Grep` — `mergeDrawOverlayUseCase` present in `PlayerActivity.kt` or its callback impl.
- `Grep` — `DIRECTORY_DOWNLOADS` present (Downloads fallback).
- `Grep` — `Timber.d("S0107: overlay merged and saved` present.
- `Grep` — `Log\.d\(` returns zero hits in modified files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: PlayerActivity.kt (+70 LOC), PlayerManagerInitializer.kt (+1 LOC). Dev logs recorded.

---

### Step 4.5 — Navigate to new file after successful save in same resource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 4.4

**Prompt for developer:**

> After the file bytes are written to the same resource directory, add the new `MediaFile` to the player's file list and navigate to it. Inspect the existing patterns used by `SaveVideoFrameManager` (`SaveVideoFrameManager.kt`) and the camera-capture flow for the correct approach to refreshing the file list and jumping to the newly created file. Apply the same pattern here. The exact call depends on the current player API (`viewModel.onFileCreatedInCurrentDirectory(newFile)` or similar); if no such API exists, add a minimal one to `PlayerViewModel` following the naming convention `VerbNounUseCase` / method naming `onNounVerbed`.

**Verification:**

- `Grep` — Navigation-to-new-file call present in `PlayerActivity.kt` save callback (look for the method call after `DIRECTORY_DOWNLOADS` branch).
- Project compiles — run `/build`.
- `Grep` — `Log\.d\(` returns zero hits in modified files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Build PASS. Files: PlayerViewModel.kt (+12 LOC), PlayerActivity.kt (+3 LOC fix + withContext import). Dev logs recorded.

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run — `MergeDrawOverlayUseCase.kt` registered.
- [ ] `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- Full draw-overlay flow is functional: enter mode → draw → save dialog → merge → write → navigate.
- `Timber.d("S0107: ...")` tags are present throughout; logcat verification is possible.
- Phase 05 updates docs, cleans Timber tags, and regenerates the catalog.

---

## Rollback Plan

Revert phase commits. `MergeDrawOverlayUseCase.kt` is new — deletable. `PlayerActivity.kt` and `PlayerViewModel.kt` changes are additive property and coroutine wiring.
