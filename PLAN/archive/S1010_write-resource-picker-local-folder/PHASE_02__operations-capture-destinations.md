# Phase 02 - Operations/capture destination pickers

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (parallel-independent of Phase 03/04; only Phase 05 depends on it)
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Add the "Local Folder" leading option to `OperationsSettingsFragment.showDestinationPicker()` - the single
private method backing five of the seven target settings: `linkAutoDownloadResourceId` (direct call site) plus
`cameraPhotosDestinationResourceId`, `videoRecordingDestinationResourceId`, `micRecordingDestinationResourceId`,
`screenRecordingDestinationResourceId` (all four via the `::showDestinationPicker` function reference already
injected into `OperationsCaptureManager`). One method edit covers all five.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`LocalFolderDestinationPickerManager` and the `SettingsViewModel` wrapper methods exist).
- [ ] Working tree is clean or on a feature branch.
- [ ] `OperationsSettingsFragment.kt` confirmed at
      `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` -
      already registers `folderPickerLauncher` (lines 173-176, S1009) and constructs `destinationsManager`
      (line 92) / `captureManager` (line 104) as field-init `by lazy` properties; `showDestinationPicker` is the
      private method at lines 698-719.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | existing + ≤ 30 |

---

## Steps

### Step 02.1 - Register a dedicated SAF launcher and construct the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a second, dedicated `ActivityResultLauncher<Uri?>` field next to the existing `folderPickerLauncher`
> (S1009's own launcher stays untouched - it still serves only the scheduled-op dialog): `private val
> localFolderDestinationPickerLauncher: ActivityResultLauncher<Uri?> =
> registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
> localFolderDestinationPickerManager.onFolderPicked(uri) }`. Add a `by lazy` field next to `destinationsManager`/
> `captureManager`: `private val localFolderDestinationPickerManager by lazy {
> LocalFolderDestinationPickerManager(this, viewModel, localFolderDestinationPickerLauncher) }`. Import
> `LocalFolderDestinationPickerManager` from `com.sza.fastmediasorter.ui.settings.helpers`.

**Verification:**

- `Grep` - `localFolderDestinationPickerLauncher` present as an `ActivityResultLauncher<Uri?>` field, distinct
  from `folderPickerLauncher`.
- `Grep` - `localFolderDestinationPickerManager` present as a `by lazy` field constructing
  `LocalFolderDestinationPickerManager`.
- `Grep` - `import com.sza.fastmediasorter.ui.settings.helpers.LocalFolderDestinationPickerManager` present.

**Status:** `[x]` done

---

### Step 02.2 - Insert the sentinel into showDestinationPicker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `showDestinationPicker(currentResourceId, onPicked)`, change `loader = { destinationsManager.currentDestinations
> }` to `loader = { listOf(LocalFolderDestinationPickerManager.sentinelItem(requireContext())) +
> destinationsManager.currentDestinations }`, and change `onSelected = onPicked` to `onSelected =
> localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked)`. Leave every other
> `ListSelectionConfig` field (`title`, `formatter`, `hasSelection`, `isSelected`, `allowClear`,
> `emptyMessageRes`, `errorMessageRes`) untouched - `isSelected = { it.id == currentResourceId }` already
> correctly never matches the sentinel's reserved `id`, so the check icon behaves the same as before.

**Verification:**

- `Grep` - `LocalFolderDestinationPickerManager.sentinelItem(requireContext())` present inside
  `showDestinationPicker`'s `loader`.
- `Grep` - `localFolderDestinationPickerManager.wrapOnSelected(currentResourceId, onPicked)` present as the
  `onSelected` value.
- `pwsh -NoProfile -File ./a.ps1 dq` exits 0 (debug build, standard flavor - first UI consumer of Phase 01's
  code, so this phase runs the first real build).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 dq` exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `OperationsSettingsFragment.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05 (batched with Phases 03/04's signature changes).
- [x] Phase-boundary audit run (Layer 1 always; Layer 2 lifecycle/coroutine since `onFolderPicked`/
      `wrapOnSelected` run on `viewLifecycleOwner.lifecycleScope` - confirm no leaked callback survives a
      Fragment view teardown mid-SAF-pick).

---

## Step Log

- 2026-08-02 - Step 02.1: Verification 3/3 PASS. Files: ui/settings/fragments/OperationsSettingsFragment.kt (+`localFolderDestinationPickerLauncher`, +`localFolderDestinationPickerManager`, +3 imports). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Step 02.2: Verification 3/3 PASS (`a.ps1 dq` exit 0 - first real build of the ticket). Files: ui/settings/fragments/OperationsSettingsFragment.kt (`loader` prepends the sentinel, `onSelected` routed through `wrapOnSelected`). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Screenshot (S1338 UI-phase requirement): `temp/S1010/phase02_operations_destination_picker_local_folder.png`.
  Device `emulator-5554` (two emulators were online - 5554 and 5556 - so the probe needed an explicit `-DeviceId`;
  5554 selected per the session's device choice). Captured from the Management/Operations tab -> "Video recording" card
  -> "Select resource.." row, i.e. one of the four settings reached through `OperationsCaptureManager`'s
  `::showDestinationPicker` reference, which proves the shared-method wiring. Dialog rows in order: **"Local Folder"
  (first/top)**, "Downloads". Matches the owner's verbatim placement ruling in strategic §3.1 ("first item, at the very top").
- 2026-08-02 - Phase-boundary audit (Layer 1 + Layer 2 + Layer 3): no P0/P1/P2 findings. Layer 1 - the fragment gained
  only two fields and two expression changes; no business logic moved into the Fragment. Layer 2 (the criterion above) -
  `pendingCompletion` is consumed and nulled at the top of `onFolderPicked` on **every** path including the cancel path
  (`uri == null`), so no captured `onPicked` outlives one SAF round-trip; ActivityResult callbacks are dispatched only
  once the Fragment is at least STARTED, so `viewLifecycleOwner` is always resolvable at that point - the same
  invariant S1009's shipped `OperationsScheduledManager.onFolderPicked` already depends on in this very file. If the
  Fragment instance is destroyed mid-pick, the recreated manager's `pendingCompletion` is null and the result is
  dropped rather than applied to a stale binding. Layer 3 - the new launcher is `registerForActivityResult` at
  field-init (auto-unregistered with the Fragment), and is deliberately a second instance so S1009's
  `folderPickerLauncher` -> `scheduledManager` path cannot receive this ticket's picks.

---

## Handoff Notes to Next Phase

`linkAutoDownloadResourceId`, `cameraPhotosDestinationResourceId`, `videoRecordingDestinationResourceId`,
`micRecordingDestinationResourceId`, `screenRecordingDestinationResourceId` all now expose the "Local Folder"
option. Phase 03 repeats the same two-step shape against `EdgeGestureConfigDialogFragment` (a different host,
its own launcher and its own near-duplicate `showDestinationPicker`).

---

## Rollback Plan

Low-risk: revert `OperationsSettingsFragment.kt` - the new launcher/manager fields and the two `loader`/
`onSelected` lines are additive; S1009's `folderPickerLauncher`/`scheduledManager` path is untouched by
construction (separate field, separate launcher instance).
