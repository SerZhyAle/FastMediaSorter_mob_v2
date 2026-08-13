# Phase 03 - Export v2 emission

**Goal:** `ExportCompanionConfigUseCase` writes the resource's real params into a schemaVersion-2 config.

## Steps

1. [ ] Set `schemaVersion = 2` (still via `CompanionConfigParser.SUPPORTED_SCHEMA_VERSION`, now 2).
2. [ ] Build the single `CompanionRootDto` with the resource's params:
   - `profile = if (resource.profile == ResourceProfile.NONE) null else profileToToken(resource.profile)`.
   - `mediaTypes = mediaTypesToTokens(resource.supportedMediaTypes)` when NOT fully derivable from the profile; simplest correct rule: always emit `mediaTypes` when `resource.profile == NONE && !resource.allFiles` (so the receiver reconstructs the exact set), else omit and rely on profile/allFiles. Keep it deterministic; a redundant `mediaTypes` alongside a profile is harmless (import precedence handles it).
   - `allFiles = resource.allFiles.takeIf { it }` (emit only when true; null otherwise to stay compact).
   - `scanSubdirectories`, `showSubfoldersAsItems`, `showHiddenFiles`: emit the actual boolean (they are meaningful either way; emit as-is).
   - `isDestination = resource.isDestination.takeIf { it }`; `destinationColor = if (resource.isDestination) resource.destinationColor else null`.
   - `comment = resource.comment` (nullable pass-through).
   - `accessPin = resource.accessPin` (nullable pass-through).
   - `slideshowInterval = resource.slideshowInterval`.
3. [DEFERRED - owner decision] Password/pin caution in the export dialog (`MainSftpShareManager` / `dialog_share_sftp_access.xml`). The pin only leaks when a resource carries one AND is shared; a faithful fix needs a new warning TextView in portrait + landscape, a trilingual string, and an owner call on whether to add an "exclude PIN" checkbox mirroring the password one (strategic §6 #2). Deferred to owner; covered in the companion deliverable (Phase 05) and the export-time security note. Data-layer pin round-trip already works.
4. [ ] Round-trip stays intact: a resource exported then re-imported reproduces the same params (covered by serializer test in Phase 04).

## Notes

- Do not emit empty/default noise for every field - keep the payload compact for the QR path (emit null to omit). Import fallbacks cover omitted fields.
