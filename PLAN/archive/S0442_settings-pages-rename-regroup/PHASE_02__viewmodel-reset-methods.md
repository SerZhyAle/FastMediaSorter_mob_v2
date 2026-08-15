# Phase 02 - viewmodel-reset-methods

**Strategic spec:** [`../S0442_settings-pages-rename-regroup.md`](../S0442_settings-pages-rename-regroup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Narrow `resetPlaybackSection()` to player-only fields and introduce `resetOperationsSection()` covering all Management-tab settings. Both methods must be in place before Phase 03 wires the Operations reset button.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 624 (backup first) |

> **Backup required** — file is 624 LOC. Create timestamped copy in `temp/` before editing.

---

## Steps

### Step 2.1 - Backup SettingsViewModel.kt

**Files:** _(backup only, no source change)_
**Depends on:** start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` to `temp/SettingsViewModel_S0442_<timestamp>.kt` before making any edits to it.

**Verification:**

- `Glob` - `temp/SettingsViewModel_S0442_*.kt` returns at least one match.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Backup created at temp/SettingsViewModel_S0442_20260615_161625.kt.

---

### Step 2.2 - Narrow resetPlaybackSection() to player-only fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Edit `resetPlaybackSection()` in `SettingsViewModel`. Keep only the fields whose UI controls live in the groups **staying** in the Player tab:
>
> - **Sorting/Slideshow** group: `defaultSortMode`, `slideshowInterval`, `slideshowMusicUri`, `enableSlideshowBackgroundMusic`, `slideshowMusicResourceId`, `playToEndInSlideshow`
> - **File ops in player** group (`headerFileOperations` — "Удаление и переименование в плеере"): `allowRename`, `allowDelete`, `fileOpsOverflowMenuHintShown`
> - **Player UI** group: `hideSystemUiInFullscreen`, `defaultShowCommandPanel`, `showDetailedErrors`, `showPlayerHintOnFirstRun`
> - **Touch zones** group: `alwaysShowTouchZonesOverlay` — plus the repository calls `settingsRepository.setPlayerFirstRun(true)` and `settingsRepository.resetAllTouchZoneHints()`
>
> Remove from the method: `preventSleep`, `useTrash`, `showBlackScreenButton`, `defaultRememberFileList`, `enableCalculator`, `embeddedGameEnabled`, `isResourceGridMode`, `confirmDelete`, and any other field whose UI widget will move to the Management tab. Cross-check each removed field against the layout groups listed in strategic §5.1 to confirm it belongs to a moved group. No trivial comments — if a field removal requires explanation, add a single-line comment stating WHICH group owns it and THAT it now belongs to `resetOperationsSection()`.
>
> Do not use `Log.d()` — Timber only.

**Verification:**

- `Grep` - `resetPlaybackSection` in `SettingsViewModel.kt` → method body does **not** contain `preventSleep`.
- `Grep` - `resetPlaybackSection` in `SettingsViewModel.kt` → method body does **not** contain `showBlackScreenButton`.
- `Grep` - `resetPlaybackSection` in `SettingsViewModel.kt` → method body does **not** contain `embeddedGameEnabled`.
- `Grep` - `resetPlaybackSection` in `SettingsViewModel.kt` → method body **does** contain `defaultSortMode`.
- `Grep` - `resetPlaybackSection` in `SettingsViewModel.kt` → method body **does** contain `alwaysShowTouchZonesOverlay`.
- `Grep` - `Log\.d\(` in `SettingsViewModel.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 6/6 PASS. preventSleep/showBlackScreenButton/embeddedGameEnabled removed from resetPlaybackSection; defaultSortMode/alwaysShowTouchZonesOverlay retained. Log.d zero hits.

---

### Step 2.3 - Replace resetDestinationsSection() with resetOperationsSection()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> `resetDestinationsSection()` has no call sites outside ViewModel itself (confirmed: grep shows declaration only). Rename it to `resetOperationsSection()` and broaden its scope to cover **all** Management-tab setting groups:
>
> - **Safety** group: `enableSafeMode`, `confirmDelete`, `confirmMove`, `useTrash`
> - **Copy/Move** group (formerly resetDestinationsSection scope): `enableCopying`, `goToNextAfterCopy`, `overwriteOnCopy`, `enableMoving`, `overwriteOnMove`, `enableUndo`, `maxRecipients`
> - **Behaviour** group (moving from Playback): `preventSleep`, `defaultRememberFileList` — and any other fields whose UI widget is inside the `containerBehaviour` card in the layout; audit the Behaviour container in `fragment_settings_playback.xml` to obtain the complete field list
> - **OtherFeatures** group (moving from Playback): `showBlackScreenButton`, `enableCalculator`, `embeddedGameEnabled`, `micRecordingEnabled`, `cameraOcrTranslationEnabled`, `cameraOcrOnly` — audit `containerOtherFeatures` in the layout to confirm
> - **SystemApps** group (moving from Playback): `gestureOverlayEnabled` and any accept-shared-files, system-media-handler, prevent-sleep fields found in `containerSystemApps`; audit the layout
> - **ScreenGestures** subgroup: any fields governed by `containerScreenGestures`
>
> The field lists above are a starting point; the authoritative source is the XML group containers in `fragment_settings_playback.xml`. For each group, open its container XML, list all `SettingsToggleRow`/spinner/dropdown views, and map each to an `AppSettings` field via the `setOnCheckedChangeListener` / `setOnItemClickListener` handlers in `PlaybackSettingsFragment.kt`.

**Verification:**

- `Grep` - `fun resetDestinationsSection` in `SettingsViewModel.kt` → zero hits (renamed).
- `Grep` - `fun resetOperationsSection` in `SettingsViewModel.kt` → exactly one hit.
- `Grep` - `resetOperationsSection` body contains `enableCopying`.
- `Grep` - `resetOperationsSection` body contains `preventSleep`.
- `Grep` - `resetOperationsSection` body contains `showBlackScreenButton`.
- `Grep` - `Log\.d\(` in `SettingsViewModel.kt` → zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-06-15 - Verification 6/6 PASS. resetDestinationsSection renamed to resetOperationsSection; body includes enableCopying, preventSleep, showBlackScreenButton. Log.d zero hits.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] `.\a.ps1 fk` → exit 0 (Kotlin compiles; the renamed method has no broken call sites).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `SettingsViewModel.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`resetPlaybackSection()` now resets only Player-tab fields. `resetOperationsSection()` exists and covers all Management-tab fields. Phase 03 can wire `btnResetOperationsSection` to call `viewModel.resetOperationsSection()`.

---

## Rollback Plan

Restore `temp/SettingsViewModel_S0442_*.kt` to original path — no data migration or UI change involved.
