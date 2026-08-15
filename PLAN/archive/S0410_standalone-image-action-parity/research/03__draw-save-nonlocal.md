# Research 03 - Draw overlay save for non-local images (§6.3)

**Status:** Resolved
**Question:** How does draw save work, and how should it save for a non-local source?

## Findings

`ImageDrawOverlayManager` is binding-free already (takes `activity`, `imageContainer: ViewGroup`, `screenRotationManager`, `hasAccelerometer`, `keepExportHelper`). It does NOT save by itself - it raises callbacks the host implements:
- `DrawOverlaySaveCallback.onSaveRequested(overlayBitmap, filename)`
- `DrawOverlayActionCallback.onSaveRequested / onSaveAndCloseRequested / onSaveAndShareRequested(overlayBitmap)`
- `DrawOverlayInPlaceSaveCallback` (in-place overwrite)
- `baseBitmapProvider: () -> Bitmap?` - the base image to merge under the overlay.

In `PlayerActivity`, `PlayerManagerInitializer` wires `baseBitmapProvider = { viewModel.currentDisplayedBitmap }` and the save callbacks via `setupDrawOverlaySaveCallback()` / `setupDrawOverlayActionCallbacks()` / `setupDrawOverlayInPlaceSaveCallback()`, backed by `PlayerDrawingSaveHelper` and `MergeDrawOverlayUseCase`. The merge (base + overlay → bitmap/bytes) lives in `MergeDrawOverlayUseCase`; the destination logic lives in the host callbacks / `PlayerDrawingSaveHelper`.

## Resolution

Standalone implements a minimal subset of the draw callbacks:
- `baseBitmapProvider = { binding.photoView.drawable?.toBitmap() }`.
- Save = merge base + overlay (reuse `MergeDrawOverlayUseCase`) → write to **Downloads** (mirror the crop manager's `saveTo = null` Downloads fallback) → `MediaScannerConnection.scanFile` → toast. No in-place overwrite for non-local (URI not writable); save-as only. For a local writable source, in-place overwrite stays available.
- In-place overwrite, in-place-save, and Keep export are optional in standalone; the minimum for parity is save-as to Downloads.

Single save mechanism: the host's Downloads-save helper is shared by crop-to-file/compress callbacks and the draw save callback.

## Plan impact

- Phase 04 must wire `baseBitmapProvider`, instantiate `ImageDrawOverlayManager` into `overlayMountTarget`, add the menu item, and implement the save callback writing to Downloads via `MergeDrawOverlayUseCase`.
- Draw is a real port (callbacks + merge + save), heavier than crop/compress which reuse `ImageCropManager` unchanged.
- Phase 04 depends on Phase 03 only for sharing the host Downloads-save helper.
