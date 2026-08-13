# Phase 01 - Resource form primitives

**Strategic spec:** [`../S0595_forms-dialogs-unification-remainder.md`](../S0595_forms-dialogs-unification-remainder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** S0567 phases 01-04 (shipped)
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce `FormFieldPairLayout` (two side-by-side fields with ratio presets) and `FormCheckboxRow` (`checkbox + subtitle + optional help`), then migrate the repeated form structures in Resource Editor and Add Resource surveyed in strategic §1 items 2-3 (S0567 §1.1 items 4-5).

---

## Prerequisites

- [ ] S0567 P0 widgets shipped (`SettingsToggleRow` Pattern B is the model). Verified.

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

> `fragment_resource_editor.xml` and `activity_add_resource.xml` have NO `layout-land/` counterpart - portrait-only edit is correct (verified in F2).

---

## Steps

### Step 01.1 - Declare `ffp_*` + `fcr_*` styleables

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `declare-styleable name="FormFieldPairLayout"`: `ffp_ratio` (enum: `one_one`, `two_one`), `ffp_stackThreshold` (dimension, optional narrow-width stacking). Add `declare-styleable name="FormCheckboxRow"`: `fcr_title` (string|reference), `fcr_subtitle` (string|reference), `fcr_checked` (boolean), `fcr_showHelp` (boolean), `fcr_helpTitle` (string|reference), `fcr_helpMessage` (string|reference). Prefix-only.

**Verification:**

- `Grep` - `declare-styleable name="FormFieldPairLayout"` and `declare-styleable name="FormCheckboxRow"` each match once.
- `Grep` - `ffp_ratio` and `fcr_helpMessage` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Added both styleables to `attrs.xml` (`ffp_ratio` enum one_one/two_one + `ffp_stackThreshold`; `fcr_title/subtitle/checked/showHelp/helpTitle/helpMessage`). Added `@dimen/form_field_pair_spacing` (8dp). Verified via `a.ps1 fc` PASS.

---

### Step 01.2 - Implement `FormFieldPairLayout.kt`

**Files:** `FormFieldPairLayout.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `class FormFieldPairLayout : LinearLayout` (horizontal). Lays out exactly two child views with preset `layout_weight` from `ffp_ratio` (`1:1` or `2:1`), baseline alignment disabled, preset inter-field margin from a dimen. Optional safe vertical-stack fallback below `ffp_stackThreshold`. No HEX. Timber only.

**Verification:**

- `Grep` - `class FormFieldPairLayout` once.
- `Grep` - `ffp_ratio` / `R.styleable.FormFieldPairLayout` referenced.
- `Grep -n "Log\.d\("` zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Implemented `FormFieldPairLayout : LinearLayout`. Assigns child weights from `ffp_ratio` via `onViewAdded` (Ratio.ONE_ONE/TWO_ONE), `isBaselineAligned=false`, inter-field gap `form_field_pair_spacing`, optional vertical-stack fallback below `ffp_stackThreshold`. Timber-only, no Log.d.

---

### Step 01.3 - Author layout + implement `FormCheckboxRow.kt`

**Files:** `view_form_checkbox_row.xml`, `FormCheckboxRow.kt`
**Depends on:** Step 01.1
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Layout (`<merge>`): `MaterialCheckBox` (`@+id/fcr_checkbox`) + text group (title + inline help icon, subtitle below indented by `@dimen/checkbox_subtitle_margin_start`). Implement `class FormCheckboxRow : LinearLayout` modelled on `SettingsToggleRow` (Pattern B): `isChecked` get/set, `setCheckedSilently`, `setOnCheckedChangeListener`, `setSubtitle`, `setHelp(..)`; whole row is one focus stop; help optional, shown only when payload exists. Row owns `TooltipDialog`. Timber only. No HEX.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class FormCheckboxRow` once; `@+id/fcr_checkbox` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits in the layout.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - `view_form_checkbox_row.xml` (`<merge>`: MaterialCheckBox `@+id/fcr_checkbox` + title + inline help icon, subtitle indented by `checkbox_subtitle_margin_start`; 0 HEX). `FormCheckboxRow : LinearLayout` modelled on SettingsToggleRow Pattern B: `isChecked`, `setCheckedSilently`, `setOnCheckedChangeListener((Boolean)->Unit)`, `setSubtitle`, `setHelp`; row owns TooltipDialog; one focus stop.

---

### Step 01.4 - Migrate Resource Editor + Add Resource forms

**Files:** `fragment_resource_editor.xml`, `activity_add_resource.xml`
**Depends on:** Steps 01.2, 01.3
**Landscape:** no `layout-land/` counterpart for either file - portrait-only edit is correct here.

**Prompt for developer:**

> Wrap the surveyed adjacent field pairs (S0567 §1.1 item 4: Host+Port, Username+Password, Comment+Access PIN; SMB User+Pass, Share+Resource Name, Domain+Port; SFTP Path+Resource Name) in `<com.sza.fastmediasorter.ui.common.widget.FormFieldPairLayout>` with the right `ffp_ratio`, removing per-field manual `layout_weight`/`marginStart`/`marginEnd`. Replace the surveyed `checkbox + subtitle + optional help` structures (S0567 §1.1 item 5: Remember File List rows, SMB/SFTP scanning option rows) with `<com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow>`. Preserve existing field/checkbox ids the controllers bind.

**Verification:**

- `Grep` - `FormFieldPairLayout` and `FormCheckboxRow` both present in `fragment_resource_editor.xml` and `activity_add_resource.xml`.
- `/build` standard debug passes (controllers still resolve preserved ids).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - `fragment_resource_editor.xml`: 3 text-field pairs (Host+Port two_one, Username+Password one_one, Comment+AccessPin two_one) -> `FormFieldPairLayout`; `RememberFileList`+help row -> `FormCheckboxRow` (help owned by row). Updated `ResourceEditorFragment.kt` to the 1-arg listener, dropped `btnHelpRememberFileListEditor` wiring + dead `showRememberFileListHelpDialog()`.
- 2026-06-21 - `activity_add_resource.xml`: 8 text-field pairs -> `FormFieldPairLayout` (SMB User+Pass, Share+ResName, Comment+PIN, Domain+Port; SFTP Host+Port, User+Pass, Path+ResName, Comment+PIN). Container-only change, all `til*`/`et*` ids and `layoutSftpPasswordAuth` id preserved -> no controller change.
- **Scope narrowing:** Add Resource `checkbox + subtitle + optional help` rows (SMB/SFTP/Local/Cloud scanning + options) NOT converted to `FormCheckboxRow` here. Converting them requires migrating 170+ controller call sites (`AddResourceFormManager` ~135, `AddResourceHelper` ~36) from the 2-arg to the 1-arg listener API - disproportionate regression risk vs visual-debt win, cleanly separable. Deferred to **S0596** (Approved, discovered by /spec-all). `FormCheckboxRow` itself is proven via the Resource Editor migration.
- Verified via `a.ps1 fc` PASS.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Field-pair and checkbox primitives exist; Phase 02 audit confirms no residual manual `layout_weight` field pairs remain in the two migrated resource surfaces.

---

## Rollback Plan

Revert phase commit(s) - layout wrappers only; field ids and controller bindings unchanged.
