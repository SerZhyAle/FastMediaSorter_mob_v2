# Development Changelog

Auto-generated log of all code modifications.
Format: | datetime | file | target | description |

---

| DateTime | File | Target | Description |
|----------|------|--------|-------------|
| 2026-03-02 00:59:40 | `scripts/add_to_dev_log.ps1` | `add_to_dev_log.ps1` | Created dev changelog logging script for mandatory change tracking |
| 2026-03-02 01:03:07 | `AGENTS.md` | `AGENTS.md` | Added section 6 DEV CHANGELOG (MANDATORY) with script usage rule |
| 2026-03-02 01:03:12 | `.agent/CUSTOM_RULES.md` | `CUSTOM_RULES` | Added Dev Changelog Rule section with script usage and examples |
| 2026-03-02 01:03:17 | `dev/universal_copilot_instructions.md` | `AI_AGENT_DIRECTIVES` | Added mandatory DEV_CHANGELOG directive to agent-specific section |
| 2026-03-02 01:03:22 | `.github/copilot-instructions.md` | `strict_constraints` | Added DEV_CHANGELOG constraint to copilot instructions |
| 2026-03-02 01:03:27 | `dev/WORK_PLAN_ACTIONABLE_RU.md` | `P2-4` | Marked P2-4 (A1-T13..T15) as completed — 31 tests, 4 classes |
| 2026-03-02 01:12:20 | `app_v2/src/test/.../CloudFileOperationHandlerTest.kt` | `CloudFileOperationHandlerTest` | Fixed MockK exception: stubbed cloudPathParser.isCloudPath + relaxed context mock |
| 2026-03-02 01:12:20 | `dev/TECH_REQUIREMENTS.md` | `TECH_REQUIREMENTS` | Created full tech stack document: platform, 60+ libraries, constraints, min/recommended requirements |
| 2026-03-02 01:12:20 | `docs/DOCS_MAP.md` | `DOCS_MAP` | Added TECH_REQUIREMENTS.md reference to Technical Specifications section |
| 2026-03-02 01:12:20 | `dev/PROJECT_OPERATIONS_INDEX.md` | `PROJECT_OPERATIONS_INDEX` | Added TECH_REQUIREMENTS.md to research routing |
| 2026-03-02 01:12:20 | `AGENTS.md` | `AGENTS` | Added TECH_REQUIREMENTS.md to research order and tech requirements directive |
| 2026-03-02 01:12:20 | `.github/copilot-instructions.md` | `copilot-instructions` | Added TECH_REQUIREMENTS.md to routing rules and research shortcuts |
| 2026-03-02 01:12:20 | `dev/universal_copilot_instructions.md` | `universal_copilot_instructions` | Added TECH_REQUIREMENTS.md to fast routing section |
| 2026-03-02 01:41:00 | `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportSettingsUseCase.kt` | `ImportSettingsUseCase` | Fixed credential import overwriting existing passwords with empty string - now preserves DB password when import XML has no password (OnConflictStrategy.REPLACE was destroying working FTP/SFTP/SMB credentials) |
| 2026-03-02 01:55:50 | `scripts/builders/build-aab-release.ps1` | `build-aab-release.ps1` | Fixed: ZIP always contained previous build - now deletes old ZIP before recreating + uses 7z -w flag to store only filename (not full path) in archive |
| 2026-03-02 02:08:42 | `scripts/builders/build-aab-release.ps1` | `build-aab-release.ps1` | Fixed empty ZIP: replaced incorrect 7z -w flag with Push-Location/Pop-Location so 7z archives file by relative name |
| 2026-03-02 02:21:25 | `scripts/builders/build-aab-release.ps1` | `build-aab-release.ps1` | Added assembleStandardRelease build step - ZIP now contains both AAB and APK release files |
| 2026-03-02 03:24:28 | `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/MediaStoreRepositoryImpl.kt` | `MediaStoreRepositoryImpl.getFoldersWithMedia` | Two-segment resource name: use parent/folder when parent < 15 chars to avoid collisions (e.g. Pictures/Telegram vs Movies/Telegram) |
| 2026-03-02 03:24:36 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | `AddResourceViewModel.suggestLocalResourceName` | Added helper: two-segment name suggestion for manually added SAF folder URIs |
| 2026-03-02 03:24:36 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/ResourceToAddAdapter.kt` | `ResourceToAddAdapter.ViewHolder.bind` | Fix cursor-jump bug: skip etName.setText when field has focus during inline edit |
| 2026-03-02 03:40:01 | `app_v2/src/main/res/raw/msal_config.json` | `msal_config` | Fix MSAL redirect URI hash to match release keystore SHA1 (FYsxzaNPAAPFK3rigkV29z+r0es=) |
| 2026-03-02 03:40:01 | `app_v2/src/main/AndroidManifest.xml` | `BrowserTabActivity` | Update msauth intent-filter path to release keystore SHA1 hash |
| 2026-03-02 03:40:01 | `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/InteractiveCloudAuthenticator.kt` | `InteractiveCloudAuthenticator` | Add consumeImmediateResult() default method for synchronous MSAL failure detection |
| 2026-03-02 03:40:01 | `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveAuthPlugin.kt` | `OneDriveAuthPlugin` | Override consumeImmediateResult() to drain deferred when MSAL fails synchronously (no UI shown) |
| 2026-03-02 03:40:01 | `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt` | `UnifiedCloudAuthManager` | Check consumeImmediateResult() after startInteractiveSignIn to surface silent MSAL errors |
| 2026-03-02 03:40:01 | `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt` | `LoggingHelper` | Plant FileLoggingTree in release (minPriority=WARN) to enable log export in release builds |
| 2026-03-02 03:40:01 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | `GeneralSettingsFragment` | Long-press version info triggers shareLogs() in release instead of returning false |
| 2026-03-02 23:53:32 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | `ResourceEditorFragment` | Fix: guard-pattern for all boolean checkboxes in renderFormData to prevent spurious onFieldChanged from programmatic setChecked; remove redundant btnSave.isEnabled from renderLoadingStates (canSave already covers it) |
| 2026-03-02 23:58:38 | `app_v2/src/main/res/values-night/colors.xml` | `player_filename_overlay_text` | Fix: player filename overlay text color was black on black background in night mode (audio file, no cover art); changed to white |
| 2026-03-03 00:26:12 | `app_v2/src/.../db/FileMetadataCacheEntity.kt` | `FileMetadataCacheEntity` | Added artist, album, title nullable columns for audio metadata persistence |
| 2026-03-03 00:26:17 | `app_v2/src/.../db/AppDatabase.kt` | `AppDatabase` | Added MIGRATION_17_18: ALTER TABLE file_metadata_cache ADD artist/album/title columns; bumped version to 18 |
| 2026-03-03 00:26:21 | `app_v2/src/.../di/DatabaseModule.kt` | `DatabaseModule` | Registered MIGRATION_17_18 in Room builder |
| 2026-03-03 00:26:26 | `app_v2/src/.../util/CachedMediaMetadataExtractor.kt` | `CachedMediaMetadataExtractor` | Fix: cache hit now restores artist/album/title; mapToEntity now persists artist/album/title |
| 2026-03-03 00:26:31 | `app_v2/src/.../player/ImageLoadingManager.kt` | `ImageLoadingManager` | showAudioFileInfo: instant display of embedded artist/title/album from MediaFile instead of waiting for online search |
| 2026-03-03 00:26:36 | `app_v2/src/.../player/PlayerActivity.kt` | `PlayerActivity` | onAudioMetadataLoaded: do not overwrite embedded metadata with empty online results |
| 2026-03-03 00:43:26 | `app_v2/src/.../ui/browse/BrowseViewModel.kt` | `BrowseViewModel` | Background audio metadata enrichment: inject CachedMediaMetadataExtractor, add enrichAudioMetadataInBackground() method triggered after DB/RAM cache hit and fresh scan |
| 2026-03-03 00:43:31 | `app_v2/src/.../ui/browse/MediaFileAdapter.kt` | `MediaFileAdapter` | PAYLOAD_AUDIO_METADATA partial rebind: DiffUtil getChangePayload detects audio metadata changes, updateAudioMetadataText() updates text labels without thumbnail reload |
| 2026-03-03 02:01:46 | `app_v2/src/main/res/layout/activity_add_resource.xml` | `activity_add_resource` | Added cbSmbRememberFileList, btnSmbHelpRememberFileList, cbSftpRememberFileList, btnSftpHelpRememberFileList to add-resource form |
| 2026-03-03 02:01:46 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | `AddResourceViewModel` | Added rememberFileList param to addSmbResourceManually, addSftpFtpResource, addSftpResourceWithKey; passed to MediaResource construction |
| 2026-03-03 02:01:46 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | `AddResourceActivity` | Fixed form parity with ResourceEditorFragment: added rememberFileList wiring, full 6-profile preset dialog, showRememberFileListHelpDialog() |
| 2026-03-03 02:01:46 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt` | `AddResourceHelper` | Added rememberFileList pre-fill in SMB, SFTP-SSH, and FTP copy-mode cases |
| 2026-03-03 02:03:22 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt` | `NetworkErrorClassifier` | Added STATUS_BAD_NETWORK_NAME to isSmbNotFound() - maps to NetworkFileNotFoundException instead of unclassified ConnectionLost |
| 2026-03-03 02:05:00 | `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt` | `SmbClient.performTestConnection` | Fix: use folderExists()\|\|fileExists() for subpath check - fileExists() returns false for directories in SMBJ |
| 2026-03-03 02:10:57 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | `renderConnectionResult` | Show diagnosticMessage (live stats) in SUCCESS result instead of generic string |
| 2026-03-03 02:10:57 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt` | `onTestConnection` | Refresh state.statistics from DB after successful test in EDIT mode; add ResourceConnectionStatus import |
| 2026-03-03 02:16:54 | `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResourceEditorUseCase.kt` | `updateCredentialInPlace` | Fix: shareName not updated in NetworkCredentialsEntity on EDIT - caused stale share name in SMB connection |
| 2026-03-03 02:20:18 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt` | `onTestConnection` | Fix: Unresolved reference 'mode'/'resourceId' - use currentForm.mode/currentForm.id instead of initialize() params |
| 2026-03-03 02:23:43 | `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | `testSmbConnection` | Fix: derive shareName from resource.path (authoritative) instead of stale credentials value - self-heals existing resources without DB migration |
| 2026-03-03 02:34:33 | `app_v2/src/main/res/layout-land/activity_player_unified.xml` | `audioCoverArtView` | Fix: audio controls not visible in landscape - cover art view expanded to full height blocking player controls |
