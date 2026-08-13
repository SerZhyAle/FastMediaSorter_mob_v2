# Phase 05 - Resource form primitives

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce `FormFieldPairLayout` (two side-by-side fields with ratio presets) and `FormCheckboxRow` (`checkbox + subtitle + optional help`), then migrate the repeated form structures in Resource Editor and Add Resource surveyed in strategic §1.1 items 4-5.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | +18 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/FormFieldPairLayout.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/view_form_checkbox_row.xml` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/FormCheckboxRow.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/fragment_resource_editor.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified | ≤ 900 |

> `fragment_resource_editor.xml` and `activity_add_resource.xml` have NO `layout-land/` counterpart - landscape parity not applicable (verified in `/spec-tech` step 2).

---

## Steps

### Step 05.1 - Declare `ffp_*` + `fcr_*` styleables

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `declare-styleable name="FormFieldPairLayout"`: `ffp_ratio` (enum: `one_one`, `two_one`), `ffp_stackThreshold` (dimension, optional narrow-width stacking). Add `declare-styleable name="FormCheckboxRow"`: `fcr_title` (string|reference), `fcr_subtitle` (string|reference), `fcr_checked` (boolean), `fcr_showHelp` (boolean), `fcr_helpTitle` (string|reference), `fcr_helpMessage` (string|reference). Prefix-only.

**Verification:**

- `Grep` - `declare-styleable name="FormFieldPairLayout"` and `declare-styleable name="FormCheckboxRow"` each match once.
- `Grep` - `ffp_ratio` and `fcr_helpMessage` present.

**Status:** `[ ]` not done

---

### Step 05.2 - Implement `FormFieldPairLayout.kt`

**Files:** `FormFieldPairLayout.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> `class FormFieldPairLayout : LinearLayout` (horizontal). Lays out exactly two child views with preset `layout_weight` from `ffp_ratio` (`1:1` or `2:1`), baseline alignment disabled, preset inter-field margin from a dimen. Optional safe vertical-stack fallback below `ffp_stackThreshold`. No HEX. Timber only.

**Verification:**

- `Grep` - `class FormFieldPairLayout` once.
- `Grep` - `ffp_ratio` / `R.styleable.FormFieldPairLayout` referenced.
- `Grep -n "Log\.d\("` zero hits.

**Status:** `[ ]` not done

---

### Step 05.3 - Author layout + implement `FormCheckboxRow.kt`

**Files:** `view_form_checkbox_row.xml`, `FormCheckboxRow.kt`
**Depends on:** Step 05.1
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Layout (`<merge>`): `MaterialCheckBox` (`@+id/fcr_checkbox`) + text group (title + inline help icon, subtitle below indented by `@dimen/checkbox_subtitle_margin_start`). Implement `class FormCheckboxRow : LinearLayout` modelled on `SettingsToggleRow` (Pattern B): `isChecked` get/set, `setCheckedSilently`, `setOnCheckedChangeListener`, `setSubtitle`, `setHelp(..)`; whole row is one focus stop; help optional, shown only when payload exists. Row owns `TooltipDialog`. Timber only. No HEX.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class FormCheckboxRow` once; `@+id/fcr_checkbox` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits in the layout.

**Status:** `[ ]` not done

---

### Step 05.4 - Migrate Resource Editor + Add Resource forms

**Files:** `fragment_resource_editor.xml`, `activity_add_resource.xml`
**Depends on:** Steps 05.2, 05.3
**Landscape:** no `layout-land/` counterpart for either file - portrait-only edit is correct here.

**Prompt for developer:**

> Wrap the surveyed adjacent field pairs (strategic §1.1 item 4: Host+Port, Username+Password, Comment+Access PIN; SMB User+Pass, Share+Resource Name, Domain+Port; SFTP Path+Resource Name) in `<com.sza.fastmediasorter.ui.common.widget.FormFieldPairLayout>` with the right `ffp_ratio`, removing per-field manual `layout_weight`/`marginStart`/`marginEnd`. Replace the surveyed `checkbox + subtitle + optional help` structures (strategic §1.1 item 5: Remember File List rows, SMB/SFTP scanning option rows) with `<com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow>`. Preserve existing field/checkbox ids the controllers bind.

**Verification:**

- `Grep` - `FormFieldPairLayout` and `FormCheckboxRow` both present in `fragment_resource_editor.xml` and `activity_add_resource.xml`.
- `/build` standard debug passes (controllers still resolve preserved ids).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] `Grep` for `TODO(phase-05)` zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Field-pair and checkbox primitives exist; Phase 06 audit confirms no residual manual `layout_weight` field pairs remain in the two migrated resource surfaces.

---

## Rollback Plan

Revert phase commit(s) - layout wrappers only; field ids and controller bindings unchanged.
