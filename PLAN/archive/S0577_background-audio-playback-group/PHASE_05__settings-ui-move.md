# Phase 05 - Settings UI Move

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (UI relocation; independent of Phases 01-04)
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** -
**Completed:** -

---

## Objective

Move the background-playback block (toggle, notification-permission button, exit-behavior radio section, now-playing toggle) from the Media/Audio sub-section into a new collapsed-by-default group "Фоновое воспроизведение аудио" on the Player tab, and rename the exit-behavior label to cover streams. Storage keys and domain model are unchanged.

---

## Prerequisites

- [ ] `PlaybackSettingsFragment.kt` (459 LOC) grows past 500 after this phase - take a timestamped backup into `temp/` before editing.
- [ ] Confirm landscape variants exist: `res/layout-land/fragment_settings_playback.xml` and `res/layout-land/fragment_settings_audio.xml` (both present).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout/fragment_settings_audio.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_settings_audio.xml` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> **Landscape parity (MANDATORY):** every structural edit to `layout/fragment_settings_playback.xml` and `layout/fragment_settings_audio.xml` MUST be mirrored in the `layout-land/` counterpart in the same step.

---

## Steps

### Step 05.1 - Add the group title string

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one new trilingual key `background_audio_group_title` via `scripts/utils/set-android-string.ps1 -Action add -Key background_audio_group_title -En "Background audio playback" -Ru "Фоновое воспроизведение аудио" -Uk "Фонове відтворення аудіо"`. This is the collapsible group header title on the Player tab. RU/UK must use Ё/ё correctly (here none required). Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - `background_audio_group_title` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "background_audio_group_title"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. background_audio_group_title added EN/RU/UK (Cyrillic intact, parity OK).

---

### Step 05.2 - Rename the exit-behavior label to cover streams

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Change the value of the existing key `background_audio_exit_behavior_title` in place (keep the key) via `scripts/utils/set-android-string.ps1 -Action set` per locale: EN `"When leaving player or streams"`, RU `"Поведение при выходе из плеера и трансляций"`, UK `"При виході з плеєра та трансляцій"`. Do not add a new key (preserves `BackupMapper` compatibility). Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - EN value `When leaving player or streams` present for `background_audio_exit_behavior_title`.
- `Grep` - RU value contains `и трансляций`; UK value contains `та трансляцій`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "background_audio_exit_behavior_title"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. background_audio_exit_behavior_title renamed in EN/RU/UK (key preserved).

---

### Step 05.3 - Insert the collapsible group into the Player tab layout

**Files:** `layout/fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a new `MaterialCardView` following the existing card pattern (e.g. the Send-Commands card) containing a `CollapsibleSectionHeader android:id="@+id/headerBackgroundAudio"` with title `@string/background_audio_group_title`, and a sibling container `LinearLayout android:id="@+id/containerBackgroundAudio"`. Inside the container place, verbatim from `fragment_settings_audio.xml`, the moved views with their existing ids: `rowEnablePersistentAudioPlayback` (SettingsToggleRow), `btnNotificationPermission` (MaterialButton), `layoutExitBehaviorSection` (LinearLayout with `radioGroupExitBehavior` + `radioExitBehaviorAsk`/`radioExitBehaviorAlwaysStop`/`radioExitBehaviorAlwaysContinue` + `rowShowNowPlayingPanel`). Mirror identically into the landscape variant. Keep ids unchanged so the moved Kotlin binds. No hardcoded hex colors - use `?attr/`/`@color/`; preserve focus/D-pad attributes.

**Verification:**

- `Grep` - `headerBackgroundAudio` and `containerBackgroundAudio` present in both `layout/` and `layout-land/` playback files.
- `Grep` - `rowEnablePersistentAudioPlayback`, `radioGroupExitBehavior`, `rowShowNowPlayingPanel` present in both playback files.
- `Grep -n "#[0-9a-fA-F]\{6\}"` on the edited layouts returns zero new hardcoded colors.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. New collapsible card (cardBackgroundAudio + header + container) added to playback portrait + land.

---

### Step 05.4 - Remove the block from the Audio sub-section layout

**Files:** `layout/fragment_settings_audio.xml`, `layout-land/fragment_settings_audio.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Delete the background-playback block (the `<!-- Background audio playback -->` region: `rowEnablePersistentAudioPlayback`, `btnNotificationPermission`, `layoutExitBehaviorSection` with its RadioGroup and `rowShowNowPlayingPanel`) from both portrait and landscape audio layouts. Leave the rest of the audio settings (support audio, covers search, size limits, photos-during-audio, empty-state, default player) intact.

**Verification:**

- `Grep` - `rowEnablePersistentAudioPlayback` and `radioGroupExitBehavior` return zero hits in both `fragment_settings_audio.xml` files.
- `Grep` - `rowSupportAudio` still present in `fragment_settings_audio.xml` (rest of section intact).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Background block removed from audio portrait + land; rest of audio settings intact.

---

### Step 05.5 - Move the background-audio Kotlin into PlaybackSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Port the background-audio wiring from `AudioSettingsFragment` into `PlaybackSettingsFragment`, adapted to the plain-`Fragment` idiom already used there (`SettingsToggleRow.setOnCheckedChangeListener`/`setCheckedSilently`, not `BaseSettingsFragment.bindSwitch`): the `notificationPermissionLauncher` (`registerForActivityResult(RequestPermission())`), `setupBackgroundAudioSection`, `setupExitBehaviorSection`, `updateExitBehaviorVisibility`, `updateNotificationPermissionButtonVisibility`, `isNotificationPermissionGranted`, `showBatteryOptimizationHintIfNeeded`, the `PREFS_NAME_HINT`/`KEY_HAS_SHOWN_BATTERY_HINT` constants, and the `observeData` sync block for the three rows. Wire the toggle/radio/now-playing rows to `viewModel.updateSettings(..)` exactly as before. Guard the whole block with `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` (hide the card on lite/photos). Register the collapsible group in `setupCollapsibleSections` with `sectionsManager.register(binding.headerBackgroundAudio, binding.containerBackgroundAudio, "playback__bg_audio")` (defaultExpanded false - collapsed per S0535). Collect via `collectOnLifecycle` (no bare `lifecycleScope.launch { collect }`).

**Verification:**

- `Grep` - `headerBackgroundAudio` and `"playback__bg_audio"` referenced in `PlaybackSettingsFragment.kt`.
- `Grep` - `notificationPermissionLauncher` and `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` present in `PlaybackSettingsFragment.kt`.
- `Grep` - `enablePersistentAudioPlayback`, `backgroundAudioExitBehavior`, `showNowPlayingPanel` all written via `updateSettings(` in `PlaybackSettingsFragment.kt`.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Background-audio wiring ported to PlaybackSettingsFragment (plain-Fragment idiom); section registered playback__bg_audio.

---

### Step 05.6 - Strip the moved logic from AudioSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** Step 05.5

**Prompt for developer:**

> Remove from `AudioSettingsFragment`: `notificationPermissionLauncher`, `setupBackgroundAudioSection`, `setupExitBehaviorSection`, `updateExitBehaviorVisibility`, `updateNotificationPermissionButtonVisibility`, `isNotificationPermissionGranted`, `showBatteryOptimizationHintIfNeeded`, the `PREFS_NAME_HINT`/`KEY_HAS_SHOWN_BATTERY_HINT` constants, the `setupBackgroundAudioSection()` call in `setupViews`, and the `if (BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) { .. }` background-audio sync block in `observeData`. Remove now-unused imports (e.g. `Build`, `Settings`, `PackageManager`, `ContextCompat`) only if no other code in the file uses them. Keep all other audio settings behavior unchanged.

**Verification:**

- `Grep` - `setupBackgroundAudioSection`, `radioGroupExitBehavior`, `notificationPermissionLauncher` return zero hits in `AudioSettingsFragment.kt`.
- `Grep` - `rowSupportAudio` and `rowSearchAudioCoversOnline` still present (rest intact).
- Compile: `.\a.ps1 fc` exits 0 (layouts + Kotlin consistent; no dangling binding refs).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. AudioSettingsFragment stripped of moved logic + 9 unused imports. ..ps1 fc BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exits 0.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Background-audio ids exist only under `fragment_settings_playback.xml` (portrait + land), not under `fragment_settings_audio.xml`.
- [ ] Dev log entry added for the phase.

---

## Handoff Notes to Next Phase

The three background-audio rows now live in `fragment_settings_playback.xml` (PLAYBACK destination). The settings manifest, reference docs, and annotations are stale until Phase 06 regenerates them; `SettingsSearchTabMapping`/`SettingsSearchLayoutCatalog` need no change (no new layout file).

---

## Rollback Plan

Revert the phase commit(s) - layouts, fragments, and string values return to the Media/Audio location. Storage keys never changed, so persisted settings survive a rollback unchanged.
