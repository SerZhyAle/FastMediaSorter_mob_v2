# Phase 01 — Settings UI

**Strategic spec:** [`../S0071_use-trash-setting.md`](../S0071_use-trash-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Add `switchUseTrash` with tooltip to the Safety section of `OperationsSettingsFragment`, wiring it to `AppSettings.useTrash` via the existing ViewModel path.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Context

**What already exists — no changes required:**

- `AppSettings.useTrash: Boolean = true` (domain model) — field exists.
- `SettingsManager.USE_TRASH` DataStore key + `setUseTrash()` method — persistence exists.
- `SettingsRepositoryImpl` reads/writes `KEY_USE_TRASH` — repository wired.
- `strings.xml` (EN/RU/UK): `use_trash`, `use_trash_summary`, `disable_trash_warning` — all present.
- `SettingsViewModel.updateSettings()` — generic updater accepts any `AppSettings.copy(...)`.

**What is missing:**

- Tooltip strings `tooltip_use_trash_title` / `tooltip_use_trash_message` in all three locales.
- `layoutUseTrash` row in `fragment_settings_destinations.xml`.
- Handler and observer in `OperationsSettingsFragment`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ~2000 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ~1700 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ~1700 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ~350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 640 |

---

## Steps

### Step 1.1 — Add tooltip strings in all three locales

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Add two new string resources adjacent to the existing `use_trash` block in each locale file.
>
> **EN** (`values/strings.xml`) — insert after `disable_trash_warning`:
> ```xml
> <string name="tooltip_use_trash_title">Move to trash</string>
> <string name="tooltip_use_trash_message">When enabled, deleted local files are moved to a hidden .trash folder and can be recovered via Undo. When disabled, files are permanently deleted immediately and disk space is freed at once. Network and cloud files are always deleted permanently.</string>
> ```
>
> **RU** (`values-ru/strings.xml`) — insert after `disable_trash_warning`:
> ```xml
> <string name="tooltip_use_trash_title">Удаление в корзину</string>
> <string name="tooltip_use_trash_message">При включённой опции удалённые локальные файлы перемещаются в скрытую папку .trash и могут быть восстановлены через Undo. При выключённой — файлы удаляются немедленно и место освобождается сразу. Сетевые и облачные файлы всегда удаляются окончательно.</string>
> ```
>
> **UK** (`values-uk/strings.xml`) — insert after `disable_trash_warning`:
> ```xml
> <string name="tooltip_use_trash_title">Видалення до кошика</string>
> <string name="tooltip_use_trash_message">При увімкненій опції видалені локальні файли переміщуються до прихованої папки .trash і можуть бути відновлені через Undo. При вимкненій — файли видаляються негайно і місце звільняється одразу. Мережеві та хмарні файли завжди видаляються остаточно.</string>
> ```

**Verification:**

- `Grep` — `tooltip_use_trash_title` matches in `values/strings.xml`.
- `Grep` — `tooltip_use_trash_title` matches in `values-ru/strings.xml`.
- `Grep` — `tooltip_use_trash_title` matches in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 1.2 — Add `layoutUseTrash` row to the Safety section layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`

**Depends on:** Step 1.1

**Prompt for developer:**

> In `fragment_settings_destinations.xml`, locate `containerSafety`. After the closing `</LinearLayout>` of `containerConfirm` (which wraps `layoutConfirmDelete` and `layoutConfirmMove`) and before the closing `</LinearLayout>` of `containerSafety`, insert the following full-width row:
>
> ```xml
> <LinearLayout android:id="@+id/layoutUseTrash" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="@dimen/margin_tiny" android:gravity="center_vertical" android:minHeight="@dimen/settings_item_min_height" android:orientation="horizontal">
>
>     <com.google.android.material.switchmaterial.SwitchMaterial android:id="@+id/switchUseTrash" android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_marginEnd="@dimen/settings_switch_margin_end" android:checked="true" android:contentDescription="@string/use_trash" />
>
>     <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/use_trash" android:textSize="@dimen/toggler_title_text_size" />
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/use_trash_summary" android:textSize="@dimen/toggler_desc_text_size" android:textColor="@color/text_color_secondary" />
>     </LinearLayout>
>
>     <ImageButton android:id="@+id/iconHelpUseTrash" android:layout_width="@dimen/settings_help_icon_size" android:layout_height="@dimen/settings_help_icon_size" android:layout_marginStart="@dimen/settings_help_icon_margin" android:background="?attr/selectableItemBackgroundBorderless" android:contentDescription="@string/tooltip_use_trash_title" android:src="@drawable/ic_help_outline_24" app:tint="@color/text_color_secondary" />
>
> </LinearLayout>
> ```

**Verification:**

- `Grep` — `switchUseTrash` present in `fragment_settings_destinations.xml`.
- `Grep` — `iconHelpUseTrash` present in `fragment_settings_destinations.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: fragment_settings_destinations.xml. Dev log recorded.

---

### Step 1.3 — Wire switchUseTrash in OperationsSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`

**Depends on:** Step 1.2

**Prompt for developer:**

> The file is 637 lines — create a timestamped backup in `temp/` before editing.
>
> In `setupViews()`, add after the `binding.iconHelpSafeMode.setOnClickListener` block (around line 186):
>
> ```kotlin
> binding.switchUseTrash.setOnCheckedChangeListener { _, isChecked ->
>     if (isUpdatingFromSettings) return@setOnCheckedChangeListener
>     viewModel.updateSettings(viewModel.settings.value.copy(useTrash = isChecked))
> }
> binding.iconHelpUseTrash.setOnClickListener {
>     com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
>         requireContext(),
>         R.string.tooltip_use_trash_title,
>         R.string.tooltip_use_trash_message
>     )
> }
> ```
>
> In `observeData()`, inside the `settings.collect` lambda, after the `binding.switchConfirmMove.isChecked` assignment, add:
>
> ```kotlin
> binding.switchUseTrash.isChecked = settings.useTrash
> ```

**Verification:**

- `Grep` — `switchUseTrash` present in `OperationsSettingsFragment.kt`.
- `Grep` — `tooltip_use_trash_title` referenced in `OperationsSettingsFragment.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `OperationsSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: OperationsSettingsFragment.kt (+14 LOC). Backup: temp/OperationsSettingsFragment_20260503_234301.kt.backup. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 01.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the UI surface and confirms that `AppSettings.useTrash` is correctly persisted. Phases 02 and 03 can proceed independently — they read the same DataStore key that is now writeable by the user.

---

## Rollback Plan

Revert phase commit(s) — no data migration or schema change. The `useTrash` DataStore key defaults to `true` on missing value, so no existing user data is affected.
