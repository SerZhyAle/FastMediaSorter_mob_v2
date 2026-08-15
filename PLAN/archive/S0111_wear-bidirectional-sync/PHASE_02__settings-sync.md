# Phase 02 — Settings Sync (Phone → Watch)

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 9 / 9
**Started:** 2026-05-07
**Completed:** 2026-05-08

---

## Objective

Phone can push Wear settings (enabled media types, slideshow configuration) to the watch in a single action; watch receives, deserializes, and applies settings via the existing preferences repository. The `WearSyncSettingsFragment` gains a "Watch Settings" section with toggles and a push button.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PushWearSettingsUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt` | New | ≤ 30 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt` | New | ≤ 60 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt` | Modified | ≤ 160 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsViewModel.kt` | Modified | ≤ 150 |
| `wear/src/main/res/values/strings.xml` | Modified | — |
| `wear/src/main/res/values-ru/strings.xml` | Modified | — |
| `wear/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 2.1 — Create `WearSettingsPayload` on phone side

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the listed file. Declare `data class WearSettingsPayload(val audioEnabled: Boolean, val videoEnabled: Boolean, val imagesEnabled: Boolean, val slideshowEnabled: Boolean, val slideshowIntervalSeconds: Int, val slideshowWaitForFinish: Boolean, val downloadAlbumArt: Boolean)` in package `com.sza.fastmediasorter.domain.model`. Add a KDoc note that this mirrors the fields in `WearPreferencesRepository`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt` exists.
- `Grep` — `data class WearSettingsPayload` matches.
- `Grep` — `val slideshowIntervalSeconds: Int` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 3/3 PASS. Files: app_v2/.../domain/model/WearSettingsPayload.kt (+14 LOC). Dev log recorded.

---

### Step 2.2 — Create `WearSettingsPayload` mirror on watch side

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt`
**Depends on:** — start of phase (parallel with 2.1)

**Prompt for developer:**

> Create the listed file. Declare an identical `data class WearSettingsPayload` in package `com.sza.fastmediasorter.wear.domain.model` with the same seven fields as the phone-side copy in Step 2.1.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt` exists.
- `Grep` — `data class WearSettingsPayload` matches.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 2/2 PASS. Files: wear/.../domain/model/WearSettingsPayload.kt (+14 LOC). Dev log recorded.

---

### Step 2.3 — Create `PushWearSettingsUseCase` on phone

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PushWearSettingsUseCase.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Create the listed file. Declare `class PushWearSettingsUseCase @Inject constructor(private val wearableRepository: WearableDataLayerRepository, private val gson: Gson)`. Implement `suspend operator fun invoke(settings: WearSettingsPayload): Result<Unit>` using `runCatching`:
> 1. Check `wearableRepository.getConnectedNodes()`; if empty, error("No watch connected").
> 2. Serialize `settings` to JSON bytes.
> 3. Build `WearEventEnvelope(eventType = WearDataLayerPaths.EVENT_SETTINGS, sentAt = System.currentTimeMillis(), data = settingsBytes)`.
> 4. Call `wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.SETTINGS_PUSH, envelope)`.
> 5. Add `Timber.d("S0111: PushWearSettingsUseCase — settings pushed to watch")` at method entry.
>
> Gson is not yet provided in `RepositoryModule`; inject it via `@Inject` — Hilt will resolve it from the existing `@Provides fun provideGson` in the DI module (verify this binding exists before coding; if absent, add it to `RepositoryModule`).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PushWearSettingsUseCase.kt` exists.
- `Grep` — `class PushWearSettingsUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `WearDataLayerPaths.SETTINGS_PUSH` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 5/5 PASS. Files: app_v2/.../domain/usecase/PushWearSettingsUseCase.kt (+29 LOC); core/di/RepositoryModule.kt (+provideGson). Dev log recorded.

---

### Step 2.4 — Create `ApplyWearSettingsUseCase` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Create the listed file. Declare `class ApplyWearSettingsUseCase @Inject constructor(private val preferencesRepository: WearPreferencesRepository)`. Implement `suspend operator fun invoke(payload: WearSettingsPayload)`: call each setter on `preferencesRepository` (`setAudioEnabled`, `setVideoEnabled`, `setImagesEnabled`, `setSlideshowEnabled`, `setSlideshowIntervalSeconds`, `setSlideshowWaitForFinish`, `setDownloadAlbumArt`) with the corresponding field from `payload`.
>
> Add `Timber.d("S0111: ApplyWearSettingsUseCase — applying received settings")` at method entry.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt` exists.
- `Grep` — `class ApplyWearSettingsUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `setAudioEnabled` present.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../domain/usecase/ApplyWearSettingsUseCase.kt (+21 LOC). Dev log recorded.

---

### Step 2.5 — Wire settings handler in `WatchWearListenerService`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WatchWearListenerService.kt`
**Depends on:** Step 2.4

**Prompt for developer:**

> In `WatchWearListenerService`, inject `ApplyWearSettingsUseCase` and `Gson`. Replace the stub `handleSettingsPush(payloadBytes)` with a real implementation: deserialize `payloadBytes` → `WearEventEnvelope` → inner `data` bytes → `WearSettingsPayload` via Gson → call `applyWearSettingsUseCase(payload)` in `serviceScope.launch`.
>
> On deserialization failure log with `Timber.e` and emit to a new `WatchSyncEvents.settingsErrorFlow: MutableSharedFlow<String>`.

**Verification:**

- `Grep` — `applyWearSettingsUseCase` present in `WatchWearListenerService.kt`.
- `Grep` — `WearSettingsPayload` imported in that file.
- `Grep` — `settingsErrorFlow` present in `WatchSyncEvents` object (same file).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../data/wear/WatchWearListenerService.kt (+13 LOC). Dev log recorded.

---

### Step 2.6 — Add `pushSettings()` to `WearSyncViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt`
**Depends on:** Step 2.3

**Prompt for developer:**

> Inject `PushWearSettingsUseCase` into `WearSyncViewModel`. Add `fun pushSettings(settings: WearSettingsPayload)`: set state to `Sending`, launch coroutine, call `pushWearSettingsUseCase(settings)`, on success set state to `Success(0, 0)` with a distinct "settings pushed" marker (add a new `Success` subtype `SettingsPushed` to `WearSyncUiState` or reuse the existing `Success` with a `label: String` parameter — choose the simpler option that avoids breaking existing callers). On failure set state to `Error`.

**Verification:**

- `Grep` — `fun pushSettings` present in `WearSyncViewModel.kt`.
- `Grep` — `PushWearSettingsUseCase` injected (present in constructor or `@Inject`).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: app_v2/.../ui/settings/WearSyncViewModel.kt (+24 LOC). Dev log recorded.

---

### Step 2.7 — Add settings section to `WearSyncSettingsFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt`
**Depends on:** Step 2.6

**Prompt for developer:**

> In `WearSyncScreen` composable, below the existing "Push to Watch" button, add a collapsible "Watch Settings" section (use `var expanded by remember { mutableStateOf(false) }` with an expand/collapse `TextButton`). When expanded, show:
> - A `Switch` row for each of: Audio, Video, Images, Slideshow, Album Art.
> - A `Slider` (1–3600 seconds) for Slideshow Interval.
>
> Read current values from `viewModel.watchSettingsState: StateFlow<WearSettingsPayload?>` (add this StateFlow to the ViewModel — initialized to `null`; updated when user toggles).
> Include a "Push Watch Settings" `Button` at the bottom of the section.
>
> Use `stringResource` for all labels (`R.string.wear_settings_*` keys added in Step 2.8).

**Verification:**

- `Grep` — `wear_settings_audio` or equivalent string key reference present in `WearSyncSettingsFragment.kt`.
- `Grep` — `pushSettings` call present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: app_v2/.../ui/settings/fragments/WearSyncSettingsFragment.kt (+180 LOC). Dev log recorded.

---

### Step 2.8 — Add localized strings for settings section

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 2.7

**Prompt for developer:**

> Add the following keys to all three files (`values/`, `values-ru/`, `values-uk/`). Provide accurate translations in Russian and Ukrainian:
> - `wear_settings_section_title` — "Watch Settings" / "Настройки часов" / "Налаштування годинника"
> - `wear_settings_audio` — "Audio" / "Аудио" / "Аудіо"
> - `wear_settings_video` — "Video" / "Видео" / "Відео"
> - `wear_settings_images` — "Images" / "Изображения" / "Зображення"
> - `wear_settings_slideshow` — "Slideshow" / "Слайдшоу" / "Слайдшоу"
> - `wear_settings_album_art` — "Album Art" / "Обложки альбомов" / "Обкладинки альбомів"
> - `wear_settings_slideshow_interval` — "Slideshow interval (seconds)" / "Интервал слайдшоу (сек)" / "Інтервал слайдшоу (с)"
> - `wear_push_settings` — "Push Watch Settings" / "Отправить настройки часам" / "Надіслати налаштування годиннику"

**Verification:**

- `Grep` — `wear_settings_section_title` present in `values/strings.xml`.
- `Grep` — `wear_settings_section_title` present in `values-ru/strings.xml`.
- `Grep` — `wear_settings_section_title` present in `values-uk/strings.xml`.
- `Grep` — `wear_push_settings` present in all three files.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml (+8 keys each). Dev log recorded.

---

### Step 2.9 — Wire settings application in watch `SettingsViewModel`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 2.4

**Prompt for developer:**

> In `SettingsViewModel`, observe `WatchSyncEvents.settingsErrorFlow` and log errors with `Timber.e`. Add a no-argument `fun reloadSettings()` that re-calls `loadSettings()` — the watch UI can call this after a push to refresh its displayed state. Settings are already loaded from `WearPreferencesRepository` by `loadSettings()`; no additional wiring is needed since `ApplyWearSettingsUseCase` writes directly to the same repository.

**Verification:**

- `Grep` — `settingsErrorFlow` referenced in `SettingsViewModel.kt`.
- `Grep` — `fun reloadSettings` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../ui/settings/SettingsViewModel.kt (+16 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 02.* above is `[x] done`.
- [x] Project compiles — both BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] String locale audit passes: 7/7 keys OK in EN/RU/UK.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalogs regenerated — app_v2: 954 records, wear: 58 records.

---

## Handoff Notes to Next Phase

- Phone can push settings to watch via `WearSyncViewModel.pushSettings()`.
- Watch `WatchWearListenerService` fully handles `SETTINGS_PUSH`; stub removed.
- `WatchSyncEvents.settingsErrorFlow` available for future error surfaces.

---

## Rollback Plan

Revert phase commit(s). No DB schema change. Settings on watch retain their previous values until next push.
