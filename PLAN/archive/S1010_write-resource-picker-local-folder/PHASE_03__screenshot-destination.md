# Phase 03 - Screenshot destination picker

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (parallel-independent of Phase 02/04; only Phase 05 depends on it)
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Add the "Local Folder" leading option to `EdgeGestureConfigDialogFragment.showDestinationPicker()` - the sole
insertion point backing `screenshotDestinationResourceId`. This is a separate, near-duplicate method in a
different host (`DialogFragment`, not `Fragment`) - no launcher exists here yet, unlike `OperationsSettingsFragment`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `EdgeGestureConfigDialogFragment.kt` confirmed at
      `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` -
      `showDestinationPicker` at lines 170-188, no existing `ActivityResultLauncher` field in this class.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` | Modified | existing + ≤ 25 |

---

## Steps

### Step 03.1 - Register the SAF launcher and construct the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a field-init `ActivityResultLauncher<Uri?>` (this class has none yet - `registerForActivityResult` must
> be called before the fragment reaches `CREATED`, same constraint as `OperationsSettingsFragment`'s existing
> launchers): `private val localFolderDestinationPickerLauncher: ActivityResultLauncher<Uri?> =
> registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
> localFolderDestinationPickerManager.onFolderPicked(uri) }`. Add a `by lazy` field next to
> `gestureActionPickerManager`: `private val localFolderDestinationPickerManager by lazy {
> LocalFolderDestinationPickerManager(this, viewModel, localFolderDestinationPickerLauncher) }`. Import
> `LocalFolderDestinationPickerManager` from `com.sza.fastmediasorter.ui.settings.helpers`, plus
> `androidx.activity.result.ActivityResultLauncher`, `androidx.activity.result.contract.ActivityResultContracts`
> and `android.net.Uri` (none of the three are imported in this file yet).

**Verification:**

- `Grep` - `localFolderDestinationPickerLauncher` present as an `ActivityResultLauncher<Uri?>` field.
- `Grep` - `localFolderDestinationPickerManager` present as a `by lazy` field constructing
  `LocalFolderDestinationPickerManager`.
- `Grep` - `import androidx.activity.result.contract.ActivityResultContracts` present.

**Status:** `[x]` done

---

### Step 03.2 - Insert the sentinel into showDestinationPicker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `showDestinationPicker(currentResourceId, onPicked)`, change `loader = { viewModel.destinations.value }` to
> `loader = { listOf(LocalFolderDestinationPickerManager.sentinelItem(requireContext())) +
> viewModel.destinations.value }`, and change `onSelected = onPicked` to `onSelected =
> localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked)`. Every other
> `ListSelectionConfig` field stays untouched.

**Verification:**

- `Grep` - `LocalFolderDestinationPickerManager.sentinelItem(requireContext())` present inside
  `showDestinationPicker`'s `loader`.
- `Grep` - `localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked)` present as the
  `onSelected` value.
- `pwsh -NoProfile -File ./a.ps1 dq` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 dq` exits 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `EdgeGestureConfigDialogFragment.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05.
- [x] Phase-boundary audit run (Layer 1 always; Layer 2 - `EdgeGestureConfigDialogFragment` re-inflates its
      binding on `onConfigurationChanged` without recreating the Fragment itself, per its own S1123 comment;
      confirm the new launcher/manager fields, unlike `binding`, do not need re-creation on that path since
      they are not view-bound).

---

## Step Log

- 2026-08-02 - Step 03.1: Verification 3/3 PASS. Files: ui/settings/gesture/EdgeGestureConfigDialogFragment.kt (+`localFolderDestinationPickerLauncher`, +`localFolderDestinationPickerManager`, +4 imports). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Step 03.2: Verification 3/3 PASS (`a.ps1 dq` exit 0). Files: ui/settings/gesture/EdgeGestureConfigDialogFragment.kt (`loader` prepends the sentinel, `onSelected` routed through `wrapOnSelected`). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Screenshot (S1338 UI-phase requirement): `temp/S1010/phase03_edge_gesture_screenshot_destination_local_folder.png`.
  Device `emulator-5554`. Captured inside the "Edge gestures" dialog (`EdgeGestureConfigDialogFragment` - zone tabs and
  Close button visible behind it), from the "Save screenshots to.." destination row. Dialog rows in order:
  **"Local Folder" (first/top)**, "Downloads". Matches the owner's verbatim placement ruling in strategic §3.1.
- 2026-08-02 - Phase-boundary audit (Layer 1 + Layer 2 + Layer 3): no P0/P1/P2 findings. Layer 1 - two additive fields
  and two expression changes; the picker method keeps its shape. Layer 2 (the criterion above) - confirmed the two new
  fields are correctly NOT re-created in `onConfigurationChanged`: unlike `manager`/`_binding`, neither captures the
  binding or any view, so the S1123 re-inflate path needs no change. `localFolderDestinationPickerLauncher` in
  particular MUST NOT be re-registered there - `registerForActivityResult` after the fragment passes CREATED throws,
  and field-init is the only correct site. `localFolderDestinationPickerManager` holds only the Fragment, the
  activity-scoped ViewModel and the launcher, all of which outlive a re-inflate. Layer 3 - no listener registered
  without a matching removal; the launcher is auto-unregistered with the Fragment lifecycle.

---

## Handoff Notes to Next Phase

`screenshotDestinationResourceId` now exposes the "Local Folder" option. Phase 04 covers the last remaining
setting (`videoSnapshotResourceId`), which - unlike Phases 02/03 - goes through the shared `DestinationPickerDialog`
class rather than a per-host private method, so it needs an opt-in constructor parameter instead of an in-place
edit.

---

## Rollback Plan

Low-risk: revert `EdgeGestureConfigDialogFragment.kt` - the new launcher/manager fields and the two `loader`/
`onSelected` lines are additive; no other method in this class is touched.
