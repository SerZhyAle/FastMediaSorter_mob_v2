# Стратегическая спецификация: S1815 - SettingsDropdownRow subtitle support

**Ticket:** S1815
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-19
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - out-of-scope находка, запаркована 2026-08-19 (CLAUDE.md 3.1)
**Tactical spec:** inline (compact spec)

<!-- auto-approved by /spec-all - 2026-08-19 -->

---

## 1. Goal

Добавить поддержку подписи (`sdr_subtitle`, `setSubtitle`) в канонический виджет `SettingsDropdownRow` по аналогии с `SettingsToggleRow` и `SettingsSelectionRow`. Подключить уже локализованную строку `launcher_settings_screen_timeout_subtitle` к `rowLauncherScreenTimeout` в портретной и альбомной разметках `dialog_launcher_settings.xml`, удалив её из `scripts/quality/assert-unreferenced-strings-baseline.txt`.

---

## 2. Requirements & Constraints

- **Flavor:** All flavors (src/main).
- **API level:** Min SDK without API-specific branches.
- **Localization:** `launcher_settings_screen_timeout_subtitle` is already translated in EN, RU, UK.
- **Layout symmetry (CLAUDE.md Rule 11):** Both `res/layout/dialog_launcher_settings.xml` and `res/layout-land/dialog_launcher_settings.xml` must be updated.
- **Settings doc sync (CLAUDE.md Rule 22):** Settings docs and quality gates must pass.
- **Detekt & Quality Gates:** Passes `assert-unreferenced-strings.ps1`, `assert-dialog-cancel-style.ps1`, and standard build checks.

---

## 3. Approval Gate

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

# Phase 01 - SettingsDropdownRow Subtitle Support

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Introduce `sdr_subtitle` attribute and programmatic `setSubtitle` support in `SettingsDropdownRow`, wire the subtitle into launcher settings layouts, and remove the unreferenced-string baseline exemption.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/view_settings_dropdown_row.xml` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt` | Modified | ≤ 350 |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 400 |
| `scripts/quality/assert-unreferenced-strings-baseline.txt` | Modified | ≤ 50 |

---

## Steps

### Step 01.1 - Declare `sdr_subtitle` in `attrs.xml` and add subtitle TextView in `view_settings_dropdown_row.xml`

**Files:** `app_v2/src/main/res/values/attrs.xml`, `app_v2/src/main/res/layout/view_settings_dropdown_row.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `attrs.xml`, add `<attr name="sdr_subtitle" format="string|reference" />` under `declare-styleable name="SettingsDropdownRow"`.
> In `view_settings_dropdown_row.xml`, wrap the title line (title + help icon + spacer) and a new subtitle `TextView` (`@+id/sdr_subtitle`) inside a vertical `LinearLayout` (`@+id/sdr_textGroup`) matching `view_settings_toggle_row.xml` / `view_settings_selection_row.xml`.

**Why:**

> `SettingsDropdownRow` currently lacks a subtitle XML attribute and layout element, making it impossible for dropdown settings to render explanatory text.

**Verification:**

> `sdr_subtitle` attribute is declared in `attrs.xml` and `sdr_subtitle` TextView is present in `view_settings_dropdown_row.xml`.

**Status:** `[x]` done

---

### Step 01.2 - Support subtitle in `SettingsDropdownRow.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsDropdownRow.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Bind `subtitleView: TextView` (`R.id.sdr_subtitle`) and `textGroup: LinearLayout` (`R.id.sdr_textGroup`).
> Implement `setSubtitle(text: CharSequence?)` and `setSubtitle(@StringRes resId: Int)` following the `SettingsToggleRow` pattern (hide `subtitleView` when null/empty).
> In `applyAttributes`, read `R.styleable.SettingsDropdownRow_sdr_subtitle` and call `setSubtitle`.
> In `setEnabled(enabled: Boolean)`, update `subtitleView.isEnabled = enabled`.
> In `applyInlineLayout()`, adjust `textGroup` layout params (`width = 0`, `weight = 1f`, with `marginEnd`) so title and subtitle stay on the left in inline mode while input field stays on the right.

**Why:**

> Exposes programmatic and XML-driven subtitle configuration on `SettingsDropdownRow` compound widget.

**Verification:**

> `SettingsDropdownRow` compiles and passes symbol checks (`./a.ps1 fk`).

**Status:** `[x]` done

---

### Step 01.3 - Wire `launcher_settings_screen_timeout_subtitle` in launcher settings dialog layouts

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`, `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `app:sdr_subtitle="@string/launcher_settings_screen_timeout_subtitle"` to `rowLauncherScreenTimeout` in both `dialog_launcher_settings.xml` and `layout-land/dialog_launcher_settings.xml`.

**Why:**

> Connects the authored and translated explanatory text to the screen timeout setting row in both portrait and landscape layouts (CLAUDE.md Rule 11).

**Verification:**

> `rowLauncherScreenTimeout` in both layout files carries `app:sdr_subtitle="@string/launcher_settings_screen_timeout_subtitle"`.

**Status:** `[x]` done

---

### Step 01.4 - Remove `launcher_settings_screen_timeout_subtitle` from baseline and run quality gates

**Files:** `scripts/quality/assert-unreferenced-strings-baseline.txt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Remove `launcher_settings_screen_timeout_subtitle` and its S1815 explanatory comment from `scripts/quality/assert-unreferenced-strings-baseline.txt`.
> Run `scripts/quality/assert-unreferenced-strings.ps1` and verify exit code 0.

**Why:**

> The string is now referenced in XML, so it is no longer unreferenced and the temporary baseline exemption is obsolete.

**Verification:**

> `pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] All steps 01.1 - 01.4 are done.
- [x] Build and quality gates pass (`.\a.ps1 fc`, `.\a.ps1 fg`).
- [x] Dev log recorded.

---

## Rollback Plan

Revert git changes - no database migration or persistent data changed.
