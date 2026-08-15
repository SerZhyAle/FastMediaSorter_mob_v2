# Research 01 - Crop source resolution and share target

Resolves strategic §6.1 (форма источника для обрезки) and §6.3 (поверхность отправки). Findings from reading the live tree on 2026-06-25.

## Question

After a gesture screenshot is saved, the new action must (a) open it with a crop frame, (b) overwrite with the cropped result, (c) open the existing "Отправить в.." menu for the cropped image. Two unknowns: in what form does the saved screenshot reach the crop engine, and which surface is the "Отправить в.." menu.

## How the saved screenshot reaches the crop engine

- The save step yields one of two URI forms depending on destination policy:
  - Selected resource (local folder) -> a FileProvider URI wrapping a real local file path.
  - Public collection -> a MediaStore `content://` URI; no direct local file path.
- The standalone image host already resolves ANY incoming image URI (including `content://`) into a writable local image copy held as a separate state value (`editableImageFile`). Its explicit purpose is to give in-place edit operations (crop, draw, rotate) a writable file to overwrite. The crop engine operates on this resolved local file via the host action seam.
- Therefore no new URI plumbing is needed: the new action reuses the same writable-local-copy seam the existing manual crop and draw flows already use. The crop overwrites that local copy in place; on the success path the engine reports the overwritten local path and the host re-decodes it.

### Nuance that creates a real risk

- For a selected-resource (local) screenshot, the host's "current file" and the writable crop copy point to the SAME local path -> overwrite and any subsequent share of "current file" both see the cropped bytes.
- For a MediaStore (`content://`) screenshot, the writable crop copy is a SEPARATE cache file. The host's "current file" still carries the original `content://`. The crop overwrites only the cache copy; the MediaStore original stays un-cropped. This matches existing manual-crop behavior for `content://` images (no write-back to MediaStore).
- Consequence for SHARE: the existing "share current file" path builds its share URI from `contentUri ?: path` of the host's current file. For a MediaStore screenshot that is the ORIGINAL `content://` (un-cropped). Sharing it after crop would deliver the UN-cropped image.

## Which surface is the "Отправить в.." menu

- The standalone host's share entry routes through the in-app curated send-to menu (receiver-applicability gated: email, social, print, etc.), NOT the raw Android system chooser. This is the app's own "Отправить в.." surface and already includes print / social / email - matching the owner's description.
- The existing gesture action that "shares to recipients" opens the standalone host and triggers exactly this send-to menu. The existing plain "share" gesture action instead uses the raw system chooser. The owner's described menu ("стандартное меню Отправить в.. .. печать, соцсети, почта") maps to the in-app send-to menu.

## Resolution for the new action

- Reuse the writable-local-copy seam for crop (CROP / overwrite-in-place mode). No new URI plumbing.
- The share-after-crop MUST target the cropped writable local copy, building the share URI from its local path (not from the host current file's `contentUri`), so MediaStore-backed screenshots also share the cropped bytes. This requires a share entry that takes an explicit file/local-path rather than the generic "share current file".
- Use the in-app send-to menu as the share surface (same surface as the "send to recipients" action), not the raw system chooser.
- Trigger share only on crop SUCCESS. The crop engine reports success and cancellation through distinct callbacks (success path does not fire the exit/cancel callback), so cancel can suppress the share cleanly.

## Implications carried into the tactical plan

- A one-shot "share after this crop" intent is set when the action opens, consumed on crop success, cleared on crop cancel.
- A share-by-explicit-local-file entry is added so the cropped copy (not the original URI) is delivered.
- ADR-3 in the strategic spec is corrected from "system share sheet" to "the app's existing in-app «Отправить в..» menu".
