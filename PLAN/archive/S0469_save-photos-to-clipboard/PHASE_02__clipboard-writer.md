# Phase 02 - clipboard-writer

**Goal:** Extend the reusable `ImageClipboardWriter` (S0468) with a file-based copy method so a captured photo file can be placed on the clipboard preserving its original format/quality (no decode->PNG re-encode).

**Depends on:** -

---

## Steps

- [ ] **02.1 - Add `copyImageFile(source: File): Boolean`.**
  - In `app_v2/.../core/clipboard/ImageClipboardWriter.kt`: add a `suspend fun copyImageFile(source: File): Boolean` running on `Dispatchers.IO`.
  - Copy `source` bytes verbatim into a dedicated app-cache file (e.g. `clipboard/capture_clip.<ext>` preserving the source extension) - do NOT decode/recompress, so quality matches the saved photo (strategic goal 3).
  - Expose it via FileProvider (`"${context.packageName}.fileprovider"`) and `clipboard.setPrimaryClip(ClipData.newUri(contentResolver, label, uri))` - the resolver derives the MIME (`image/jpeg`) from the file, so an `image/*`-accepting receiver pastes the picture.
  - Reuse the existing failure handling: log via `Timber.w` and return `false` on any error; never throw.
  - Keep `copyBitmap` unchanged (S0468 still uses it).
  - **Verification:** `copyImageFile` present; `.\a.ps1 fk` PASS; no decode/`compress` call on the file path.

---

## Phase Done Criteria

- A single reusable writer offers both a bitmap path (S0468) and a file path (S0469); no parallel clipboard implementation introduced (strategic ADR-1).
