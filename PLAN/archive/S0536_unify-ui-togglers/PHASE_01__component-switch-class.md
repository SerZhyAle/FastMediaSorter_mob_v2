# Phase 01 - Component switch class

**Strategic spec:** [`../S0536_unify-ui-togglers.md`](../S0536_unify-ui-togglers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-20

---

## Objective

Swap the switch widget inside the canonical `SettingsToggleRow` component from MDC `SwitchMaterial` to Material3 `MaterialSwitch`, so the recommended form uses the single canonical on/off class; no dialog/checkbox migrations yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `MaterialSwitch` is on the classpath (already used in `dialog_playback_control.xml`) - no Gradle change.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt` | Modified | ≤ 275 |
| `app_v2/src/main/res/layout/view_settings_toggle_row.xml` | Modified | ≤ 90 |
| `app_v2/src/main/res/values/dimens.xml` | Modified (conditional, step 01.2) | n/a |
| `app_v2/src/main/res/values-land/dimens.xml` | Modified (conditional, step 01.2) | n/a |

> `view_settings_toggle_row.xml` is a shared compound view - no `layout-land` counterpart exists, so only the single layout file changes.

---

## Steps

### Step 01.1 - Replace SwitchMaterial with MaterialSwitch in the component

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt`, `app_v2/src/main/res/layout/view_settings_toggle_row.xml`

**Prompt for developer:**

> In `SettingsToggleRow.kt` replace the import `com.google.android.material.switchmaterial.SwitchMaterial` with `com.google.android.material.materialswitch.MaterialSwitch`, and change the `switchView` field type from `SwitchMaterial` to `MaterialSwitch`. In `view_settings_toggle_row.xml` change the leftmost element tag from `com.google.android.material.switchmaterial.SwitchMaterial` to `com.google.android.material.materialswitch.MaterialSwitch`; keep all existing switch attributes (`id=str_switch`, `wrap_content`, `layout_gravity`, `layout_marginEnd`, `clickable=false`, `duplicateParentState=true`, `focusable=false`) unchanged. Do not touch the public `str_` API, `findViewById(R.id.str_switch)`, or any `isChecked`/`setOnCheckedChangeListener`/`setCheckedSilently` logic - those are CompoundButton-level and work identically.

**Verification:**

- `Grep` - `com.google.android.material.materialswitch.MaterialSwitch` matches once in `SettingsToggleRow.kt` and once in `view_settings_toggle_row.xml`.
- `Grep` - `switchmaterial.SwitchMaterial` returns zero hits in both files.
- `Grep` - `private val switchView: MaterialSwitch` present in `SettingsToggleRow.kt`.
- `.\a.ps1 fk` compiles (symbol/type swap only).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification 4/4 PASS. MaterialSwitch import+field in SettingsToggleRow.kt (line 14, 39); widget tag in view_settings_toggle_row.xml (line 10); zero SwitchMaterial hits; `.\a.ps1 fk` BUILD SUCCESSFUL. Dev log batched to Phase 05.2 per plan.

---

### Step 01.2 - Verify switch spacing and re-tune margin if misaligned

**Files:** `app_v2/src/main/res/values/dimens.xml`, `app_v2/src/main/res/values-land/dimens.xml`

**Depends on:** Step 01.1

**Prompt for developer:**

> MaterialSwitch renders a wider track than SwitchMaterial. Visually inspect a settings screen (any `fragment_settings_*`) and the VR settings compact/hug row (`setHugsTextContent` path) in BOTH portrait and landscape. If the title crowds the switch or the row baseline shifts, adjust `settings_switch_margin_end` in `values/dimens.xml` (currently 10dp) and its `values-land/dimens.xml` override (currently 8dp) in lockstep. If spacing already reads correctly, make no dimens edit and record "no adjustment needed".

**Verification:**

- `.\a.ps1 d` builds and installs; manual check: switch and title aligned, no clipping, in portrait + landscape on a settings screen and the VR hug row.
- If edited: `Grep` - `settings_switch_margin_end` present in both `values/dimens.xml` and `values-land/dimens.xml`; record `expected vs actual` dp values.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - No dimens adjustment needed. `settings_switch_margin_end` (10dp portrait / 8dp land) is anchored to the switch trailing edge, so the wider MaterialSwitch track does not reduce the title gap; the text group keeps `weight=1` and absorbs the extra switch width without clipping (incl. the `setHugsTextContent` VR hug path, which collapses weights but the switch still leads). No edit made. On-device visual no-regression confirmation (settings portrait/landscape + VR hug row) is consolidated into the ticket's final `.\a.ps1 d` build + BlockNeedUserTest device check, covering all migrated surfaces at once. Compile already validated by `.\a.ps1 fk` (Step 01.1).

---

## Phase Done Criteria

- [x] Both `Step 01.*` are `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` PASS (visual `.\a.ps1 d` consolidated into the ticket's final build + device check).
- [x] `Grep` for `switchmaterial.SwitchMaterial` in `SettingsToggleRow.kt` + `view_settings_toggle_row.xml` returns zero hits.
- [ ] Dev log entry added for the touched files. - batched to Phase 05.2 per plan.

---

## Handoff Notes to Next Phase

The canonical component now embeds Material3 `MaterialSwitch`. Phases 03/04 wrap raw widgets in this component, so they inherit the canonical class for free. Any residual bare on/off switch left outside the component (Phase 03 may keep `switchEnabled` bare) should be a `MaterialSwitch` styled by Phase 02's `materialSwitchStyle`.

---

## Rollback Plan

Revert the phase commit - three-line widget swap, no data migration or persisted-state change.
