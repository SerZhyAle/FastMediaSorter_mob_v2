# Phase 02 - Remove Grid View block from Playback

**Strategic spec:** [`../S0254_settings-grid-to-interface.md`](../S0254_settings-grid-to-interface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Delete the entire "GROUP: Grid View" `MaterialCardView` block from `fragment_settings_playback.xml` (both portrait and landscape), and remove all Kotlin code in `PlaybackSettingsFragment` that wired its controls, observers, tooltip, expansion-section registration, and the `KEY_GRID_VIEW_EXPANDED` preferences key. After this phase, the Playback tab no longer contains any Grid View elements; behavior is fully provided by General.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `SettingsSearchIndex` destinations for the five moved view ids are already pointing to GENERAL (verified in Phase 01).
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 260 (currently 303, removing ~61 lines + card wrapper) |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 600 (currently ~600; removing the grid-view bindings + section helpers) |

> **Backup rule:** `PlaybackSettingsFragment.kt` is already near the 500-line threshold. **Create a timestamped backup in `temp/` before editing** (Step 02.4 below).
> **Landscape parity:** the landscape variant of `fragment_settings_playback.xml` exists. Both portrait and landscape are edited in matching steps.

---

## Steps

### Step 02.1 - Delete Grid View MaterialCardView from portrait playback layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the entire `MaterialCardView` block that begins around line 103 (`<com.google.android.material.card.MaterialCardView ..>`) and ends at its closing `</com.google.android.material.card.MaterialCardView>` around line 163. The block contains the `<!-- GROUP: Grid View -->` comment, `headerGridView`, `containerGridView`, and its three child rows. Remove the entire card wrapper - not just the inner container. Do NOT leave behind the surrounding `MaterialCardView` element, the empty `LinearLayout` shell, or stray comments. Adjacent cards above (line 101 closing tag) and below (line 165 opening tag of "Player UI" card) must remain untouched and stay direct siblings of the outer vertical `LinearLayout`.

**Verification:**

- `Grep` - `android:id="@+id/headerGridView"` does **not** match in `app_v2/src/main/res/layout/fragment_settings_playback.xml` (expected: 0 | actual: must be 0).
- `Grep` - `android:id="@+id/containerGridView"` does **not** match.
- `Grep` - `android:id="@+id/switchGridMode"` does **not** match.
- `Grep` - `android:id="@+id/switchHideGridActionButtons"` does **not** match.
- `Grep` - `android:id="@+id/switchFileOpsOverflowMenu"` does **not** match.
- `Grep` - `android:id="@+id/etIconSize"` does **not** match.
- `Grep` - `android:id="@+id/tilIconSize"` does **not** match.
- `Grep` - `android:id="@+id/iconHelpGridSize"` does **not** match.
- `Grep` - `GROUP: Grid View` does **not** match.
- `Grep` - `android:id="@+id/headerPlayerUI"` still matches exactly once (the next card survives untouched).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 10/10 PASS (8 ids and GROUP comment = 0, headerPlayerUI = 1). File: `app_v2/src/main/res/layout/fragment_settings_playback.xml` (-61 LOC). Dev log recorded.

---

### Step 02.2 - Delete Grid View MaterialCardView from landscape playback layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Repeat Step 02.1 against the landscape variant `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`. Delete the corresponding `MaterialCardView` wrapping the "GROUP: Grid View" content (same line range relative to that file). Surrounding cards must remain untouched.

**Verification:**

- Same predicate set as Step 02.1, applied to `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`. All eight grid-related ids and the "GROUP: Grid View" comment must return zero matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 10/10 PASS (8 ids + GROUP comment = 0, headerPlayerUI = 1). File: `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` (-61 LOC). Dev log recorded.

---

### Step 02.3 - Backup PlaybackSettingsFragment.kt before edit

**Files:** `temp/PlaybackSettingsFragment.kt.<YYYYMMDD_HHmmss>.backup`
**Depends on:** Step 02.2

**Prompt for developer:**

> The file `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` exceeds 500 LOC. Per CLAUDE.md Strict Rules §5, create a timestamped backup before editing. Use:
> ```powershell
> Copy-Item `
>   "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt" `
>   "temp/PlaybackSettingsFragment.kt.$(Get-Date -Format 'yyyyMMdd_HHmmss').backup"
> ```

**Verification:**

- `Glob` - `temp/PlaybackSettingsFragment.kt.*.backup` matches at least one file dated within the last 5 minutes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Backup: `temp/PlaybackSettingsFragment.kt.20260519_173458.backup`.

---

### Step 02.4 - Remove Grid View bindings from PlaybackSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Remove every Kotlin block in `PlaybackSettingsFragment.kt` that references any of the moved view ids. Specifically:
>
> - Lines 167-176 region: `binding.switchGridMode.setOnCheckedChangeListener` block and `binding.switchHideGridActionButtons.setOnCheckedChangeListener` block.
> - Lines 179-191 region: `binding.switchFileOpsOverflowMenu.setOnCheckedChangeListener` block (note: this includes the `fileOpsOverflowMenuHintShown` hint logic - delete it entirely from Playback; the copy added in Phase 01 §01.5 must already preserve it in General).
> - Lines 333-340 region: `binding.etIconSize.setAdapter` + initial `setText`.
> - Lines 336-340 region: `binding.etIconSize.setOnItemClickListener`.
> - Lines 342-348 region: `binding.iconHelpGridSize.setOnClickListener` tooltip handler.
> - Lines 391-405 region: `binding.etIconSize.setOnFocusChangeListener` clamp logic.
> - Lines 439-446 region (inside observer): `binding.switchGridMode.isChecked` / `binding.switchHideGridActionButtons.isChecked` / `binding.switchFileOpsOverflowMenu.isChecked` assignments. Remove every reference to these binding fields.
> - Lines 477-481 region (inside observer): `binding.etIconSize.text` / `defaultIconSize` icon-size reconciliation. Remove every reference.
> - Imports/variables that become orphaned (e.g. `ArrayAdapter` may no longer be needed if it was only for iconSize - check, do not assume; if still used elsewhere, leave it).
>
> Do NOT delete the `setupViews()` / `setupExpandableSections()` / `observeData()` function declarations themselves - only their grid-related contents. Leave the surrounding listener wiring for Sorting/PlayerUI/Behaviour/TouchZones intact.

**Verification:**

- `Grep` - `binding.switchGridMode` returns zero matches in `PlaybackSettingsFragment.kt`.
- `Grep` - `binding.switchHideGridActionButtons` returns zero matches.
- `Grep` - `binding.switchFileOpsOverflowMenu` returns zero matches.
- `Grep` - `binding.etIconSize` returns zero matches.
- `Grep` - `binding.iconHelpGridSize` returns zero matches.
- `Grep` - `binding.tilIconSize` returns zero matches.
- `Grep` - `defaultGridMode` returns zero matches (no read or write path for the moved field remains in this file).
- `Grep` - `hideGridActionButtons` returns zero matches.
- `Grep` - `fileOpsInOverflowMenu` returns zero matches (write path moved to General).
- `Grep` - `defaultIconSize` returns zero matches.
- `Grep` - `Log\.d\(` returns zero matches in `PlaybackSettingsFragment.kt`.
- Build: `.\a.ps1 dq` returns exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Static greps 11/11 PASS (all 6 binding.* refs = 0, all 5 settings field refs = 0, Log.d = 0). Build initially failed (unresolved `binding.headerGridView`/`containerGridView` at lines 464-465) - 02.5 removed the section registration and the orphan `KEY_GRID_VIEW_EXPANDED to prefs.getBoolean(...)` entry in `getSavedSectionStates()`. After 02.5 final cleanup: `.\a.ps1 dq` BUILD SUCCESSFUL in 1m 43s. File: `PlaybackSettingsFragment.kt` (-50 LOC: 3 listeners + icon size adapter + iconHelpGridSize tooltip + focus listener + 4 observer reconciliation blocks). Dev log recorded.

---

### Step 02.5 - Remove KEY_GRID_VIEW_EXPANDED constant and Grid View section registration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Delete the `KEY_GRID_VIEW_EXPANDED` companion-object constant (line 38) and every reference to it in the file. Delete the registration block inside `setupExpandableSections()` around lines 540-545 that registers `binding.headerGridView` + `binding.containerGridView` as a collapsible section. The other section registrations (Sorting, FileOps, PlayerUI, TouchZones, Behaviour) remain untouched.
>
> Decision per strategic §6.2: do **not** add a SharedPreferences migration to delete the orphan `section_grid_view_expanded` key from `playback_sections_state`. The key becomes unused at the code level; leaving it in the prefs file is harmless. Phase 03 will note this decision in `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `KEY_GRID_VIEW_EXPANDED` returns zero matches in `PlaybackSettingsFragment.kt`.
- `Grep` - `section_grid_view_expanded` returns zero matches in the same file (string literal also removed).
- `Grep` - `binding.headerGridView` returns zero matches.
- `Grep` - `binding.containerGridView` returns zero matches.
- `Grep` - `KEY_FILE_OPS_EXPANDED` still matches exactly once (sanity: adjacent constants untouched).
- Build: `.\a.ps1 dq` returns exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS (`KEY_GRID_VIEW_EXPANDED`=0, `section_grid_view_expanded`=0, `binding.headerGridView/containerGridView`=0, `KEY_FILE_OPS_EXPANDED`=4 still present - companion-object const + bindSectionToggle + 2x getSavedSectionStates ref pair). Also removed orphan entry `KEY_GRID_VIEW_EXPANDED to prefs.getBoolean(...)` in `getSavedSectionStates()` mapOf - not initially mentioned in step prompt but mandatory for compilation. BUILD SUCCESSFUL. File: `PlaybackSettingsFragment.kt` (-9 LOC: companion-object const + bindSectionToggle block + savedStates map entry). Note: SharedPreferences key `section_grid_view_expanded` in `playback_sections_state` file remains as a harmless orphan per strategic §6.2 (decision recorded in Phase 03.2). Dev log recorded.

---

### Step 02.6 - Verify SettingsSearchIndex still resolves correctly

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`
**Depends on:** Step 02.5

**Prompt for developer:**

> No code edit in this step. Re-confirm Phase 01.7 outcome: every `SettingsSearchIndex(..)` record whose `viewId` is `R.id.switchGridMode`, `R.id.switchHideGridActionButtons`, `R.id.switchFileOpsOverflowMenu`, `R.id.etIconSize`, `R.id.iconHelpGridSize`, or `R.id.tilIconSize` has `destination = SettingsSearchDestination.GENERAL`. The view ids still resolve at compile time because their XML declarations now live in `fragment_settings_general.xml` (portrait + landscape).
>
> If the build fails at this step with "unresolved reference: switchGridMode" or similar, that indicates an XML id was deleted from one layout but not added in the General layout in Phase 01 - return to Phase 01 and fix before continuing.

**Verification:**

- Build: `.\a.ps1 dq` returns exit 0 (proves R-class still defines all five ids from General).
- `Grep` - `SettingsSearchDestination.PLAYBACK` lines that appear within 8 lines above any of `R.id.switchGridMode|R.id.switchHideGridActionButtons|R.id.switchFileOpsOverflowMenu|R.id.etIconSize|R.id.iconHelpGridSize|R.id.tilIconSize` return zero matches.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Confirmed via Step 01.7 grep that the only six moved viewIds present in the search index (switchGridMode, etIconSize) both have `destination = SettingsSearchDestination.GENERAL` within 8 lines above. The other four (switchHideGridActionButtons, switchFileOpsOverflowMenu, iconHelpGridSize, tilIconSize) are not indexed - no records to update. R-class resolves because both ids now declared in `fragment_settings_general.xml` (portrait + landscape). BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Eight grid-related view ids return zero matches in both `fragment_settings_playback.xml` and `fragment_settings_playback.xml` (landscape).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 02, the move is functionally complete: controls live exclusively in General → Interface; Playback no longer contains them. SearchIndex routes to General. Phase 03 handles cleanup decisions (orphan string, expansion-key policy), runs `add_to_functionality_log.ps1`, regenerates the class catalog, runs the strings audit, and inserts the `Timber.d("S0254: …")` device-verification tags at the boundary into `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s). The deletion has no data migration. If Phase 02 is reverted while Phase 01 is kept, the controls appear in both tabs again, which is functionally safe (same backing settings, no divergence). Search index will continue routing to General; users searching for grid options will be sent to General even though Playback also shows them - acceptable interim state.
