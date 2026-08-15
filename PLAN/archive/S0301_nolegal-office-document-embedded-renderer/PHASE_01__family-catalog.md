# Phase 01 - Family Catalog

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Introduce a flavor-safe Office family catalog so noLegal can recognize the full Office-family without widening the verified standard surface from S0299.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] The strategic owner decisions in §0 and §3 are unchanged.
- [ ] The team agrees to preserve S0299 standard behavior while extending only the noLegal Office family set.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamily.kt` | New | ≤ 140 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 120 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 120 |
| `app_v2/src/vrOnly/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 120 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 160 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 120 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt` | Modified | ≤ 420 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Define the Office family model and flavor catalogs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamily.kt`, `app_v2/src/standard/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`, `app_v2/src/legacy/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`, `app_v2/src/vrOnly/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`, `app_v2/src/photos/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`, `app_v2/src/lite/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a shared `OfficeDocumentFamily` model in `src/main`, then provide one `OfficeDocumentFamilyCatalog` peer per flavor source set. Standard, legacy, and vr stay on the S0299 Word-family subset; noLegal advertises the full Office-family; photos and lite stay empty/unsupported.
>
> Use this exact family contract:
> - Word-family: `doc`, `docx`, `rtf`, `odt`.
> - Spreadsheet-family: `xls`, `xlsx`, `ods`.
> - Presentation-family: `ppt`, `pptx`, `odp`.
> Standard, legacy, and vr catalogs expose only Word-family entries. noLegal exposes all three families. photos and lite expose no Office entries.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamily.kt` exists.
- `Grep` - `enum class OfficeDocumentFamily` matches exactly once in `OfficeDocumentFamily.kt`.
- `Grep` - `SPREADSHEET` and `PRESENTATION` exist in `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`.
- `Grep` - no `.xlsx` or `.pptx` entries exist in `app_v2/src/standard/java/com/sza/fastmediasorter/data/common/OfficeDocumentFamilyCatalog.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-29 - Verification 4/4 PASS. Expected: OfficeDocumentFamily.kt exists = 1 | actual: 1. Expected: `enum class OfficeDocumentFamily` matches = 1 | actual: 1. Expected: noLegal catalog contains `SPREADSHEET` and `PRESENTATION` | actual: both present. Expected: standard catalog contains no `.xlsx`/`.pptx` entries | actual: 0 matches. Files: OfficeDocumentFamily.kt + 6 flavor OfficeDocumentFamilyCatalog.kt files.
- 2026-05-29 - Build repair: moved the VR Word-only catalog from `src/vr` to `src/vrOnly` because `noLegal` mounts `src/vr/java`; this preserves noLegal full-family support without duplicate class declarations or widening the VR catalog.

---

### Step 01.2 - Route Office MIME and family detection through the catalogs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace hardcoded Word-only Office extension/MIME checks with the flavor-provided catalog. Keep `standard`/`legacy`/`vr` aligned with S0299 while letting noLegal resolve spreadsheet and presentation MIME families for browse, standalone intent aliases, and default-player routing.
>
> Required MIME coverage:
> - Word: `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/rtf`, `application/x-rtf`, `text/rtf`, `application/vnd.oasis.opendocument.text`.
> - Spreadsheet: `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/vnd.oasis.opendocument.spreadsheet`.
> - Presentation: `application/vnd.ms-powerpoint`, `application/vnd.openxmlformats-officedocument.presentationml.presentation`, `application/vnd.oasis.opendocument.presentation`.

**Verification:**

- `Grep` - `OfficeDocumentFamilyCatalog` exists in `MediaTypeUtils.kt`.
- `Grep` - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` exists in either `MediaTypeUtils.kt` or `MediaStoreRepositoryImpl.kt`.
- `Grep` - `application/vnd.ms-powerpoint` exists in `DefaultPlayerHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-29 - Verification 3/3 PASS. Expected: `OfficeDocumentFamilyCatalog` exists in `MediaTypeUtils.kt` | actual: 3 matches. Expected: spreadsheet MIME exists in `MediaTypeUtils.kt` or `MediaStoreRepositoryImpl.kt` | actual: 2 matches in `MediaTypeUtils.kt`. Expected: `application/vnd.ms-powerpoint` exists in `DefaultPlayerHelper.kt` | actual: 2 matches. Editor diagnostics: 0 errors in touched Kotlin files.

---

### Step 01.3 - Keep classification flavor-safe and TODO-free

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/common/MediaTypeUtils.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Verify that full Office-family recognition is gated only by the flavor catalogs, not by `BuildConfig.IS_NO_LEGAL_FLAVOR` branches in `src/main`. Preserve the existing `MediaType.OFFICE_DOCUMENT` bucket so filters and persistence stay stable.

**Verification:**

- `Grep` - `MediaType.OFFICE_DOCUMENT` still exists in `MediaTypeUtils.kt`.
- `Grep` - no `BuildConfig.IS_NO_LEGAL_FLAVOR` hits exist in the files touched by this phase.
- `Grep` - no `TODO(phase-01)` hits exist under `app_v2/src/main/java/com/sza/fastmediasorter/data/common`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-29 - Verification 3/3 PASS. Expected: `MediaType.OFFICE_DOCUMENT` still exists in `MediaTypeUtils.kt` | actual: 4 matches. Expected: no `BuildConfig.IS_NO_LEGAL_FLAVOR` in touched phase files | actual: 0 matches. Expected: no `TODO(phase-01)` under data/common | actual: 0 matches.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `./gradlew.bat assembleNoLegalDebug` passed on 2026-05-29 after the `vrOnly` source-set repair.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Office-family recognition is now flavor-scoped: noLegal can see spreadsheet/presentation families without silently widening S0299 market behavior.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.