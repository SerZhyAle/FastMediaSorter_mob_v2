# Phase 06 - Screen keep-awake

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

The three players hold the watch screen on whenever they are actively playing or viewing, unconditionally; every other screen follows the new `keepScreenAwakeOutsidePlayers` setting.

---

## Prerequisites

- [ ] Phase 01 and Phase 03 are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `AudioPlayerScreen.kt` already carries a private `KeepScreenOn` composable tied to `uiState.isDimmed` only (the "black screen" ambient state) - confirm this before starting, since Step 06.1 extends it rather than inventing a parallel mechanism.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/KeepScreenOnEffect.kt` | New | ≤ 45 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerScreen.kt` | Modified | ≤ 570 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerScreen.kt` | Modified | ≤ 360 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerScreen.kt` | Modified | ≤ 265 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt` | Modified | ≤ 330 (was ≤ 280, which the file already exceeded at 295 before this phase; landed at 324) |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/ScreenSettingsScreen.kt` | Modified | ≤ 160 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsViewModel.kt` | Modified | ≤ 175 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsUiState.kt` | Modified | ≤ 45 |
| `wear/src/main/res/values/strings.xml` | Modified | ≤ 20 |

---

## Steps

### Step 06.1 - Extract KeepScreenOnEffect and hold it during playback/viewing

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/KeepScreenOnEffect.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerScreen.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `AudioPlayerScreen.kt` is already 590 lines; take a timestamped backup before editing, per the file-size rule (Rule 5 puts it under `temp/`, which is scratch by design and is not part of this spec's durable evidence). Move its private `KeepScreenOn(enabled: Boolean)` composable and the `Context.findActivity()` extension it uses into a new shared `wear/.../ui/common/KeepScreenOnEffect.kt`, keeping the same `FLAG_KEEP_SCREEN_ON` add-on-enter/clear-on-dispose behaviour. Update `AudioPlayerScreen.kt`'s call site to `enabled = uiState.isPlaying || uiState.isDimmed` - today it only fires during the dimmed "black screen" state, which is narrower than "while playback is active". Call the shared composable from `VideoPlayerScreen.kt` with `enabled = uiState.isPlaying`, and from `ImageViewerScreen.kt` with `enabled = uiState.mediaFile != null` - an image has no "playing" state, so viewing it is itself the active condition.

**Why:**

Strategic §2 goal 11 and §0 owner text verbatim: "Аудио и Видеопроигрыватель, проигрывание изображений держит устройство включенным, оно не засыпает" - unconditional during playback/viewing, which today's audio screen does not fully deliver since its existing flag is scoped to the dimmed state alone; §11 criterion 11 is the strategic-level pass condition this step exists to satisfy.

**Verification:**

- `Glob` - `KeepScreenOnEffect.kt` exists; `Grep` - the old private `KeepScreenOn` definition is gone from `AudioPlayerScreen.kt`.
- `Grep` - `KeepScreenOnEffect` present in all three player screen files.
- `Grep` - `uiState.isPlaying` present in the `KeepScreenOnEffect` call inside `VideoPlayerScreen.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `KeepScreenOnEffect.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 06.1: the private KeepScreenOn composable and its Context.findActivity extension moved out of AudioPlayerScreen into a shared wear/ui/common/KeepScreenOnEffect.kt, keeping the same add-on-enter, clear-on-dispose behaviour. All three players now go through it: audio with isPlaying || isDimmed - previously it fired only while dimmed, which is narrower than while playback is active - video with isPlaying, and the image viewer with mediaFile != null, since an image has no playing state. Dead imports (Activity, Context, ContextWrapper, WindowManager, DisposableEffect, LocalContext) removed from AudioPlayerScreen in the same step; the file dropped 590 to 561 lines. Verified: KeepScreenOnEffect.kt exists; grep private fun KeepScreenOn in AudioPlayerScreen = 0; KeepScreenOnEffect present in all three player screens; VideoPlayerScreen call reads KeepScreenOnEffect(enabled = uiState.isPlaying); Log.d in the new file = 0; a.ps1 fw exit 0; a.ps1 fk exit 0.

---

### Step 06.2 - Apply keepScreenAwakeOutsidePlayers everywhere else

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> In `MainNavigation()` (the composable hosting `SwipeDismissableNavHost`), collect `WearPreferencesRepository.keepScreenAwakeOutsidePlayers` and the current route via `navController.currentBackStackEntryAsState()`. Call the shared `KeepScreenOnEffect` from Step 06.1 with `enabled = keepScreenAwakeOutsidePlayers && currentRoute` is not one of `AUDIO_PLAYER_PATTERN`/`VIDEO_PLAYER_PATTERN`/`IMAGE_VIEWER_PATTERN`. Route this through one shared call site rather than adding the effect to each non-player screen individually - two independent composables both calling `addFlags`/`clearFlags` on the same window without coordinating would clear each other's flag on navigation.

**Why:**

Strategic §2 goal 11's second half - "а по всей остальной программе Настраивается собственной настройкой" - and §5.1 "Удержание экрана" both scope this to a setting outside the three players; centralising it in the nav host is a deliberate implementation choice to avoid the flag-clobbering risk between the player-owned effect and a setting-owned effect that a per-screen approach would create, not something the strategic spec dictates directly.

**Verification:**

- `Grep` - `keepScreenAwakeOutsidePlayers` present in `MainActivity.kt`.
- `Grep` - `currentBackStackEntryAsState` present in `MainActivity.kt`.
- `Grep` - `AUDIO_PLAYER_PATTERN` present in the `PLAYER_ROUTES` exclusion set that `MainNavigation` reads.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 06.2: MainNavigation now holds the screen for every non-player route through the one shared KeepScreenOnEffect - enabled = keepScreenAwakeOutsidePlayers and the current route not in PLAYER_ROUTES, with the route read from navController.currentBackStackEntryAsState(). MainActivity field-injects WearPreferencesRepository and hands the flow down through WearApp; that adds no Hilt module, provider or scope, only field injection into an activity that was already an @AndroidEntryPoint. Correction: the step's third predicate asked for the exclusion list inside the same function; it lives in a file-level PLAYER_ROUTES set instead, so the three route patterns are not rebuilt on every recomposition, and the predicate was rewritten to name that set. Verified: grep keepScreenAwakeOutsidePlayers=5, currentBackStackEntryAsState=2 in MainActivity; PLAYER_ROUTES carries all three player patterns; a.ps1 fw exit 0; a.ps1 fk exit 0.

---

### Step 06.3 - Add the keep-awake row to Screen settings

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/ScreenSettingsScreen.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a toggle row to `ScreenSettingsScreen.kt` (created in Phase 03.1) reading and writing `WearPreferencesRepository.keepScreenAwakeOutsidePlayers`, placed below the view-mode row. Add its label through `set-android-string.ps1 -Action add`, prefixed `screen_settings_keep_awake`.

**Why:**

Owner ruling recorded in strategic §3.3 and the `/ui-clarify` decisions of 2026-08-18 place both new watch settings - view mode and keep-awake - in the same "Screen" section; this row is the second of the two the owner named.

**Verification:**

- `Grep` - `keepScreenAwakeOutsidePlayers` present in `ScreenSettingsScreen.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_settings_keep_awake"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 06.3: Screen settings gained a keep-awake toggle row below the view-mode radio group, reading and writing WearPreferencesRepository.keepScreenAwakeOutsidePlayers. Correction: the step named only ScreenSettingsScreen.kt and strings.xml, but the screen reaches the repository through SettingsViewModel like every other row on it - putting a repository read in the composable would be business logic in the UI layer - so SettingsUiState gained keepScreenAwakeOutsidePlayers, SettingsViewModel gained the tenth combine flow (INDEX_KEEP_AWAKE) and toggleKeepScreenAwakeOutsidePlayers(), and both files were added to Files Touched. Label screen_settings_keep_awake added in en/ru/uk via set-android-string.ps1 -Action add. Verified: grep keepScreenAwakeOutsidePlayers in ScreenSettingsScreen.kt = 1 hit; check_strings_localized.ps1 -KeyPrefix screen_settings_keep_awake -Module wear exit 0 (all 1 key present in en/ru/uk); a.ps1 fw exit 0; a.ps1 fk exit 0.
- 2026-08-18 - Step 06.3: PRE-RESOLVED - the KeepAwakeRow ToggleChip was already present in ScreenSettingsScreen below the view-mode rows when this step was reached, bound to uiState.keepScreenAwakeOutsidePlayers and SettingsViewModel.toggleKeepScreenAwakeOutsidePlayers, with screen_settings_keep_awake in EN/RU/UK (Keep screen on / Не гасить экран / Не гасити екран). Step 03.1's log records only the view-mode rows, so the row landed alongside that screen without being written down; nothing was re-implemented here. Communication policy 6: a plain setting label, no jargon, no error phrasing. Verified in this run: grep keepScreenAwakeOutsidePlayers=2 in ScreenSettingsScreen and toggleKeepScreenAwakeOutsidePlayers=1 in SettingsViewModel; check_strings_localized -Module wear -KeyPrefix screen_settings_keep_awake exit 0; a.ps1 fw exit 0; a.ps1 fk exit 0.
- 2026-08-18 - Phase-06 boundary audit (layers 1-3): no P0/P1. AUDIT-P2: MainActivity hands a repository Flow straight into composables, so MainNavigation reads a repository without a ViewModel between them. Accepted for now - the nav host sits above every screen and owns no ViewModel, and the step named none; a KeepAwakeViewModel would have been invented, not derived. AUDIT-P2 (device): two KeepScreenOnEffect instances exist either side of the player boundary - the nav-host one and the player's own - and on navigating into a player the nav-host effect disposes (clearFlags) in the same frame the player's adds the flag. Compose disposes forgotten effects before running newly remembered ones, so the order should hold, but that is reasoning, not measurement: the device test must confirm the screen actually stays awake in each of the three players. Layer 2 otherwise clean - DisposableEffect keyed on (window, enabled), flag cleared on every exit path, audio widened from isDimmed to isPlaying || isDimmed. Layer 3 clean - no listener or context retained past the effect. UI evidence (S1338), placement per the owner's /ui-clarify ruling that both watch settings live in the Screen section: evidence/S1781_phase06_watch_settings.png - the Keep screen on toggle rendered below the view-mode rows on the watch. Verified: a.ps1 dq exit 0; a.ps1 fw exit 0; TODO(phase-06)=0; dev-log covers all six files; wear catalog regenerated.
- 2026-08-18 - Phase 06 boundary audit (Layers 1-3, CODE_AUDIT_PROTOCOL). Layer 2/3 checked the one real risk this phase carries - two independent FLAG_KEEP_SCREEN_ON owners on one window. Not a defect: Compose dispatches every onForgotten before any onRemembered in a single applyChanges, so entering a player (nav-host effect clears, player effect adds) and leaving one (player effect clears, nav-host effect re-adds) both settle with the flag in the correct state. AUDIT-P2 fixed: MainActivity collected the keep-awake flow with a bare collectAsState while every other wear screen uses collectAsStateWithLifecycle - a DataStore flow left collecting in the background. Now collectAsStateWithLifecycle(initialValue = false); a.ps1 fw exit 0, post-change PASS. Layout evidence (S1338), Wear emulator sdk_gwear_x86_64 SDK 37: temp/scratch/emulator-5554_20260818_211800.png shows the Keep screen on row below the view-mode radio group, and temp/scratch/emulator-5554_20260818_211816.png shows it toggled on, so the value round-trips repository to DataStore to flow to UI. Owner placement ruling for the row is strategic 3.3 (both new watch settings in the Screen section).

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/wear.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module wear`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

All three players hold the screen on unconditionally during playback/viewing via the shared `KeepScreenOnEffect`; every other screen follows `keepScreenAwakeOutsidePlayers`, applied once at the nav-host level. Phase 07 carries this setting - and the view mode - into the phone-side mirror.

---

## Rollback Plan

Revert phase commit(s) - `KeepScreenOnEffect.kt` reverts to the pre-extraction private composable in `AudioPlayerScreen.kt`; video and image players lose keep-awake entirely, matching current shipped behaviour; the nav-host effect and the settings row are additive and safe to drop.
