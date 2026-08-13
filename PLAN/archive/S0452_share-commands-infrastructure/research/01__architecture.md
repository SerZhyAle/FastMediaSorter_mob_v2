# Research 01 - Share-commands architecture (S0452)

**Date:** 2026-06-16
**Method:** read-only codebase research (catalog + grep + read), module app_v2.

## Storage model (decided)

- Owner decision 2026-06-16: per-command visibility flags are **app-global DataStore** fields on `AppSettings` (`domain/model/AppSettings.kt`), mirroring `AppSettings.enableGoogleLens` (`domain/model/AppSettings.kt:99`).
- **No Room schema change.** "Profile" in this app = `DeviceProfile` (single active `DeviceProfileType`, one row in `DeviceProfileEntity`), not a per-user multi-row store. "По умолчанию для всех профилей" = per-`DeviceProfileType` preset default, applied via `ApplyProfilePresetUseCase` / `DeviceProfilePresetApplier` from CSV (`data/preset/DeviceProfilePresetCsvDataSource.kt`).
- "Migration" for a DataStore flag = its default value for existing installs; no `MIGRATION_NN` class needed.

## Settings UI - Player tab

- "Воспроизведение" tab renamed to "Плеер"/"Player" by S0442. Fragment: `ui/settings/fragments/PlaybackSettingsFragment.kt`, collapsible groups built in `setupExpandableSections()` (currently 4 sections; `PREFS_NAME = "playback_sections_state"`).
- Toggle pattern (app-global): `SettingsToggleRow` `setOnCheckedChangeListener` -> `viewModel.updateSettings(current.copy(field = isChecked))` -> `SettingsRepositoryImpl.updateSettings()` -> DataStore write. VM observes `SettingsViewModel.settings: StateFlow<AppSettings>` via `collectOnLifecycle`.
- New group "Команды отправить файл в.." = a 5th `ExpandableSection`, rendering toggles from the share-target registry; needs a `KEY_SEND_COMMANDS_EXPANDED` constant.

## Share / send command surfaces (audit)

- `core/share/SystemShareInvoker.kt` - centralised `ACTION_SEND`/`ACTION_SEND_MULTIPLE` launcher. `core/share/SharePayload.kt` - sealed `Text`/`Image`.
- `core/share/TelegramShareTargets.kt` - stateless object: Telegram package list + `firstInstalledPackage(PackageManager)`.
- `util/GoogleKeepAvailabilityChecker.kt` - instance-cached PackageManager probe for Keep packages.
- `ui/player/helpers/GoogleLensShare.kt` - host-agnostic Lens `ACTION_SEND` object.
- `ui/player/helpers/PlayerShareManager.kt` - player: open-external, Lens, Telegram, Office share.
- `ui/player/FileOperationsHandler.kt` + `ui/player/helpers/StandaloneFileOperationsHandler.kt` - system share + network-file staging (duplicated `share_temp/` logic - /spec-draft candidate).
- `ui/browse/managers/BrowseShareOperationsHelper.kt` - browse multi-select share + Telegram.
- `ui/browse/helpers/BrowseFileOverflowMenuManager.kt` - single-file overflow menu; Telegram gate inline at construction.
- `ui/player/helpers/CommandPanelLayoutPlanner.kt` - `PlayerCommand` enum (share targets as entries: priority, `menuItemId`, `barCapable`, `titleResId`, `iconResId`) + `buildActiveCommands()`.
- `ui/player/CommandPanelAvailabilityUpdater.kt` - per-state `isTelegramInstalled()` / `isKeepInstalled()`.
- `ui/player/CommandPanelController.kt` - `showOverflowMenu()` builds PopupMenu, dispatches item ids to `CommandPanelCallback.onXClicked()`; impl `ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`.

## Registry gap (decided abstraction)

- No unified share-target registry today: availability checked ad hoc in 3 places (`isTelegramInstalled`, `isKeepInstalled`, `firstInstalledPackage`) via 3 different checkers.
- `PlayerCommand` enum doubles as a target catalogue but is coupled to layout planning - not a clean domain abstraction.
- ADR (this spec): extract a domain-level `ShareTarget` registry + a single `ShareTargetAvailabilityResolver` (package-installed / has-Google / has-internet). Settings group, command-panel gate, and browse overflow all consume the registry. `PlayerCommand` references registry entries rather than re-declaring availability.

## Availability predicates

- Package-installed: `TelegramShareTargets.firstInstalledPackage()` and `GoogleKeepAvailabilityChecker` exist; unify into resolver. `getPackageInfo(pkg, 0)` requires `<queries>` declaration on API 30+ - Telegram likely already declared (S0303); Email/Gmail packages NOT yet declared - foundation or target ticket must add `<queries>`.
- "Has Google": derive from primary Google account presence (`identity/PrimaryGoogleAccountState`, test `identity/PrimaryGoogleAccountStateTest.kt`).
- "Has internet": no profile flag; derive from source/cloud config or connectivity. Resolver exposes a predicate; exact derivation a tactical detail.

## Decision points resolved

1. Storage: app-global DataStore (owner-decided). No Room bump.
2. Abstraction: clean `ShareTarget` registry + `ShareTargetAvailabilityResolver` (best-practice; the purpose of the foundation).
3. Settings host: 5th collapsible section in `PlaybackSettingsFragment`.

## Deferred to target tickets

- Email chooser-vs-Gmail-shortcut semantics -> S0444.
- Messenger send-with-recipient implementation -> S0446.
- Concrete per-target flags + preset defaults + `<queries>` additions -> respective target tickets (foundation provides the registration seam).

## /spec-draft candidates (not acted on)

- `GoogleKeepAvailabilityChecker` stale instance cache after runtime install/uninstall (`util/GoogleKeepAvailabilityChecker.kt:26`).
- `AppSettings.enableGoogleLens` appears to have no read path in `CommandPanelAvailabilityUpdater` - Lens commands may show regardless of the toggle.
- `BrowseShareOperationsHelper` duplicates network-file `share_temp/` staging from `FileOperationsHandler`.
