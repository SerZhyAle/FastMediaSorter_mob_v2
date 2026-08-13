# S0837 - Screenshot draw-save overwrites original by default

**Ticket:** S0837
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 2 - Easy
**Source:** User request 2026-07-01 (`/spec-draft`)
**Complexity:** Simple

<!-- auto-approved by /spec-all - 2026-07-02 -->

## Goal

При жесте «скриншот -> открыть в редакторе рисования» (`OPEN_IN_DRAW`) захваченный снимок сохраняется как файл (`savedUri`), а затем «Сохранить и выйти» в draw-редакторе создаёт ВТОРОЙ файл - остаётся и оригинал, и рисунок. Владельцу оригинал тут не нужен. По умолчанию отредактированная картинка должна перезаписывать исходный скриншот; отдельный файл создаётся только при явном «Сохранить как..». Область - строго screenshot -> draw flow; обычный просмотрщик и photo-edit сохраняют прежнюю семантику.

## 0. Captured request

**Captured:** 2026-07-01

**Text:**

Когда я делаю скриншот- редакцию картинки - созранить и выййти - я в итоге получаю и файл оригинала скриншота и файл рисованной укартинки. А мне тут оригинал не нужен - пусть редактированная картинка его перезаписывает, если я не задал "Сохранить как.." там внутри

**Attachments:** none.

## 1. Problem

`ScreenshotGestureAction.OPEN_IN_DRAW` route:

1. Edge gesture captures + silently saves the screenshot (`SaveScreenshotUseCase`) -> `savedUri` (a writable MediaStore content:// URI in the default PublicCollection case, or a FileProvider URI for a selected local resource).
2. `ScreenshotGestureActionDispatcher.runPostSave` opens `PhotoVideoStandaloneActivity` via `ACTION_VIEW` on `savedUri` with `EXTRA_AUTO_ACTION = AUTO_ACTION_DRAW` and only `FLAG_GRANT_READ_URI_PERMISSION`.
3. `maybeRunAutoAction` enters draw mode through `StandaloneDrawSaveHelper`.
4. `StandaloneDrawSaveHelper.save()` (S0410) ALWAYS inserts a NEW MediaStore Pictures entry - the source URI is never overwritten.

Result: two files (original screenshot + edited drawing).

## 2. Resolved decisions

Answers to the Draft §3 open points, resolved from the code, not the owner:

- **Scope = the screenshot `OPEN_IN_DRAW` gesture path only.** The overwrite signal is injected by the dispatcher exclusively for `OPEN_IN_DRAW`. `TAKE_PHOTO_EDIT` (a just-captured photo) and the generic standalone viewer (manual `menu_draw_overlay`) keep the S0410 save-as-new behavior - they never receive the extra. Matches the owner's explicit "non-screenshot images stay unchanged".
- **Only the draw save-and-exit flow duplicates.** Screenshot `CROP_AND_SHARE` shares (no persisted duplicate); `crop-to-file` is a separate explicit "save as new" the user invokes. Crop-only is out of scope and is not a duplicate source.
- **Overwrite preserves filename / path / MediaStore identity.** Writing the merged bytes back into the same source content:// URI via `openOutputStream(uri, "wt")` (write+truncate) keeps the original MediaStore entry, name and path; `wt` updates `_size` on our owned URIs automatically.

Default-vs-explicit split maps cleanly onto the existing toolbar contract:

- `btn_draw_save` / `btn_draw_save_close` -> `filename == null` -> DEFAULT save -> overwrite source.
- overflow `draw_overflow_save_new` -> filename dialog -> `filename != null` -> "Save as.." -> new file (unchanged).

## 3. Direction

- Dispatcher tags only the `OPEN_IN_DRAW` viewer intent as an overwrite-on-default-save session and grants write.
- The activity remembers the launch source URI and exposes it to the draw helper as an overwrite target, but only while the file currently open in the editor is still the launched screenshot (a folder-paged neighbour must not be overwritten).
- The draw helper, on a default save (`filename == null`) with an overwrite target present, writes the merged bytes back to the source URI instead of inserting a new file; "Save as.." always inserts a new file.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0410 (standalone draw save-as pipeline), S0679 (draw crop), S0680 (gesture crop-and-share).
- **UI text:** no new user-visible strings - reuses `draw_save_ok_toast` / `draw_overlay_save_failed`.
- **Flavor:** none - shared `src/main` behaviour gated only by the screenshot capability that already compiles the gesture path.

## 4. Phases

### Phase 01 - Dispatcher tags the OPEN_IN_DRAW session for overwrite

- In `ScreenshotGestureActionDispatcher.openInViewer`, add a `overwriteSourceOnSave: Boolean = false` parameter.
- When true: add `Intent.FLAG_GRANT_WRITE_URI_PERMISSION` alongside the existing read grant, and put `PhotoVideoStandaloneActivity.EXTRA_DRAW_OVERWRITE_SOURCE = true`.
- In `runPostSave`, pass `overwriteSourceOnSave = true` ONLY for `ScreenshotGestureAction.OPEN_IN_DRAW`; all other routes stay `false`.
- **Verification:** `.\a.ps1 fk` compiles; grep shows `EXTRA_DRAW_OVERWRITE_SOURCE` set only on the `OPEN_IN_DRAW` branch and no other `openInViewer` caller passes `true`.

### Phase 02 - Activity remembers source URI and exposes overwrite target

- Add companion `const val EXTRA_DRAW_OVERWRITE_SOURCE = "draw_overwrite_source"` to `PhotoVideoStandaloneActivity`.
- Add field `private var drawOverwriteSourceUri: Uri? = null`; set it to the resolved source `uri` in `parseIncomingIntent` when `intent.getBooleanExtra(EXTRA_DRAW_OVERWRITE_SOURCE, false)` is true.
- In `ensureDrawHelper`, pass `getOverwriteTargetUri = { drawOverwriteSourceUri?.takeIf { viewModel.state.value.mediaFile?.contentUri == it.toString() } }` so overwrite is disabled once the editor pages to a different file.
- **Verification:** `.\a.ps1 fk` compiles; the getter returns non-null only for the launched screenshot.

### Phase 03 - Draw helper overwrites source on default save

- Add constructor param `getOverwriteTargetUri: () -> Uri? = { null }` to `StandaloneDrawSaveHelper`; update the class KDoc (S0410) to note the conditional screenshot overwrite.
- In `save(overlay, filename, close)`: compute `val overwriteUri = if (filename == null) getOverwriteTargetUri() else null`.
- When `overwriteUri != null`: merge (existing crop+merge pipeline), then `activity.contentResolver.openOutputStream(overwriteUri, "wt")?.use { it.write(bytes) }` on `Dispatchers.IO`; on success show `draw_save_ok_toast`, exit draw mode, `finish()` if `close`; on failure/null-stream show `draw_overlay_save_failed` and stay in draw mode.
- When `overwriteUri == null`: keep the existing new-file MediaStore insert path verbatim.
- **Verification:** `.\a.ps1 fc` (or standard debug) compiles; a default save with an overwrite target writes back to the source URI (no second MediaStore insert); "Save as.." still inserts a new file.

### Phase 04 - Build gate + device verification tag

- Build standard debug; if any code changed after tag insertion is required for on-device test, insert a single `Timber.d("S0837: ..")` at the overwrite entry only while `BlockNeedUserTest`.
- **Verification:** standard debug build PASS.

## Related

- S0410 (standalone draw save-as pipeline)
- S0679 (draw-editor crop tool)
- S0680 (gesture crop screenshot and share)
