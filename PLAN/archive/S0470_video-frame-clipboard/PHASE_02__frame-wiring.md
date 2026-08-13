# Phase 02 - frame-wiring

**Goal:** When the flag is on, copy the saved frame `tempFile` to the system clipboard via the reusable `ImageClipboardWriter.copyImageFile(File)`, before the temp file is deleted, and confirm with a toast. Runs alongside (never replaces) the assigned save operation.

**Depends on:** 01

---

## Steps

- [ ] 1. `SaveVideoFrameManager.kt`: inject `ImageClipboardWriter` via the constructor (Hilt `@Inject` already wires the writer; pass it from `PlayerActivity` where the manager is constructed). Confirm the construction site supplies the new dependency.
  - **Verification:** constructor param present; construction site updated; no new Hilt scope/qualifier introduced.

- [ ] 2. In `saveCurrentFrame()`'s coroutine, after `tempFile` is written and before `tempFile.delete()`, if `settings.videoFrameCopyToClipboard` is true call `imageClipboardWriter.copyImageFile(tempFile)` (already off the UI thread inside `withContext(Dispatchers.IO)` internally). On success, append/raise a short toast `R.string.video_frame_copied_to_clipboard`. The clipboard step must not short-circuit the existing save/Downloads path or its toast.
  - **Verification:** clipboard call gated by flag; placed before delete; existing save flow + toast preserved; copy runs off UI thread.

---

- [ ] 3. Mirror the clipboard step in the second frame-save host `PhotoVideoStandaloneActivity.saveCurrentFrame()` (player-family glue - compiler will not catch a missed host). This host holds the live bitmap (no encoded temp file), so gate on the flag and call `imageClipboardWriter.copyBitmap(bitmap)` (lossless PNG) plus the same confirmation toast. Inject `ImageClipboardWriter` via `@Inject` (already `@AndroidEntryPoint`).
  - **Verification:** clipboard call gated by flag in both hosts; standalone host injects the writer; existing save path + toast preserved.

---

## Phase Done Criteria

- [ ] `.\a.ps1 fk` compiles.
- [ ] Manual reasoning: with flag off, behaviour is byte-identical to today (no clipboard call) in both hosts.
