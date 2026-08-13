# Phase 01 - SettingsSelectionRow

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce the `SettingsSelectionRow` compound view (clickable `title + optional help + value/subtitle + trailing chevron/slot`) and migrate the manual clickable selection rows surveyed in strategic §1.1 item 2.

---

## Prerequisites

- [ ] Strategic spec `Status:` is `Tactical` or later.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | +20 |
| `app_v2/src/main/res/layout/view_settings_selection_row.xml` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsSelectionRow.kt` | New | ≤ 260 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/...OperationsDestinationsManager.kt` | Modified | ≤ 500 |

> Model the new view on `SettingsToggleRow.kt` + `view_settings_toggle_row.xml` (same package, same dimens, same `TooltipDialog` ownership). Use the `ssr_` attr prefix.

---

## Steps

### Step 01.1 - Declare `ssr_*` styleable

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `declare-styleable name="SettingsSelectionRow"` block following the `str_*` / `csh_*` convention already in the file. Attributes: `ssr_title` (string|reference), `ssr_value` (string|reference), `ssr_subtitle` (string|reference), `ssr_icon` (reference), `ssr_showHelp` (boolean), `ssr_helpTitle` (string|reference), `ssr_helpMessage` (string|reference), `ssr_showChevron` (boolean). No unprefixed public attrs.

**Verification:**

- `Grep` - `declare-styleable name="SettingsSelectionRow"` matches once in `attrs.xml`.
- `Grep` - `ssr_showChevron` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Added `SettingsSelectionRow` styleable (8 `ssr_*` attrs) to `attrs.xml`.

---

### Step 01.2 - Author `view_settings_selection_row.xml`

**Files:** `app_v2/src/main/res/layout/view_settings_selection_row.xml`
**Depends on:** Step 01.1
**Landscape:** widget layouts are orientation-agnostic - no `layout-land/` counterpart needed.

**Prompt for developer:**

> Create a `<merge>` layout mirroring `view_settings_toggle_row.xml`: optional leading icon (`@+id/ssr_icon`, gone by default), text group (`@+id/ssr_title` at `@dimen/toggler_title_text_size` + inline `@+id/ssr_iconHelp` ImageButton reusing `@drawable/ic_help_outline_24`, subtitle `@+id/ssr_subtitle` at `@dimen/toggler_desc_text_size`), a trailing value `TextView` (`@+id/ssr_value`, `?attr/colorOnSurfaceVariant`), a trailing chevron `ImageView` (`@+id/ssr_chevron`), and a trailing slot `FrameLayout` (`@+id/ssr_trailingSlot`, gone). All colors via theme attrs / `@color/*` - no hardcoded HEX.

**Verification:**

- `Glob` - `view_settings_selection_row.xml` exists.
- `Grep` - `@+id/ssr_chevron` and `@+id/ssr_value` present.
- `Grep -i "#[0-9a-f]\{6\}"` in this file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Created `view_settings_selection_row.xml` (`<merge>`, leading icon + text group + value + chevron + trailing slot, theme-attr colors, 0 HEX).

---

### Step 01.3 - Implement `SettingsSelectionRow.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsSelectionRow.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `class SettingsSelectionRow @JvmOverloads constructor(..) : LinearLayout(..)` mirroring `SettingsToggleRow`: inflate `view_settings_selection_row`, `isClickable/isFocusable = true`, `selectableItemBackground` ripple, `minimumHeight = button_height`. Public API: `setTitle`, `setValue(CharSequence?)`, `setSubtitle(CharSequence?)`, `setIcon(@DrawableRes)`, `setHelp(@StringRes,@StringRes)` + `setHelpVisible`, `setChevronVisible(Boolean)`, `setTrailingControl(View?)`, and `setOnRowClickListener((View)->Unit)`. The whole row owns the help icon -> `TooltipDialog.show(..)` wiring; hosts must not bind raw help-icon listeners. Read `ssr_*` attrs in `applyAttributes`. Forward `setEnabled` to children. No `Log.d` - Timber only.

**Verification:**

- `Grep` - `class SettingsSelectionRow` matches once (declaration).
- `Grep` - `fun setOnRowClickListener` and `fun setValue` present.
- `Grep` - `TooltipDialog.show` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Created `SettingsSelectionRow.kt` (~280 LOC) modelled on `SettingsToggleRow`: row-click listener, value/subtitle/icon/chevron API, owns `TooltipDialog`. Timber only.

---

### Step 01.4 - Migrate clickable selection rows to `SettingsSelectionRow`

**Files:** `fragment_settings_general.xml` (+`layout-land`), `fragment_settings_destinations.xml` (+`layout-land`), `GeneralSettingsViewSetupHelper.kt`, `OperationsDestinationsManager.kt`
**Depends on:** Step 01.3
**Landscape:** both fragments have `layout-land/` counterparts - migrate symmetrically.

**Prompt for developer:**

> Replace the manual nested-`LinearLayout` clickable rows surveyed in strategic §1.1 item 2 with `<com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow>`: Device Profile, Saved Authorizations, Statistics (`fragment_settings_general.xml`), Link autodownload resource, Screenshot gesture action/destination selectors (`fragment_settings_destinations.xml`). Keep the same string keys and ids the controllers read. In `GeneralSettingsViewSetupHelper.kt` / `OperationsDestinationsManager.kt`, swap manual click + help wiring to the new row API (`setOnRowClickListener`, `setValue`, `setHelp`). Mirror every XML change in the `layout-land/` variant.

**Verification:**

- `Grep` - `SettingsSelectionRow` appears in both `layout/` and `layout-land/` `fragment_settings_general.xml`.
- `Grep` - `SettingsSelectionRow` appears in both orientations of `fragment_settings_destinations.xml`.
- `Grep` - no orphaned `findViewById` referencing deleted inner-row ids in the two helper files (manual count of removed ids = 0 remaining references).
- `/build` standard debug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS. Migrated 5 surfaces to `SettingsSelectionRow` (both orientations): Device Profile, Saved Authorizations (folded help icon into `ssr_help*`), Statistics open (folded card+icon), Link autodownload, Screenshot gesture Up/Right/Down + destination. Controllers rewired: `GeneralSettingsProfileHelper`, `GeneralSettingsFragment` (dropped `iconHelpSavedAuthorizations` block), `GeneralSettingsViewSetupHelper`, `OperationsSettingsFragment` + `OperationsCaptureManager` + `OperationsGesturesManager` (`refreshDestinationLabel`/`refreshLabel` retyped to `setLabel: (CharSequence)->Unit`). `a.ps1 fc` PASS; 0 orphaned ids; 0 hex.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the logical change via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public widget) - deferred to Phase 07 batch is acceptable.

---

## Handoff Notes to Next Phase

`SettingsSelectionRow` establishes the `ssr_*` prefixed-attr + `view_*.xml` `<merge>` + `TooltipDialog`-ownership template reused by Phases 02-05.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted state touched; pure view substitution.
