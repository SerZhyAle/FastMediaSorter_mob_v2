# Phase 01 - Settings Surface

**Strategic spec:** [../S0375_video-recording-destination-resource.md](../S0375_video-recording-destination-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Introduce the persisted video destination setting and expose it in playback settings for portrait and landscape.

---

## Prerequisites

- [x] No pre-implementation blockers remain unchecked in `INDEX.md`.
- [x] Working tree is on a feature branch.
- [x] Backups exist for large touched files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 760 |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 620 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 660 |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | ≤ 260 |

---

## Steps

### Step 01.1 - Add the persisted video destination preference

**Files:** `AppSettings.kt`, `SettingsRepositoryImpl.kt`
**Depends on:** - start of phase
**Prompt for developer:** Add a dedicated nullable `videoRecordingDestinationResourceId` to the app settings model and wire a matching DataStore preference key through read/write paths. Keep naming parallel to the existing microphone and camera destination settings.
**Verification:** The model exposes `videoRecordingDestinationResourceId`, the repository reads it from DataStore, and `updateSettings` persists it with `setOrRemove`.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added `videoRecordingDestinationResourceId` to `AppSettings` and `SettingsRepositoryImpl`.

### Step 01.2 - Add the video destination selector to both playback layouts

**Files:** `fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`, `values/strings_settings.xml`, `values-ru/strings_settings.xml`, `values-uk/strings_settings.xml`
**Depends on:** 01.1
**Prompt for developer:** Add a `Video recordings destination` selector under the existing `open in player` row inside the video options block in portrait and landscape. Add EN/RU/UK strings for the title and fallback label. Keep the selector hidden together with the video options block when video recording is disabled. Strings must pass `COMMUNICATION_POLICY.md` §6.
**Verification:** Both layouts contain the new selector ids, all three locales contain the new strings, and the fallback text uses `Movies` wording rather than camera/download wording.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added portrait/landscape selector rows and EN/RU/UK strings for the video destination setting.

### Step 01.3 - Wire the selector in playback settings fragment

**Files:** `PlaybackSettingsFragment.kt`
**Depends on:** 01.2
**Prompt for developer:** Reuse the existing destination picker and label-refresh patterns to save, clear, and render the video destination setting. Use the device `Movies` folder fallback label when the configured value is empty or stale. Keep the selector inside the video options gating.
**Verification:** The fragment handles click/save/clear for the new selector, refreshes its label from settings, and keeps the options block visibility tied to `disableVideoCapture`.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Wired the new selector through `PlaybackSettingsFragment` using the shared picker and fallback-label refresh pattern.

---

## Phase Done Criteria

- [x] The new setting is stored in `AppSettings` and `SettingsRepositoryImpl`.
- [x] Portrait and landscape both expose the same video selector structure.
- [x] EN/RU/UK strings exist for the new setting and fallback label.
- [x] Playback settings fragment renders and saves the selector value.