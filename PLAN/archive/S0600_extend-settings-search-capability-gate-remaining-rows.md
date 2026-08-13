**Status:** Archived

# S0600 - Extend settings-search capability gate to the remaining audited capability-gated rows

## Goal

Расширить capability-aware фильтр поиска по настройкам (механизм S0599) на оставшиеся строки, которые скрыты в рантайме отсутствующей способностью, но живут в всегда-доступных секциях, поэтому не подавляются ни секционным `SettingsSearchAvailability`, ни контейнерным гейтом S0599. Каждая такая строка - мёртвый результат поиска на флейворах без способности: поиск находит её, тап ведёт на вкладку, где строка `GONE`. Решение - добавить в `SettingsSearchCapabilityGate` подавление по ключу строки, зеркалящее рантайм-предикат её фрагмента, через те же src/main-безопасные источники истины (`MediaCapabilities`, `CapabilityAvailability`).

<!-- auto-approved by /spec-all - 2026-06-21 -->

## 0. Capture (raw)

Discovered during S0599 research. S0599 delivers the gate mechanism (`SettingsSearchCapabilityGate` injected into `SettingsSearchRegistry`) and applies it to the screen-capture rows via two empty-default `@Multibinds` DI sets. The audit found the same dead-search-result class for more rows gated through different authorities, deferred here because each needs its own wiring and per-flavor verification.

## 1. Problem

These rows live in always-available sections (`general`/`playback`/`destinations`/`other`), so the media-only `SettingsSearchAvailability` cannot suppress them, and the S0599 gate only knows the two screen-capture DI sets. Each row is hidden at runtime by a typed/compiled capability, so search returns dead results on flavors lacking that capability.

## 2. Findings (confirmed)

Research artifact: `PLAN/S0600_extend-settings-search-capability-gate-remaining-rows/research/01__indexed-rows-and-predicates.md`.

In scope - indexed rows that are genuinely dead in an always-available section, with the runtime predicate each must mirror:

- Mic recording (`fragment_settings_destinations`, section `destinations`): `rowMicRecordingEnabled`, `rowMicRecordingAskFilename`, `btnSelectMicRecordingDest`; gate `OperationsCaptureManager.kt:92-96`; authority `MediaCapabilities.supportsMicRecording`; hidden on lite, photos.
- Background audio (`fragment_settings_playback`, section `playback`): `rowEnablePersistentAudioPlayback`, `rowShowNowPlayingPanel`, `headerBackgroundAudio`; gate `PlaybackSettingsFragment.kt:465-468`; authority `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK`; hidden on lite, photos.
- Cloud source (`fragment_settings_general`, section `general`): `rowSourceCloud`; gate `GeneralSettingsViewSetupHelper.kt:210-211`; authority `MediaCapabilities.supportsCloud` (`isCloudGroupSupported()` is its alias); hidden on lite.
- OCR/translation (`fragment_settings_other` + `fragment_settings_destinations`, sections `other`/`destinations`): `rowEnableTranslation`, `rowEnableOcr`, `rowTranslationLensStyle`, `rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly`; gates `OtherMediaSettingsFragment.kt:108-123` and `OperationsSettingsFragment.kt:526-540`; compile/flavor authority `CapabilityAvailability.isTranslationAvailable()`; hidden on lite, photos.
- Downloadable extensions (`fragment_settings_general`, section `general`): `btnDownloadableExtensions`; gate `GeneralSettingsFragment.kt:258-260`; authority `CapabilityAvailability.isExtensionsScreenAvailable()`; hidden on lite, photos.

Out of scope - confirmed never a dead result:

- EPUB `rowSupportEpub` lives in `fragment_settings_documents` (a media section already gated by `SettingsSearchAvailability`); no flavor has `SUPPORT_DOCUMENTS=true` with `ENABLE_EPUB=false`, so the section gate fully covers it.
- Scheduled ops `rowEnableScheduledOps` is gated by `BuildConfig.ENABLE_SCHEDULED_OPERATIONS`, declared only in `buildTypes` (debug+release), always `true`, with no per-flavor override, so the row is never hidden.

Design boundaries:

- The `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` flag is declared in every flavor block, so reading it through the existing `CapabilityAvailability` facade compiles for all variants - the same pattern `isStreamsAvailable()` already uses for `BuildConfig.SUPPORT_STREAMS`. That facade method is the Rule-15-safe wrapper §1 needs; no per-flavor multibind is required.
- Default-player keys (`rowPrimaryMediaPlayer`, `rowAcceptSharedFiles`, `btnSettingsDefaultPlayer*`) stay in `SettingsSearchRegistry.isCapabilityAvailable()` where S0602 placed and documented them; S0600 does not move or duplicate them.
- The camera-OCR device axis (`DeviceCapabilities.isOcrSupported`) is the device-feature axis owned by S0601's `SettingsSearchDeviceFeatureGate`; S0600 mirrors only the compile/flavor axis (`isTranslationAvailable()`).

## 3. Resolution

Extend `SettingsSearchCapabilityGate` (the dedicated per-row capability filter the registry already calls) with a per-key capability check, ANDed with its existing container-membership check. Each key maps to a predicate that mirrors its fragment's runtime gate, read through the same src/main-safe source of truth. No registry change is required - the registry already invokes the gate.

### Phase 01 - Rule-15-safe background-audio capability wrapper

- In `core/capability/CapabilityAvailability.kt`, add `fun isPersistentAudioPlaybackAvailable(): Boolean = BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK`, mirroring the adjacent `isStreamsAvailable()`.
- KDoc: single source of truth for "is background audio compiled into this build"; mirrors the runtime gate in `PlaybackSettingsFragment`.
- Verification: `.\a.ps1 fk` exits 0.

### Phase 02 - Per-key capability rules in the gate

- Inject `MediaCapabilities` and `CapabilityAvailability` into `SettingsSearchCapabilityGate` (both `@Singleton`-injectable; no new Hilt module).
- Add `private fun isKeyCapabilityAvailable(key: String): Boolean` as a `when (key)` and AND it into `isAvailable(entry)` next to the container check.
- Key groups and predicates (each mirrors the confirmed runtime gate):
  - `rowMicRecordingEnabled`, `rowMicRecordingAskFilename`, `btnSelectMicRecordingDest` -> `mediaCapabilities.supportsMicRecording`.
  - `rowEnablePersistentAudioPlayback`, `rowShowNowPlayingPanel`, `headerBackgroundAudio` -> `capabilityAvailability.isPersistentAudioPlaybackAvailable()`.
  - `rowSourceCloud` -> `mediaCapabilities.supportsCloud`.
  - `rowEnableTranslation`, `rowEnableOcr`, `rowTranslationLensStyle`, `rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly` -> `capabilityAvailability.isTranslationAvailable()`.
  - `btnDownloadableExtensions` -> `capabilityAvailability.isExtensionsScreenAvailable()`.
  - `else -> true`.
- Update the class KDoc: the gate now covers both container-membership gating (S0599) and per-key capability gating (S0600); default-player capability keys remain in `SettingsSearchRegistry` (S0602); the camera-OCR device axis is left to the S0601 device gate.
- Do not add `rowSupportEpub` or `rowEnableScheduledOps` (confirmed non-dead in §2).
- Verification: `.\a.ps1 fk` exits 0; `SettingsSearchRegistry` unchanged.

### Phase 03 - Unit tests + build

- Extend `SettingsSearchCapabilityGateTest`: update the `gate(..)` factory to also supply a `MediaCapabilities` instance and a relaxed `CapabilityAvailability` mock with stubbable predicates.
- Add cases: each in-scope key is suppressed when its capability is absent and kept when present; a default-player key (e.g. `rowPrimaryMediaPlayer`) is NOT gated here (returns available - the registry owns it); the existing container cases still pass.
- Verification: `SettingsSearchCapabilityGateTest` passes (per-class XML report); `.\a.ps1 dq` PASS.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0599 (the gate mechanism this extends), S0602 (default-player keys kept in the registry), S0601 (device-feature axis for camera OCR), S0598 (indexed the dropdown/selection rows)
- **Flavor scope:** suppression is driven by `MediaCapabilities`/`CapabilityAvailability`, so it activates per-flavor automatically (mic/background-audio/translation/extensions hidden on lite+photos; cloud hidden on lite); no per-flavor source-set code added
- **User-facing strings:** none; no app strings, no layout change, no setting added/moved/renamed - settings manifest and `SETTINGS_REFERENCE*.md` unaffected (Rule 22 not triggered); the search index suppression is not serialized by `SettingsManifestExportTest` (doc-sync gate stays green)

## 6. Notes / deferred

- Camera-OCR rows still surface on a translation-capable flavor running on a device that fails `DeviceCapabilities.isOcrSupported()`; that device axis is S0601's `SettingsSearchDeviceFeatureGate`, not S0600.
- Two adjacent findings parked as separate tickets: `AppCompatSpinner` not recognized by `LayoutSettingsSearchSource.kindFromTag` (four OCR spinners silently non-indexed); `PlaybackSettingsFragment` reads `BuildConfig` directly in `src/main`.

## Last Audit

Date: 2026-06-21 (inline audit by /spec-all).

- Phase 01 done: `CapabilityAvailability.isPersistentAudioPlaybackAvailable()` added, mirroring `isStreamsAvailable()`; the flag is declared in every flavor block so it compiles variant-wide.
- Phase 02 done: `SettingsSearchCapabilityGate` injects `MediaCapabilities` + `CapabilityAvailability`; `isKeyCapabilityAvailable(key)` gates exactly the in-scope keys with the documented predicates and is ANDed into `isAvailable`. `SettingsSearchRegistry` untouched (it already calls the gate); default-player keys left in the registry per S0602. `rowSupportEpub` / `rowEnableScheduledOps` excluded as confirmed non-dead.
- Phase 03 done: `SettingsSearchCapabilityGateTest` extended; 17 tests, 0 failures (per-class XML report). `assembleStandardDebug` PASS. Neuroslop + ticket-log gates clean (delta 0).
- Keys verified against the actual layout `android:id`s in the research artifact.
- Deferred (non-blocking): on-device confirmation that search hides these rows on the `lite` and `photos` editions. The standard emulator cannot exercise suppression (standard ships every capability), and the logic is deterministic and exhaustively unit-tested per key in both states. Run during a `lite`/`photos` build sweep.
