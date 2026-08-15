# S0833 - Settings Media: remove duplicate document icon from text/PDF toggles

**Ticket:** S0833
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

В Settings -> Media, группа «Просмотр текста, PDF, EPUB и Office» у четырёх внутренних тогглеров стоит та же иконка «документ» (`ic_book`), что и у самого сворачиваемого заголовка группы - визуальное дублирование. Убрать `ic_book` у четырёх тогглеров, оставив её только на заголовке группы. Portrait + landscape. Поведение, подписи, порядок, сворачивание - без изменений.

## 1. Confirmed scope (research 2026-07-01)

Group header `headerDocuments` (`fragment_settings_media_container.xml`) carries `app:csh_icon="@drawable/ic_book"` and hosts a runtime-inflated body (`containerDocuments`) filled from `fragment_settings_documents.xml` (+ `layout-land/`). Four support toggles there duplicate the same icon via `app:str_icon="@drawable/ic_book"`:

- `rowSupportText` (`support_text_description`)
- `rowSupportPdf` (`support_pdf_description`)
- `rowSupportEpub` (`support_epub_description`)
- `rowSupportOfficeDocuments` (`support_office_documents_description`)

The two secondary inline toggles (`rowShowTextLineNumbers`, `rowShowPdfThumbnails`) already carry no icon. So exactly the four the owner meant (Open point 1 resolved). Both orientations define these rows independently -> both XML variants edited (Open point 2 / Rule 11).

## 2. Phase 1 - Drop the duplicated icon (portrait + landscape)

In BOTH `layout/fragment_settings_documents.xml` and `layout-land/fragment_settings_documents.xml`, remove the `app:str_icon="@drawable/ic_book"` line from the four support toggles. Keep `str_title` / `str_subtitle` and the header's `csh_icon`.

**Verification:** `.\a.ps1 fr` passes; 0 `str_icon="@drawable/ic_book"` remain in the documents fragments; header `csh_icon="@drawable/ic_book"` retained; `ic_book` still referenced (header) - not orphaned.

## 3. Open points

Resolved (see §1).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0834 / S0832 (sibling Settings -> Media row/icon tweaks).

## Related

- S0834, S0832 - sibling Settings -> Media quick wins.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_documents.xml` + landscape `layout-land/fragment_settings_documents.xml`, both edited (Rule 11): removed `app:str_icon="@drawable/ic_book"` from `rowSupportText`, `rowSupportPdf`, `rowSupportEpub`, `rowSupportOfficeDocuments` (4 rows x 2 orientations = 8 removals).
- Group header `headerDocuments` keeps `csh_icon="@drawable/ic_book"` (verified present); the two secondary toggles were already icon-less.
- No behavior / label / order / collapse change; `str_icon` lines only. `ic_book` stays referenced by the header - not orphaned.
- `a.ps1 fr` (mergeStandardDebugResources + processStandardDebugResources executed) -> BUILD SUCCESSFUL; grep confirms 0 residual `str_icon="@drawable/ic_book"` in both fragments.
- No settings-manifest / Rule 22 regen: decorative per-row icon removal, no settings metadata (title/behavior/position) affected.
- No ALL_FEATURES record: cosmetic de-duplication of an existing settings group's icons, not a new capability.
