# Phase 04 — File Information Block (Path Decomposition, MIME, Copy, lastModified)

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 7 / 7
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Restructure the "File Information" block of `FileInfoDialog` to render the path decomposed into host / port / share / directory / filename for network sources, show extension and MIME, expose a "Copy path" button that copies the human-readable path (per strategic §6.5), and split the date row into separate `created` and `lastModified` lines when the source distinguishes them. Add read-only / hidden attribute lines for sources that report them.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — yes.
- [ ] Working tree is clean or on a feature branch.
- [ ] Confirm `MediaFile` already carries `lastModified` (or equivalent) — if not, the field must be added to the domain model in this phase. Run `Grep` for `lastModified` in `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaFile.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFilePathDescriptor.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MimeTypeResolver.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoFileSectionHelper.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` | Modified | ≤ 950 |
| `app_v2/src/main/res/layout/dialog_file_info.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout-land/dialog_file_info.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 04.1 — Create `MediaFilePathDescriptor` utility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFilePathDescriptor.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create a top-level `object MediaFilePathDescriptor` with `data class Decomposition(scheme: String, host: String?, port: Int?, share: String?, directory: String?, filename: String, extension: String?, displayPath: String, canonicalPath: String)` and `fun decompose(path: String, cloudDisplayPath: String? = null): Decomposition`. Recognise schemes: `smb://`, `sftp://`, `ftp://`, `cloud://`, `content://`, and bare local paths (`/storage/..` or any path matching `^/.*`). For `smb://`, `sftp://`, `ftp://` rely on the existing `SmbPathUtils.parseSmbPath` / `SftpPathUtils.parseSftpPath` / `FtpPathUtils.parseFtpPath` to extract host, port, share, and remote path; the directory is `remotePath.substringBeforeLast('/')` and filename is `substringAfterLast('/')`. For `cloud://` populate `displayPath = cloudDisplayPath ?: path` and leave host/share/port `null`. For `content://` populate `displayPath = path` (no decomposition possible). For local paths populate `directory` and `filename` from the path. `canonicalPath` is the original `path` argument verbatim. `extension` is `filename.substringAfterLast('.', "")` lower-cased, or `null` if no extension. Wrap each protocol parse in `try` returning a degenerate `Decomposition(scheme=path.substringBefore("://"), .., displayPath=path, canonicalPath=path)` on parse failure.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFilePathDescriptor.kt` exists.
- `Grep` — `object MediaFilePathDescriptor` matches exactly once.
- `Grep` — `data class Decomposition\(` matches exactly once.
- `Grep` — `fun decompose\(` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. `object MediaFilePathDescriptor` ×1; `data class Decomposition(` ×1; `fun decompose(` ×1; `Log.d(` = 0 hits. File: `MediaFilePathDescriptor.kt` (142 LOC ≤ 220). Dev log recorded.

---

### Step 04.2 — Create `MimeTypeResolver` utility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MimeTypeResolver.kt` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> Create a top-level `object MimeTypeResolver` exposing `fun resolve(extension: String?, headBytesProvider: (() -> ByteArray?)? = null): String`. (1) If `extension` is non-empty, return `MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())` if non-null. (2) If still unknown and `headBytesProvider` is non-null, invoke it to fetch the first 32 bytes and probe a hard-coded signature table (JPEG `FFD8FF`, PNG `89504E47`, GIF `474946`, PDF `25504446`, ZIP/EPUB/DOCX `504B0304`, MP4/M4A `66747970` at offset 4, MP3 ID3 `494433` or sync `FFFB`/`FFFA`, FLAC `664C6143`, OGG `4F676753`, RIFF/WAV `52494646`, BMP `424D`); return the matched MIME or `null`. (3) Final fallback: `application/octet-stream`. Sniffing is invoked only when extension yielded nothing — never on every call.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MimeTypeResolver.kt` exists.
- `Grep` — `object MimeTypeResolver` matches exactly once.
- `Grep` — `MimeTypeMap.getSingleton\(\)` referenced.
- `Grep` — `application/octet-stream` literal present.
- `Grep` — `0xFF.*0xD8.*0xFF` (JPEG signature, with byte values) or `byteArrayOf\(0xFF.toByte\(\), 0xD8` present.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `object MimeTypeResolver` ×1; `MimeTypeMap.getSingleton()` ×1; `application/octet-stream` ×2; `0xFF.*0xD8.*0xFF` ×1; `Log.d(` = 0 hits. File: `MimeTypeResolver.kt` (46 LOC ≤ 200). Dev log recorded.

---

### Step 04.3 — Add new layout fields and copy-path button to `dialog_file_info.xml`

**Files:** `app_v2/src/main/res/layout/dialog_file_info.xml`, `app_v2/src/main/res/layout-land/dialog_file_info.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inside the File Information section of both dialog layouts, replace the single `tvFilePath` `TextView` with a vertically-stacked block of `TextView`s and a copy button: `tvFileHost` (host:port — visibility `gone` if host is null), `tvFileShare` (share/root — visibility `gone` for non-SMB), `tvFileDirectory`, `tvFileNameLine` (filename only), `tvFileExtensionMime` (extension + MIME, single row), `tvFileLastModified` (separate from `tvFileDate`, visibility `gone` if equal to created), `tvFileReadOnly` and `tvFileHidden` (each `gone` unless source returns the attribute), and a `MaterialButton` with id `btnCopyPath` styled as `OutlinedButton`, text `@string/file_info_copy_path`. Keep the original `tvFilePath` `TextView` as the canonical wrapping fallback row when decomposition is degenerate (e.g. `content://` URIs) — when the helper renders structured fields it sets `tvFilePath.visibility = View.GONE` and the structured rows visible.

**Verification:**

- `Grep` — `@+id/tvFileHost` matches exactly twice across both layout files.
- `Grep` — `@+id/btnCopyPath` matches exactly twice.
- `Grep` — `@+id/tvFileLastModified` matches exactly twice.
- `Grep` — `@+id/tvFileExtensionMime` matches exactly twice.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. `@+id/tvFileHost` ×2; `@+id/btnCopyPath` ×2; `@+id/tvFileLastModified` ×2; `@+id/tvFileExtensionMime` ×2. Dev log recorded for 2 layout files.

---

### Step 04.4 — Add trilingual strings for file-information labels and the copy button

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add to all three files: `file_info_host_label` ("Host: %1$s" / "Хост: %1$s" / "Хост: %1$s"), `file_info_share_label` ("Share: %1$s" / "Шара: %1$s" / "Шара: %1$s"), `file_info_directory_label` ("Directory: %1$s" / "Каталог: %1$s" / "Каталог: %1$s"), `file_info_filename_label` ("File: %1$s" / "Файл: %1$s" / "Файл: %1$s"), `file_info_extension_mime_label` ("Format: %1$s · %2$s" / "Формат: %1$s · %2$s" / "Формат: %1$s · %2$s"), `file_info_last_modified_label` ("Modified: %1$s" / "Изменён: %1$s" / "Змінено: %1$s"), `file_info_read_only_label` ("Read-only" / "Только чтение" / "Тільки читання"), `file_info_hidden_label` ("Hidden" / "Скрытый" / "Прихований"), `file_info_copy_path` ("Copy path" / "Копировать путь" / "Копіювати шлях"), `file_info_copy_path_done` ("Path copied" / "Путь скопирован" / "Шлях скопійовано"). All Russian strings must use `ё` where grammatically correct (e.g. `ещё`, `всё`); the strings above must use `..` style if any ellipsis is needed (none here, but rule applies).

**Verification:**

- `Grep` — `file_info_host_label` present in all three of `values/`, `values-ru/`, `values-uk/` `strings.xml`.
- `Grep` — `file_info_copy_path` present in all three.
- `Grep` — `file_info_last_modified_label` present in all three.
- `Grep` — `Изменён` present in `values-ru/strings.xml` (Ё-rule check).
- `Grep` — `\.\.\.` returns zero hits among the new keys (verify by `Grep -A 1 "file_info_"` and visual scan).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `file_info_host_label`, `file_info_copy_path`, `file_info_last_modified_label` in all 3 locale files; `Изменён` ×2 in values-ru/strings.xml; no `...` in new keys. Dev log recorded for 3 files.

---

### Step 04.5 — Create `FileInfoFileSectionHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoFileSectionHelper.kt` (New)
**Depends on:** Step 04.4

**Prompt for developer:**

> Create `FileInfoFileSectionHelper` in package `com.sza.fastmediasorter.ui.dialog.helpers`. Constructor: `Context`, `DialogFileInfoBinding`. Public method: `fun render(file: MediaFile, lastModifiedMs: Long?, isReadOnly: Boolean, isHidden: Boolean)`. Body: (1) call `MediaFilePathDescriptor.decompose(file.path, file.cloudDisplayPath)`; (2) populate `tvFileName`, `tvFileSize`, `tvFileDate` (created), `tvFileType` as before; (3) if decomposition has host — set `tvFileHost.text = getString(file_info_host_label, "$host" + (if (port != null) ":$port" else ""))` and `isVisible = true`, otherwise `isVisible = false`; same for `tvFileShare`, `tvFileDirectory`, `tvFileNameLine`; (4) call `MimeTypeResolver.resolve(decomposition.extension, headBytesProvider = null)` for the MIME — pass `null` provider in this phase (no signature sniffing for network performance reasons in info-dialog) — and set `tvFileExtensionMime` to `getString(file_info_extension_mime_label, decomposition.extension?.uppercase() ?: "—", mime)`; (5) if `lastModifiedMs != null` and `lastModifiedMs != file.createdDate` — render `tvFileLastModified` with `formatDate(lastModifiedMs)` and make visible; (6) `tvFileReadOnly` / `tvFileHidden` visible based on flags; (7) wire `btnCopyPath.setOnClickListener` to copy `decomposition.displayPath` (the human-readable form per §6.5) into the system clipboard via `ClipboardManager` and show a `Toast` with `file_info_copy_path_done`; (8) hide the original `tvFilePath` once structured fields are rendered. Wrap entire body in `try` — on exception fall back to existing single-line `tvFilePath` rendering by re-using the legacy `buildPathInfoText` logic.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoFileSectionHelper.kt` exists.
- `Grep` — `class FileInfoFileSectionHelper` matches exactly once.
- `Grep` — `fun render\(` present.
- `Grep` — `MediaFilePathDescriptor.decompose\(` present.
- `Grep` — `MimeTypeResolver.resolve\(` present.
- `Grep` — `ClipboardManager` referenced.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 7/7 PASS. File exists; `class FileInfoFileSectionHelper` ×1; `fun render(` ×1; `MediaFilePathDescriptor.decompose(` ×1; `MimeTypeResolver.resolve(` ×1; `ClipboardManager` ×2; `Log.d(` = 0 hits. File: `FileInfoFileSectionHelper.kt` (~110 LOC ≤ 250). Dev log recorded.

---

### Step 04.6 — Wire `FileInfoFileSectionHelper` into `FileInfoDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> In `FileInfoDialog.displayFileInfo()`, replace the body that sets `tvFileName` / `tvFileSize` / `tvFileDate` / `tvFileType` / `tvFilePath` with a single call to `FileInfoFileSectionHelper(context, binding).render(mediaFile, mediaFile.lastModified, isReadOnly = mediaFile.attributes?.readOnly == true, isHidden = mediaFile.attributes?.hidden == true)`. If `MediaFile` does not currently carry `lastModified` or `attributes` properties, add them as nullable fields with default `null` in `MediaFile.kt` (no migration needed — model is in-memory only). Delete the now-unused private `buildPathInfoText()` method. Re-run `Grep` for any leftover `tvFilePath.text =` to confirm removal.

**Verification:**

- `Grep` — `FileInfoFileSectionHelper\(` present in `FileInfoDialog.kt`.
- `Grep` — `fun buildPathInfoText\(` returns zero hits in `FileInfoDialog.kt`.
- `Grep` — `tvFilePath\.text =` returns zero hits in `FileInfoDialog.kt`.
- `Grep -n "Log\.d\("` in `FileInfoDialog.kt` returns zero hits.
- `Bash` — `wc -l app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileInfoDialog.kt` ≤ 950.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `FileInfoFileSectionHelper(` ×1; `fun buildPathInfoText(` = 0 hits; `tvFilePath.text =` = 0 hits; `Log.d(` = 0 hits; 706 LOC ≤ 950. `FileAttributes` and `attributes` field added to Models.kt. Dev log recorded for 2 files.

---

### Step 04.7 — Smoke-build and manual verification

**Files:** —
**Depends on:** Step 04.6

**Prompt for developer:**

> Run `/build`. Open the info-dialog on (a) a file at `sftp://host:22022/share/dir/file.flac` and confirm host:port, share, directory, filename, format (extension + MIME), and copy-path button are rendered; (b) a `cloud://` file and confirm copy-path copies the human-readable `cloudDisplayPath`; (c) a local file and confirm last-modified row is hidden when equal to created; (d) a Windows SMB file marked hidden — confirm "Скрытый" line appears.

**Verification:**

- `/build` exits with success.
- `Grep` — `TODO\(phase-04\)` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — BUILD SUCCESSFUL in 1m 30s. `TODO(phase-04)` = 0 hits.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API of `MediaFilePathDescriptor`, `MimeTypeResolver`, `FileInfoFileSectionHelper` is new → `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The File Information block now renders structured rows for network paths and offers a copy-path button. Phase 05 documents the user-visible changes in `docs/FEATURES.md`.

---

## Rollback Plan

Revert phase commit(s). The new utilities (`MediaFilePathDescriptor`, `MimeTypeResolver`) are pure helpers with no other consumers and can be deleted without ripple. `MediaFile` fields added in Step 04.6 are in-memory only — no migration to undo.
