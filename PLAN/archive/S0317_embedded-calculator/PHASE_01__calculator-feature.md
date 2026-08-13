# Phase 01 - Calculator Feature

**Strategic spec:** [`../S0317_embedded-calculator.md`](../S0317_embedded-calculator.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 8 / 8
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Introduce the opt-in embedded calculator, including settings state, Settings UI, calculator Activity, main-menu launch, and standalone widget.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Branch is `DEBUG-v010`.
- [ ] Unrelated dirty files are ignored and not reverted.
- [ ] `res/layout-land/fragment_settings_general.xml` exists and is updated with the portrait layout change.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0317_SettingsRepositoryImpl_20260531.kt` | New | backup |
| `temp/S0317_MainActivity_20260531.kt` | New | backup |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 760 |
| `app_v2/src/main/res/values/strings.xml` | Modified | any |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | any |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | any |
| `app_v2/src/main/res/drawable/ic_calculator.xml` | New | ≤ 40 |
| `app_v2/src/main/res/drawable/ic_widget_calculator.xml` | New | ≤ 40 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | any |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | any |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 480 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt` | New | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt` | New | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt` | New | ≤ 240 |
| `app_v2/src/main/res/layout/activity_calculator.xml` | New | any |
| `app_v2/src/main/res/layout-land/activity_calculator.xml` | New | any |
| `app_v2/src/main/AndroidManifest.xml` | Modified | any |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CalculatorWidgetProvider.kt` | New | ≤ 120 |
| `app_v2/src/main/res/layout/widget_calculator.xml` | New | any |
| `app_v2/src/main/res/xml/widget_calculator_info.xml` | New | any |

> `SettingsRepositoryImpl.kt` and `MainActivity.kt` are already >500 lines, so Step 01.1 creates timestamped backups in `temp/` before editing them.

---

## Steps

### Step 01.1 - Back Up Large Files

**Files:** `temp/S0317_SettingsRepositoryImpl_20260531.kt`, `temp/S0317_MainActivity_20260531.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` to `temp/S0317_SettingsRepositoryImpl_20260531.kt` and copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` to `temp/S0317_MainActivity_20260531.kt`. Do not modify source files in this step.

**Verification:**

- `Glob` - `temp/S0317_SettingsRepositoryImpl_20260531.kt` exists.
- `Glob` - `temp/S0317_MainActivity_20260531.kt` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 2/2 PASS. Files: temp/S0317_SettingsRepositoryImpl_20260531.kt, temp/S0317_MainActivity_20260531.kt. Backups created.

---

### Step 01.2 - Persist Calculator Setting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `enableCalculator: Boolean = false` to `AppSettings`. Add `KEY_ENABLE_CALCULATOR = booleanPreferencesKey("enable_calculator")` to `SettingsRepositoryImpl`, read it into `AppSettings(enableCalculator = preferences[KEY_ENABLE_CALCULATOR] ?: false)`, and persist it in `updateSettings()`.

**Verification:**

- `Grep` - `val enableCalculator: Boolean = false` appears in `AppSettings.kt`.
- `Grep` - `KEY_ENABLE_CALCULATOR = booleanPreferencesKey("enable_calculator")` appears in `SettingsRepositoryImpl.kt`.
- `Grep` - `enableCalculator = preferences[KEY_ENABLE_CALCULATOR] ?: false` appears in `SettingsRepositoryImpl.kt`.
- `Grep` - `preferences[KEY_ENABLE_CALCULATOR] = settings.enableCalculator` appears in `SettingsRepositoryImpl.kt`.
- `Grep` - `Log.d(` returns zero hits in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 5/5 PASS. Files: AppSettings.kt, SettingsRepositoryImpl.kt. Dev log and catalog sync recorded.

---

### Step 01.3 - Add Calculator Strings And Icons

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, `app_v2/src/main/res/drawable/ic_calculator.xml`, `app_v2/src/main/res/drawable/ic_widget_calculator.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add EN/RU/UK strings for `settings_category_other_functionality`, `calculator_title`, `setting_calculator_desc`, `tooltip_calculator_title`, `tooltip_calculator_message`, `calculator_enable_prompt_title`, `calculator_enable_prompt_message`, `calculator_open_settings`, `widget_calculator_label`, `widget_calculator_description`, and calculator button content descriptions. Create vector drawables `ic_calculator.xml` and `ic_widget_calculator.xml`. Check new user-visible strings against `docs/COMMUNICATION_POLICY.md` §2 and §6 before marking this step done.

**Verification:**

- `Grep` - `name="settings_category_other_functionality"` appears in all three strings files.
- `Grep` - `name="calculator_title"` appears in all three strings files.
- `Grep` - `name="widget_calculator_label"` appears in all three strings files.
- `Glob` - `app_v2/src/main/res/drawable/ic_calculator.xml` exists.
- `Glob` - `app_v2/src/main/res/drawable/ic_widget_calculator.xml` exists.
- `Command` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "calculator"` exits 0.
- `Command` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_calculator"` exits 0.
- `Manual` - Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 8/8 PASS. Files: strings.xml variants, ic_calculator.xml, ic_widget_calculator.xml. Dev log recorded; localization checks for calculator, setting_calculator, settings_category_other_functionality, and widget_calculator passed.

---

### Step 01.4 - Add Settings UI Toggle

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a separate General settings card headed by `@string/settings_category_other_functionality`, containing `SettingsToggleRow` id `rowEnableCalculator` with title `@string/calculator_title`, summary `@string/setting_calculator_desc`, and help strings from Step 01.3. Add `headerOtherFunctionality` and `containerOtherFunctionality` to `GeneralSettingsSectionsHelper`. Wire `rowEnableCalculator` in `GeneralSettingsViewSetupHelper.setupSwitches()` and `GeneralSettingsObserversHelper.observeData()` using `AppSettings.enableCalculator`.

**Verification:**

- `Grep` - `@+id/headerOtherFunctionality` appears in both `fragment_settings_general.xml` files.
- `Grep` - `@+id/rowEnableCalculator` appears in both `fragment_settings_general.xml` files.
- `Grep` - `KEY_OTHER_FUNCTIONALITY_EXPANDED` appears in `GeneralSettingsSectionsHelper.kt`.
- `Grep` - `binding.rowEnableCalculator.setOnCheckedChangeListener` appears in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - `settings.enableCalculator` appears in `GeneralSettingsObserversHelper.kt`.
- `Grep` - `SettingsToggleRow` appears near `rowEnableCalculator` in both layouts.
- `Grep` - `Log.d(` returns zero hits in all three Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 7/7 PASS. Files: General settings layouts and helpers. Dev log and catalog sync recorded.

---

### Step 01.5 - Add Calculator Engine And Tests

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngine.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorEngineTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add a pure Kotlin `CalculatorEngine` under `ui/calculator/helpers` that supports digits, decimal, `+`, `-`, `×`, `÷`, `%`, sign change, `C`, `CE`, backspace, and equals. Add JVM tests covering addition, subtraction, multiplication, division, decimal input, percent, sign change, clear entry, backspace, and division by zero display.

**Verification:**

- `Grep` - `class CalculatorEngine` appears once in `CalculatorEngine.kt`.
- `Grep` - `fun inputDigit` appears in `CalculatorEngine.kt`.
- `Grep` - `fun inputOperator` appears in `CalculatorEngine.kt`.
- `Grep` - `class CalculatorEngineTest` appears once in the test file.
- `Grep` - `divisionByZero` appears in the test file.
- `Command` - `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*CalculatorEngineTest"` exits 0, unless `/build` prompt selects a stricter wrapper.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Files: CalculatorEngine.kt, CalculatorEngineTest.kt. Unit test `:app_v2:testStandardDebugUnitTest --tests "*CalculatorEngineTest"` passed after resolving unrelated game compile blockers.

---

### Step 01.6 - Add Calculator Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/helpers/CalculatorInputManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt`, `app_v2/src/main/res/layout/activity_calculator.xml`, `app_v2/src/main/res/layout-land/activity_calculator.xml`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add `CalculatorActivity` as an `@AndroidEntryPoint` `BaseActivity<ActivityCalculatorBinding>`. Keep Activity logic limited to binding, manifest entry, settings fallback, and key dispatch; delegate calculator button and keyboard handling to `CalculatorInputManager`. The Activity must expose `createIntent(context: Context, fromWidget: Boolean = false)`, show the calculator when `enableCalculator` is true, and show the fallback prompt with `calculator_open_settings` when launched while disabled. Add portrait and landscape layouts with stable grid dimensions and TalkBack content descriptions.

**Verification:**

- `Grep` - `class CalculatorActivity : BaseActivity<ActivityCalculatorBinding>()` appears in `CalculatorActivity.kt`.
- `Grep` - `fun createIntent(context: Context, fromWidget: Boolean = false)` appears in `CalculatorActivity.kt`.
- `Grep` - `class CalculatorInputManager` appears in `CalculatorInputManager.kt`.
- `Grep` - `.ui.calculator.CalculatorActivity` appears in `AndroidManifest.xml`.
- `Glob` - `app_v2/src/main/res/layout/activity_calculator.xml` exists.
- `Glob` - `app_v2/src/main/res/layout-land/activity_calculator.xml` exists.
- `Grep` - `@+id/calculatorGrid` appears in both calculator layouts.
- `Grep` - `@+id/calculatorFallbackGroup` appears in both calculator layouts.
- `Grep` - `Log.d(` returns zero hits in both Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 9/9 PASS. Files: CalculatorActivity.kt, CalculatorInputManager.kt, calculator layouts, AndroidManifest.xml, calculator strings. Dev log, catalog sync, and strings audit recorded.

---

### Step 01.7 - Add Main Menu Launch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> Add `CalculatorActivity` to the main-window dropdown menu. Track `enableCalculator` from the existing `settingsRepository.getSettings()` observer, refresh menu visibility when it changes, change the dropdown visibility threshold so one real item is enough, and add a menu item `MENU_ITEM_CALCULATOR` with `R.string.calculator_title` and `R.drawable.ic_calculator`. Keep the existing `S0319` debug tag while `S0319` remains `BlockNeedUserTest`.

**Verification:**

- `Grep` - `CalculatorActivity` appears in `MainActivity.kt`.
- `Grep` - `MENU_ITEM_CALCULATOR` appears in `MainActivity.kt`.
- `Grep` - `private var isCalculatorEnabled = false` appears in `MainActivity.kt`.
- `Grep` - `(if (isCalculatorEnabled) 1 else 0)` appears in `MainActivity.kt`.
- `Grep` - `if (itemCount <= 0)` appears in `MainActivity.kt`.
- `Grep` - `startActivity(CalculatorActivity.createIntent(this))` appears in `MainActivity.kt`.
- `Grep` - `Timber.d("S0319: main window dropdown menu setup")` still appears in `MainActivity.kt`.
- `Grep` - `Log.d(` returns zero hits in `MainActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 8/8 PASS. Files: MainActivity.kt. Dev log and catalog sync recorded.

---

### Step 01.8 - Add Calculator Widget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CalculatorWidgetProvider.kt`, `app_v2/src/main/res/layout/widget_calculator.xml`, `app_v2/src/main/res/xml/widget_calculator_info.xml`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.7

**Prompt for developer:**

> Add a standalone home-screen widget provider that opens `CalculatorActivity.createIntent(context, fromWidget = true)`. Add its layout, appwidget metadata, and manifest receiver with label `@string/widget_calculator_label` and icon `@drawable/ic_widget_calculator`. The widget does not perform calculation itself; disabled-setting fallback is owned by `CalculatorActivity`.

**Verification:**

- `Grep` - `class CalculatorWidgetProvider : AppWidgetProvider()` appears in `CalculatorWidgetProvider.kt`.
- `Grep` - `CalculatorActivity.createIntent(context, fromWidget = true)` appears in `CalculatorWidgetProvider.kt`.
- `Glob` - `app_v2/src/main/res/layout/widget_calculator.xml` exists.
- `Glob` - `app_v2/src/main/res/xml/widget_calculator_info.xml` exists.
- `Grep` - `widget_calculator_info` appears in `AndroidManifest.xml`.
- `Grep` - `widget_calculator_container` appears in `widget_calculator.xml`.
- `Grep` - `Log.d(` returns zero hits in `CalculatorWidgetProvider.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 7/7 PASS. Files: CalculatorWidgetProvider.kt, widget_calculator.xml, widget_calculator_info.xml, AndroidManifest.xml. Dev log and catalog sync recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `.\build-debug.PS1`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `app_v2`.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- [x] Strings localization checks for prefixes `calculator`, `setting_calculator`, `settings_category_other_functionality`, and `widget_calculator` exit 0.

---

## Handoff Notes to Next Phase

The feature is implemented and buildable; Phase 02 records user-facing docs, functionality log, catalog state, and final validation evidence.

---

## Rollback Plan

Revert the phase commit(s). No Room schema, migration, network endpoint, or persisted destructive operation is introduced.
