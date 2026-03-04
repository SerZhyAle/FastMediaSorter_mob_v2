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
| 2026-03-04 18:56:17 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourcePasswordManager.kt` | `ResourcePasswordManager` | Added generic PIN validation callback method for protected resource actions |
| 2026-03-04 18:56:32 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | `MainActivity` | Added PIN gate before NavigateToAddResourceCopy for protected resources |
| 2026-03-04 20:12:04 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Created comprehensive improvement proposal document covering documentation, UI/UX, functionality, stability, and web presence |
| 2026-03-04 20:18:22 | `docs/V2_Specification.md` | `V2_Specification` | Added missing technical specification page with stable permalink for landing links |
| 2026-03-04 20:18:22 | `docs/V2_Specification_RU.md` | `V2_Specification_RU` | Added missing Russian technical specification page with stable permalink |
| 2026-03-04 20:18:22 | `docs/V2_architecture_overview.md` | `V2_architecture_overview` | Added missing architecture overview page with stable permalink |
| 2026-03-04 20:18:22 | `docs/V2_TERMS.md` | `V2_TERMS` | Added missing terminology reference page with stable permalink |
| 2026-03-04 20:18:22 | `docs/TODO_V2.md` | `TODO_V2` | Added missing roadmap page with stable permalink |
| 2026-03-04 20:18:35 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Marked task I.1 as completed after adding missing landing documentation pages |
| 2026-03-04 20:22:51 | `gradle.properties` | `org.gradle.jvmargs` | Replaced R:/temp java.io.tmpdir with project-local ./temp |
| 2026-03-04 20:23:09 | `scripts/utils/init-ramdisk.ps1` | `init-ramdisk` | Migrated directory initialization from R: RAM disk to project temp/* paths |
| 2026-03-04 20:25:06 | `docs/V2_Specification_UK.md` | `V2_Specification_UK` | Added missing Ukrainian technical specification page with stable permalink |
| 2026-03-04 20:25:06 | `docs/V2_architecture_overview_RU.md` | `V2_architecture_overview_RU` | Added missing Russian architecture overview page with stable permalink |
| 2026-03-04 20:25:06 | `docs/V2_architecture_overview_UK.md` | `V2_architecture_overview_UK` | Added missing Ukrainian architecture overview page with stable permalink |
| 2026-03-04 20:25:06 | `docs/V2_TERMS_RU.md` | `V2_TERMS_RU` | Added missing Russian terminology page with stable permalink |
| 2026-03-04 20:25:06 | `docs/V2_TERMS_UK.md` | `V2_TERMS_UK` | Added missing Ukrainian terminology page with stable permalink |
| 2026-03-04 20:25:06 | `docs/TODO_V2_RU.md` | `TODO_V2_RU` | Added missing Russian roadmap page with stable permalink |
| 2026-03-04 20:25:06 | `docs/TODO_V2_UK.md` | `TODO_V2_UK` | Added missing Ukrainian roadmap page with stable permalink |
| 2026-03-04 20:25:06 | `index-ru.html` | `index-ru Documentation section` | Switched V2 documentation cards to Russian-localized pages |
| 2026-03-04 20:25:06 | `index-uk.html` | `index-uk Documentation section` | Switched V2 documentation cards to Ukrainian-localized pages |
| 2026-03-04 20:25:06 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Updated I.1 status to fully completed for all three languages |
| 2026-03-04 20:26:00 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Clearly marked I.1 as implemented in section title and prioritization list |
| 2026-03-04 20:27:23 | `dev/IMPROVEMENT_PROPOSAL.md` | `Section II UI/UX` | Expanded item II with detailed user-centric examples: current behavior, pain points, and target UX for points II.1-II.6 |
| 2026-03-04 20:28:23 | `dev/IMPROVEMENT_PROPOSAL.md` | `Section III Functionality` | Expanded item III with detailed examples and user use-cases for points III.1-III.10 (current pain points and target UX) |
| 2026-03-04 20:30:36 | `gradle.properties` | `org.gradle.jvmargs` | Removed java.io.tmpdir from gradle.properties to avoid relative path issues |
| 2026-03-04 20:30:36 | `gradlew.bat` | `DEFAULT_JVM_OPTS` | Added project-local absolute java.io.tmpdir and auto-create temp/gradle-tmp |
| 2026-03-04 20:30:36 | `gradlew` | `DEFAULT_JVM_OPTS` | Added project-local absolute java.io.tmpdir and mkdir -p temp/gradle-tmp |
| 2026-03-04 20:31:02 | `gradlew.bat` | `TEMP/TMP` | Forced TEMP and TMP to project temp/gradle-tmp to remove R: dependency |
| 2026-03-04 20:31:02 | `gradlew` | `TMPDIR/TMP/TEMP` | Forced TMPDIR, TMP and TEMP to project temp/gradle-tmp |
| 2026-03-04 20:31:04 | `app_v2/src/main/res/layout/activity_player.xml` | `activity_player.xml` | Removed confirmed-unused legacy player layout after pre-change backup |
| 2026-03-04 20:31:04 | `app_v2/src/main/res/values/bools.xml` | `values/bools.xml` | Removed 4 unused bool flags (is_small_screen, enable_compact_layout, reduce_animations, use_single_pane_layout) |
| 2026-03-04 20:31:04 | `app_v2/src/main/res/values-sw480dp/bools.xml` | `values-sw480dp/bools.xml` | Removed duplicate unused bool flags for sw480dp |
| 2026-03-04 20:31:04 | `dev/IMPROVEMENT_PROPOSAL.md` | `IV.3 status` | Marked IV.3 as safe-pass partial implementation with explicit backup path and cleaned resources |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/badge_background.xml` | `badge_background` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/bg_progress_dialog.xml` | `bg_progress_dialog` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/button_hover_selector.xml` | `button_hover_selector` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/error_placeholder.xml` | `error_placeholder` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_add_24.xml` | `ic_add_24` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_image_error.xml` | `ic_image_error` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_save.xml` | `ic_save` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_swap_vert.xml` | `ic_swap_vert` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_video_error.xml` | `ic_video_error` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `app_v2/src/main/res/drawable/ic_video_placeholder.xml` | `ic_video_placeholder` | Removed confirmed-unused drawable (safe-pass #2, with backup) |
| 2026-03-04 20:32:54 | `dev/IMPROVEMENT_PROPOSAL.md` | `IV.3 status` | Updated IV.3 status after safe-pass #2 cleanup with backup paths |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/drawable/touch_zones_numbered.xml` | `touch_zones_numbered` | Removed confirmed-unused drawable (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/drawable/touch_zones_numbered_simple.xml` | `touch_zones_numbered_simple` | Removed confirmed-unused drawable (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/drawable/touch_zones_video_image.xml` | `touch_zones_video_image` | Removed confirmed-unused drawable (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/drawable/touch_zones_with_labels.xml` | `touch_zones_with_labels` | Removed confirmed-unused drawable (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/layout/touch_zones_overlay.xml` | `touch_zones_overlay` | Removed confirmed-unused layout (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/layout/player_command_panel_mode.xml` | `player_command_panel_mode` | Removed confirmed-unused layout (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/layout/dialog_rename_single.xml` | `dialog_rename_single` | Removed confirmed-unused layout (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `app_v2/src/main/res/menu/context_menu_file.xml` | `context_menu_file` | Removed confirmed-unused menu (safe-pass #3, backup 20260304_204007) |
| 2026-03-04 20:40:55 | `dev/IMPROVEMENT_PROPOSAL.md` | `IV.3 status` | Updated IV.3 status after safe-pass #3 cleanup with backup path |
| 2026-03-04 20:48:18 | `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | `AppDatabase` | Reset DB version from 18 to 1, removed all 17 migration objects |
| 2026-03-04 20:48:18 | `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | `DatabaseModule` | Replaced addMigrations() with fallbackToDestructiveMigration() |
| 2026-03-04 20:48:19 | `gradle.properties` | `jvmargs/kapt` | Added java.io.tmpdir=C: to jvmargs, kapt.use.worker.api=false to fix R: disk space issue |
| 2026-03-04 20:48:33 | `dev/IMPROVEMENT_PROPOSAL.md` | `IV.7 status` | Marked IV.7 as DONE - DB migrations reset, fallbackToDestructiveMigration |
| 2026-03-04 20:50:16 | `favicon.ico` | `favicon` | Created favicon.ico (16+32+48px) from ic_launcher xxxhdpi |
| 2026-03-04 20:50:16 | `index.html` | `favicon` | Added favicon links (ico/32/16/apple-touch) to EN landing |
| 2026-03-04 20:50:16 | `index-ru.html` | `favicon` | Added favicon links to RU landing |
| 2026-03-04 20:50:16 | `index-uk.html` | `favicon` | Added favicon links to UK landing |
| 2026-03-04 20:50:32 | `dev/IMPROVEMENT_PROPOSAL.md` | `V.2 status` | Marked V.2 as DONE - favicon created and inserted into all 3 HTML pages |
| 2026-03-04 20:56:02 | `robots.txt` | `robots.txt` | Add robots.txt: allow all crawlers, reference sitemap |
| 2026-03-04 20:56:02 | `sitemap.xml` | `sitemap.xml` | Add sitemap.xml with 3 language URLs + hreflang annotations |
| 2026-03-04 20:56:02 | `index.html` | `index.html` | SEO: add OG tags, Twitter Card, canonical, hreflang alternates, JSON-LD schema |
| 2026-03-04 20:56:02 | `index-ru.html` | `index-ru.html` | SEO: add OG tags (ru_RU), Twitter Card, canonical, hreflang alternates, JSON-LD schema |
| 2026-03-04 20:56:02 | `index-uk.html` | `index-uk.html` | SEO: add OG tags (uk_UA), Twitter Card, canonical, hreflang alternates, JSON-LD schema |
| 2026-03-04 20:56:02 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Mark V.4 (SEO) as done |
| 2026-03-04 20:59:47 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/views/TranslationOverlayView.kt` | `TranslationOverlayView` | a11y stub: add override performClick() to satisfy ClickableViewAccessibility lint |
| 2026-03-04 20:59:47 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt` | `PlayerGestureSetupManager` | a11y stub: call performClick on ACTION_UP in root and playerView touch listeners |
| 2026-03-04 20:59:47 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | `TextViewerManager` | a11y stub: call performClick on ACTION_UP in all 5 setOnTouchListener lambdas |
| 2026-03-04 20:59:47 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt` | `EpubViewerManager` | a11y stub: call performClick on ACTION_UP in WebView and translationOverlay touch listeners |
| 2026-03-04 20:59:47 | `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/MouseEventHandler.kt` | `MouseEventHandler` | a11y stub: call performClick on ACTION_UP in createTouchListener |
| 2026-03-04 20:59:47 | `dev/IMPROVEMENT_PROPOSAL.md` | `IMPROVEMENT_PROPOSAL` | Mark II.4 (a11y stub) as done |
