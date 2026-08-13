# Phase 03 - Editor three-path add flow

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Give the panel editor three explicit add paths - external app (existing), OS part, our feature or resource - and persist the chosen target as an `INTERNAL_ROUTE` tile.

---

## Prerequisites

- [ ] Phase 01 & Phase 02 ✅ Done.
- [ ] Strategic §6.1 (disabled-feature presentation) and §6.4 (chooser pattern) resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/InternalRoutePickerDialogFragment.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/OsShortcutPickerDialogFragment.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/dialog_panel_route_picker.xml` | New | n/a |
| `app_v2/src/main/res/layout-land/dialog_panel_route_picker.xml` | New | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelViewModel.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt` | Modified | ≤ 200 |

---

## Steps

### Step 03.1 - Add strings for the three paths and route/OS labels (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add, in one lockstep call via `scripts/utils/set-android-string.ps1 -Action add` (parity across EN/RU/UK), the chooser title and three category labels (External app / OS part / Our feature or resource), plus labels for each feature route and each curated OS target. Reuse existing feature strings where they already exist; only add what is missing. Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - the new category keys present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS (`check_strings_localized.ps1 -KeyPrefix app_launch_panel_` exit 0, all 30 keys EN/RU/UK). Added chooser title + 3 category labels + 3 picker titles + disabled-route hint via `set-android-string.ps1 -Action add`. Feature/OS labels were added in 01.3 (catalog dependency).

---

### Step 03.2 - Add the internal-route picker dialog

**Files:** `ui/applaunchpanel/edit/InternalRoutePickerDialogFragment.kt`, `res/layout/dialog_panel_route_picker.xml`, `res/layout-land/dialog_panel_route_picker.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a dialog listing the feature routes from `InternalRouteCatalog`, annotated by `ResolvePanelRouteAvailabilityUseCase` (hide build-unavailable; show available-but-disabled per §6.1). Return the chosen route key to the host via `setFragmentResult`. Follow `AppPickerDialogFragment` for sizing, accessibility (`DialogAccessibilityHelper`), keyboard (`DialogKeyboardDelegate`) and lifecycle-safe collection. Provide both portrait and landscape layouts.

**Verification:**

- `Glob` - the fragment and both layout files exist.
- `Grep` - `InternalRouteCatalog` and `setFragmentResult` referenced.
- `Glob` - `res/layout-land/dialog_panel_route_picker.xml` exists (landscape parity).

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: InternalRoutePickerDialogFragment.kt (+185 LOC), dialog_panel_route_picker.xml (portrait+land). Lists catalog routes annotated by `ResolvePanelRouteAvailabilityUseCase.all()`; hides build-unavailable, textual disabled-hint (§6.1, not colour-only); reuses item_app_picker_row + DialogAccessibilityHelper/DialogKeyboardDelegate.

---

### Step 03.3 - Add the OS-shortcut and resource picker dialogs

**Files:** `ui/applaunchpanel/edit/OsShortcutPickerDialogFragment.kt`, `ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add two dialogs reusing the route-picker layout. The OS dialog lists `OsShortcutCatalog.available(context)` (resolvable targets only) and returns the chosen OS target key. The resource dialog lists resources (reuse the resource-selection pattern from the resource-launch widget config) and returns the chosen resource id. Both return via `setFragmentResult`; both keep `DialogAccessibilityHelper`/`DialogKeyboardDelegate` wiring.

**Verification:**

- `Glob` - both fragments exist.
- `Grep` - `OsShortcutCatalog` referenced in the OS dialog.
- `Grep` - `resourceDao|ResourceEntity` referenced in the resource dialog.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/3 PASS; 1 intentional deviation. PASS: both fragments exist; `OsShortcutCatalog` referenced in OS dialog. DEVIATION: resource dialog lists via domain `ResourceRepository.getAllResourcesSync()` (mirroring `AppPickerDialogFragment` injecting a use case), NOT `resourceDao`/`ResourceEntity` - a DialogFragment touching the DAO/entity would break layer discipline (UI -> ViewModel -> UseCase -> Repository, CLAUDE.md). Predicate intent (dialog lists resources) is met. Files: OsShortcutPickerDialogFragment.kt (+150 LOC), ResourcePickerDialogFragment.kt (+160 LOC).

---

### Step 03.4 - Persist new tile kinds in the editor ViewModel

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelViewModel.kt`
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> Add `addInternalFeatureToSlot(slot, routeKey)`, `addOsShortcutToSlot(slot, targetKey)`, `addResourceToSlot(slot, resourceId)`. Each persists an `AppLaunchPanelTile` of type `INTERNAL_ROUTE` whose `targetId` is built via `AppLaunchPanelRouteTarget.encode()`. Keep all persistence in `viewModelScope` through the repository (Rule 3). Leave `addAppToSlot` untouched.

**Verification:**

- `Grep` - `addInternalFeatureToSlot`, `addOsShortcutToSlot`, `addResourceToSlot` all present.
- `Grep` - `AppLaunchPanelTileType.INTERNAL_ROUTE` referenced.
- `Grep` - `encode(` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: EditAppLaunchPanelViewModel.kt (+30 LOC). Three add* methods persist INTERNAL_ROUTE tiles via `AppLaunchPanelRouteTarget.encode()` in viewModelScope; `addAppToSlot` untouched.

---

### Step 03.5 - Wire the category chooser pre-step in the editor

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> When a slot is tapped (empty or replace), first show the three-path chooser (§6.4 default: a `MaterialAlertDialogBuilder` items dialog: External app / OS part / Our feature or resource), then open the matching picker for the same slot. Register fragment-result listeners for the three new dialogs and forward to the ViewModel's new methods. Keep the existing app-picker path for the External-app branch. No business logic in the Activity beyond dialog routing.

**Verification:**

- `Grep` - all three new dialog `RESULT_KEY`s have `setFragmentResultListener` registrations.
- `Grep` - `addInternalFeatureToSlot|addOsShortcutToSlot|addResourceToSlot` invoked.
- `Grep -n "Log\.d\("` on this file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: EditAppLaunchPanelActivity.kt (+70 LOC). 3-path chooser pre-step (External app / OS part / Our feature or resource) replaces direct app-picker on tap and Replace; third path expands to feature/resource sub-choice (§5.1.A). 3 new fragment-result listeners forward to ViewModel; External-app path unchanged. No business logic beyond dialog routing.

---

### Step 03.6 - Distinguish tile kinds in the edit grid

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelTileAdapter.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Ensure the edit grid renders feature/OS/resource tiles with their resolved label + icon and an accessible `contentDescription` that names the kind (not colour-only differentiation, strategic §3.2). No new layout file if the existing tile item suffices; otherwise extend it with both orientations.

**Verification:**

- `Grep` - `contentDescription` set for tiles in the adapter or its item layout.
- Build passes via `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: EditAppLaunchPanelTileAdapter.kt (+18 LOC). contentDescription now "<label>, <kind>" (App / System shortcut / App feature / Resource) decoding INTERNAL_ROUTE targetId - kind named for TalkBack, not colour-only. `.\a.ps1 fc` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `scripts/check_strings_localized.ps1 -KeyPrefix "app_launch_panel_"` exit 0 (32 keys EN/RU/UK).
- [~] Dev log entry added for every file in "Files Touched" - batched at Phase 05.
- [~] `dev/CATALOG/app_v2.jsonl` regenerated - batched at Phase 05 Step 05.3.

---

## Handoff Notes to Next Phase

The editor offers all three paths and persists `INTERNAL_ROUTE` tiles. Phase 04 enriches the first-run seed using the same catalogs and availability resolver.

---

## Rollback Plan

Revert the phase commit(s) - new dialogs/strings/layouts drop out and the editor returns to app-only adding; persisted internal-route tiles still resolve via Phase 02. No migration involved.
