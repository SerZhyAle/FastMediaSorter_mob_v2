# S0391 Research Findings - Remote Source Runtime Toggles

Consolidated read-only research for the central availability gate, toggle placement, and the complete consumption-point coverage. Feeds `/ui-clarify` and a `/spec-tech` refresh. Tactical level: file paths and line numbers included. All paths are under `app_v2/src/main/java/com/sza/fastmediasorter/` unless noted.

---

## 0. Owner clarifications (2026-06-13)

Authoritative decisions given by the owner; they refine the strategic spec.

1. Welcome screen: the three network tiles must become actual toggles, not decorative images. This is in scope, not optional.
2. Core behavior: a disabled source disappears from the entire UI and the app stops interacting with its resources completely. Already-added resources are NOT deleted - they become invisible and inert (no scan, no sync, no connection, no thumbnails, no open).
3. The mechanism is one central node the app always passes through and always asks "is source X enabled right now". Enabled -> proceed; disabled -> turn back. The node serves: resource listing, the create-resource entry, and presence inside any internal resource-selection dialog.
4. Tab strip rule: if no remote source is enabled (no SMB, no SFTP/FTP, no cloud), the resource-type tab strip in the main window disappears entirely. Rationale: only ALL and Local would remain, and Local is conceptually a subset of ALL, so the strip carries no choice.
5. Auth decoupling: cloud-source toggles are unrelated to Google account presence (other Google services keep working) and unrelated to stored authorizations (which may belong to Instagram or anything). The auth/account subsystems are NOT touched, even where they hold OneDrive/Dropbox credentials. Disabling a cloud source must not clear or affect any stored credential.
6. Settings granularity (2026-06-13, second round): the user sees THREE group toggles - SMB, (S)FTP (merges SFTP+FTP), Cloud (merges all three providers) - not six. Each has an explanation line and a "?" help button like existing rows. Placement: new collapsible "Remote sources" section between File Browser and Authorization in the General tab. Same three toggles on welcome (symmetric). Storage stays six per-source flags; a group toggle mass-writes its members. Portrait and landscape both.

---

## 1. The central node (availability gate)

### Source identity is two-tiered
- `ResourceType` is flat: `LOCAL, SMB, SFTP, FTP, CLOUD` ([Models.kt:6-15](../../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt#L6-L15)). `isNetworkResource = SMB|SFTP|FTP|CLOUD`.
- The cloud sub-provider is one layer below `ResourceType`: `CloudProvider { GOOGLE_DRIVE, ONEDRIVE, DROPBOX }` ([CloudStorageClient.kt:12](../../../app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudStorageClient.kt#L12)), carried on the `MediaResource` record, not on `ResourceType`.
- Consequence: the gate cannot key on `ResourceType` alone for the three cloud toggles. It needs a unified source identity covering the six managed sources: SMB, SFTP, FTP, GDrive, OneDrive, Dropbox.
- Recommendation: introduce a `RemoteSourceId` (sealed/enum) mapping `{SMB, SFTP, FTP}` from `ResourceType` and `{GDrive, OneDrive, Dropbox}` from `CloudProvider`, and have the gate answer `isEnabled(RemoteSourceId)`. Resolve the exact shape at `/spec-tech` (it is the gate's public contract).

### Extension seam
- The runtime capability layer that already folds compile-time support with runtime checks is [CapabilityAvailability.kt](../../../app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt) (OCR/Translation/VR today, multibound `@CompiledCapabilities Set<String>`, `@Singleton`). The new gate is structurally identical: fold compile-tier (`MediaCapabilities.supportsCloud`) with runtime-tier (user toggle) into one query.
- Compile-tier reality: only cloud has a flag (`MediaCapabilities.supportsCloud`, [MediaCapabilities.kt](../../../app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt)). SMB/SFTP/FTP have NO compile flag - they are always compiled in, so their compile-tier is always true and only the user toggle matters.
- Rule 14/15: the gate reads `MediaCapabilities`, never `BuildConfig`. `MediaCapabilities.supportsCloud` is already consumed correctly in [WelcomeActivity.kt:246](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt#L246).
- Source available now == compile-supported AND user-enabled. Both true -> proceed.
- Hot-path cost: the gate must answer from an in-memory snapshot of settings (no DataStore read on the query). Mirror how `enableBackgroundSync` is observed as a flow and cached.

---

## 2. Toggle state storage (mirror `enableBackgroundSync`)

- Precedent boolean: `AppSettings.enableBackgroundSync` ([AppSettings.kt:36](../../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt#L36)) <-> DataStore key `KEY_ENABLE_BACKGROUND_SYNC = booleanPreferencesKey("enable_background_sync")` ([SettingsRepositoryImpl.kt:53](../../../app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt#L53)); read in the map block (~:282), written in `updateSettings()` (~:509).
- Add six booleans to `AppSettings`, default `true` (upgrade keeps current behavior). Naming convention in the file is `enable*`; pick consistent names at `/spec-tech`.
- Sub-store extraction precedent: `data/repository/settings/AudioSettingsStore.kt` (and siblings) - a `RemoteSourceSettingsStore` object keeps `SettingsRepositoryImpl` under the LOC cap.
- Side-effect sites that enumerate `AppSettings` fields and must include the six new ones:
  - `SettingsViewModel.resetGeneralSection()` ([SettingsViewModel.kt:238-265](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt#L238-L265)) - decide whether reset restores all sources to enabled.
  - Backup/import mapper (`domain/usecase/BackupMapper.kt`, `BackupData.kt`) - decide whether toggles are part of settings export.
- Optimistic update pattern: `SettingsViewModel.updateSettings()` sets `_settingsOverride.value` before the async write to avoid rapid-toggle races - reuse for the six toggles.

---

## 3. Complete consumption-point checklist (the "всё охватить" coverage)

Every place that routes by remote source / picks a remote client. Missing one is the spec's top risk (strategic §7 row 1). The gate must be consulted at each, or upstream of it.

| # | Point | File:line | Dispatch today | Where the gate slots in |
|---|-------|-----------|----------------|--------------------------|
| 1 | Add-folder type picker | [ResourceTypeSelectorDialog.kt:34-58](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourceTypeSelectorDialog.kt#L34-L58) | 5 hardwired cards, no gate | Hide disabled-source cards (owner: create-resource entry) |
| 2 | Add-resource form | AddResourceFormManager.kt:61 | reads `BuildConfig.SUPPORT_CLOUD` directly | Route cloud card visibility through gate, not BuildConfig |
| 3 | Main tab strip | [MainResourceTabsManager.kt:25-119](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt#L25-L119) | builds ALL/Local/SMB/FTP-SFTP/[Cloud]; `BuildConfig.SUPPORT_CLOUD` at :43,75,97,102 | Per-tab visibility by gate; hide whole strip when no remote source enabled (owner rule #4) |
| 4 | Filter chips | FilterResourceDialog.kt:121 | `ResourceType.CLOUD -> BuildConfig.SUPPORT_CLOUD` | Suppress chip for disabled sources via gate |
| 5 | Resource list scan loop | ui/main/helpers/ResourceScanCoordinator.kt | iterates all resources, `getScanner(type)` unconditionally | Skip resources whose source is disabled |
| 6 | Scanner selection | MediaScannerFactory.kt:36 / ScanDispatcher.kt | `when(resourceType)` | Gate upstream (coordinator), not inside factory |
| 7 | Background sync worker | NetworkFilesSyncWorker.kt:64-69 (filter), :123 (preload enqueue) | filters LOCAL+SMB+SFTP+FTP | Add gate to the filter; gate before preload enqueue |
| 8 | Manual network sync | SyncNetworkResourcesUseCase.kt:33 | `filter { type==SMB|SFTP|FTP }` | Add gate to the same predicate |
| 9 | Thumbnail preload worker | ThumbnailPreloadWorker.kt:94-101 | protocol dispatch, no gate | Gate before scheduling preload for a source |
| 10 | Glide model loaders | data/network/glide/NetworkFileModelLoader.kt:46-50; data/cloud/glide/CloudThumbnailModelLoader.kt:99-103 | per-file by URI prefix / `CloudProvider`; not per-resource | Prefer gating upstream (Browse/Player) before requesting a load; loader-level gating needs `EntryPointAccessors` (invasive) |
| 11 | Video/audio playback | ui/player/VideoPlayerManager.kt (`playVideo` when(resourceType)) | 5 extension funcs | Gate before play |
| 12 | Player loader (Favorites) | ui/player/helpers/PlayerMediaLoaderManager.kt:267,279 | infers `actualResourceType` from path prefix; Favorites virtual id=-100L | Mixed-source path - gate needs original resource resolution (see §6 open Q) |
| 13 | File operations | data/transfer/UnifiedFileOperationHandler.kt:401-411 | dispatch by path prefix (string) | Gate before starting a file op on a remote path |
| 14 | Connection lifecycle | data/network/lifecycle/ConnectionGateRegistry.kt; core/di/NetworkLifecycleModule.kt:38 | cloud gate registered iff `BuildConfig.SUPPORT_CLOUD` | Passive: never `acquire` for disabled; proactive: `closeFor(...)` on disable |
| 15 | Quick verifier | data/verifier/QuickVerifierDispatcher.kt | `when(resource.type)` (FTP skipped) | Gate before probe |
| 16 | Browse load decisions | ui/browse/managers/BrowseResourceLoadManager.kt:150,161 | `isNetworkResource` / `isCloudResource` | Gate before auth pre-check / load |
| 17 | Settings last-sync label | SettingsViewModel.kt:459-463 | filters SMB/SFTP/FTP for UI | Cosmetic; gate to avoid stale date for disabled source |

Note (uncertain completeness): points 10-13 dispatch by URI/path-prefix rather than `ResourceType`, so a grep on `ResourceType` alone will miss them. `/spec-tech` should treat the gate as a guard at resource-entry boundaries (list build, open, sync enqueue) rather than trying to inject it into every leaf.

---

## 4. Rule 14 violations to remediate (gate coherence)

The gate is incoherent if any caller still routes on `BuildConfig.SUPPORT_CLOUD` directly in `src/main/`. Inventory of direct reads that gate availability/visibility:

- AddResourceFormManager.kt:61
- FilterResourceDialog.kt:121
- MainResourceTabsManager.kt:43,75,97,102
- core/di/NetworkLifecycleModule.kt:38
- ui/cloud picker activities: OneDriveFolderPickerActivity.kt:64, GoogleDriveFolderPickerActivity.kt:80, DropboxFolderPickerActivity.kt:64

These must read the gate (or `MediaCapabilities` for the pure compile-tier part), not `BuildConfig`. The cleanest interpretation of owner rule #3 ("one node everywhere") is that the availability-gating reads above become gate queries. `MainResourceTabsManager` also reads `BuildConfig` for the Favorites tab index math (:75) - that part is compile-tier only and can move to `MediaCapabilities`.

---

## 5. Welcome page: tiles -> toggles

- Pager: `WelcomePagerAdapter` ([WelcomePagerAdapter.kt](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt)); networks page is `VIEW_TYPE_NETWORKS`, `NetworksViewHolder.bind()` (~:170-177) currently only toggles `tileNetworkCloud.isVisible = page.showCloudNetworkTile`. No read/write, no onBind callback yet.
- Layout (both variants must change together): [res/layout/page_welcome_networks.xml](../../../app_v2/src/main/res/layout/page_welcome_networks.xml) (234 LOC) and [res/layout-land/page_welcome_networks.xml](../../../app_v2/src/main/res/layout-land/page_welcome_networks.xml) (233 LOC). Three `MaterialCardView` tiles with `clickable=false`/`focusable=false` (~:95-97) - the replacing toggles must set these true for D-pad/TalkBack.
- onBind seam to add (mirror S0400 functionality page): add `onBindNetworks: ((PageWelcomeNetworksBinding) -> Unit)?` to `WelcomePage`, invoke it in `NetworksViewHolder.bind()`, set it in `WelcomeActivity` to a new `WelcomeRemoteSourcesController.bind(binding, owner)`.
- Controller pattern: [WelcomeFunctionalityController.kt](../../../app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt) is the template - one-shot `getSettings().first()` for initial state (welcome is the sole writer), `setOnCheckedChangeListener` -> `persist { it.copy(...) }` on `appScope` (write survives page tear-down), capability-gated row visibility (`if (!mediaCapabilities.supportsCloud) GONE`).
- Group -> per-source mass write: the documents row in `WelcomeFunctionalityController` (~:123-143) is the precedent - one master toggle copies N fields at once. Owner decision 2026-06-13: THREE group toggles (SMB / (S)FTP / Cloud), matching settings - SMB writes `smbEnabled`; (S)FTP writes `sftpEnabled`+`ftpEnabled`; Cloud writes the three cloud flags. Storage stays the six flags; each group displays ON if any member is on.
- Cross-screen consistency: welcome toggles read/write the same `AppSettings` fields as the Settings section, so onboarding choice and Settings stay in sync.
- Mixed-state semantics: `SwitchMaterial` has no indeterminate state. Owner decision 2026-06-13: the group toggle is ON if any member source is ON (no tri-state). Cloud group is hidden entirely when `!mediaCapabilities.supportsCloud`.

---

## 6. Settings placement

- Host: `SettingsActivity` + 4-tab `SettingsPagerAdapter` (General/Media/Playback/Operations). General tab uses collapsible sections.
- Section model: `CollapsibleSectionHeader` + container `LinearLayout` in `fragment_settings_general.xml`, registered as an `ExpandableSection` in `GeneralSettingsSectionsHelper.setup()` (~:36-44) with a `KEY_*_EXPANDED` constant. Rows use the `SettingsToggleRow` widget (auto contentDescription, focusable/clickable, optional help tooltip).
- Existing precedent home: `containerSystem` ("Background sync, network and cache") already owns `rowEnableBackgroundSync`.
- Placement RESOLVED (owner 2026-06-13): new collapsible "Remote sources" section in the General tab, positioned between the File Browser section and the Authorization section. Three group toggles (SMB / (S)FTP / Cloud), each with an explanation subtitle and a "?" help button. Rows live in the already-catalogued `fragment_settings_general.xml` -> settings search auto-indexes them (no `SettingsSearchLayoutCatalog`/`SettingsSearchTabMapping` change needed). Both portrait and `layout-land/fragment_settings_general.xml` edited in lockstep.
- Obligations when rows are added: EN/RU/UK strings with parity, accessibility (the `SettingsToggleRow` widget already covers contentDescription/focus and the "?" help affordance).
- Auth area is orthogonal: `ui/settings/auth/AuthSessionsActivity` is web cookie sessions (Instagram/TikTok), not cloud/SMB account management. Owner rule #5 confirms the toggles do not touch it.

---

## 7. UI/UX decisions (all resolved 2026-06-13)

- Fate of existing disabled-source resources (strategic §6.1): hidden and inert, never deleted, no interaction (hide from list).
- Communicating "folders not deleted, just hidden": confirmation dialog shown when disabling a source that has existing resources.
- In-flight background sync on disable (strategic §6.2): cancel running work for the disabled source (best-effort - already-synced items in the current pass are not rolled back).
- Group-toggle mixed-state display (welcome + Settings): group toggle is ON if any member source is ON (Switch-native, no tri-state).
- Cloud group in cloud-unsupported builds (strategic §6.3): hide the whole block.
- Tab-strip vanish (no remote source enabled): silent, no one-time hint.
- Favorites/mixed-source player path (checklist #12): gated - the player resolves the original source and turns back if it is disabled.

---

## 8. Tactical-plan refresh implied

The current six phases predate owner clarifications. `/spec-tech` should fold in:
- New behavior: main tab strip disappears when no remote source enabled (Phase 03/04 area, point 3).
- Rule 14 remediation of the cloud-gating reads (§4) as part of gate adoption, not a separate ticket - the gate is incoherent otherwise.
- Cloud sub-provider granularity: gate keys on `RemoteSourceId`, not bare `ResourceType` (Phase 02 gate contract).
- Glide/player/file-op gating strategy: guard at entry boundaries, not leaf loaders (Phase 04).
- Explicit non-touch of auth/account subsystems (scope guard across all phases).
