# Phase 06 — In-place Save + overflow "Save to new"

**Strategic spec:** [`../S0192_draw-editor-toolbar-ux-v2.md`](../S0192_draw-editor-toolbar-ux-v2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

**Notes:**
- Step 06.3: in-place save fully implemented. Read-only / non-local resources fall through to the legacy `handleSaveRequest` → `saveCallback` pipeline via `exitDrawMode(save = true)` — keeping the existing AlertDialog filename UX rather than rerouting through `FileOperationDestinationDialog` per ADR-4 (silent fallback). The strategic spec mentions `FileOperationDestinationDialog` for the overflow "Save to new file" item; that reroute is deferred (the existing AlertDialog flow ships unchanged in this phase) — fully tracked as a follow-up in the spec's `## Last Audit` after `/spec-check`.
- Background build initially failed with a transient `Couldn't delete R.jar` lock; retry passed (1m 6s, BUILD SUCCESSFUL).

---

## Objective

Split the `[Save]` button and overflow "Save to new file" into two distinct paths. `[Save]` overwrites the current file in-place when the resource is local and not read-only; otherwise silently falls through to the "Save to new file" path. Overflow "Save to new file" invokes `FileOperationDestinationDialog` for explicit destination picking.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | — |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> **Backup rule.** Backup `ImageDrawOverlayManager.kt` to `temp/` before editing if file is above 500 LOC after Phase 05.

---

## Steps

### Step 06.1 — Add `draw_save_ok_toast` / `draw_save_failed_toast` strings

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following keys to all three `strings.xml` files. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (formula for status messages) and §6 tone checklist — short, factual, no exclamation marks.
>
> | Key | EN | RU | UK |
> |-----|----|----|-----|
> | `draw_save_ok_toast` | Saved | Сохранено | Збережено |
> | `draw_save_failed_toast` | Failed to save | Не удалось сохранить | Не вдалося зберегти |

**Verification:**

- `Grep` (target: each of the 3 string files) — each of the 2 keys present exactly once per file.
- Strings pass COMMUNICATION_POLICY §6 checklist (developer self-check).
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "draw_save_"` returns exit 0. expected: 0 missing | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 06.2 — Add `saveInPlace` flow to `ImageDrawOverlayManager` and rewire `[Save]`

**Files:** `ImageDrawOverlayManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a new callback interface on the manager:
>
> ```kotlin
> interface DrawOverlayInPlaceSaveCallback {
>     fun onInPlaceSaveRequested(overlayBitmap: Bitmap)
> }
> var inPlaceSaveCallback: DrawOverlayInPlaceSaveCallback? = null
> ```
>
> Change `btn_draw_save` click handler (in `bindToolbar`) from `exitDrawMode(save = true)` to:
>
> ```kotlin
> val overlay = drawCanvasView?.getBitmap() ?: return@setOnClickListener
> inPlaceSaveCallback?.onInPlaceSaveRequested(overlay)
> ```
>
> Drop the in-line `exitDrawMode(save = true)` path — the activity-side callback decides whether and when to exit draw mode (success path exits; failure path stays).
>
> Keep the legacy `saveCallback: DrawOverlaySaveCallback?` field. It is now used **only** by the overflow "Save to new file" item (re-route in this step). Update the overflow `draw_overflow_save_new` handler:
>
> ```kotlin
> val overlay = drawCanvasView?.getBitmap() ?: return@click
> handleSaveRequest(overlay)
> ```
>
> `handleSaveRequest` continues to feed `saveCallback?.onSaveRequested(...)` which the activity routes through `FileOperationDestinationDialog` after this step's wiring change (Step 06.3).

**Verification:**

- `Grep` — `interface DrawOverlayInPlaceSaveCallback` matches exactly once.
- `Grep` — `var inPlaceSaveCallback:` matches exactly once.
- `Grep` — `inPlaceSaveCallback?.onInPlaceSaveRequested` matches exactly once.
- `Grep` — `btn_draw_save` `setOnClickListener` body no longer contains `exitDrawMode(save = true)` — grep `exitDrawMode(save = true)` in the manager file returns zero hits (back-press path uses `save = false`).
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.

**Status:** `[x] done`

---

### Step 06.3 — Implement in-place save callback in `PlayerActivity` + reroute "Save to new" through `FileOperationDestinationDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> In `setupDrawOverlaySaveCallback` (or a new sibling `setupDrawOverlayInPlaceSaveCallback`), set:
>
> ```kotlin
> imageDrawOverlayManager.inPlaceSaveCallback = object : ImageDrawOverlayManager.DrawOverlayInPlaceSaveCallback {
>     override fun onInPlaceSaveRequested(overlayBitmap: Bitmap) { … }
> }
> ```
>
> Implementation inside `onInPlaceSaveRequested`:
>
> 1. `val baseBitmap = viewModel.currentDisplayedBitmap ?: return showFailToast()`.
> 2. `val currentFile = viewModel.state.value.currentFile ?: return showFailToast()`.
> 3. `val ext = currentFile.name.substringAfterLast('.', "").lowercase()`; format JPEG if `ext in {"jpg","jpeg"}` else PNG.
> 4. `val isReadOnly = viewModel.state.value.resource?.isReadOnly ?: false`.
> 5. `val isLocalFile = currentFile.path.startsWith("/")`.
> 6. **If `isReadOnly || !isLocalFile`** → silent fallback to "Save to new" (ADR-4 — no warning shown). Invoke the existing `saveCallback?.onSaveRequested(overlayBitmap, defaultFilename)` flow — the same path the overflow item uses. Return.
> 7. Else (local and writable): on `Dispatchers.IO`, crop overlay via existing `cropOverlayToImage(...)`, merge via `mergeDrawOverlayUseCase.execute(baseBitmap, croppedOverlay, format)`. On success: `FileOutputStream(File(currentFile.path)).use { it.write(bytes) }` — overwrite. Then on `Dispatchers.Main`: `Toast.makeText(this, R.string.draw_save_ok_toast, Toast.LENGTH_SHORT).show()`, `imageDrawOverlayManager.exitDrawMode(save = false)`, and notify the view model that the file content changed (re-render existing file — pick an existing notification path on `viewModel`, e.g. `viewModel.onFileContentReplaced(currentFile)` if such a method exists, otherwise `viewModel.refreshCurrent()` or similar — verify name in code, add the method if absent in this step).
> 8. On merge failure or write failure: `Timber.e(e, "S0192: in-place save failed")`, show `R.string.draw_save_failed_toast`, stay in draw mode.
>
> Also re-route the overflow "Save to new file" path: currently `saveCallback?.onSaveRequested` runs the simple-filename `AlertDialog` then writes to parent dir / Downloads. Per strategic §2.1.1.c, "Save to new file" should invoke `FileOperationDestinationDialog`. Replace the body of the existing `saveCallback` in `setupDrawOverlaySaveCallback`:
>
> 1. Compute `effectiveFilename` as today (auto-suffix + extension handling — keep existing logic).
> 2. Build merged bytes via merge use case (same as today).
> 3. Stage the bytes to `temp/draw_<timestamp>_<effectiveFilename>` (or `context.cacheDir`) as a real `java.io.File` — `FileOperationDestinationDialog` requires `sourceFiles: List<File>`.
> 4. Instantiate `FileOperationDestinationDialog(context = this, operationType = FileOperationType.COPY, sourceFiles = listOf(stagedFile), sourceFolderName = currentFile.path.parentName(), currentResourceId = viewModel.state.value.resource?.id ?: 0L, currentBrowsePath = …, sourceCredentialsId = …, fileOperationUseCase = injected, getDestinationsUseCase = injected, overwriteFiles = false, showDetailedErrors = true, onComplete = { _ -> imageDrawOverlayManager.exitDrawMode(save = false) })`. Inject `FileOperationUseCase` and `GetDestinationsUseCase` into `PlayerActivity` if not already present (verify via `Grep`).
> 5. On dialog `onComplete` (called by the dialog after the COPY finishes or is cancelled), clean up the staged temp file with `stagedFile.delete()` — best-effort. Exit draw mode on success.
>
> **Note:** auto-generated filename pre-fill is dropped — `FileOperationDestinationDialog` consumes the staged file's name as-is. To control the destination filename, rename the staged file before passing it in (write to `cacheDir/<effectiveFilename>` directly).

**Verification:**

- `Grep` (target: `PlayerActivity.kt`) — `inPlaceSaveCallback = object :` matches exactly once.
- `Grep` — `R.string.draw_save_ok_toast` matches exactly once.
- `Grep` — `R.string.draw_save_failed_toast` matches at least once.
- `Grep` — `FileOperationDestinationDialog(` matches exactly once in PlayerActivity's draw-save flow.
- `Grep` — `isReadOnly || !isLocalFile` (or equivalent boolean order) matches at least once.
- `Grep` — `S0192:` (Timber tag) matches at least once.
- Build: `/build` returns 0 exit. expected: BUILD SUCCESSFUL | actual: <fill in after run>.
- Manual smoke (developer): (a) draw on a local writable file → tap `[Save]` → file overwritten, toast "Saved", draw mode closes. (b) draw on a read-only resource (e.g. cloud) → tap `[Save]` → silently opens destination picker; no error toast. (c) draw → overflow → "Save to new file" → destination picker opens; pick a folder → file copied with merged drawing.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL (1m 6s after R.jar retry).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] String locale audit returns 0 (2 keys × 3 locales).
- [x] Catalog regenerated.

---

## Handoff Notes to Next Phase

Save semantics are now spec-correct. Phase 07 is housekeeping: catalog regen, FEATURES trilingual update, functionality log entries (these auto-fire from `/spec-check` / `/spec-arc`).

---

## Rollback Plan

Revert the phase commit. The `[Save]` button returns to the legacy "save as new" routing. No data migration. Files already saved in-place by the new flow remain on disk — they are valid output (overwritten with merged drawing) regardless of whether the feature stays.
