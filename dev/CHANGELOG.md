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
