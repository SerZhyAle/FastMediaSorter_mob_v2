# Phase 04 — Settings UI

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add the "File ops in overflow menu" toggle to `PlaybackSettingsFragment`, wire the one-time Toast hint, and provide trilingual string resources for all new keys.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 5 new strings |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 5 new strings |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 5 new strings |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 15 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 640 |

> `PlaybackSettingsFragment.kt` is 614 LOC → create timestamped backup in `temp/` before editing.

---

## Steps

### Step 4.1 — Add trilingual string resources

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string keys to all three `strings.xml` files. Apply the tone and formula rules from `docs/COMMUNICATION_POLICY.md` §2 and §6 before committing.
>
> **`values/strings.xml` (EN):**
> ```xml
> <string name="pref_file_ops_overflow_menu_title">File actions in overflow menu</string>
> <string name="pref_file_ops_overflow_menu_desc">Collapses copy, move, rename, and delete into a ⋮ button on each file row. Play button always stays separate.</string>
> <string name="file_ops_moved_to_menu_hint">File actions moved to ⋮ menu on each file</string>
> <string name="overflow_menu">More actions</string>
> <string name="move_up">Move up</string>
> <string name="move_down">Move down</string>
> ```
>
> **`values-ru/strings.xml` (RU):**
> ```xml
> <string name="pref_file_ops_overflow_menu_title">Операции в меню ⋮</string>
> <string name="pref_file_ops_overflow_menu_desc">Копировать, переместить, переименовать и удалить скрываются в кнопку ⋮ на каждом файле. Кнопка воспроизведения остаётся отдельной.</string>
> <string name="file_ops_moved_to_menu_hint">Операции перемещены в меню ⋮ на каждом файле</string>
> <string name="overflow_menu">Дополнительно</string>
> <string name="move_up">Вверх</string>
> <string name="move_down">Вниз</string>
> ```
>
> **`values-uk/strings.xml` (UK):**
> ```xml
> <string name="pref_file_ops_overflow_menu_title">Операції в меню ⋮</string>
> <string name="pref_file_ops_overflow_menu_desc">Копіювати, перемістити, перейменувати та видалити приховуються в кнопку ⋮ на кожному файлі. Кнопка відтворення залишається окремою.</string>
> <string name="file_ops_moved_to_menu_hint">Операції переміщено в меню ⋮ на кожному файлі</string>
> <string name="overflow_menu">Додатково</string>
> <string name="move_up">Вгору</string>
> <string name="move_down">Вниз</string>
> ```
>
> Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist: informational, no blame, no imperative commands to the user, action-confirming phrasing.

**Verification:**

- `Grep` — `pref_file_ops_overflow_menu_title` present in `values/strings.xml`.
- `Grep` — `pref_file_ops_overflow_menu_title` present in `values-ru/strings.xml`.
- `Grep` — `pref_file_ops_overflow_menu_title` present in `values-uk/strings.xml`.
- `Grep` — `overflow_menu` present in all three `strings.xml` files.
- `Grep` — `move_up` and `move_down` present in all three `strings.xml` files.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "pref_file_ops_overflow_menu"` — exit code 0.
- **Strings pass COMMUNICATION_POLICY.md §6 tone checklist** (manual check before commit).

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 6/6 PASS (title×3, overflow_menu×3, locale audit exit 0). Files: `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (+4 strings each). Dev log recorded.

---

### Step 4.2 — Add toggle to `fragment_settings_playback.xml`

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** Step 4.1

**Prompt for developer:**

> In `fragment_settings_playback.xml`, locate the `<!-- Hide Grid Action Buttons -->` LinearLayout block (around line 129). Immediately after the closing `</LinearLayout>` of that block (and before the parent container's `</LinearLayout>`), insert a new block following the same pattern:
>
> ```xml
> <!-- File Ops Overflow Menu -->
> <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
>     android:layout_marginBottom="@dimen/margin_small"
>     android:gravity="center_vertical" android:orientation="horizontal">
>
>     <com.google.android.material.switchmaterial.SwitchMaterial
>         android:id="@+id/switchFileOpsOverflowMenu"
>         android:layout_width="wrap_content" android:layout_height="wrap_content"
>         android:layout_marginEnd="@dimen/settings_switch_margin_end" />
>
>     <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content"
>         android:orientation="vertical">
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
>             android:text="@string/pref_file_ops_overflow_menu_title"
>             android:textSize="@dimen/toggler_title_text_size" />
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
>             android:text="@string/pref_file_ops_overflow_menu_desc"
>             android:textSize="@dimen/toggler_desc_text_size"
>             android:textColor="@color/text_color_secondary" />
>     </LinearLayout>
> </LinearLayout>
> ```

**Verification:**

- `Grep` — `switchFileOpsOverflowMenu` present in `fragment_settings_playback.xml`.
- `Grep` — `pref_file_ops_overflow_menu_title` referenced in `fragment_settings_playback.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS (`switchFileOpsOverflowMenu` + `pref_file_ops_overflow_menu_title` in both portrait and landscape xml). Files: `layout/fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml` (+15 lines each). Dev log recorded.

---

### Step 4.3 — Backup and wire toggle + one-time hint in `PlaybackSettingsFragment`

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 4.2

**Prompt for developer:**

> 1. Copy `PlaybackSettingsFragment.kt` to `temp/PlaybackSettingsFragment_<timestamp>.kt.backup`.
>
> 2. In `setupListeners()` (the block that contains `binding.switchHideGridActionButtons.setOnCheckedChangeListener`), add immediately after that block:
>    ```kotlin
>    binding.switchFileOpsOverflowMenu.setOnCheckedChangeListener { _, isChecked ->
>        if (isUpdatingFromSettings) return@setOnCheckedChangeListener
>        val current = viewModel.settings.value
>        if (isChecked && !current.fileOpsOverflowMenuHintShown) {
>            android.widget.Toast.makeText(
>                requireContext(),
>                R.string.file_ops_moved_to_menu_hint,
>                android.widget.Toast.LENGTH_LONG
>            ).show()
>            viewModel.updateSettings(current.copy(fileOpsInOverflowMenu = true, fileOpsOverflowMenuHintShown = true))
>        } else {
>            viewModel.updateSettings(current.copy(fileOpsInOverflowMenu = isChecked))
>        }
>    }
>    ```
>
> 3. In the `observeSettings()` block (the `viewLifecycleOwner.collectOnLifecycle(viewModel.settings)` lambda), find the line `if (binding.switchHideGridActionButtons.isChecked != settings.hideGridActionButtons) { ... }` and add after it:
>    ```kotlin
>    if (binding.switchFileOpsOverflowMenu.isChecked != settings.fileOpsInOverflowMenu) {
>        binding.switchFileOpsOverflowMenu.isChecked = settings.fileOpsInOverflowMenu
>    }
>    ```

**Verification:**

- `Glob` — `temp/PlaybackSettingsFragment_*.kt.backup` exists.
- `Grep` — `switchFileOpsOverflowMenu.setOnCheckedChangeListener` present in `PlaybackSettingsFragment.kt`.
- `Grep` — `fileOpsOverflowMenuHintShown` present in `PlaybackSettingsFragment.kt`.
- `Grep` — `file_ops_moved_to_menu_hint` referenced in `PlaybackSettingsFragment.kt`.
- `Grep` — `Log\.d(` returns zero hits in `PlaybackSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 5/5 PASS (backup exists, listener wired, hintShown used, toast key referenced, Log.d=0). Files: `ui/settings/fragments/PlaybackSettingsFragment.kt` (+15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 4.* above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "pref_file_ops_overflow_menu"` exits 0.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "overflow_menu"` exits 0.
- [x] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "move_up"` exits 0.
- [x] Strings pass COMMUNICATION_POLICY.md §6 checklist (manual verification before commit).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Toggle visible in Settings → Playback.
- Enabling toggle shows one-time Toast; subsequent toggles do not re-show it.
- `fileOpsInOverflowMenu` flows from toggle → DataStore → `BrowseObserverManager` → adapter → ⋮ button visibility.
- Phase 05 updates FEATURES docs and syncs catalog.

---

## Rollback Plan

Revert phase commit(s). No schema or DataStore key added in this phase (those were Phase 01). Safe to revert independently.
