# Phase 02 - Opt-in gate + always-on baseline init

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add the `enableStatistics` setting (off by default), surface it as a toggle in the General settings tab, wire the always-on baseline launch record into app startup, and wipe detailed activity when the toggle is switched off. No dashboard navigation row yet (that needs the Activity from Phase 04); no sink yet (Phase 03).

---

## Prerequisites

- [x] Phase 01 is ✅ Done (`StatsBaselineDataStore`, `StatsAggregateDataStore.wipeDetailed`, `StatisticsRepository` exist).
- [x] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SettingsRepository.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 770 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SetStatisticsCollectionEnabledUseCase.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 470 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> `SettingsManager.kt` (~240 LOC) and `SettingsRepositoryImpl.kt` (756 LOC) are close to / over 500 - take a timestamped backup into `temp/` before editing each (strategic Rule 5). Landscape counterpart `layout-land/fragment_settings_general.xml` exists - the toggle row MUST be added to both.

---

## Steps

### Step 02.1 - Add `enableStatistics` flag to settings storage

**Files:** `data/local/preferences/SettingsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `SettingsManager.kt` to `temp/` first (>500 LOC guard is borderline; do it). Add a `booleanPreferencesKey("enable_statistics")` constant, an `enableStatistics: Boolean` field on the `AppSettings` data class defaulting to `false`, read it in the settings flow mapper, and add a setter following the exact pattern of an existing boolean flag (e.g. the favorites/enable-* flags already in this file). Default MUST be `false` (strategic §3.3 Default state, §11.1).

**Verification:**

- `Grep` - `enable_statistics` literal present in `SettingsManager.kt`.
- `Grep` - `enableStatistics` field present on `AppSettings`.
- `Grep` - the field default resolves to `false` (declaration `enableStatistics: Boolean = false`).

**Status:** `[x]` done

**Step Log:** Added `ENABLE_STATISTICS = booleanPreferencesKey("enable_statistics")` + `enableStatistics: Boolean = false` field/mapper/`setEnableStatistics` to `SettingsManager.kt`. The live settings flow is `SettingsRepositoryImpl` → `domain.model.AppSettings` (not `SettingsManager`, which is currently unreferenced), so the functional wiring was also added there: new `enableStatistics: Boolean = false` field on `domain/model/AppSettings.kt` (+ empty `device_profile_presets.csv` row, never preset-applied) and `KEY_ENABLE_STATISTICS` read/write in `SettingsRepositoryImpl.getSettings()/updateSettings()`.

---

### Step 02.2 - Expose the flag through SettingsRepository

**Files:** `domain/repository/SettingsRepository.kt`, `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Back up `SettingsRepositoryImpl.kt` to `temp/` first (756 LOC). Add to `SettingsRepository` a way to read and set the statistics flag, matching how other boolean settings are exposed (a suspend setter `setStatisticsEnabled(enabled: Boolean)` plus exposure of the value in the existing settings `Flow`/snapshot). Implement in `SettingsRepositoryImpl` by delegating to `SettingsManager`.

**Verification:**

- `Grep` - `setStatisticsEnabled` present in both `SettingsRepository.kt` and `SettingsRepositoryImpl.kt`.
- `Grep` - `enableStatistics` referenced in `SettingsRepositoryImpl.kt`.

**Status:** `[x]` done

**Step Log:** Added `suspend fun setStatisticsEnabled(enabled: Boolean)` to `SettingsRepository` and a focused single-key override in `SettingsRepositoryImpl` (`dataStore.edit { it[KEY_ENABLE_STATISTICS] = enabled }`, mirroring `updateEmbeddedGameEnabled`). The value is also surfaced in the existing `getSettings()` Flow (done in 02.1).

---

### Step 02.3 - Use case: set collection enabled (+ wipe detailed on disable)

**Files:** `domain/usecase/SetStatisticsCollectionEnabledUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `class SetStatisticsCollectionEnabledUseCase @Inject constructor(private val settings: SettingsRepository, private val statistics: StatisticsRepository)`. `suspend operator fun invoke(enabled: Boolean)`: set the flag via `settings.setStatisticsEnabled(enabled)`; if `enabled == false`, also call `statistics.wipeDetailed()` so detailed activity is erased while baseline survives (strategic §3.2 "Поведение при выключении", §11.5, ADR-2). Keep it a thin orchestration use case - no Android imports.

**Verification:**

- `Glob` - `SetStatisticsCollectionEnabledUseCase.kt` exists.
- `Grep` - `class SetStatisticsCollectionEnabledUseCase` matches once.
- `Grep` - `wipeDetailed` called inside an `if (!enabled)` / `if (enabled.not())` branch.

**Status:** `[x]` done

**Step Log:** Created `domain/usecase/SetStatisticsCollectionEnabledUseCase.kt` - `@Inject` ctor `(settings: SettingsRepository, statistics: StatisticsRepository)`, `suspend operator fun invoke(enabled)` calls `settings.setStatisticsEnabled(enabled)` then `if (!enabled) statistics.wipeDetailed()`. No Android imports.

---

### Step 02.4 - Always-on baseline launch record at startup

**Files:** `core/init/AppStartupInitializer.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Inject `StatisticsRepository` (or `StatsBaselineDataStore` directly) into `AppStartupInitializer`. Add a deferred startup task (follow the existing `runDeferredStartupTasks()` / deferred-task pattern in this file) that calls `recordLaunch(installVersion, installFlavor)` exactly once per process start. Source `installVersion` from `BuildConfig.VERSION_NAME` and `installFlavor` from `BuildConfig.FLAVOR`. This runs unconditionally - it is the always-on baseline, independent of the opt-in toggle (strategic §3 refinement 3, §5.2, §11.3). Run it off the main thread (the deferred-task scope is already off-main).

**Verification:**

- `Grep` - `recordLaunch` invoked in `AppStartupInitializer.kt`.
- `Grep` - `BuildConfig.VERSION_NAME` and `BuildConfig.FLAVOR` referenced in `AppStartupInitializer.kt`.
- Build: `.\a.ps1 fk` compiles.

**Status:** `[x]` done

**Step Log:** Injected `dagger.Lazy<StatisticsRepository>` into `AppStartupInitializer`; added deferred task `record-launch-baseline` (registered first in `runDeferredStartupTasks()`) calling `recordLaunchBaseline()` → `recordLaunch(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)`, guarded by a new `AtomicBoolean launchRecorded` so it runs at most once per process. Runs off-main via the existing deferred-task scope; unconditional (independent of the opt-in toggle). Compile via `.\a.ps1 fk` deferred to the central sequential build (per task instruction not to run gradle).

---

### Step 02.5 - Toggle strings (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the toggle title and summary strings in lockstep across all three locales using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, `-En -Ru -Uk`, parity-enforced). Keys: `settings_statistics_collection_title` ("Statistics collection" / "Сбор статистики" / "Збір статистики") and `settings_statistics_collection_summary` (one line: collected locally, off by default, no automatic sending). RU/UK MUST use ё/є correctly. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (settings-label formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `settings_statistics_collection_title` present in all three `strings.xml` files.
- `Grep` - `settings_statistics_collection_summary` present in all three.
- Script: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_statistics_collection"` exits 0.
- Predicate: strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:** Added both keys to `strings_settings.xml` (EN/RU/UK) via `set-android-string.ps1 -Action add`. `settings_statistics_collection_title` = "Statistics collection" / "Сбор статистики" / "Збір статистики". `settings_statistics_collection_summary` = "Collected on your device. Off by default - nothing is sent automatically." (RU/UK adapted, correct ё/є). `check_strings_localized.ps1 -KeyPrefix "settings_statistics_collection"` → exit 0. Strings are local-data, off-by-default, friendly, fit 360dp - pass §2/§6.

---

### Step 02.6 - Toggle row in General tab (portrait + landscape)

**Files:** `res/layout/fragment_settings_general.xml`, `res/layout-land/fragment_settings_general.xml`, `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** Step 02.3, Step 02.5

**Prompt for developer:**

> Add a switch row for statistics collection to the General tab layout in BOTH `layout/` and `layout-land/` `fragment_settings_general.xml`, reusing the existing switch-row style/ids convention (mirror an existing `switchEnable*` row; use theme attrs `?attr/...`, never hardcoded hex). Wire it in `GeneralSettingsViewSetupHelper`: bind the switch to the current `enableStatistics` value and on toggle call `SetStatisticsCollectionEnabledUseCase` (via the settings ViewModel, off the UI thread - do not block). Title/summary use the Step 02.5 strings. Ensure the row is keyboard/D-pad focusable and reachable (strategic §3.2 accessibility, Rule 16). Do NOT add the "Statistics" navigation row here - that arrives in Phase 04 once the Activity exists.

**Verification:**

- `Grep` - the new switch row id present in BOTH `layout/` and `layout-land/fragment_settings_general.xml`.
- `Grep` - `settings_statistics_collection_title` referenced from both layout files.
- `Grep` - the new switch id and `SetStatisticsCollectionEnabledUseCase` (or the VM method invoking it) referenced in `GeneralSettingsViewSetupHelper.kt`.
- `Grep` - no `="#` hardcoded-hex color introduced in the edited layout regions.
- Build: `.\a.ps1 fc` (code + resources) passes.

**Status:** `[x]` done

**Step Log:** Added full-width `layoutEnableStatistics` → `rowEnableStatistics` (`SettingsToggleRow`, title+subtitle, `?attr/`-only via the widget) to the Interface section after `rowResourceOpsInOverflowMenu` in BOTH `layout/` and `layout-land/fragment_settings_general.xml` (landscape parity). Wired in `GeneralSettingsViewSetupHelper.setupSwitches()` (`setOnCheckedChangeListener` → `viewModel.setStatisticsCollectionEnabled(isChecked)`, idempotency-guarded) and initial/observed state in `GeneralSettingsObserversHelper.observeData()` (`setCheckedSilently`). New `SettingsViewModel.setStatisticsCollectionEnabled(enabled)` injects + calls `SetStatisticsCollectionEnabledUseCase` in `viewModelScope` (off-main) with optimistic `_settingsOverride`. Row is keyboard/D-pad focusable via `SettingsToggleRow` (focusable+clickable by construction). No "Statistics" nav row (Phase 04). `.\a.ps1 fc` deferred to central build.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles + resources link - run `.\a.ps1 fc`. (deferred to central sequential build per implementation handoff)
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_statistics_collection"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched". (batched centrally)
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case). (batched centrally via `catalog_sync.ps1`)

---

## Handoff Notes to Next Phase

- The gate flag `enableStatistics` is now readable from `SettingsRepository` - Phase 03's sink reads it to no-op when off.
- Baseline launch count + first-launch + install version now accrue on every start.
- Disabling the toggle wipes detailed aggregates and keeps baseline - the invariant Phase 04 relies on for §11.5.
- The dashboard navigation row is intentionally NOT present yet; Phase 04 adds it next to this toggle, visible only when the toggle is on.

---

## Rollback Plan

Revert phase commit(s). The added preference key is inert if unused; restore `temp/` backups of `SettingsManager.kt` / `SettingsRepositoryImpl.kt` if a partial edit must be undone. No data migration.
