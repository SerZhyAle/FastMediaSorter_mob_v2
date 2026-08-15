# 01 - Permission surface inventory and divergence catalog

Research performed 2026-08-06 for S1436, read-only sweep of `app_v2`. Verified claims carry `file:line`.
Two claims were re-verified by hand after the sweep and are marked **(verified)**.

## Verdict on the owner's premise

The three surfaces named in the request - the Settings permissions screen, its "Grant all" button and the
Welcome onboarding page - already share one catalog, so their per-permission titles and descriptions are
identical *by construction*. The defect the owner suspected is real, but it lives elsewhere: roughly fifteen
in-feature call sites request the same permissions with their own hardcoded text, and those texts contradict
both the catalog and each other.

## The catalog that already exists

| Role | File | Note |
|---|---|---|
| Entry model | `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt:1-27` | id, manifest name, title/description string res, icon res, group, optional, minSdk/maxSdk, flavor gates |
| Registry contract | `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRegistryRepository.kt:6-19` | `getEntries()`, `getGroups()`, `getWelcomeEntries()` |
| Registry implementation | `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt:20-153` | one hardcoded list of 13 entries - the only place the requestable set is enumerated |
| Live status resolution | `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt:19-53` | special-cases the three permissions that are not `ContextCompat`-checkable |
| Shared row rendering | `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt:17-113` | used by both screens |

## The three named surfaces

| Surface | Entry point | List source | Result |
|---|---|---|---|
| Settings permissions screen | `GeneralSettingsFragment.kt:291-296`, row `btnPermissionsManagement` | `getEntries()` (`PermissionsManagementFragment.kt:164`) | catalog-driven |
| Settings "Grant all" | `PermissionsManagementFragment.kt:118-137` | same `getEntries()`, filtered to denied, minus special-grant permissions (`:125-127`) | matches its own rows by construction |
| Welcome permissions page | `WelcomeActivity.kt:353-358`, pager page 4 | `getWelcomeEntries()` (`WelcomePermissionsManager.kt:124`) | catalog-driven |
| Welcome "Grant all" | `WelcomePermissionsManager.kt:166-210` | same `welcomeEntries`, filtered to denied | matches its own rows by construction |

**Composition divergence between the two screens (verified).** `getWelcomeEntries()` re-adds POST_NOTIFICATIONS
past its `ENABLE_PERSISTENT_AUDIO_PLAYBACK` gate (`PermissionRegistryRepositoryImpl.kt:162-179`). On a build
without persistent audio playback, onboarding lists a permission the settings screen hides. The relaxation is
deliberate and commented, but it means "the same list on both screens" is not true today.

**Structural duplication.** `PermissionsManagementFragment.buildRows()` (`:163-190`) and
`WelcomePermissionsManager.buildRows()` (`:298-323`) are two near-identical hand-maintained copies of the same
grouping logic.

## Everything outside the catalog

About fifteen call sites request permissions directly against `Manifest.permission.*` with their own strings and
no reference to the registry: `MainStoragePermissionsHelper`, `AddResourceScanManager`, `BrowseLifecycleHelper`,
`ResourceEditorFragment`, `AddResourceConnectionManager`, `BrowseEventHandler`, `CameraCaptureActivity`,
`CameraLaunchWidgetManager`, `CameraQuickCaptureLaunchManager`, `PhotoCaptureLaunchManager`, `BrowseActivity`,
`MainVoiceCaptureManager`, `QuickAudioRecorderLaunchManager`, `MainScreenRecordingManager`,
`CompanionQrScanActivity`, `OperationsScheduledManager`, `OperationsCaptureManager`.

`WelcomeActivity.kt:657-688` additionally carries a private fourth reimplementation of "which media permissions
this SDK needs", used only to set a view-model flag, decoupled from the `WelcomePermissionsManager` in the same
Activity.

## Divergence catalog - the problem statement

**All-files access / MANAGE_EXTERNAL_STORAGE - six wordings.**

- Catalog: `perm_title_manage_external_storage` "Full storage management" / `perm_desc_manage_external_storage`.
- Startup gate `MainStoragePermissionsHelper.kt:65-83`: `permission_storage_rationale_r` / `permission_storage_rationale`, buttons `grant_permissions` + `continue_anyway`.
- Folder picker, duplicated verbatim in `AddResourceScanManager.kt:365-374` and `BrowseLifecycleHelper.kt:74-105`: `all_files_access_required` / `all_files_access_explanation`, button `grant_permission` - a second, singular key for the same action.
- Same flow, preceding toast `android_media_requires_permission` (`AddResourceScanManager.kt:263`).
- Local resource creation `ResourceEditorFragment.kt:387-392`: `permissions_required_for_local_resource`, plus warning `permissions_denied_warning` (`:80`).

**Local network access - content mismatch, not only wording.** The catalog description names SMB and DLNA;
the rationale dialog duplicated in `AddResourceConnectionManager.kt:525-535` and `BrowseEventHandler.kt:210-220`
names SMB, SFTP, FTP and Chromecast. Each omits what the other states.

**Camera - three wordings, one undocumented use.** Catalog describes in-app photo capture and OCR.
`camera_permission_required` is reused consistently across six call sites. `CompanionQrScanActivity.kt:77` uses
`companion_qr_camera_denied` for QR pairing - a use of the camera the catalog never mentions.

**Microphone - five call sites, four wordings, and one wrong gate.** `mic_recording_permission_denied`,
`quick_recorder_permission_needed` / `quick_recorder_permission_settings`, `screen_recording_permission_denied`
(which bundles microphone and notifications into one sentence), and `camera_capture_microphone_muted`
(non-blocking - recording continues without sound).

**Notifications.** Catalog description, versus a bare button label `grant_notifications_permission` in scheduled
operations (`OperationsScheduledManager.kt:296-309`) with no description at all.

**Battery optimization.** Catalog description, versus `OperationsScheduledManager.kt:311-330` launching the
system intent with no rationale at all, versus a third dead set of strings `perm_battery_optim_*`.

**Location.** Catalog text only; `OperationsCaptureManager.kt:65-75` requests it silently on toggle.

**Contacts.** No divergence - only the catalog requests it; `ContactSnapshotDataSource.kt:20-37` deliberately
needs no rationale because the system picker grants a one-time URI.

## Correctness defects found on the way

1. **Wrong flavor gate on microphone (verified).** `record_audio` is gated on `SUPPORT_AUDIO`
   (`PermissionRegistryRepositoryImpl.kt:130`), while every real microphone feature is gated on
   `SUPPORT_MIC_RECORDING` (`MainActivity.kt:741`, `BrowseActivity.kt:472`, `OperationsCaptureManager.kt:112,208`,
   `ResolvePanelRouteAvailabilityUseCase.kt:77`). On `lite` those two flags differ (`docs/FLAVOR_MATRIX.md:27,29`),
   so both permission screens offer a microphone permission for a capability that build does not contain.
   The existing test only asserts each gate name resolves to a real `BuildConfig` field
   (`PermissionRegistryRepositoryImplTest.kt:81-90`), so it cannot catch a gate that exists but is the wrong one.
2. **Three disagreeing storage-SDK branch implementations.** `PermissionChecker.kt:10-26` (never requests write,
   at any API), `PermissionHelper.kt:221-274` (four-way split, does request write on M-P) and
   `WelcomeActivity.kt:657-682` (three-way split, never surfaces all-files access on 30-32).
3. **Dead weight.** `ContextualRationaleRepository` + impl are Hilt-wired (`di/PermissionModule.kt:21-23`) with no
   consumer; `PermissionHelper`'s three message getters have no callers; string keys `manage_media_title`,
   `permission_internet_*`, `permission_storage_title`, `perm_battery_optim_*`, and the `PermissionGroup.VR`
   remnants left by S0241 are unreferenced.
4. **Dead model field.** `PermissionEntry.iconRes` is always `0` and `item_permission_entry.xml` has no icon view.

## Test coverage

One file: `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
(92 lines). Nothing covers the two screens, the row adapter, the status use case, the helper objects, or any of
the scattered call sites.
