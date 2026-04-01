# Implementation Plan: Unarchive on Click (ZIP)

Date: 2026-04-01

## Phase 1 — Domain
- [x] Create ExtractArchiveUseCase with Flow<ExtractProgress>
- [x] Implement ZipInputStream extraction, zip bomb + traversal protection
- [x] Support SAF/content:// output via ContentResolver/DocumentFile
- [x] Provide cancel support callback

## Phase 2 — ViewModel
- [x] Extend BrowseState with ExtractionState
- [x] Add BrowseEvent: ShowExtractConfirmDialog / ExtractionProgress / ExtractionSuccess / ExtractionFailed
- [x] Implement prepareExtraction(), extractArchive(), cancelExtraction()
- [x] Wire ExtractArchiveUseCase collection to events/state

## Phase 3 — UI
- [x] Add UnarchiveConfirmDialog (MaterialAlertDialogBuilder)
- [x] Update BrowseActivity.showBinaryFileMenu() to intercept BINARY_ARCHIVE
- [x] Handle new events in observeEvents() (progress + success + error)
- [x] Use progress dialog with percent display

## Phase 4 — Strings
- [x] Add new strings in values/values-ru/values-uk
- [x] Reuse existing action_open if present

## Phase 5 — Tests & Hygiene
- [ ] Unit tests for ExtractArchiveUseCase
- [ ] Manual smoke: local ZIP + SAF (SD card)
- [ ] Run build + lint + unit tests (if requested)
- [ ] Add dev log entries via scripts/add_to_dev_log.ps1 per file change
- [x] Update docs/FEATURES*.md (EN/RU/UK) for new user-facing feature
