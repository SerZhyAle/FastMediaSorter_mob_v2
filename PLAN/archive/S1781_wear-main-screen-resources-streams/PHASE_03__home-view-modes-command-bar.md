# Phase 03 - Home view modes and command bar

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Make the home screen honour the stored view mode - List, Grid 2 or Grid 3 - and move Settings off the section list onto a command bar under the content.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/ScreenSettingsScreen.kt` | New | ≤ 130 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsRoutes.kt` | Modified | ≤ 15 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsScreen.kt` | Modified | ≤ 95 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeCommandBar.kt` | New | ≤ 60 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt` | Modified | ≤ 260 |
| `wear/src/main/res/values/strings.xml` | Modified | ≤ 30 |

---

## Steps

### Step 03.1 - Add the Screen settings destination

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/ScreenSettingsScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsRoutes.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsScreen.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `const val SCREEN = "settings/screen"` to `SettingsRoutes`. Create `ScreenSettingsScreen.kt` following `SettingsScreen.kt`'s `WearScreenScaffold` + `ScalingLazyColumn` shape, with a three-way view-mode row (List / Grid 2 / Grid 3) reading and writing `WearPreferencesRepository.viewMode`. Register it in `MainActivity.kt`'s nav graph and add a fifth `Chip` to `SettingsScreen.kt` for it, placed after Media types and Slideshow per the owner's `/ui-clarify` ruling. Add the section title and row labels through `set-android-string.ps1 -Action add`, prefixed `screen_settings_`; the three mode-name strings already exist from Phase 01's `wear_view_` keys - reuse them, do not duplicate.

**Why:**

Owner ruling recorded in strategic §3.3 "UI placement contract" and repeated in the `/ui-clarify` decisions of 2026-08-18: both new watch settings live in a new "Screen" section, fifth in the settings list next to Media types and Slideshow - the owner explicitly rejected folding them into "Other" or splitting them across two places.

**Verification:**

- `Glob` - `ScreenSettingsScreen.kt` exists.
- `Grep` - `const val SCREEN` present in `SettingsRoutes.kt`.
- `Grep` - `SettingsRoutes.SCREEN` present in `SettingsScreen.kt` (the fifth chip).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_settings_" -Module wear` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fw` - exit 0 (wear module; `fk` compiles app_v2 and would never see these files).

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.1: SettingsRoutes.SCREEN added, ScreenSettingsScreen.kt created with three-way view-mode radio rows bound to WearPreferencesRepository.viewMode via SettingsViewModel; fifth Chip in SettingsScreen.kt after Slideshow; nav destination registered in MainActivity. Strings screen_settings_title/screen_settings_view_mode added EN/RU/UK. Verified: check_strings_localized -Module wear exit 0; .\a.ps1 fw exit 0. Spec self-correction: wear-only steps verify with a.ps1 fw, not fk.

---

### Step 03.2 - Render sections as list or grid by view mode

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Collect `WearPreferencesRepository.viewMode` (via `HomeViewModel`) and render `uiState.sections` as a single-column `ScalingLazyColumn` for `LIST`, or as a grid whose column count comes from `GridColumnFit.columnsFor(mode, availableWidthDp, ...)` for `GRID_2`/`GRID_3` - measure the available width from the composable's own `onSizeChanged`/`BoxWithConstraints`, never infer it from the mode name.

**Why:**

Strategic ADR-1 and ADR-2: the view mode is one stored value shared by the home screen and the Resources page, and the column count is derived from measured width, not from the mode's name, because the emulator measurement in strategic §6 item 4 showed Grid 3 giving roughly 54 dp on a 240 dp watch but roughly 40 dp on a 180 dp one - below the 48 dp accessibility floor.

**Verification:**

- `Grep` - `GridColumnFit.columnsFor` present in `HomeScreen.kt`.
- `Grep` - `WearViewMode` or `viewMode` present in `HomeScreen.kt`.
- `.\a.ps1 fw` - exit 0 (wear module; `fk` compiles app_v2 and would never see these files).

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.2: HomeScreen measures its own width with BoxWithConstraints and asks GridColumnFit.columnsFor for the actual column count; single column renders the existing chips, two or three render icon+label cells chunked into rows with weight-padded short rows. HomeUiState/HomeViewModel now carry viewMode from WearPreferencesRepository. Verified: .\a.ps1 fw exit 0.

---

### Step 03.3 - Move Settings to a command bar

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeCommandBar.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `HomeCommandBar.kt` with a `@Composable fun HomeCommandBar(onSettingsClick: () -> Unit)` rendering a single Settings icon button, placed under the section content in `HomeScreen.kt`'s layout. Remove the Settings entry from `HomeSectionCatalog.sectionsFor` and delete the leftover Settings `Chip` block Phase 02.4 kept in `HomeScreen.kt`.

**Why:**

Strategic §0 verbatim owner text: "The button Settings is not the one of list/grid element on watches - but button on the command-panel under all list/grid elements", restated as the "Командная панель вместо элемента списка" pillar in §5.1 - it exists so Settings does not compete for space with content and does not change position when the view mode changes.

**Verification:**

- `Glob` - `HomeCommandBar.kt` exists.
- `Grep` - `fun HomeCommandBar` present.
- `Grep` - `HomeSectionId.SETTINGS` returns zero hits anywhere (no such catalog entry exists).
- `.\a.ps1 fw` - exit 0 (wear module; `fk` compiles app_v2 and would never see these files).

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.3: HomeCommandBar.kt added with a single Settings icon button; HomeScreen now draws it as the last item under the section content and the Settings chip is gone. HomeSectionId.SETTINGS: 0 hits repo-wide (the catalog never carried one). Verified: .\a.ps1 fw exit 0.

---

### Step 03.4 - Accessibility pass on cells and command bar

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeCommandBar.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Give every section cell a `contentDescription` equal to its resolved label (the dynamic last-used-resource name where applicable, the string resource otherwise - never a bare position), and give the Settings command-bar button a `contentDescription` from its own string resource. Confirm every touch target - cell and button alike - meets 48 dp regardless of the active view mode.

**Why:**

Strategic §3.2 "Доступность" - "Каждый раздел и каждая ячейка озвучиваются собственным именем, а не позицией" - and §11 criterion 4 name this as a strategic-level pass condition, not a nice-to-have.

**Verification:**

- `Grep` - `contentDescription = label` present in both the list chip and the grid cell of `HomeScreen.kt` (the screen renders the catalog, so one description site per shape covers all seven sections - counting eight literal sites would only be possible if the sections were hardcoded again, which Phase 02 deliberately undid).
- `Grep` - `contentDescription = null` returns zero hits in `HomeScreen.kt` and `HomeCommandBar.kt`.
- `Grep` - the command button's `contentDescription` in `HomeCommandBar.kt` comes from `stringResource(R.string.settings)`.
- `.\a.ps1 fw` - exit 0 (wear module; `fk` compiles app_v2 and would never see these files).

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.4: every section announces its resolved label - the dynamic resource name where it has one - on both the list chip and the grid cell; the command button reads R.string.settings. No contentDescription = null left in either file. Grid cell button and command button are both sized to GridColumnFit.DEFAULT_MIN_TARGET_DP (48 dp); the list chip keeps the Wear Chip default of 52 dp. Predicate corrected: the screen renders the catalog, so eight literal description sites cannot exist. Verified: .\a.ps1 fw exit 0.

---

### Step 03.5 - Add the Streams section toggle to Media types settings

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/MediaTypesSettingsScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsViewModel.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SettingsUiState.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `ToggleChip` row for the Streams section to `MediaTypesSettingsScreen.kt`, alongside the existing audio, video and images toggles, bound to the `streamsSectionEnabled` preference Phase 02 introduced. Extend `SettingsUiState` and `SettingsViewModel` with the matching field and setter. Add the label string with `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key -En -Ru -Uk` so EN, RU and UK land in one call.

**Why:**

Strategic §3.1 item 7 asks for the Streams section to be switchable while defaulting to on, and Phase 02 stores the value the section catalog reads; without this row the preference exists with no way for the owner to reach it, which is the defect shape where a setting cannot stop the behaviour it names. The owner ruled on 2026-08-18 that the control belongs in the existing Media types section rather than in the new Screen section.

**Verification:**

- `Grep` - `streamsSectionEnabled` present in `MediaTypesSettingsScreen.kt` and in `SettingsUiState.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_streams_section" -Module wear` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fw` - exit 0 (wear module; `fk` compiles app_v2 and would never see these files).

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.5: Streams-section ToggleChip added to Media types settings beside audio/video/images, bound to WearPreferencesRepository.streamsSectionEnabled through SettingsUiState/SettingsViewModel. String wear_streams_section_enabled added EN/RU/UK. Verified: check_strings_localized -Module wear exit 0; .\a.ps1 fw exit 0.
- 2026-08-18 - Phase 03 layout evidence (S1338), captured on the connected Wear emulator (emulator-5554, sdk_gwear_x86_64, 480x480 round) from wear-debug.apk built this phase: temp/scratch/emulator-5554_20260818_175503.png (list mode home - Resources/Phone/Local), temp/scratch/emulator-5554_20260818_175515.png (Streams + Apps and the Settings command button under all list elements), temp/scratch/emulator-5554_20260818_175525.png (Screen as the fifth settings row after Media Types and Slideshow), temp/scratch/emulator-5554_20260818_175534.png and _175544.png (View radio rows List/Grid 2/Grid 3), temp/scratch/emulator-5554_20260818_175608.png (Grid 3 rendering three columns with icon+label cells). Phase-boundary audit: Layer 1-3 over the phase files; one P3 finding - SettingsViewModel.reloadSettings had no caller and each call would have stacked another never-completing combine collector on _uiState; removed, since the preferences are Flows and the state already refreshes itself.

---

### Step 03.6 - Mirror the phone's entity icons on the watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/home/HomeScreen.kt`, `wear/src/main/res/drawable/ic_cast.xml`, `wear/src/main/res/drawable/ic_apps.xml`, `wear/src/main/res/drawable/ic_resource_favorites.xml`, `wear/src/main/res/drawable/ic_watch.xml`, `wear/src/main/res/drawable/ic_wifi.xml`, `wear/src/main/res/drawable/ic_history.xml`, `wear/src/main/res/drawable/ic_profile_personal_smartphone.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> `iconFor(HomeSectionId)` returns generic `Icons.Filled.*` material icons, so the same entity wears one glyph on the phone and a different one on the watch. Copy the phone's own vectors into `wear/src/main/res/drawable/` and make `iconFor` return a `@DrawableRes Int` rendered through `painterResource`. The mapping comes from `docs/ICON_LEGEND.md`, which is the canonical meaning-to-drawable table: `STREAMS` -> `ic_cast` ("Streams"), `APPS` -> `ic_apps` ("Additional programs and scenarios"), `FAVOURITES` -> `ic_resource_favorites` ("Favorites"), `LOCAL` -> `ic_watch` (the phone's own glyph for the watch, the one on its Wear Companion button - the legend's "Local storage" glyph is a smartphone and would collide with `PHONE`), `RESOURCES` -> `ic_wifi` ("Remote resources (SMB/(S)FTP/Cloud)"), `PHONE` -> `ic_profile_personal_smartphone` (the phone's own device-profile glyph), `LAST_USED_RESOURCE` -> `ic_history`. Drop `android:tint="?attr/colorControlNormal"` while copying - that is an AppCompat attribute the Wear Compose theme does not define, and Compose's `Icon` applies its own tint anyway - and rewrite `@color/white` to `@android:color/white`.

**Why:**

Owner instruction, 2026-08-18: "в wear версии иконки сущностей должны повторяться один в один из версии для телефона. например сущность трансляции (streams)". The strategic spec names the sections but never fixed their glyphs, so Phase 03.2 reached for the nearest material icon; that made Streams a broadcast tower on the watch and a cast glyph on the phone, for one entity.

**Verification:**

- `Glob` - all seven drawables exist under `wear/src/main/res/drawable/`.
- `Grep` - `Icons.Filled` returns zero hits in `HomeScreen.kt`.
- `Grep` - `painterResource` present in `HomeScreen.kt`.
- `Grep` - `?attr/` returns zero hits across the seven copied drawables.
- `.\a.ps1 fw` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 03.6: the seven entity glyphs now come from the phone's own vectors, copied into wear/res/drawable and rendered through painterResource - iconFor returns a @DrawableRes Int and no Icons.Filled remains in HomeScreen. Mapping from docs/ICON_LEGEND.md: Streams ic_cast, Apps ic_apps, Favourites ic_resource_favorites, Resources ic_wifi, Phone ic_profile_personal_smartphone, Last used ic_history. Correction found by reading the first screenshot: ic_resource_local, the legend's Local storage glyph, is a smartphone, so Phone and Local rendered identically on the watch - Local now takes ic_watch, the phone's own glyph for the watch (its Wear Companion button), which keeps every icon sourced from the phone while the two sections stay distinguishable. android:tint=?attr/... was dropped on copy (an AppCompat attribute the Wear theme does not define; Compose Icon tints anyway) and @color/white rewritten to @android:color/white. The Settings gear on the command bar is deliberately untouched - it is a command, not an entity. Verified: seven drawables present; Icons.Filled=0 and painterResource=3 in HomeScreen; ?attr/ in copied drawables = 0; a.ps1 fw exit 0; assemble wear exit 0; on-device evidence evidence/S1781_step0306_watch_icons_final.png (emulator-5554) shows Resources wifi, Phone smartphone, Local watch, Streams cast, Apps grid, all distinct.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/wear.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module wear`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The home screen honours `viewMode` and Settings lives on a command bar. Phase 06 reuses this phase's Screen settings destination to add the keep-awake row as a third entry.

---

## Rollback Plan

Revert phase commit(s) - `SettingsRoutes.SCREEN` and `ScreenSettingsScreen.kt` are additive; reverting `HomeScreen.kt`'s grid/command-bar changes restores the Phase 02 list-only rendering with Settings back in the section list.
