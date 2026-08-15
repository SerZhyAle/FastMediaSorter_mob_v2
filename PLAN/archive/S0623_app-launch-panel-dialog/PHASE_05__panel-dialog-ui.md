# Phase 05 - Panel Dialog UI

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, 04
**Blocks:** Phase 06
**Steps done:** 6 / 6
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Build the quick-launch panel: a transparent host Activity launched from the gesture dispatcher, hosting a large 15-tile grid dialog. Tapping a tile launches its target; tapping an empty slot or the Edit affordance opens the Edit screen.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (`EditAppLaunchPanelActivity` exists to route into).
- [ ] Mirror reference read: `ui/profile/DeviceProfilePickerDialogFragment.kt`, `ui/profile/DeviceProfileTileAdapter.kt`, helpers `DialogAccessibilityHelper`, `DialogKeyboardDelegate`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelActivity.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelDialogFragment.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelViewModel.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/AppLaunchPanelTileAdapter.kt` | New | ≤ 120 |
| `app_v2/src/main/res/layout/dialog_app_launch_panel.xml` | New | - |
| `app_v2/src/main/res/layout/item_app_launch_panel_tile.xml` | New | - |
| `app_v2/src/main/res/values/strings.xml` (+ ru/uk) | Modified | - |
| `app_v2/src/main/res/values/themes.xml` | Modified | - |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 400 |

---

## Steps

### Step 05.1 - Panel strings + transparent theme

**Files:** `res/values/strings.xml` (+ ru/uk), `res/values/themes.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `set-android-string.ps1 -Action add`: `app_launch_panel_title` ("Quick launch"), `app_launch_panel_edit_action` ("Edit"). In `themes.xml` add a transparent dialog-host theme `Theme.FMS.TransparentPanelHost` (parent a translucent app theme, `windowIsTranslucent = true`, `windowBackground = @android:color/transparent`, `windowNoTitle = true`) for `AppLaunchPanelActivity`. Reuse an existing transparent theme if one already exists - grep `themes.xml` for `Translucent`/`Transparent` first and extend rather than duplicate (Rule: reuse existing).

**Verification:**

- `Grep` - `app_launch_panel_title` present in `values/strings.xml`, `values-ru`, `values-uk`.
- `Grep` - `Theme.FMS.TransparentPanelHost` (or the reused theme name referenced by the Activity) present in `themes.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_title"` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Panel dialog + tile layouts

**Files:** `res/layout/dialog_app_launch_panel.xml`, `res/layout/item_app_launch_panel_tile.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> `dialog_app_launch_panel.xml`: a header row (title `TextView` + an "Edit" `MaterialButton`/icon) above a `RecyclerView` for the 15-tile grid. `item_app_launch_panel_tile.xml`: a large `MaterialCardView` with a centered app `ImageView` icon and a label `TextView` below (mirror `item_device_profile_tile.xml`), touch target >= 88dp, `android:focusable="true"`, `nextFocus*` left to the runtime adapter/GridLayoutManager. Empty-slot styling: a dashed/ghost "add" look driven by an adapter view-state, not a separate layout. The dialog window is sized programmatically in the fragment's `onStart` (like `DeviceProfilePickerDialogFragment`), so no `layout-land` variant is required - note this explicitly. Only `?attr/`/`@color/` (Rule 19).

**Verification:**

- `Glob` - both layouts exist.
- `Grep -n "#[0-9a-fA-F]\{6\}"` across both - zero hardcoded-hex hits.
- `Grep` - `RecyclerView` in `dialog_app_launch_panel.xml`.
- `Grep` - `android:focusable` in `item_app_launch_panel_tile.xml`.
- Note recorded in phase file: no `layout-land` needed (programmatic window sizing) - Rule 11 satisfied by explicit note.

**Status:** `[x]` done

---

### Step 05.3 - Panel tile adapter

**Files:** `ui/applaunchpanel/AppLaunchPanelTileAdapter.kt`
**Depends on:** Step 05.2, Phase 03 (`AppLaunchPanelTileUi`)

**Prompt for developer:**

> `AppLaunchPanelTileAdapter` binds the 15-slot `List<AppLaunchPanelTileUi>` to `item_app_launch_panel_tile`. Filled tile: icon + label, `contentDescription` = label. Empty slot: ghost "add" appearance, `contentDescription` = the empty-slot string. Expose `onTileClick: (AppLaunchPanelTileUi) -> Unit`. Set each card `focusable`/`clickable` for D-pad/mouse (Rule 16).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class AppLaunchPanelTileAdapter` present.
- `Grep` - `onTileClick` present.
- `Grep` - `contentDescription` present.

**Status:** `[x]` done

---

### Step 05.4 - Panel ViewModel

**Files:** `ui/applaunchpanel/AppLaunchPanelViewModel.kt`
**Depends on:** Phase 03 (`ResolveAppLaunchPanelTilesUseCase`, `LaunchAppLaunchPanelTileUseCase`, `SeedDefaultAppLaunchPanelUseCase`)

**Prompt for developer:**

> `@HiltViewModel class AppLaunchPanelViewModel @Inject constructor(resolveTiles: ResolveAppLaunchPanelTilesUseCase, private val launchTile: LaunchAppLaunchPanelTileUseCase, private val seed: SeedDefaultAppLaunchPanelUseCase)`. On init, run `seed()` in `viewModelScope`, then expose `StateFlow<List<AppLaunchPanelTileUi>>` from `resolveTiles()`. `fun onTileSelected(tile): PanelResult` returns whether to dismiss/launch vs route to Edit (empty slot). `fun launch(tile)` delegates to `launchTile.launch`. No Android UI imports beyond what the UseCases expose.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@HiltViewModel` and `class AppLaunchPanelViewModel` present.
- `Grep` - `seed(` invoked and `resolveTiles(` referenced.
- `Grep` - `viewModelScope` present.

**Status:** `[x]` done

---

### Step 05.5 - Panel dialog fragment + host Activity

**Files:** `ui/applaunchpanel/AppLaunchPanelDialogFragment.kt`, `ui/applaunchpanel/AppLaunchPanelActivity.kt`
**Depends on:** Steps 05.3, 05.4, Phase 04 (`EditAppLaunchPanelActivity`)

**Prompt for developer:**

> `AppLaunchPanelDialogFragment : DialogFragment` (`@AndroidEntryPoint`, mirror `DeviceProfilePickerDialogFragment`): `setStyle(STYLE_NO_TITLE, 0)`, ViewBinding `dialog_app_launch_panel`, `GridLayoutManager` span 3 portrait / 5 landscape, observe the ViewModel via `collectOnLifecycle`. Tap a filled tile -> `viewModel.launch(tile)` then `dismiss()` + `requireActivity().finish()`; tap an empty slot or the Edit button -> `startActivity(Intent(context, EditAppLaunchPanelActivity::class.java))`. Apply `DialogAccessibilityHelper.applyInitialFocus` + `DialogKeyboardDelegate` in `onStart`, and size the window there. `AppLaunchPanelActivity : AppCompatActivity` (`@AndroidEntryPoint`, transparent theme from 05.1): in `onCreate`, if no panel dialog is shown, show `AppLaunchPanelDialogFragment`; `finish()` when it is dismissed so the transparent host does not linger. No business logic in the Activity (Rule 3).

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class AppLaunchPanelDialogFragment` and `DialogFragment` present.
- `Grep` - `class AppLaunchPanelActivity` and `@AndroidEntryPoint` present.
- `Grep` - `EditAppLaunchPanelActivity::class.java` referenced (Edit + empty-slot route).
- `Grep` - `collectOnLifecycle` (or `repeatOnLifecycle`) present.

**Status:** `[x]` done

---

### Step 05.6 - Register the host Activity in the manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 05.5

**Prompt for developer:**

> Add `<activity android:name=".ui.applaunchpanel.AppLaunchPanelActivity" android:exported="false" android:theme="@style/Theme.FMS.TransparentPanelHost" android:excludeFromRecents="true" android:launchMode="singleTask" />`. `excludeFromRecents` + transparent theme keep it from appearing as a normal task when launched over another app from the gesture dispatcher.

**Verification:**

- `Grep` - `.ui.applaunchpanel.AppLaunchPanelActivity` present in the manifest.
- `Grep` - `android:theme="@style/Theme.FMS.TransparentPanelHost"` (or the reused theme) on that entry.
- `Grep` - `android:excludeFromRecents="true"` on that entry.
- Build: `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_title"` exits 0.
- [ ] Dev log entry added; `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

`AppLaunchPanelActivity` is a launchable transparent host that shows the panel over the current foreground app. Phase 06 wires the gesture dispatcher to start it.

---

## Rollback Plan

Revert phase commit(s). No dispatcher wiring yet - the panel is unreachable until Phase 06, so reverting is isolated.
