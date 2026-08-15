# Phase 04 - Edit Panel Screen

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, 03
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Build the standalone `Edit panel` screen: a 15-slot editable grid where the user adds an external app to a slot, and uses long-press to move/replace/remove tiles. Mirrors `KeybindingRemapActivity` as the manage-list host.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (`QueryLaunchableAppsUseCase`, `ResolveAppLaunchPanelTilesUseCase`, repository operations).
- [ ] Mirror reference read: `ui/keybinding/KeybindingRemapActivity.kt`, `ui/profile/DeviceProfileTileAdapter.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelViewModel.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelTileAdapter.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/AppPickerDialogFragment.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/AppPickerAdapter.kt` | New | ≤ 90 |
| `app_v2/src/main/res/layout/activity_edit_app_launch_panel.xml` | New | - |
| `app_v2/src/main/res/layout-land/activity_edit_app_launch_panel.xml` | New | - |
| `app_v2/src/main/res/layout/item_app_launch_panel_edit_tile.xml` | New | - |
| `app_v2/src/main/res/layout/dialog_app_picker.xml` | New | - |
| `app_v2/src/main/res/layout/item_app_picker_row.xml` | New | - |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 400 |

---

## Steps

### Step 04.1 - Add Edit-panel strings (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `scripts/utils/set-android-string.ps1 -Action add` (one lockstep call per key, EN/RU/UK parity enforced): `edit_app_launch_panel_title` ("Edit panel"), `app_launch_panel_empty_slot` ("Empty - tap to add"), `app_launch_panel_add_app` ("Add app"), `app_launch_panel_action_move` ("Move"), `app_launch_panel_action_replace` ("Replace"), `app_launch_panel_action_remove` ("Remove"), `app_launch_panel_own_app_label` (the app's own short name), `app_picker_title` ("Choose an app"). Check copy against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist).

**Verification:**

- `Grep` - each key present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "edit_app_launch_panel"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 04.2 - Edit-tile + app-picker layouts (portrait + landscape)

**Files:** `res/layout/activity_edit_app_launch_panel.xml`, `res/layout-land/activity_edit_app_launch_panel.xml`, `res/layout/item_app_launch_panel_edit_tile.xml`, `res/layout/dialog_app_picker.xml`, `res/layout/item_app_picker_row.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> `activity_edit_app_launch_panel.xml` (portrait) + its `layout-land` counterpart: a toolbar/title plus a `RecyclerView` for the 15-slot grid. Both variants required (Rule 11). The Activity sets `GridLayoutManager` span at runtime (3 portrait / 5 landscape), so the layouts only differ in padding/title placement. `item_app_launch_panel_edit_tile.xml`: a `MaterialCardView` with an app `ImageView` icon and a label `TextView` (mirror `item_device_profile_tile.xml`), large touch target (>= 88dp), `android:focusable="true"`. `dialog_app_picker.xml`: a titled `RecyclerView`. `item_app_picker_row.xml`: icon + label row. Use only `?attr/` / `@color/` references - no hardcoded hex (Rule 19).

**Verification:**

- `Glob` - all five layout files exist, including `res/layout-land/activity_edit_app_launch_panel.xml`.
- `Grep -n "#[0-9a-fA-F]\{6\}"` across the five layouts - zero hardcoded-hex hits.
- `Grep` - `androidx.recyclerview.widget.RecyclerView` present in `activity_edit_app_launch_panel.xml` and its land variant.
- `Grep` - `android:focusable` present in `item_app_launch_panel_edit_tile.xml`.

**Status:** `[x]` done

---

### Step 04.3 - App-picker adapter + dialog

**Files:** `ui/applaunchpanel/edit/AppPickerAdapter.kt`, `ui/applaunchpanel/edit/AppPickerDialogFragment.kt`
**Depends on:** Steps 04.1, 04.2, Phase 03 (`QueryLaunchableAppsUseCase`)

**Prompt for developer:**

> `AppPickerAdapter` binds `List<LaunchableApp>` to `item_app_picker_row` (icon + label), exposing an `onPicked: (LaunchableApp) -> Unit`. Set `contentDescription` to the app label for TalkBack. `AppPickerDialogFragment` is a `DialogFragment` that loads launchable apps via the injected `QueryLaunchableAppsUseCase` (collect on `viewLifecycleOwner` with `collectOnLifecycle`/`repeatOnLifecycle`, not a bare `lifecycleScope.launch`), shows them, and returns the chosen package to the host via a `FragmentResult` or a callback interface. `@AndroidEntryPoint`.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class AppPickerAdapter` and `class AppPickerDialogFragment` present.
- `Grep` - `QueryLaunchableAppsUseCase` referenced in the dialog.
- `Grep` - `contentDescription` set in the adapter.
- `Grep -n "lifecycleScope.launch \{ .*collect"` - zero unsafe-collection hits.

**Status:** `[x]` done

---

### Step 04.4 - Edit-tile adapter

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelTileAdapter.kt`
**Depends on:** Steps 04.1, 04.2, Phase 03 (`AppLaunchPanelTileUi`)

**Prompt for developer:**

> `EditAppLaunchPanelTileAdapter` binds the 15-slot `List<AppLaunchPanelTileUi>` to `item_app_launch_panel_edit_tile`. A filled tile shows icon + label; an empty slot shows an "add" placeholder. Expose `onTileClick: (AppLaunchPanelTileUi) -> Unit` (empty slot or filled tile tap) and `onTileLongClick: (AppLaunchPanelTileUi) -> Unit` (opens the move/replace/remove menu). Set `contentDescription` per tile (resolved label, or the empty-slot string). `MaterialCardView` focusable for D-pad.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class EditAppLaunchPanelTileAdapter` present.
- `Grep` - `onTileLongClick` present.
- `Grep` - `contentDescription` present.

**Status:** `[x]` done

---

### Step 04.5 - Edit ViewModel + Activity

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelViewModel.kt`, `ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt`
**Depends on:** Steps 04.3, 04.4

**Prompt for developer:**

> `EditAppLaunchPanelViewModel @Inject constructor(resolveTiles: ResolveAppLaunchPanelTilesUseCase, private val repository: AppLaunchPanelRepository)`: expose `StateFlow<List<AppLaunchPanelTileUi>>` from `resolveTiles()` in `viewModelScope`. Functions: `addAppToSlot(slot, packageName)` (build an `EXTERNAL_APP` tile, `repository.setTile`), `removeTile(slot)`, `moveTile(from, to)`, `replaceTile(slot)` (UI triggers picker then `addAppToSlot`). All mutations in `viewModelScope`. `EditAppLaunchPanelActivity : BaseActivity<...>` (`@AndroidEntryPoint`): inflate the binding, set `GridLayoutManager` span 3 portrait / 5 landscape, wire the adapter, observe the ViewModel via `collectOnLifecycle`, present the long-press action menu (Move/Replace/Remove) and the `AppPickerDialogFragment`. Provide `getInitialFocusView()` for D-pad. No business logic in the Activity - delegate to the ViewModel (Rule 3).

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class EditAppLaunchPanelViewModel` and `@HiltViewModel` present.
- `Grep` - `class EditAppLaunchPanelActivity` and `@AndroidEntryPoint` present.
- `Grep` - `collectOnLifecycle` (or `repeatOnLifecycle`) present in the Activity.
- `Grep` - `addAppToSlot`, `removeTile`, `moveTile` present in the ViewModel.

**Status:** `[x]` done

---

### Step 04.6 - Register the Activity in the manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 04.5

**Prompt for developer:**

> Add `<activity android:name=".ui.applaunchpanel.edit.EditAppLaunchPanelActivity" android:exported="false" android:label="@string/edit_app_launch_panel_title" />`. Standard (non-transparent) Activity. No intent filter - it is launched internally only.

**Verification:**

- `Grep` - `.ui.applaunchpanel.edit.EditAppLaunchPanelActivity` present in the manifest.
- `Grep` - `android:exported="false"` on that activity entry.
- Build: `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_"` exits 0.
- [ ] Landscape counterpart present for every edited `res/layout/*.xml` that has one (Rule 11).
- [ ] Dev log entry added; `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

`EditAppLaunchPanelActivity` is launchable via an explicit `Intent`. Phase 05's panel dialog routes its "Edit" affordance and empty-slot taps here.

---

## Rollback Plan

Revert phase commit(s). The Activity has no entry point until Phase 05 wires it; manifest and strings additions are additive.
