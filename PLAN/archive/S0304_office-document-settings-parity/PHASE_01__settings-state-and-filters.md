# Phase 01 - Settings state and document filters

## Goal

Persist an explicit Office document setting and use it when global media-type filters, default document resources and virtual document resources are built.

## Tasks

- Add `supportOfficeDocuments` to the settings model with a default enabled state.
- Persist the new setting in the settings repository.
- Include the setting in settings reset, backup, export and import paths.
- Replace implicit Office inclusion from Text/PDF/EPUB with explicit Office inclusion.
- Include Office documents in default and virtual document resources only when the setting is enabled.

## Verification

- Static check confirms `supportOfficeDocuments` is read and written beside `supportText`, `supportPdf` and `supportEpub`.
- Static check confirms `MediaType.OFFICE_DOCUMENT` is no longer added as a side effect of Text/PDF/EPUB.
