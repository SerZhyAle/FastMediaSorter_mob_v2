# Phase 01 - Classification

**Strategic spec:** [`../S0299_office-document-viewing-legal-routing.md`](../S0299_office-document-viewing-legal-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-28
**Completed:** 2026-05-28

---

## Objective

Introduce Office document classification and keep document filters, persistence flags, scanners, and thumbnails coherent.

---

## Prerequisites

- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working branch is `DEBUG-v008`.
- [x] No Room schema change is required; resource media types still use the existing integer flags column.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/MediaExtensions.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceEntity.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 580 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ResourceFormData.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/FileTypeFilter.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ScanLocalFoldersUseCase.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt` | Modified | ≤ 720 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt` | Modified | ≤ 580 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MediaGroupPalette.kt` | Modified | ≤ 60 |

---

## Steps

### Step 01.1 - Add Office document media type

**Files:** `Models.kt`, `MediaExtensions.kt`, `MediaTypeUtils.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add `MediaType.OFFICE_DOCUMENT` without changing existing enum ordinals. Map `.doc`, `.docx`, `.rtf`, and `.odt` by extension and MIME. Keep Office documents passive and outside `isBinaryFile()`.

**Verification:**

- `Grep` - `OFFICE_DOCUMENT` exists in `Models.kt`.
- `Grep` - `OFFICE_DOCUMENT_EXTENSIONS` exists in `MediaTypeUtils.kt`.
- `Grep` - `application/vnd.openxmlformats-officedocument.wordprocessingml.document` exists in `MediaTypeUtils.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: `Models.kt`, `MediaExtensions.kt`, `MediaTypeUtils.kt`. Office enum, extensions, and MIME map added.

### Step 01.2 - Persist Office document support in resource media flags

**Files:** `ResourceEntity.kt`, `ResourceRepositoryImpl.kt`, `AppSettings.kt`, `ResourceFormData.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a new high bit for `OFFICE_DOCUMENT` in the existing `supportedMediaTypesFlags` integer. Include Office documents in document presets when documents are enabled, without a Room migration.

**Verification:**

- `Grep` - `0b10000000` exists in `ResourceRepositoryImpl.kt`.
- `Grep` - `OFFICE_DOCUMENT` exists in `ResourceFormData.kt`.
- `Grep` - `OFFICE_DOCUMENT` exists in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: `ResourceEntity.kt`, `ResourceRepositoryImpl.kt`, `AppSettings.kt`, `ResourceFormData.kt`. Office document flag bit and document presets added.

### Step 01.3 - Include Office documents in scanners and filters

**Files:** `FileTypeFilter.kt`, `ProvisionDefaultResourcesUseCase.kt`, `ProvisionDownloadsDestinationUseCase.kt`, `ScanLocalFoldersUseCase.kt`, `ExecuteScheduledOperationUseCase.kt`, `GetMediaFilesUseCase.kt`, `LocalMediaScanner.kt`, `MediaStoreRepositoryImpl.kt`, `FtpMediaScanner.kt`, `SftpMediaScanner.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Make document resource scans and scheduled document operations include `OFFICE_DOCUMENT`. Update size-filter branches so Office documents use the same unbounded document size behavior as PDF, EPUB, and text.

**Verification:**

- `Grep` - `OFFICE_DOCUMENT` exists in every file listed for this step.
- `Grep` - `application/msword` exists in `MediaStoreRepositoryImpl.kt`.
- `Grep` - no `TODO(phase-01)` hits under `app_v2/src/main/java`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: scanner/usecase/filter set. Office documents included in document scans, scheduled masks, MediaStore MIME selection, and unbounded document size filters.

### Step 01.4 - Render Office document list identity

**Files:** `AdapterThumbnailLoader.kt`, `PagingMediaFileAdapter.kt`, `ResourceAdapter.kt`, `FilterResourceDialog.kt`, `MediaGroupPalette.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Treat Office documents as document-colored list entries. Use extension placeholders for thumbnails and include them in document-only resource indicators.

**Verification:**

- `Grep` - `OFFICE_DOCUMENT` exists in every file listed for this step.
- `Grep` - `Office (O)` exists in `FilterResourceDialog.kt`.
- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `MediaGroupPalette.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: browse thumbnails and resource filters. Office document placeholders and document color routing added.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` exit 0 after one S0299 exhaustiveness fix pass.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every modified file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exit 0.

---

## Handoff Notes to Next Phase

Office documents are first-class document media types, but no UI route launches them until Phase 02.

---

## Rollback Plan

Revert phase edits; no Room schema migration or persisted data rewrite is introduced.
