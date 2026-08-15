# Phase 02 - StandaloneImageEditController (crop / cropToFile / compress)

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** 01
**Blocks:** 04

## Objective

A standalone-side controller that wires the unedited generic `ImageCropManager` to the standalone layout - the equivalent of the `PlayerActivity`-bound `PlayerCropDelegate`, but driven by `editableImageFile` (resolved local path) and `resource = null`.

## Steps

### Step 02.1 - StandaloneImageEditController

**Files (new):** `app_v2/.../ui/player/standalone/StandaloneImageEditController.kt`

- Ctor: `activity: AppCompatActivity`, `mediaContentArea: ViewGroup`, `pinchTarget: View` (photoView), `imageCropManager: ImageCropManager`, `getEditFile: () -> MediaFile?` (the resolved local file), `onInPlaceCropSaved: () -> Unit` (activity re-renders).
- `enterCropMode(mode)`: mount `R.layout.player_crop_overlay_content` into `mediaContentArea`; wire `crop_overlay_view` pinch passthrough to `pinchTarget`, confirm/cancel. Mirror `PlayerCropDelegate` confirm logic but with `getEditFile()` and `resource = null`. CROP_TO_FILE shows `showCropFilenameDialog` then `performCropToFile(..., saveTo = null)` (→ Downloads). 
- `startCompressedCopy()`: `showCropFilenameDialog(COMPRESS_COPY)` → `performCompressedCopy(file, null, name, null, cb)`.
- Callback: CROP success → `onInPlaceCropSaved()`; CROP_TO_FILE/COMPRESS success → toast saved-to path; error → toast; `onCropModeExited` → remove overlay.
- ≤ 230 LOC; no edits to `ImageCropManager`.

**Verification:**

- `Glob` - `StandaloneImageEditController.kt` exists.
- `Grep` - references `ImageCropManager`, `player_crop_overlay_content`, `crop_overlay_view`; `resource = null` path (no `MediaResource` plumbing).
- `Grep` - `ImageCropManager.kt` unchanged (engine untouched).

## Phase Done Criteria

- [ ] Compiles (`/build`).
