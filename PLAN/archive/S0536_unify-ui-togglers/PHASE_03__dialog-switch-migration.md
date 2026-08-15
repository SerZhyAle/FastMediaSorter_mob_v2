# Phase 03 - Dialog switch migration

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Wrap the raw on/off switches in dialogs and the scheduled-operations list item in the canonical `SettingsToggleRow`, removing hand-built switch+label rows; preserve each existing state seam exactly.

---

## Prerequisites

- [ ] Phase 01 ✅ (component on `MaterialSwitch`).
- [ ] Phase 02 ✅ (`materialSwitchStyle` available for any switch left bare).
- [ ] Backup `PlaybackControlDialogFragment.kt` (669 LOC > 500) to `temp/` before editing in step 03.2.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_scheduled_operation.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt` | Modified | ≤ 470 |
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 690 |
| `app_v2/src/main/res/layout/item_scheduled_operation.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt` | Modified | ≤ 140 |

> Landscape parity: `dialog_scheduled_operation.xml` and `dialog_playback_control.xml` both have `layout-land` counterparts - edit in lockstep. `item_scheduled_operation.xml` has NO `layout-land` (orientation-agnostic list item).

---

## Steps

### Step 03.1 - Migrate scheduled-operation dialog switches

**Files:** `app_v2/src/main/res/layout/dialog_scheduled_operation.xml`, `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Replace the hand-built switch+label rows for `switchOverwrite` and `switchSilentMode` with `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` in BOTH portrait and landscape layouts. Keep each `android:id` unchanged (`switchOverwrite`, `switchSilentMode`) so the ViewBinding field names stay. Move the existing label string into `app:str_title` (and a description, if the row had one, into `app:str_subtitle`) and delete the now-redundant label/description `TextView`s. Leave the file-type mask checkboxes in this dialog untouched (out of scope - S0537). In `ScheduledOperationDialog.kt` the populate-writes (`b.switchOverwrite.isChecked = op.overwrite`, `b.switchSilentMode.isChecked = op.silentMode`) and the read-on-save (`overwrite = b.switchOverwrite.isChecked`, `silentMode = b.switchSilentMode.isChecked` in `trySave()`) keep working through the `isChecked` getter/setter - no listener exists to port. Verify the `containerOverwrite` show/hide-by-op-type logic still targets the right view.

**Verification:**

- `Grep` - `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` matches `switchOverwrite` and `switchSilentMode` rows in both `layout/` and `layout-land/dialog_scheduled_operation.xml`.
- `Grep` - `SwitchMaterial` returns zero hits in both `dialog_scheduled_operation.xml` files.
- `Grep` - `android:id="@+id/switchOverwrite"` and `@+id/switchSilentMode` still present in both orientations.
- `.\a.ps1 fc` (code + resources) passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). Both `dialog_scheduled_operation.xml` (portrait + land): switchOverwrite + switchSilentMode now `SettingsToggleRow`; zero SwitchMaterial; ids preserved. `containerOverwrite` wrapper kept (op-type visibility target). `ScheduledOperationDialog.kt` needs no change - populate (374/375) + read-on-save (443/444) use the `isChecked` getter/setter unchanged; no listener; `b.containerOverwrite` still a LinearLayout. Compile consolidated into the ticket's single end build (warm-daemon `fc`/`d`).

---

### Step 03.2 - Migrate playback-control VR override switch (preserve re-entry guard)

**Files:** `app_v2/src/main/res/layout/dialog_playback_control.xml`, `app_v2/src/main/res/layout-land/dialog_playback_control.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Back up `PlaybackControlDialogFragment.kt` to `temp/` first (>500 LOC). Replace the raw `MaterialSwitch` `switchVrOverrideFormatType` with `SettingsToggleRow` in both portrait and landscape, keeping the `android:id` and moving its label into `app:str_title`. In `PlaybackControlDialogFragment.kt`: convert the `setOnCheckedChangeListener` call to the `SettingsToggleRow` single-argument lambda `{ isChecked -> .. }`, keeping the `isUpdatingStereoControls` re-entry guard intact. Replace the programmatic reset `binding.switchVrOverrideFormatType.isChecked = false` (in `bindStereoMode()`) with `setCheckedSilently(false)` so the reset does not fire the listener - this preserves the existing transient (non-persisted) behavior where the switch only gates stereo radio availability. Do not introduce an AppSettings key; the state stays ephemeral.

**Verification:**

- `Grep` - `SettingsToggleRow` matches the `switchVrOverrideFormatType` row in both `dialog_playback_control.xml` files.
- `Grep` - `MaterialSwitch` returns zero hits in both `dialog_playback_control.xml` files.
- `Grep` - `setCheckedSilently(false)` present in `PlaybackControlDialogFragment.kt`; `isUpdatingStereoControls` still referenced.
- `Glob` - timestamped backup of `PlaybackControlDialogFragment.kt` exists under `temp/`.
- `.\a.ps1 fc` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps + backup). Both `dialog_playback_control.xml` (portrait + land): `switchVrOverrideFormatType` now `SettingsToggleRow`, zero raw `MaterialSwitch`. `PlaybackControlDialogFragment.kt`: listener converted to one-arg `{ isChecked -> .. }` (line 414), re-entry guard `isUpdatingStereoControls` intact (lines 391/402/415/422/428), programmatic reset -> `setCheckedSilently(false)` (line 425) so the rebind stays silent and the stereo-override state remains ephemeral. Backup: `temp/PlaybackControlDialogFragment.kt.20260620_003606.bak`. Compile consolidated into the ticket's single end build.

---

### Step 03.3 - Migrate scheduled-operations list-item enable toggle

**Files:** `app_v2/src/main/res/layout/item_scheduled_operation.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsAdapter.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Migrate the per-item `switchEnabled` toggle. Prefer wrapping it in `SettingsToggleRow` if the card row stays compact; if the component breaks the tight RecyclerView row height, instead keep a bare `com.google.android.material.materialswitch.MaterialSwitch` (it inherits `materialSwitchStyle` from Phase 02) - record which option was taken and why. Keep the `android:id` unchanged. In `ScheduledOperationsAdapter.kt` PRESERVE the recycle-safe pattern in `bind(op)`: write state first, then `setOnCheckedChangeListener(null)`, then re-register the toggle listener - adapt the listener lambda to the chosen widget's signature (`SettingsToggleRow` → `{ _ -> onToggle(op) }`, bare `MaterialSwitch` → `{ _, _ -> onToggle(op) }`). The actual enabled flip stays in `ScheduledOperationsViewModel.toggleEnabled`, not in reading switch state. `item_scheduled_operation.xml` has no landscape variant - single file.

**Verification:**

- `Grep` - `item_scheduled_operation.xml` contains either `SettingsToggleRow` or `materialswitch.MaterialSwitch` for `switchEnabled`; `switchmaterial.SwitchMaterial` returns zero hits.
- `Grep` - `setOnCheckedChangeListener(null)` still present in `ScheduledOperationsAdapter.kt` (recycle guard preserved).
- `Grep` - `onToggle(op)` still present in the rebind path.
- `.\a.ps1 fc` passes; manual: toggling a list item still flips enabled state and persists.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS (greps). `item_scheduled_operation.xml`: `switchEnabled` swapped to bare `com.google.android.material.materialswitch.MaterialSwitch` (inherits `materialSwitchStyle` from Phase 02), zero `switchmaterial.SwitchMaterial`. Chose bare over `SettingsToggleRow` because the card row is a dense single line (switch + op icon + source-to-target + error badge) the full-width toggle row would break. `ScheduledOperationsAdapter.kt` unchanged - the recycle-safe pattern (`setOnCheckedChangeListener(null)` then re-register, line 42-43) and `onToggle(op)` already use the two-arg CompoundButton signature that `MaterialSwitch` satisfies. Compile consolidated into the ticket's single end build.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] Project compiles - consolidated `.\a.ps1 d` -> BUILD SUCCESSFUL in 58s (covers Phases 02-04).
- [x] `Grep` for `switchmaterial.SwitchMaterial` across the four touched layouts returns zero hits.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the touched files (batched). - batched in Phase 05.2.

---

## Handoff Notes to Next Phase

All raw on/off switches now use the canonical component (or a themed bare `MaterialSwitch` for the compact list row). No `SwitchMaterial`/raw-switch on/off control remains in user-facing layouts except the excluded debug screen. Phase 04 handles the on/off checkboxes.

---

## Rollback Plan

Revert the phase commit. No persisted-state or schema change - all three seams (ScheduledOperation domain fields, ephemeral stereo override) are unchanged in behavior. Restore `PlaybackControlDialogFragment.kt` from the `temp/` backup if needed.
