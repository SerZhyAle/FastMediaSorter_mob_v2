# Research 01 - Putting a screenshot image on the system clipboard

**Strategic item:** §6.1
**Status:** Resolved

## Question

How to place a captured screenshot bitmap on the Android system clipboard so it can be pasted as an image into third-party apps (messengers, notes, input fields).

## Finding

Images go on the clipboard by URI, not as raw bytes:

- Build the clip with `ClipData.newUri(contentResolver, label, contentUri)` and set it via `ClipboardManager.setPrimaryClip(clip)`.
- `contentUri` must be served by a `ContentProvider`. The app already ships a `FileProvider` under authority `"${packageName}.fileprovider"` (used by `SaveScreenshotUseCase`, `SystemShareInvoker`). Reuse it.
- The `ClipData` carries a `ClipDescription` MIME of `image/png` (derived automatically from the provider's `getType`, or set explicitly). Paste targets filter by MIME, so an image MIME is required for image paste.
- The system clipboard service grants the paste recipient temporary read access to URIs held in the primary clip - no manual `grantUriPermission` to an unknown package is needed. This mirrors the existing share path, where `SystemShareInvoker` adds `FLAG_GRANT_READ_URI_PERMISSION` for `ACTION_SEND`; for the clipboard the grant is implicit in `setPrimaryClip`.

## Consequence for the plan

- A dedicated `ImageClipboardWriter` role writes a PNG into app cache, resolves a FileProvider URI for it, and calls `setPrimaryClip(ClipData.newUri(...))` with MIME `image/png`.
- `file_provider_paths.xml` already maps `<cache-path name="cache" path="." />`, so any `cacheDir` subfolder is FileProvider-addressable - no new path entry required.

## Sources

- Established Android clipboard URI mechanism (developer.android.com "Copy and paste" - copying a content URI).
- In-repo precedent: `core/share/SystemShareInvoker.kt` (ClipData + read-grant for image share), `domain/usecase/SaveScreenshotUseCase.kt` (FileProvider authority + cache temp PNG).
