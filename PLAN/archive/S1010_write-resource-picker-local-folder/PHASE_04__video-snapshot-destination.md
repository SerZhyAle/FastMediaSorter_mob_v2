# Phase 04 - Video snapshot destination picker

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (parallel-independent of Phase 02/03; only Phase 05 depends on it)
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Add an **opt-in** "Local Folder" option to the shared `DestinationPickerDialog` class, defaulting to disabled so
its other call site (`GeneralSettingsLogHelper`'s ephemeral "save log to resource" action - strategic §6 item 2,
explicitly out of scope, not a persisted write-receiver setting) is byte-for-byte unaffected. Wire it on only from
`VideoSettingsFragment`, covering the last target setting, `videoSnapshotResourceId`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `DestinationPickerDialog.kt` confirmed at `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt`
      (39 LOC) - thin subclass of `ListSelectionDialog<MediaResource>`, two call sites:
      `VideoSettingsFragment.kt:134-145` (`videoSnapshotResourceId`, IN SCOPE) and
      `GeneralSettingsLogHelper.kt:176-186` (log-save action, OUT OF SCOPE per strategic §6 item 2 - left
      untouched by the default-`null` param below).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt` | Modified | existing + ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` | Modified | existing + ≤ 20 |

---

## Steps

### Step 04.1 - Add an opt-in local-folder parameter to DestinationPickerDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DestinationPickerDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new constructor parameter with a default, so every existing call site (including
> `GeneralSettingsLogHelper`, which must stay unchanged) keeps compiling with unchanged behavior:
> `localFolderPicker: LocalFolderDestinationPickerManager? = null` (place it right before the trailing
> `onResourceSelected` lambda param, matching the existing param-ordering style). Inside the `ListSelectionConfig`
> construction: change `loader = { getDestinationsUseCase.invoke().first() }` to `loader = { val base =
> getDestinationsUseCase.invoke().first(); if (localFolderPicker != null)
> listOf(LocalFolderDestinationPickerManager.sentinelItem(context)) + base else base }`; change `onSelected =
> onResourceSelected` to `onSelected = localFolderPicker?.wrapOnSelected(currentSelection, onResourceSelected)
> ?: onResourceSelected`. Import `LocalFolderDestinationPickerManager` from
> `com.sza.fastmediasorter.ui.settings.helpers`.

**Verification:**

- `Grep` - `localFolderPicker: LocalFolderDestinationPickerManager? = null` present in the constructor.
- `Grep` - `localFolderPicker?.wrapOnSelected(currentSelection, onResourceSelected) ?: onResourceSelected` present.
- `Grep` - `GeneralSettingsLogHelper.kt` still constructs `DestinationPickerDialog` with no `localFolderPicker`
  argument (regression check - confirms the log-save call site is untouched and keeps defaulting to `null`).

**Status:** `[x]` done

---

### Step 04.2 - Wire VideoSettingsFragment's snapshot picker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a field-init `ActivityResultLauncher<Uri?>` (this class has none yet): `private val
> localFolderDestinationPickerLauncher: ActivityResultLauncher<Uri?> =
> registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
> localFolderDestinationPickerManager.onFolderPicked(uri) }`, and a `by lazy` field: `private val
> localFolderDestinationPickerManager by lazy { LocalFolderDestinationPickerManager(this, viewModel,
> localFolderDestinationPickerLauncher) }`. In `setupSnapshotResourcePicker()`'s `DestinationPickerDialog(...)`
> construction (lines 134-145), add `localFolderPicker = localFolderDestinationPickerManager` as a new named
> argument. Import `LocalFolderDestinationPickerManager` from `com.sza.fastmediasorter.ui.settings.helpers`,
> plus `androidx.activity.result.ActivityResultLauncher`, `androidx.activity.result.contract.ActivityResultContracts`
> and `android.net.Uri` if not already imported in this file.

**Verification:**

- `Grep` - `localFolderDestinationPickerLauncher` and `localFolderDestinationPickerManager` both present in
  `VideoSettingsFragment.kt`.
- `Grep` - `localFolderPicker = localFolderDestinationPickerManager` present inside the `videoSnapshotResourceId`
  `DestinationPickerDialog(...)` call.
- `pwsh -NoProfile -File ./a.ps1 dq` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

> **This is the last code-touching phase in scope** - Phase 05 is catalog/verification only, with no build of
> its own. Strategic acceptance (§11) is directly UI-observable, so per CLAUDE.md "Debug Verification Tags":
> insert `Timber.d("S1010: ...")` tags now, as the final code edits, before this phase's `dq` build below - one
> tag in `LocalFolderDestinationPickerManager.onFolderPicked` (the shared flow entry all three host families
> funnel into) and one in `wrapOnSelected` (the sentinel-detected branch that triggers the SAF launch) - not
> one per host, since the changed behavior is the shared manager's flow, not three separate flows. Do not
> rebuild afterward; this same `dq` run validates both the Step 04.* edits and the tags.

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 dq` exits 0 (validates Step 04.* edits + the two
      `Timber.d("S1010: ...")` tags in one build).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for both files in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05.
- [x] Phase-boundary audit run (Layer 1 always; Layer 2 - confirm `GeneralSettingsLogHelper`'s
      `DestinationPickerDialog` call site genuinely emits no new UI/behavior - a diff/build check, not
      device verification, since that path is explicitly unmodified).

---

## Step Log

- 2026-08-02 - Step 04.1: Verification 3/3 PASS. Files: ui/dialog/DestinationPickerDialog.kt (+`localFolderPicker` param defaulting to `null`, conditional `loader`, conditional `onSelected`). Regression predicate confirmed: `GeneralSettingsLogHelper.kt` has zero `localFolderPicker` occurrences, so the log-save call site keeps the default and is behaviorally unchanged. Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Step 04.2: Verification 3/3 PASS (`a.ps1 dq` exit 0). Files: ui/settings/fragments/VideoSettingsFragment.kt (+launcher, +manager, +`localFolderPicker = localFolderDestinationPickerManager`, +3 imports). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Debug tags inserted per this phase's Done Criteria, as the final code edits before the build:
  `Timber.d("S1010: sentinel row selected, launching SAF folder picker")` in `wrapOnSelected`'s sentinel branch and
  `Timber.d("S1010: folder picked, uri=%s", uri)` at the top of `onFolderPicked` - two tags total, both in the shared
  `LocalFolderDestinationPickerManager`, since the changed behavior is that one manager's flow rather than three
  per-host flows.
- 2026-08-02 - Ordering note: the journal status was moved to `BlockNeedUserTest` **before** the tags were written,
  not at finalization. CLAUDE.md section 2 states the invariant as tags existing *if and only if* the ticket is in
  `BlockNeedUserTest`, and `scripts/quality/assert-no-ticket-logs.ps1` enforces exactly that at every `post-change`
  close - a tag added while the ticket was still `In Progress` is a "stale probe" and fails the gate. Flipping first
  keeps the invariant true at every instant and still costs only the single build this phase's criteria call for.
- 2026-08-02 - Gate note: the new import in `VideoSettingsFragment.kt` resurfaced that file's already-baselined
  `ImportOrdering` finding (same signature-keyed baseline behaviour seen in Phase 01 on `SettingsViewModel.kt`).
  Fixed properly by sorting the file's import block; no baseline edit was needed here. `a.ps1 dq` was then re-run
  after the sort so the phase's compile evidence covers the final file content rather than the pre-sort state.
- 2026-08-02 - Screenshot (S1338 UI-phase requirement): `temp/S1010/phase04_video_snapshot_destination_local_folder.png`.
  Device `emulator-5554`. Dialog title renders "Select Destination Resource", which is the exact EN value of
  `select_snapshot_destination` (`values/strings.xml:813`) - confirming this is `VideoSettingsFragment`'s snapshot
  picker and not one of the two surfaces already captured. Rows in order: **"Local Folder" (first/top)**, "Downloads".
- 2026-08-02 - Phase-boundary audit (Layer 1 + Layer 2 + Layer 3): no P0/P1/P2 findings. Layer 1 - the new
  `DestinationPickerDialog` parameter is additive with a default and sits before the trailing lambda, so no call site
  changes shape. Layer 2 (the criterion above) - the `GeneralSettingsLogHelper` path is provably unchanged: it passes
  no `localFolderPicker`, so `localFolderPicker != null` is false and both the `loader` and the `onSelected`
  expressions evaluate to exactly their previous values (`base`, `onResourceSelected`); a grep confirms zero
  occurrences of the parameter in that file and the build compiles it untouched. Layer 3 - `VideoSettingsFragment`'s
  launcher is field-init registered and auto-unregistered with the Fragment; its manager captures no view or binding.
  Layer 4 (Room) not applicable.

---

## Handoff Notes to Next Phase

All 7 target settings now expose the "Local Folder" option: 5 from Phase 02, 1 from Phase 03, 1 from this
phase. Phase 05 is the mandatory catalog/dev-log closure - no further picker surfaces remain per the strategic
§6 item 2 inventory.

---

## Rollback Plan

Low-risk: revert `DestinationPickerDialog.kt` (new param is additive with a default, so `GeneralSettingsLogHelper`
compiles unchanged even mid-revert) and `VideoSettingsFragment.kt` (new fields + one new named argument, no
existing line removed).
