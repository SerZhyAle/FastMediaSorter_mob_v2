---
ticket: S0304
status: Verified
strategic_spec: ../S0304_office-document-settings-parity.md
date: 2026-05-30
---

# S0304 Tactical Plan - Office document settings parity

## Scope

Make Office documents a first-class document media type in application settings, resource creation, resource editing, document presets and user-facing copy. Preserve existing S0299/S0301 rendering and routing behavior.

## Phase Map

1. [Phase 01 - Settings state and document filters](PHASE_01__settings-state-and-filters.md)
2. [Phase 02 - Document settings and default viewer](PHASE_02__document-settings-and-default-viewer.md)
3. [Phase 03 - Resource media-type controls](PHASE_03__resource-media-type-controls.md)
4. [Phase 04 - Strings, docs and validation](PHASE_04__strings-docs-validation.md)

## UI Ambiguity Gate

- **Document settings placement:** Office documents appear at the same hierarchy as Text, PDF and EPUB. Use the existing settings-row pattern.
- **All files behavior:** Office is checked and disabled when All Files is enabled, matching Text/PDF/EPUB.
- **Flavor visibility:** Use the existing flavor-safe Office family catalog or document capability surface. Do not branch on flavor names in common code.
- **Default document viewer:** Keep one entry point, then offer a bounded PDF/Office type choice before Android default-app setup.
- **Resource controls:** Add Office beside existing document media-type choices; compact surfaces may use `DOC`.
- **Orientation:** Affected default layouts have no `layout-land` counterparts, so verify default layout behavior in portrait and landscape.

## Last Audit

- **Status:** Verified on 2026-05-30.
- **Implementation:** Office documents now have explicit settings persistence, document settings UI, default-viewer type choice, add-resource SMB/SFTP controls, resource-editor controls, compact add-resource badge, EN/RU/UK strings, noLegal flavor copy and feature docs.
- **Static checks:** no `layout-land` counterpart exists for `fragment_settings_documents.xml`, `activity_add_resource.xml`, `fragment_resource_editor.xml` or `item_resource_to_add.xml`; default layouts were updated directly.
- **Diff hygiene:** `git diff --check` passed for S0304-touched files; a global check is still blocked by unrelated trailing whitespace in `BrowseUtilityManager.kt:82`.
- **String parity:** `check_strings_localized.ps1` passed for `support_office_documents`, `setting_support_office_documents`, `media_type_office`, `office_doc_d`, `item_resource_to_add_btnTypeOffice`, `settings_default_document_type` and noLegal `setting_support_office_documents`.
- **Catalog:** `scripts/catalog_sync.ps1 -Module app_v2` passed.
- **Tests:** S0304-targeted `AppSettingsTest`, `ScanLocalFoldersUseCaseTest` and two affected `ProvisionDefaultResourcesUseCaseTest` cases passed.
- **Build:** `build-debug.PS1` passed for `standardDebug`; `scripts/builders/build-nolegal-debug.ps1` passed for `noLegalDebug`.
- **Known residual risk:** full `ProvisionDefaultResourcesUseCaseTest` still has an unrelated pre-existing failure asserting `virtual://all_audio` should not be writable.
