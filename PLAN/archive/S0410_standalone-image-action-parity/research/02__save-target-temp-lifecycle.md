# Research 02 - Save target & temp lifecycle for non-local crop/compress (§6.2)

**Status:** Resolved
**Question:** Where do crop-to-file / compress outputs go for a non-local source, and how is the temp managed?

## Findings

`ImageCropManager` already handles a null/read-only destination:
- `resolveDestinationPath(saveTo = null, fileName)` → falls back to `Environment.DIRECTORY_DOWNLOADS` and returns a path there.
- `copyToDestination` writes the file locally and calls `MediaScannerConnection.scanFile`, so it appears in the gallery.
- `ensureLocalSource(file, resource, ..)` treats the source as local when `resource == null` or `file.path.startsWith("/")` and returns `File(localPath)` directly - no network download.
- `performCropToFile` / `performCompressedCopy` already create and delete their own `outTemp` (and `srcTemp` for network) under `cacheDir` in a `finally` block.

The standalone host already exposes `actionCurrentResource = null`, so it hits the Downloads fallback automatically once `actionCurrentFile` is a real local path.

## Resolution

No new "publish to Pictures" code is needed. The only missing piece is giving the crop manager a **local source path** for a non-local image. Phase 01's `MaterializeUriToFileUseCase` copies the content URI to a `cacheDir/standalone_edit` file; the host sets `editableImageFile` to that path; `ensureLocalSource` then treats it as local and the existing Downloads fallback saves the output. Temp source lives under `cacheDir` and is cleaned by Phase 01's `cleanup` (the crop manager's own `outTemp` cleanup is unchanged).

## Plan impact

- **Remove Phase 03 Step 03.4** (custom Pictures-publish) - redundant; `ImageCropManager` already saves to Downloads for `saveTo = null`.
- Phase 03 reduces to 3 steps: menu item, settings wiring, materialize-on-demand + regate.
- `ImageCropManager` is NOT modified (no regression risk to the in-app player).
- Save target of record for non-local standalone edits: **Downloads** (existing behaviour), scanned into the gallery.
