# Tactical Index — camera-capture-command

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Status:** ✅ Done

---

## Phase Overview

| # | Phase | Status | Blocks |
| - | ----- | ------ | ------ |
| 01 | [Data Model](PHASE_01__data-model.md) | ✅ Done | 02, 03, 04, 05 |
| 02 | [Browse Button & Layout](PHASE_02__browse-button-layout.md) | ✅ Done | 03, 04 |
| 03 | [BrowseCameraCaptureManager](PHASE_03__browse-camera-capture-manager.md) | ✅ Done | 04 |
| 04 | [BrowseActivity Wiring](PHASE_04__browse-activity-wiring.md) | ✅ Done | 05, 06 |
| 05 | [Settings UI](PHASE_05__settings-ui.md) | ✅ Done | 06 |
| 06 | [Strings & Housekeeping](PHASE_06__strings-housekeeping.md) | ✅ Done | — |

---

## Key Paths

- `AppSettings`: `app_v2/.../domain/model/AppSettings.kt`
- `SettingsRepositoryImpl`: `app_v2/.../data/repository/SettingsRepositoryImpl.kt`
- `BrowseActivity`: `app_v2/.../ui/browse/BrowseActivity.kt`
- `BrowseButtonSetupHelper`: `app_v2/.../ui/browse/managers/BrowseButtonSetupHelper.kt`
- `BrowseStateUiUpdater`: `app_v2/.../ui/browse/managers/BrowseStateUiUpdater.kt`
- `BrowseObserverManager`: `app_v2/.../ui/browse/managers/BrowseObserverManager.kt`
- `BrowseLauncherManager`: `app_v2/.../ui/browse/managers/BrowseLauncherManager.kt`
- Browse layout: `app_v2/src/main/res/layout/activity_browse.xml`
- Settings layout: `app_v2/src/main/res/layout/fragment_settings_playback.xml`
- `PlaybackSettingsFragment`: `app_v2/.../ui/settings/fragments/PlaybackSettingsFragment.kt`
- `SettingsSearchIndex`: `app_v2/.../ui/settings/SettingsSearchIndex.kt`
- `BackupData`: `app_v2/.../domain/usecase/BackupData.kt`
- `BackupMapper`: `app_v2/.../domain/usecase/BackupMapper.kt`
- `ExportSettingsUseCase`: `app_v2/.../domain/usecase/ExportSettingsUseCase.kt`
- `ImportSettingsUseCase`: `app_v2/.../domain/usecase/ImportSettingsUseCase.kt`
- Strings: `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
- `VirtualPathUtils`: `app_v2/.../util/VirtualPathUtils.kt`
- `LocalMediaScanner` (virtual path constants): `app_v2/.../data/local/LocalMediaScanner.kt`
