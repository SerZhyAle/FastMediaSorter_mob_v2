# Phase 03 - Settings dialog entry

**Strategic spec:** [`../S1400_reset-system-launcher-settings.md`](../S1400_reset-system-launcher-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Put a small icon button in the launcher settings dialog header that asks for confirmation and then runs the reset, in both orientations, with trilingual strings and a spoken label.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired for the multi-file source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | script-driven |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | script-driven |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | script-driven |
| `app_v2/src/main/res/drawable/ic_restore_defaults.xml` | New | ≤ 20 |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 135 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsViewModel.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 260 |

> `SettingsViewModel.kt` was in this list until the 2026-08-06 amendment on step 03.4 and is no longer touched; the backup taken before that attempt stays at `temp/S1400/`.
>
> Both orientations of `dialog_launcher_settings.xml` exist and are edited together in step 03.3 (CLAUDE.md Rule 11).
>
> No flavor-specific placement applies: the dialog and its layouts live in `src/main` and are gated at run time by `LauncherModeContract.isAvailableInBuild`.

---

## Steps

### Step 03.1 - Add the five reset strings in EN, RU and UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add five keys with one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` call each: `launcher_settings_reset_title`, `launcher_settings_reset_message`, `launcher_settings_reset_button_description`, `launcher_settings_reset_success`, `launcher_settings_reset_failed`. The message says that the system launcher settings will be reset to their defaults and names what returns to the as-installed state: the desktop layout, the pinned icons and the wallpaper. Check every string against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of its type and §6 for tone. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_settings_reset"`.

**Why:**

Strategic §3.2 requires the button label, the confirmation title and message and both result messages in EN, RU and UK through the lockstep string tool, and §3.1 pins the confirmation wording to the owner's own sentence about resetting the launcher settings to their defaults.

**Verification:**

- `Grep` - each of the five keys matches exactly once in each of the three `strings.xml` files.
- Exit code of `check_strings_localized.ps1 -KeyPrefix "launcher_settings_reset"` is 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. Five keys added through five `set-android-string.ps1 -Action add` calls; `check_strings_localized.ps1 -KeyPrefix "launcher_settings_reset"` exit 0, all 5 present in en/ru/uk (the ten best-effort locales are reported, not fatal - bulk translation is S1420). §6: the confirmation names the consequence rather than asking a bare "are you sure", the success line names what changed instead of "completed successfully", and the failure line states that nothing changed and what to do next.

---

### Step 03.2 - Add the restore-defaults vector icon

**Files:** `app_v2/src/main/res/drawable/ic_restore_defaults.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ic_restore_defaults.xml` as a 24dp vector drawable using `android:tint="?attr/colorControlNormal"` and a path that reads as "restore to defaults" - the standard settings-backup-restore glyph - not as "refresh" and not as "undo". No hardcoded `#RRGGBB` colour anywhere in the file.

**Why:**

Strategic §6 item 2 resolves that the existing glyphs mean "refresh" and "undo", neither of which is this action, so a reset button reusing one of them would mislabel a destructive operation.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/ic_restore_defaults.xml` exists.
- `Grep` - `android:tint` present in that file.
- `Grep` - `="#` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS. File: res/drawable/ic_restore_defaults.xml (New, 11 LOC). Material `settings_backup_restore` glyph - a circular arrow around a filled dot, which is what distinguishes it from `ic_refresh` at a glance.

---

### Step 03.3 - Place the icon button in both dialog headers

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`, `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> In both layouts add a `com.google.android.material.button.MaterialButton` with `android:id="@+id/btnResetLauncher"` to the header row, between the title `TextView` and `btnClose`. Use `style="@style/Widget.FastMediaSorter.Button.Icon"`, `app:icon="@drawable/ic_restore_defaults"`, no text, `android:contentDescription="@string/launcher_settings_reset_button_description"`, `android:focusable="true"` and `android:clickable="true"`. The two files share one ViewBinding, so the id and attributes must match exactly.

**Why:**

Strategic §3.3 records that the header row is the only row of this dialog not occupied by a setting and the only one that already carries a button, so a small icon fits there without a new row, and §3.2 requires the landscape counterpart to change in the same edit.

**Verification:**

- `Grep` - `@+id/btnResetLauncher` matches exactly once in each of the two layout files.
- `Grep` - `ic_restore_defaults` matches once in each of the two layout files.
- `Grep` - `launcher_settings_reset_button_description` matches once in each of the two layout files.
- `Grep` - `="#` returns zero hits in both layout files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8\8 PASS. Files: res/layout/dialog_launcher_settings.xml, res/layout-land/dialog_launcher_settings.xml (+9 LOC each, identical block). Button sits between the title and `btnClose` in both orientations, so the shared ViewBinding resolves the same id either way.

---

### Step 03.4 - Expose the reset from a dialog-scoped `LauncherSettingsViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsViewModel.kt`
**Depends on:** Step 03.1

> **Amended 2026-08-06.** This step originally injected `ResetLauncherToDefaultsUseCase` into `SettingsViewModel`. That constructor already carries an accepted `LongParameterList` in the detekt baseline, whose entry is keyed on the full parameter list, so the extra parameter resurfaced the finding and the scoped detekt gate failed the change. The prompt below is the amended one; `SettingsViewModel.kt` is no longer touched.

**Prompt for developer:**

> Create `LauncherSettingsViewModel` as a `@HiltViewModel` whose `@Inject constructor` takes only `ResetLauncherToDefaultsUseCase`. Give it `fun resetToDefaults()` running the use case in `viewModelScope` and sending its boolean result to a `Channel<Boolean>`-backed `resetResult: Flow<Boolean>`, mirroring how `SettingsViewModel.launcherWallpaperImportFailed` is declared and consumed. No business logic beyond calling the use case and forwarding its result. Do not add a parameter to `SettingsViewModel`.

**Why:**

Strategic §3.3 requires a short success notice and a distinct failure message, which means the dialog needs the outcome and not just a fire-and-forget call.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsViewModel.kt` exists.
- `Grep` - `class LauncherSettingsViewModel` matches once in that file and `@HiltViewModel` precedes it.
- `Grep` - `fun resetToDefaults()` and `val resetResult` each match once in that file.
- `Grep` - `resetLauncherToDefaultsUseCase` returns zero hits in `SettingsViewModel.kt`.
- `Grep` - `Log\.d\(` returns zero hits in the new file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - FAIL on the first attempt. Injecting the use case into `SettingsViewModel` (24th constructor parameter) resurfaced its baselined `LongParameterList`: the baseline entry is keyed on the full parameter list, so `assert-detekt [scoped]` reported it as a NEW finding in a changed file. Step amended; `SettingsViewModel.kt` reverted to its HEAD content, backup kept at `temp/S1400/SettingsViewModel.20260806-002500.kt.bak`.
- 2026-08-06 - Verification 5\5 PASS. File: ui/settings/LauncherSettingsViewModel.kt (New, 35 LOC), single-parameter constructor. `SettingsViewModel.kt` carries zero references to the use case.

---

### Step 03.5 - Wire the button, the confirmation and the result message

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`
**Depends on:** Step 03.3, Step 03.4

**Prompt for developer:**

> Hold the dialog-scoped `LauncherSettingsViewModel` with `by viewModels()`. In `setupRows()` give `binding.btnResetLauncher` a click listener that shows a `MaterialAlertDialogBuilder` with `R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive`, title `launcher_settings_reset_title`, message `launcher_settings_reset_message`, a positive button calling `launcherViewModel.resetToDefaults()` and a negative Cancel button whose listener is `null`. In `observeSettings()` collect `launcherViewModel.resetResult` through `collectOnLifecycle` and show `launcher_settings_reset_success` or `launcher_settings_reset_failed` in a `Snackbar`, matching the existing wallpaper-failure handling. Cancel must leave every stored value untouched.

**Why:**

Strategic §2 goals 2 and 4 require a confirmation before anything is destroyed and require Cancel to change nothing, and §3.3 fixes the confirmation as the destructive dialog style with the OK/Cancel pair of the S0538 taxonomy.

**Verification:**

- `Grep` - `btnResetLauncher` matches at least once in `LauncherSettingsDialogFragment.kt`.
- `Grep` - `ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive` matches once in that file.
- `Grep` - `launcher_settings_reset_title` and `launcher_settings_reset_message` each match once in that file.
- `Grep` - `collectOnLifecycle(launcherViewModel.resetResult)` matches once in that file.
- `Grep` - `lifecycleScope.launch` followed by a bare `.collect` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 6\6 PASS. File: ui/settings/LauncherSettingsDialogFragment.kt 204 -> 216 LOC. Confirmation uses the destructive overlay with the OK/Cancel pair; Cancel passes a `null` listener, so it writes nothing. Result is rendered through `collectOnLifecycle`, never a bare `lifecycleScope.launch { collect }`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` BUILD SUCCESSFUL in 2m 51s, exit 0, `hiltJavaCompileStandardDebug` included, so the new ViewModel's Hilt node is validated.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1` (verdict `post-change: PASS`).
- [x] UI placement gate (S1338): the strategic §3.3 placement decision is recorded, with its provenance stated - the owner's words fix the window and the button's form, the position inside the header is derived from the layout. Screenshot of the changed dialog captured this phase on `emulator-5554`: `temp/S1400/dialog-launcher-settings-reset-button.png`. The uiautomator dump carries `content-desc="Reset launcher settings to defaults" clickable="true" enabled="true" focusable="true" bounds="[650,600][775,726]"`, immediately left of the Close pill.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: the dialog holds no business logic, only a confirmation and a call into its ViewModel. Layer 2: the outcome stream is consumed through `collectOnLifecycle`, never a bare `lifecycleScope.launch { collect }`, and the `Channel` is `BUFFERED`, so an outcome delivered while the dialog is stopped is not dropped. Layer 3: the new ViewModel is dialog-scoped, so it dies with the dialog rather than outliving it on the activity.

---

## Handoff Notes to Next Phase

The dialog layout gained a control, so Phase 04 must re-run the settings documentation sync (CLAUDE.md Rule 22) and the icon inventory gate, and record the capability in `docs/ALL_FEATURES.jsonl`.

---

## Rollback Plan

Revert phase commit(s), then delete the five string keys with `set-android-string.ps1 -Action remove`. No data migration is involved.
