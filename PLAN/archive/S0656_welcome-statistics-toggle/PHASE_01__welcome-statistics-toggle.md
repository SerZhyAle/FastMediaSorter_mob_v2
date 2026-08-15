# Phase 01 - Welcome statistics toggle + default-on

**Strategic spec:** [`../S0656_welcome-statistics-toggle.md`](../S0656_welcome-statistics-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Surface the existing `enableStatistics` setting as a toggle on the Welcome onboarding "functionality" page (portrait + landscape), flip its code default OFF -> ON, and correct the now-stale "off by default" summary string. Reuse the existing `SettingsToggleRow` pattern and the existing trilingual title; no new setting.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | n/a |
| `app_v2/src/main/res/values/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_settings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/page_welcome_functionality.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_functionality.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt` | Modified | n/a |

---

## Steps

### Step 01.1 - Flip enableStatistics default to ON

**Files:** `AppSettings.kt`
**Prompt for developer:**

> Change `val enableStatistics: Boolean = false` to `= true`. Update the adjacent `// S0473:` comment to state the new rationale: local, anonymous, on by default (S0656); nothing is transmitted without explicit user action.

**Verification:**

- `Grep` - `val enableStatistics: Boolean = true` present; no remaining `enableStatistics: Boolean = false`.

**Status:** `[x] done`

---

### Step 01.2 - Correct statistics summary (drop "off by default")

**Files:** `values/strings_settings.xml`, `values-ru/strings_settings.xml`, `values-uk/strings_settings.xml`
**Prompt for developer:**

> Update `settings_statistics_collection_summary` in all three locales to remove the "off by default" clause while keeping the "nothing is sent automatically" reassurance.
> - EN: `Collected on your device. Nothing is sent automatically.`
> - RU: `Собирается на вашем устройстве. Ничего не отправляется автоматически.`
> - UK: `Збирається на вашому пристрої. Нічого не надсилається автоматично.`

**Verification:**

- `Grep` - none of the three summary values contains "off by default" / "по умолчанию выключено" / "за замовчуванням вимкнено".

**Status:** `[x] done`

---

### Step 01.3 - Add statistics toggle row to functionality page (portrait + landscape)

**Files:** `layout/page_welcome_functionality.xml`, `layout-land/page_welcome_functionality.xml`
**Prompt for developer:**

> In BOTH layout files, add a `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` with `android:id="@+id/rowStatistics"`, `layout_width=match_parent`, `layout_height=wrap_content`, `app:str_title="@string/settings_statistics_collection_title"`, `app:str_subtitle="@string/settings_statistics_collection_summary"`, placed alongside the existing functionality toggles (after the last existing row in the same container). No hardcoded hex.

**Verification:**

- `Grep` - `rowStatistics` present in both portrait and landscape `page_welcome_functionality.xml`.
- `Grep` - `settings_statistics_collection_title` referenced in both.

**Status:** `[x] done`

---

### Step 01.4 - Wire the row in WelcomeFunctionalityController

**Files:** `WelcomeFunctionalityController.kt`
**Prompt for developer:**

> Add `bindStatisticsRow(binding.rowStatistics, settings)` to the bind block alongside the other `bindXRow(..)` calls. Implement `private fun bindStatisticsRow(row: SettingsToggleRow, settings: AppSettings)` mirroring `bindFileManagerRow`: `row.setCheckedSilently(settings.enableStatistics)` then `row.setOnCheckedChangeListener { isChecked -> persist { it.copy(enableStatistics = isChecked) } }`.

**Verification:**

- `Grep` - `bindStatisticsRow` defined and called.
- `Grep` - `enableStatistics = isChecked` present (persist path).

**Status:** `[x] done`

---

### Step 01.5 - Build

**Files:** (build only)
**Prompt for developer:**

> Build standard debug; confirm `PageWelcomeFunctionalityBinding.rowStatistics` resolves and the controller compiles.

**Verification:**

- `/build` -> `standard debug` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every step `[x] done`.
- [ ] Project compiles.
- [ ] Both portrait + landscape `page_welcome_functionality.xml` edited.
- [ ] Dev log entry added (batched).

---

## Rollback Plan

Revert the phase: default reverts to false, summary strings restored, the welcome row and its binding removed. No data migration.
