# Settings Migration Map — S0119

**Source:** Phase 01 inventory + Phase 02 IA model, 2026-05-08
**Purpose:** Per-element canonical placement decisions. Drives future migration spec authoring. Does not execute any moves.

---

## Canonical Placement Map

Verdict values: `stays` · `relocate` · `promote-to-management-surface` · `demote-to-contextual` · `retire`

### General Tab — current items

| Key | Current tab / section | Canonical tab / section | Entity type | Verdict | Migration prerequisite |
|---|---|---|---|---|---|
| `general.language` | General / Appearance | General / Appearance | preference | stays | — |
| `switchSmallControls` | General / Appearance | General / Appearance | preference | stays | — |
| `switchCompactElements` | General / Appearance | General / Appearance | preference | stays | — |
| `general.all_files` | General / Browse | General / Browse | preference | stays | — |
| `general.hidden_files` | General / Browse | General / Browse | preference | stays | — |
| `switchShowSubfoldersAsItems` | General / Browse | General / Browse | preference | stays | — |
| `switchDefaultRememberFileList` | General / Browse | General / Browse | preference | stays | — |
| `switchPreventSleep` | General / Browse | General / Browse | preference | stays | — |
| `switchEnableFavorites` | General / Browse | General / Browse | preference | stays | — |
| `general.network_parallelism` | General / Network | General / Network | preference | stays | — |
| `etDefaultUser` | General / Network | General / Network | preference | stays | — |
| `etDefaultPassword` | General / Network | General / Network | preference | stays | — |
| `general.background_sync` | General / Network | General / Network | preference | stays | — |
| `general.sync_interval` | General / Network | General / Network | preference | stays | — |
| `btnSyncNow` | General / Network (mixed) | General / Network — labeled service-action row | service-action | relocate (visual) | Separate from preference rows with visual divider; no tab change |
| `switchEnableThumbnailPreload` | General / Network | General / Network | preference | stays | — |
| `switchThumbnailPreloadWifiOnly` | General / Network | General / Network | preference | stays | — |
| `general.cache_limit` | General / Cache | General / Cache | preference | stays | — |
| `btnAutoCalculateCache` | General / Cache (mixed) | General / Cache — labeled service-action row | service-action | relocate (visual) | Separate from preference rows with visual divider; no tab change |
| `general.clear_cache` | General / Cache (mixed) | General / Cache — labeled service-action row | service-action | relocate (visual) | Same |
| `textDeviceStorageValue` / `btnDeviceStorageRefresh` | General / Cache | General / Cache | informational | stays | — |
| `general.prefetch_cache` | General / Cache | General / Cache | preference | stays | — |
| `general.streaming_cleanup` | General / Cache | General / Cache | preference | stays | — |
| `general.streaming_ttl` | General / Cache | General / Cache | preference | stays | — |
| `general.clear_streaming_cache` | General / Cache (mixed) | General / Cache — labeled service-action row | service-action | relocate (visual) | Same |
| `general.export_settings` | General / Import-Export (mixed) | General / Service Actions section | service-action | relocate (visual) | Separate labeled section; no tab change |
| `general.import_settings` | General / Import-Export (mixed) | General / Service Actions section | service-action | relocate (visual) | Same |
| `btnResetSettings` | General / mixed | General / Service Actions section | service-action | relocate (visual) | Same |
| `general.reset_general` | General / mixed | General / Service Actions section | service-action | relocate (visual) | Same |
| `btnResetSmbConnections` | General / mixed | General / Service Actions section | service-action | relocate (visual) | Same |
| `general.backup_google_drive` | General / Backup | General / Service Actions section (standard only) | service-action | relocate (visual) | Same; must preserve `BackupRestoreViewModel` lifecycle |
| `general.restore_google_drive` | General / Backup | General / Service Actions section (standard only) | service-action | relocate (visual) | Same |
| `headerPermissions` | General / Permissions | General / Permissions (management-surface header) | management-surface | stays | — |
| `btnLocalFilesPermission` | General / Permissions | General / Permissions | permission-redirect | stays | — |
| `btnManageMediaPermission` | General / Permissions | General / Permissions | permission-redirect | stays | — |
| `btnNetworkPermission` | General / Permissions | General / Permissions | informational | stays | — |
| `btnUserGuide` | General / bottom (mixed) | General / About sub-section | informational-link | relocate (visual) | Group with other links in labeled "About" section |
| `btnHowToGuides` | General / bottom (mixed) | General / About sub-section | informational-link | relocate (visual) | Same |
| `btnOpenWelcome` | General / bottom (mixed) | General / About sub-section | service-action | relocate (visual) | Same |
| `btnPrivacyPolicy` | General / bottom (mixed) | General / About sub-section | informational-link | relocate (visual) | Same |
| `btnOpenSourceLicenses` | General / bottom (mixed) | General / About sub-section | management-surface | relocate (visual) | Same; preserves navigation behavior |
| `tvGmsSettingsLink` | General / Appearance | General / Appearance (conditional) | conditional-informational | stays | — |
| `headerDebugSettings` / `containerDebugSettings` | General / Debug | General / Debug section (DEBUG only) | debug | stays | — |
| `btnIntegrationTests` / `btnImportTestCredentials` | General / Debug | General / Debug section (DEBUG only) | debug | stays | — |

### Media Tab — current items

| Key | Current tab / section | Canonical tab / section | Entity type | Verdict | Migration prerequisite |
|---|---|---|---|---|---|
| `switchSupportImages` | Media / Images | Media / Images | capability-enable | stays | — |
| `switchSupportGifs` | Media / Images | Media / Images | capability-enable | stays | — |
| `switchLoadFullSizeImages` | Media / Images | Media / Images | preference | stays | — |
| `switchCropImagesToFullscreen` | Media / Images | Media / Images | preference | stays | — |
| `etImageSizeMin` | Media / Images | Media / Images | preference | stays | — |
| `switchSupportVideos` | Media / Video | Media / Video | capability-enable | stays | — |
| `switchShowVideoThumbnails` | Media / Video | Media / Video | preference | stays | — |
| `etVideoSizeMin` / `etVideoSizeMax` | Media / Video | Media / Video | preference | stays | — |
| `switchSupportAudio` | Media / Audio | Media / Audio | capability-enable | stays | — |
| `switchSearchAudioCoversOnline` | Media / Audio | Media / Audio | preference | stays | — |
| `switchSearchCoversOnlyWifi` | Media / Audio | Media / Audio | preference | stays | — |
| `switchSaveAudioMetadataLocally` | Media / Audio | Media / Audio | preference | stays | — |
| `switchEnablePhotosDuringAudio` | Media / Audio | Media / Audio | preference | stays | — |
| `btnSelectPhotosSource` | Media / Audio | Media / Audio (management-surface row) | management-surface | stays | — |
| `switchMicRecordingEnabled` | Media / Audio | Media / Audio | capability-enable | stays | — |
| `switchEnablePersistentAudioPlayback` | Media / Audio | Media / Audio | preference | stays | — |
| `actvAudioEmptyState` | Media / Audio | Media / Audio | preference | stays | — |
| `etAudioSizeMin` / `etAudioSizeMax` | Media / Audio | Media / Audio | preference | stays | — |
| `switchSupportText` | Media / Documents | Media / Documents | capability-enable | stays | — |
| `switchShowTextLineNumbers` | Media / Documents | Media / Documents | preference | stays | — |
| `switchSupportPdf` | Media / Documents | Media / Documents | capability-enable | stays | — |
| `switchShowPdfThumbnails` | Media / Documents | Media / Documents | preference | stays | — |
| `switchSupportEpub` | Media / Documents | Media / Documents | capability-enable | stays | — |
| `switchEnableTranslation` | Media / Other | Media / Other | capability-enable | stays | — |
| `switchEnableOcr` | Media / Other | Media / Other | capability-enable | stays | — |
| `btnSetDefaultMediaPlayer` | Media / Common | Playback / Default Player section | capability-enable | relocate | update search registry entry destination |

### Playback Tab — current items

| Key | Current tab / section | Canonical tab / section | Entity type | Verdict | Migration prerequisite |
|---|---|---|---|---|---|
| `playback.sort_mode` | Playback / Sort | Playback / Sort | preference | stays | — |
| `switchGridMode` | Playback / Grid | Playback / Grid | preference | stays | — |
| `playback.icon_size` | Playback / Grid | Playback / Grid | preference | stays | — |
| `switchHideGridActionButtons` | Playback / Grid | Playback / Grid | preference | stays | — |
| `playback.slideshow_interval` | Playback / Slideshow | Playback / Slideshow | preference | stays | — |
| `playback.play_to_end` | Playback / Slideshow | Playback / Slideshow | preference | stays | — |
| `switchAllowRename` | Playback / File ops | Playback / File ops | preference | stays | — |
| `playback.allow_delete` | Playback / File ops | **Operations / Safety** | preference | **relocate** | Update search registry: `destination = DESTINATIONS`; verify keyboard navigation in Operations tab |
| `setting_disable_camera_capture` | Playback / Camera | Playback / Camera | preference | stays | — |
| `setting_skip_camera_filename_dialog` | Playback / Camera | Playback / Camera | preference | stays | — |
| `switchHideSystemUiInFullscreen` | Playback / Player UI | Playback / Player UI | preference | stays | — |
| `switchShowCommandPanel` | Playback / Player UI | Playback / Player UI | preference | stays | — |
| `switchAlwaysShowTouchZones` | Playback / Touch zones | Playback / Touch zones | preference | stays | — |
| `switchShowBlackScreenButton` | Playback / Player UI | Playback / Player UI | preference | stays | — |
| `btnShowHintNow` | Playback / Player UI | Playback / Player UI | service-action | stays | — |
| `switchShowPlayerHint` | Playback / Player UI | Playback / Player UI | preference | stays | — |
| `switchDetailedErrors` | Playback / Behaviour | Playback / Behaviour | preference | stays | — |
| `switchResumeOnNextLaunch` | Playback / Behaviour | Playback / Behaviour | preference | stays | — |
| `switchLinkAutodownloadEnabled` | Playback / Behaviour | Playback / Behaviour | preference | stays | — |
| `switchLinkAutodownloadOpenInPlayer` | Playback / Behaviour | Playback / Behaviour | preference | stays | — |
| `switchDisable3dVr` | Playback / Behaviour | Playback / Behaviour | preference | stays | — |
| `switchPrimaryMediaPlayer` | Playback / Default Player | Playback / Default Player | capability-enable | stays | — |
| `switchAcceptSharedFiles` | Playback / Default Player | Playback / Default Player | capability-enable | stays | — |
| `switchConfirmDelete` (Playback duplicate) | Playback / File ops | **retire from Playback — exists in Operations** | preference | **retire** | Remove from `PlaybackSettingsFragment`; canonical copy in Operations stays; update search if needed |
| `rowControlsKeybindings` | Playback / Input | Playback / Input | management-surface | stays | — |
| `rowSavedAuthorizations` | Playback / Input | **General / Network or new Security sub-section** | management-surface | **relocate** | pending-future-spec; add search entry with destination=GENERAL |
| `btnResetPlaybackSection` | Playback / Reset | Playback / Reset | service-action | stays | — |

### Operations Tab — current items

| Key | Current tab / section | Canonical tab / section | Entity type | Verdict | Migration prerequisite |
|---|---|---|---|---|---|
| `operations.safe_mode` | Operations / Safety | Operations / Safety | preference | stays | — |
| `operations.confirm_delete` | Operations / Safety | Operations / Safety | preference | stays | — |
| `operations.confirm_move` | Operations / Safety | Operations / Safety | preference | stays | — |
| `switchUseTrash` | Operations / Safety | Operations / Safety | preference | stays | — |
| `btnClearTrash` | Operations / Safety | Operations / Safety (service-action row) | service-action | stays | — |
| `destinations.enable_copying` | Operations / Copy | Operations / Copy | preference | stays | — |
| `switchGoToNextAfterCopy` | Operations / Copy | Operations / Copy | preference | stays | — |
| `switchOverwriteOnCopy` | Operations / Copy | Operations / Copy | preference | stays | — |
| `destinations.enable_moving` | Operations / Move | Operations / Move | preference | stays | — |
| `switchOverwriteOnMove` | Operations / Move | Operations / Move | preference | stays | — |
| `destinations.max_recipients` | Operations / Copy | Operations / Copy | preference | stays | — |
| `destinations.add_destination` | Operations / Destinations | Operations / Destinations | management-surface | stays | — |
| `rvDestinations` | Operations / Destinations | Operations / Destinations | management-surface | stays | — |
| `switchEnableScheduledOps` | Operations / Scheduled | Operations / Scheduled | capability-enable | stays | — |
| Scheduled ops list | Operations / Scheduled | Operations / Scheduled | management-surface | stays | — |

---

## Confirmed Misplacements

Items with verdict `relocate`, `promote-to-management-surface`, or `demote-to-contextual` that carry implementation risk or user-facing impact.

### M1 — `switchAllowDelete` from Playback to Operations

- **Element key:** `playback.allow_delete` (registry key)
- **Current placement:** Playback tab → File Operations section
- **Canonical placement:** Operations tab → Safety & Confirmation section
- **Entity type mismatch:** This controls whether the destructive delete action is available in the player. It is a safety/permission setting, not a playback behavior. It belongs alongside `switchEnableSafeMode`, `switchConfirmDelete`, `switchConfirmMove`.
- **User-facing impact:** A user who wants to prevent accidental deletes looks in Operations (where safe mode lives) but cannot find this control without search or accidentally finding it in Playback.
- **Implementation risk:** medium — the element has a search entry (`playback.allow_delete`, destination=PLAYBACK) which must be updated to destination=DESTINATIONS in the same commit. The preference key itself does not change.

### M2 — Remove duplicate `switchConfirmDelete` from PlaybackSettingsFragment

- **Element:** `switchConfirmDelete` in `PlaybackSettingsFragment` (fragment_settings_playback layout)
- **Current placement:** Playback tab → File Operations section
- **Canonical placement:** Operations tab only (canonical copy already there)
- **Anomaly type:** Same preference key (`confirmDelete`) written by two separate Switch controls in separate fragments. Both update `SettingsViewModel` via `viewModel.settings`. The Playback copy is not indexed in `SettingsSearchRegistry`.
- **User-facing impact:** User may toggle the switch in Playback and see the same switch already toggled in Operations — or vice versa — creating confusion about which one is authoritative. (Both write the same preference, so they always reflect the same value, but their coexistence is confusing.)
- **Implementation risk:** low — removing the Playback copy requires only removing the binding code and the view from the layout. No preference storage change needed.

### M3 — `rowSavedAuthorizations` from Playback to General or new Security surface

- **Element:** `rowSavedAuthorizations` (launches `AuthSessionsActivity`)
- **Current placement:** Playback tab
- **Canonical placement:** General tab (Network/Credentials section) or a new dedicated Security sub-section entry
- **Entity type mismatch:** Saved authorizations are OAuth session tokens for cloud storage / remote server connections — a network credentials concern, not a playback setting.
- **User-facing impact:** A user managing their saved credentials naturally goes to General (where default user/password fields already live), not Playback.
- **Implementation risk:** medium — requires: (1) remove row from `PlaybackSettingsFragment` layout, (2) add row to `GeneralSettingsFragment` layout + code, (3) add/update search entry with `destination = GENERAL`. The `AuthSessionsActivity.start()` call itself does not change.

### M4 — Service-actions visually mixed with preferences in General tab

- **Elements:** `btnSyncNow`, `btnClearCache`, `btnClearStreamingCache`, `btnAutoCalculateCache`, `btnResetSmbConnections`, `btnResetSettings`, `btnResetGeneralSection`, `btnExportSettings`, `btnImportSettings`, `btnBackup`, `btnRestore`
- **Current placement:** Distributed across General tab sections with no visual separation from adjacent toggle/dropdown rows
- **Canonical placement:** Each service-action cluster grouped under a clearly-labeled collapsible section (e.g., "Cache Management", "Settings Data", "Cloud Backup") with visual row-type differentiation
- **Entity type mismatch:** One-shot destructive or maintenance actions live adjacent to persistent preferences with no visual cue distinguishing them.
- **User-facing impact:** Increased cognitive load; accidental action risk; search for "cache" returns both the preference (`actvCacheSizeLimit`) and the action (`btnClearCache`) without type disambiguation.
- **Implementation risk:** low — purely visual reorganization within the existing tab; no preference key changes, no search entry destination changes.

**M4 retracted (2026-05-09):** codebase already had top-level category IA (Interface / Permissions / App Data / System / Debug) that satisfied M4. S0121 sub-headers rolled back via S0124.

### M5 — Informational links mixed into General tab body

- **Elements:** `btnUserGuide`, `btnHowToGuides`, `btnOpenWelcome`, `btnPrivacyPolicy`, `btnOpenSourceLicenses`
- **Current placement:** General tab, at the bottom of the scroll body among other controls
- **Canonical placement:** General tab → dedicated "About" collapsible section
- **Entity type mismatch:** External links and navigation to legal text are not user-configurable settings and should not compete visually with preference controls.
- **Implementation risk:** low — grouping under a new `About` header; no behavior change; no search registry changes needed (none are currently indexed).

---

## Search Index Fixes Required

Entries in `SettingsSearchRegistry` whose `destination` must be updated when the corresponding migration is executed.

| Entry key | Current destination | Required destination | Migration spec | Notes |
|---|---|---|---|---|
| `playback.allow_delete` | `PLAYBACK` | `DESTINATIONS` | pending-future-spec (M1) | Update in same commit as fragment relocation |
| `rowSavedAuthorizations` (not currently indexed) | — | `GENERAL` | pending-future-spec (M3) | Add new registry entry when relocated |
| `btnSetDefaultMediaPlayer` (not currently indexed) | — | `PLAYBACK` | pending-future-spec | Already in Playback — add search entry |

---

## Migration Strategy

### Wave 1 — Zero-risk visual reorganization (no search deep-link changes, no behavior changes)

Items: M4 (service-action visual grouping), M5 (About section)

- **Approach:** Reorganize layout XML within General tab; group service-action buttons under labeled collapsible sections; group informational links under an `About` section. No Kotlin logic changes.
- **Non-regression checks required:**
  - All service-action buttons still function after visual relocation.
  - `btnBackup` / `btnRestore` remain visible only in standard flavor.
  - Keyboard focus order updated in layout; D-pad navigation still traverses all buttons.
  - Light and dark theme both render new section headers with sufficient contrast.

### Wave 2 — Medium-risk relocations (search registry updates, cross-tab moves)

Items: M1 (`switchAllowDelete` Playback → Operations), M2 (remove duplicate `switchConfirmDelete` from Playback)

- **Approach:**
  - M1: Move `switchAllowDelete` binding from `PlaybackSettingsFragment` to `OperationsSettingsFragment`. Update `SettingsSearchRegistry` entry `playback.allow_delete` — change `destination` to `DESTINATIONS` and update `sectionId` to `"operations"`. Update `viewId` to the Operations layout's switch id.
  - M2: Remove `switchConfirmDelete` from `PlaybackSettingsFragment` layout and its `setOnCheckedChangeListener` binding. Do not change the Operations copy.
- **Non-regression checks:**
  - Search for "allow delete" navigates to Operations tab and highlights the switch.
  - Keyboard/D-pad navigation in Operations tab includes the relocated switch in correct focus order.
  - `SettingsSearchRegistry` has no stale `PLAYBACK` destination for relocated items.
  - PlaybackSettingsFragment does not show `switchConfirmDelete` after removal.

### Wave 3 — Higher-risk management-surface relocation

Items: M3 (`rowSavedAuthorizations` Playback → General)

- **Approach:** Remove `rowSavedAuthorizations` from Playback layout/code. Add equivalent row to General layout, within the Network/Credentials section. Add new `SettingsSearchRegistry` entry for saved authorizations with `destination = GENERAL`.
- **Non-regression checks:**
  - `AuthSessionsActivity.start(context)` still launches from the new row in General.
  - Keyboard and D-pad navigation in General tab includes the new row.
  - Search for "authorizations" or "sessions" navigates to the General tab.
  - Playback tab no longer shows the row.

### Implementation notes

- Each wave is a separate implementation spec. S0119 defines the map and strategy; it does not execute the moves.
- Wave ordering is a recommendation. A future spec may combine Wave 1 + Wave 2 if the scope is small enough.
- Each migration must include: layout change, fragment code change, search registry update, non-regression test (search deep-link), and dev log entry.
- The `SettingsSearchRegistry` `destination` update must be committed atomically with the fragment relocation.
