# Phase 04 - Phone resource selection

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - phone side, independent of Phase 01-03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Give the phone a persisted set of "send to watch" resource ids and a full-screen picker to edit it - no watch-side or transfer-command change yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/WearResourceSelectionRepositoryImpl.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionActivity.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionAdapter.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionViewModel.kt` | New | ≤ 70 |
| `app_v2/src/main/res/layout/activity_wear_resource_selection.xml` | New | ≤ 90 |
| `app_v2/src/main/res/layout-land/activity_wear_resource_selection.xml` | New | ≤ 90 |
| `app_v2/src/main/res/layout/item_wear_resource_selection.xml` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 30 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/WearResourceSelectionRepositoryImplTest.kt` | New | ≤ 110 |

---

## Steps

### Step 04.1 - Persist the resource selection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/WearResourceSelectionRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `class WearResourceSelectionRepositoryImpl @Inject constructor(@ApplicationContext context: Context)`, backed by its own SharedPreferences file (`wear_resource_selection`), following the same shape as `PermissionRequestMarkerRepositoryImpl`: no interface, injected by concrete type. Expose `fun getSelectedIds(): Set<Long>` (empty set when the underlying key has never been written - not "select all"), `fun setSelectedIds(ids: Set<Long>)`, and `fun selectAll(allIds: Set<Long>)` as a thin wrapper over `setSelectedIds`. Store ids as a `Set<String>` via `getStringSet`/`putStringSet` since `SharedPreferences` has no native long-set type.

**Why:**

Strategic §3.2 "Совместимость данных" is explicit: "Отсутствующий набор при первом запуске трактуется как «ничего не выбрано», а не как «выбрано всё»" - an update that reinterprets an absent set as "everything" would silently push every registered resource to the watch, which is the risk strategic §7's top row names directly.

**Verification:**

- `Glob` - `WearResourceSelectionRepositoryImpl.kt` exists.
- `Grep` - `fun getSelectedIds` and `fun selectAll` both present.
- `Grep` - `Log\.d\(` returns zero hits in this file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 04.1: WearResourceSelectionRepositoryImpl added - own SharedPreferences file wear_resource_selection, getSelectedIds/setSelectedIds/selectAll, ids stored as a string set. An absent key returns an empty set, never all ids. No android.util.Log calls. Verified: .\a.ps1 fk exit 0.

---

### Step 04.2 - Add the full-screen resource picker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/wearresources/WearResourceSelectionAdapter.kt`, `app_v2/src/main/res/layout/activity_wear_resource_selection.xml`, `app_v2/src/main/res/layout-land/activity_wear_resource_selection.xml`, `app_v2/src/main/res/layout/item_wear_resource_selection.xml`, `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `WearResourceSelectionActivity`, a checkbox-per-row RecyclerView screen listing every resource from `ResourceRepository.getAllResources()`, following the multi-select checkbox pattern already used by `ui/addresource/AddResourceActivity.kt` + `ResourceToAddAdapter.kt`. Pre-check rows present in `WearResourceSelectionRepositoryImpl.getSelectedIds()`. Add a "Select all" action that calls `selectAll` with every listed id, and persist on each individual toggle rather than only on exit. Register the activity in the manifest. Add the title, row and "Select all" strings through `set-android-string.ps1 -Action add`, prefixed `wear_resource_selection_`.

**Why:**

Owner ruling, `/ui-clarify` 2026-08-18, strategic §3.3: a separate full screen, not an expandable block inside the Wear Companion bottom sheet and not a second sheet stacked on top of it - the owner rejected the collapsible-block alternative because a hundred registered resources would make the sheet unmanageably long.

**Correction applied during execution:** the step named no ViewModel, which would have left the activity reading `ResourceRepository` directly - a layering break Rule 3 and the `activity-logic` gate both refuse. `WearResourceSelectionViewModel` was added and the activity kept to wiring only. The manifest entry also drops `orientation|screenSize` from `configChanges`: keeping them would suppress recreation and the `layout-land` variant this step requires would never be applied.

**Verification:**

- `Glob` - `WearResourceSelectionActivity.kt`, both `activity_wear_resource_selection.xml` layouts, and `item_wear_resource_selection.xml` all exist.
- `Grep` - `WearResourceSelectionActivity` present in `AndroidManifest.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_resource_selection_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fr` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 04.2: WearResourceSelectionActivity (BaseActivity + ViewBinding) with WearResourceSelectionViewModel and WearResourceSelectionAdapter; portrait list and a landscape two-column grid, both carrying the same ids; Select all and every individual tick persist immediately. Registered in the manifest with configChanges=keyboardHidden so rotation recreates and layout-land actually applies. Strings wear_resource_selection_title/hint/select_all/empty added EN/RU/UK - the empty state names the action to take. Verified: .\a.ps1 fc exit 0; check_strings_localized exit 0.

---

### Step 04.3 - Wire the entry point from the Companion sheet

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a button to `WearSyncScreen` in `WearSyncSettingsFragment.kt` that launches `WearResourceSelectionActivity` via `Intent`. Gate its visibility on `MediaCapabilities.supportsWearCompanion`, the same flag `GeneralSettingsBackupHelper.setupWearCompanionButton()` already checks before showing the sheet's own entry point - the whole sheet is unreachable when it is false, so this is defense-in-depth, not the only gate.

**Why:**

Strategic §3.2 "Flavor" ties every Wear Companion surface to `SUPPORT_WEAR_COMPANION` (on in `standard`, `noLegal`, `legacy`; off in `lite`, `photos`, `vr`, per `docs/FLAVOR_MATRIX.md`) - a new entry point inside the same sheet inherits that gate rather than reintroducing a raw `BuildConfig` check in `src/main`, which Rule 14 forbids.

**Verification:**

- `Grep` - `WearResourceSelectionActivity` present in `WearSyncSettingsFragment.kt`.
- `Grep` - `supportsWearCompanion` present in `WearSyncSettingsFragment.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 04.3: WearSyncScreen gains an outlined button under Push to watch that opens WearResourceSelectionActivity, shown only when MediaCapabilities.supportsWearCompanion is true - the capability is field-injected into the fragment rather than read from BuildConfig, so Rule 14 holds. Verified: .\a.ps1 fk exit 0.

---

### Step 04.4 - Unit-test the selection contract

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/WearResourceSelectionRepositoryImplTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a Robolectric test following `PermissionRequestMarkerRepositoryImplTest`'s shape (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`, `RuntimeEnvironment.getApplication()`). Assert: `getSelectedIds()` is empty before any write; `setSelectedIds(setOf(1L, 2L))` then `getSelectedIds()` returns exactly that set; `selectAll(setOf(1L, 2L, 3L))` then `getSelectedIds()` returns all three; deselecting one id (calling `setSelectedIds` with it removed) persists and is reflected on the next read.

**Why:**

Strategic §11 criterion 5 - "его выбор сохраняется между запусками" - is a strategic-level pass condition, and the empty-means-nothing contract from Step 04.1's Why is the one behaviour this repository exists to get right; a test is the only durable proof it stays right after the next unrelated change touches this file.

**Verification:**

- `Glob` - `WearResourceSelectionRepositoryImplTest.kt` exists.
- `Grep` - `RobolectricTestRunner` present.
- `.\a.ps1 fu` - the new test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 04.4: WearResourceSelectionRepositoryImplTest added - Robolectric, @Config(sdk=[34]), RuntimeEnvironment.getApplication(). Asserts an absent key reads as an empty set (never all ids), setSelectedIds round-trips, selectAll stores every id, and a deselection survives a fresh repository instance. Verified: check-standard-fast -Mode Unit -Tests *WearResourceSelectionRepositoryImplTest* exit 0; TEST-...WearResourceSelectionRepositoryImplTest.xml tests=4 failures=0 errors=0.
- 2026-08-18 - Phase-04 boundary audit (CODE_AUDIT_PROTOCOL layers 1-3): no P0/P1. One P2 fixed in place - getInitialFocusView() ended in an unreachable elvis branch (binding.btnSelectAll is non-null), which also left the empty state with no D-pad target; now btnSelectAll.takeIf { it.isVisible } falls through to the toolbar. Layer 2 clean (repeatOnLifecycle, viewModelScope, no bare collect); layer 3 clean (row listener re-set per bind, no retained context). UI evidence (S1338 gate), placement per owner ruling /ui-clarify 2026-08-18 strategic 3.3: reproduce rather than stored - both captures exceeded the 64 KB per-file budget the durable-evidence rule sets, so the recipe replaces them. Open the phone app, Settings, Wear sync, the resource picker, in portrait and then in landscape. Expected in portrait: toolbar "Resources for the watch", the hint line, "Select all", and unticked rows carrying name and path, an absent selection reading as nothing selected. Expected in landscape - toolbar 'Resources for the watch', hint, 'Select all', unticked rows with name+path (absent set reads as nothing selected);  - two-column grid, Select all moved right, no full-width button. Verified: a.ps1 fk exit 0; post-change PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`WearResourceSelectionRepositoryImpl` holds a persisted, unit-tested id set with the "absent means nothing" contract, and the phone screen to edit it is reachable from the Companion sheet. Nothing reads this set yet - Phase 05.1 is the first consumer, when `SendResourcesToWatchUseCase` switches from "every registered resource" to "the selected ones."

---

## Rollback Plan

Revert phase commit(s) - new files only, plus one additive button in `WearSyncSettingsFragment.kt` and one manifest entry; no existing transfer behaviour changes until Phase 05 reads the new repository.
