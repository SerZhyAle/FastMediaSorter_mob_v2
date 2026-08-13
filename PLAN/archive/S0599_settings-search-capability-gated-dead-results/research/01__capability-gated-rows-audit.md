# Research artifact - S0599 capability-gated settings-search rows audit

Date: 2026-06-21. Read-only investigation supporting the strategic spec. Source: `android-solution-researcher` sweep over the settings-search subsystem + every layout in `SettingsSearchLayoutCatalog` and its setup managers.

## 1. Pipeline + the gap

`SettingsSearchRegistry.entries` (`ui/settings/search/SettingsSearchRegistry.kt:30`) filters only by `SettingsSearchAvailability.isAvailable(sectionId)`. `SettingsSearchAvailability` gates the 4 media sections (`images/video/audio/documents`) against the multibound `@SupportedMediaSection Set<String>`; every other section (`general/playback/destinations/media/other/streams`) is unconditionally available. Rows hidden at runtime by an absent DI/flavor capability inside an always-available section therefore stay in the search index = dead results.

`SettingsSearchIndex` carries `viewId: Int` (`ui/settings/SettingsSearchIndex.kt:25`), so the registry can suppress by `viewId` at filter time.

## 2. Screen-capture gating (S0599 scope) - confirmed

Interfaces (both `src/main`, package `com.sza.fastmediasorter.core.screencapture`):
- `ScreenGestureOverlayController` - `@Multibinds Set<..>` in `src/main` `di/ScreenGestureOverlayModule.kt`; impl `ScreenGestureOverlayControllerImpl` only in `src/noLegal`.
- `MenuScreenshotLauncher` - `@Multibinds Set<..>` in `src/main` `di/MenuScreenshotLauncherModule.kt`; impl `MenuScreenshotLauncherImpl` only in `src/screenCapture` (mounted into noLegal).

Both sets are injectable as empty in `src/main` - no `BuildConfig` read needed (Rule 15 OK).

Container membership (the gating unit, from the two managers):
- `groupScreenGestures` hidden when `screenGestureControllers.firstOrNull() == null` (`OperationsGesturesManager.kt:37-41`). Indexed rows inside: `rowGestureOverlayEnabled`, `rowCopyScreenshotToClipboard`, `rowScreenshotDestination`, `rowScreenshotGestureActionUp`, `rowScreenshotGestureActionRight`, `rowScreenshotGestureActionDown` (+ `btnOpenAccessibilitySettings` if it carries `android:text`).
- `groupMenuScreenshot` hidden when `launchers.firstOrNull() == null` (`OperationsCaptureManager.kt:182-188`). Indexed rows inside: `btnTakeScreenshotNow`.

Note: the spec §0 hand-listed only 4 gesture rows + the button; the gesture card actually holds 2 more indexed toggles (`rowGestureOverlayEnabled`, `rowCopyScreenshotToClipboard`). Scope S0599 by container membership, not the §0 list. F2 reads `fragment_settings_destinations.xml` for the exact indexed `R.id` set.

## 3. Other capability/flavor-gated indexed rows (-> S0600)

Different authorities, out of S0599 scope:
- mic recording - `MediaCapabilities.supportsMicRecording` (`OperationsCaptureManager.kt:93`).
- background audio (`rowEnablePersistentAudioPlayback`, `rowShowNowPlayingPanel`) - `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` (`PlaybackSettingsFragment.kt:465`) - Rule-15 unreadable in src/main.
- cloud source (`rowSourceCloud`) - `remoteSourceAvailabilityGate.isCloudGroupSupported()` (`GeneralSettingsViewSetupHelper.kt:211`).
- OCR/translation rows - `CapabilityAvailability.isTranslationAvailable()` / OCR (`OtherMediaSettingsFragment.kt:108`, `OperationsSettingsFragment.kt:527`).
- EPUB (`rowSupportEpub`) - `caps.supportsEpub` (`DocumentsSettingsFragment.kt:52`).
- default-player buttons - `supportsDefaultPlayer` (`DefaultPlayerSettingsManager.kt:46`).
- extensions (`btnDownloadableExtensions`) - `CapabilityAvailability.isExtensionsScreenAvailable()` (`GeneralSettingsFragment.kt:258`).
- scheduled ops (`rowEnableScheduledOps`) - `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` (`OperationsSettingsFragment.kt:139`) - Rule-15 unreadable; index status unconfirmed.

## 4. Device-feature-gated rows (-> S0601)

Runtime device checks, not flavor/DI capabilities; need `Build`/`PackageManager` predicate:
- `rowEnablePip` / `layoutPip` - API 31+ (`PlaybackSettingsFragment.kt:247`).
- `rowFollowSystemRotationPlayer` - `FEATURE_SENSOR_ACCELEROMETER` (`PlaybackSettingsFragment.kt:392`).

## 5. Behavioral bug (-> S0602)

`rowPrimaryMediaPlayer` / `rowAcceptSharedFiles` stay visible on `!supportsDefaultPlayer` flavors but their listeners are never registered (`OperationsSettingsFragment.kt:343`): visible toggle, no persistence.

## 6. Doc-sync scope

Of all gated rows, only the 5 screen-capture rows are also hidden in the standard flavor (standard ships neither the gesture controller nor the screenshot launcher). All other categories are present in standard, so the standard-flavor `settings-manifest.json` is correct for them. S0599 changes no setting presence/behavior/position/naming in any layout - it only suppresses runtime-hidden rows from the in-app search index - so Rule 22 (settings doc-sync) is not triggered. Making the static manifest generator capability-aware (to drop the screen-capture rows from the standard manifest) is a separate, larger change, deliberately out of scope.

## 7. Test coverage

No unit tests exist for `SettingsSearchRegistry` / `SettingsSearchAvailability` / `LayoutSettingsSearchSource`. A small unit test for the new gate is cheap and worth adding in S0599.

## 8. Reusable patterns

- Pattern A (S0599 uses): `@Multibinds` empty default in `src/main` + `@IntoSet` impl in a flavor source set - inject the set, branch on `isEmpty()`.
- Pattern B: `@SupportedMediaSection` qualifier + per-flavor `@IntoSet` String modules (the existing media-section gating).
- Pattern C: `CapabilityAvailability` `@CompiledCapabilities` multibound set - candidate authority for the `BuildConfig`-gated S0600 rows.
- Pattern D: `MediaCapabilities` typed data class injected widely - candidate authority for the typed-boolean S0600 rows.
