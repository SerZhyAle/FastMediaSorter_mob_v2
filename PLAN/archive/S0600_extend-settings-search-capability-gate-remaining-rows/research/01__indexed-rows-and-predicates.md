# Research artifact - S0600 indexed capability-gated rows + runtime predicates

Date: 2026-06-21. Read-only investigation confirming, per candidate row, whether it is actually indexed by the settings-search pipeline, its sectionId, the exact runtime gating predicate it mirrors, and the per-flavor visibility. Source: `android-solution-researcher` sweep over `LayoutSettingsSearchSource`, `SettingsSearchTabMapping`, `SettingsSearchAvailability`, the owning settings fragments/managers, and `app_v2/build.gradle.kts`.

## Indexing rule (recap)

A row is indexed iff: it lives in one of the 9 layouts in `SettingsSearchLayoutCatalog`; its XML tag's simple name is a recognized kind (`SettingsToggleRow`, `CollapsibleSectionHeader`, `Spinner`/`AutoCompleteTextView`/`MaterialAutoCompleteTextView`, `EditText`/`TextInputEditText`, `SettingsInputRow`, `SettingsDropdownRow`, `SettingsSelectionRow`, `MaterialButton`/`Button`/`ImageButton`); it has an `android:id`; it resolves a title. The search `key` = the `android:id` resource-entry name.

`SettingsSearchAvailability` already suppresses entries whose sectionId is a media section (`images`/`video`/`audio`/`documents`) absent on the flavor. Sections `general`/`playback`/`destinations`/`other`/`streams`/`media` are always available, so rows hidden at runtime inside them are dead search results with no existing mitigation.

## In-scope rows (confirmed indexed, dead in always-available sections)

- Mic recording - layout `fragment_settings_destinations`, section `destinations`. Indexed: `rowMicRecordingEnabled`, `rowMicRecordingAskFilename` (toggles), `btnSelectMicRecordingDest` (button inside `layoutMicRecordingDestSelector`). Not indexed: `layoutMicRecordingDestSelector` (plain `LinearLayout`). Runtime gate `OperationsCaptureManager.kt:92-96` hides all three when `!mediaCapabilities.supportsMicRecording`. Authority: `MediaCapabilities.supportsMicRecording`.
- Background audio - layout `fragment_settings_playback`, section `playback`. Indexed: `rowEnablePersistentAudioPlayback`, `rowShowNowPlayingPanel` (toggles), `headerBackgroundAudio` (`CollapsibleSectionHeader`). Runtime gate `PlaybackSettingsFragment.kt:465-468` hides the whole `cardBackgroundAudio` (header + both rows) when `!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK`. Authority: `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK`.
- Cloud source - layout `fragment_settings_general`, section `general`. Indexed: `rowSourceCloud` (toggle). Sibling rows `rowSourceSmb`/`rowSourceFtp` are not visibility-gated and there is no separately-indexed cloud sub-header. Runtime gate `GeneralSettingsViewSetupHelper.kt:210-211` sets `rowSourceCloud` GONE when `!remoteSourceAvailabilityGate.isCloudGroupSupported()`. Authority: `RemoteSourceAvailabilityGate.isCloudGroupSupported()` = `MediaCapabilities.supportsCloud` (`RemoteSourceAvailabilityGate.kt:72`).
- OCR/translation in `other` - layout `fragment_settings_other`, section `other`. Indexed: `rowEnableTranslation`, `rowEnableOcr`, `rowTranslationLensStyle` (toggles). Runtime gate `OtherMediaSettingsFragment.kt:108-123` hides all three when `!capabilityAvailability.isTranslationAvailable()`. Authority: `CapabilityAvailability.isTranslationAvailable()` (= `CAP_TRANSLATION` in the `@CompiledCapabilities` set).
- Camera OCR in `destinations` - layout `fragment_settings_destinations`, section `destinations`. Indexed: `rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly` (toggles; `layoutCameraOcrOnly` wrapper not indexed). Runtime gate `OperationsSettingsFragment.kt:526-540` hides both when `!(isTranslationAvailable() && DeviceCapabilities.isOcrSupported(context))`. Compile/flavor axis: `CapabilityAvailability.isTranslationAvailable()`. Device axis: `DeviceCapabilities.isOcrSupported` - owned by the S0601 device-feature gate, not S0600.
- Downloadable extensions - layout `fragment_settings_general`, section `general`. Indexed: `btnDownloadableExtensions` (`MaterialButton`, `android:text=@string/ext_manager_title`). Runtime gate `GeneralSettingsFragment.kt:258-260` sets it GONE when `!capabilityAvailability.isExtensionsScreenAvailable()` (= `isOcrCompiledIn() || isTranslationAvailable() || isStreamsAvailable()`). Authority: `CapabilityAvailability.isExtensionsScreenAvailable()`.

## Out-of-scope rows (confirmed never a dead result)

- EPUB `rowSupportEpub` - layout `fragment_settings_documents`, section `documents` (a media section). Already suppressed by `SettingsSearchAvailability` whenever documents is unsupported. No flavor has `SUPPORT_DOCUMENTS=true` with `ENABLE_EPUB=false`; both move together (true on standard/noLegal/legacy/vr, false on lite/photos). So `rowSupportEpub` is never a dead result - the section gate fully covers it.
- Scheduled ops `rowEnableScheduledOps` - layout `fragment_settings_destinations`, section `destinations`. `ENABLE_SCHEDULED_OPERATIONS` is declared only in `buildTypes` (debug+release), both `true`, no per-flavor override (`build.gradle.kts:713,726`). The row is always visible on every flavor and build type. Indexed, but never hidden, so never a dead result.

## Per-flavor hidden matrix (in-scope authorities)

- `supportsMicRecording` false on lite, photos.
- `ENABLE_PERSISTENT_AUDIO_PLAYBACK` false on lite, photos.
- `supportsCloud` false on lite only (photos has cloud).
- `isTranslationAvailable()` false on lite, photos (no `translationEnabled` source set; no `CAP_TRANSLATION`).
- `isExtensionsScreenAvailable()` false on lite, photos (no OCR, no translation, `SUPPORT_STREAMS=false`).

## Authority injectability

- `MediaCapabilities` - data class bound per flavor; already injected into `SettingsSearchRegistry`; injectable anywhere.
- `CapabilityAvailability` - `@Singleton @Inject`; injectable. Already reads a feature-flag `BuildConfig` field in `src/main` (`isStreamsAvailable() = BuildConfig.SUPPORT_STREAMS`), establishing the facade as the Rule-15-safe wrapper for compile-time feature flags.
- `RemoteSourceAvailabilityGate` - not `@Inject`; bound via `RemoteSourceAvailabilityModule` (`@Provides @Singleton`). Injectable into a `@Singleton` class, but overkill: `isCloudGroupSupported()` is a direct alias of `mediaCapabilities.supportsCloud`. Use `MediaCapabilities.supportsCloud` directly.

## /spec-draft candidates (out of scope, surfaced for the caller)

- `AppCompatSpinner` is not recognized by `LayoutSettingsSearchSource.kindFromTag` (it matches simple name `Spinner` only), so the four OCR spinners (`spinnerOcrEngineType`, `spinnerPaddleOcrModel`, `spinnerOcrFontSize`, `spinnerOcrFontFamily`) and the two `TextView`-based translation language pickers are silently non-indexed and undiscoverable in search.
- `PlaybackSettingsFragment` reads `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` directly in `src/main` rather than through a `MediaCapabilities`/`CapabilityAvailability` facade (pervasive fragment pattern, low individual value).
