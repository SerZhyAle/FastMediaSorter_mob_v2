# S1551 - Settings overwritten by constructor-default AppSettings on SettingsActivity open

**Status:** Archived

## 0. Symptom and evidence

Owner report: after installing build `2.60.8090.141-NoLegal-DEBUG (260809014)` almost all settings started resetting.

Device: samsung SM-S731B, Android 16 / API 36, flavor noLegal, `com.sza.fastmediasorter.debug`.
Log bundle: `logs/fastmediasorter_20260808_192133.log` .. `logs/fastmediasorter_20260809_032728.log`.

Regression boundary - the same user action produces opposite behaviour across the build bump:

- `260808033` (`fastmediasorter_20260808_235714.log`): SettingsActivity opened 11 times, 11 `SettingsRepo: updateSettings called`, **0** `diff detected`, **0** DataStore writes. The S0018 idempotency guard in `SettingsRepositoryImpl.updateSettings` absorbed every bind-time callback.
- `260809014` (`fastmediasorter_20260809_024410.log`, `..._032728.log`): **every** `updateSettings` call logs `diff detected - proceeding with DataStore write`. 58 and 28 writes respectively, ~25 of them in a 23 ms burst inside `SettingsActivity.onCreate`.

Three independent fields snap to their `AppSettings()` constructor defaults in that burst:

- `allFiles`: settings dump before the burst reads `allFiles : true`; the burst logs `SettingsRepo: Saved allFiles=false to DataStore` 28 times. `AppSettings.allFiles` default is `false` (`AppSettings.kt:64`).
- `cacheSizeMb`: stored `1024` in every startup line and in the settings dump; at `14:01:48.833` the burst logs `SettingsRepositoryImpl: Synced cacheSizeMb to SharedPreferences: 2048MB`. `AppSettings.cacheSizeMb` default is `2048` (`AppSettings.kt:47`).
- `language`: DataStore dump reads `language : en` (the `AppSettings.language` default, `AppSettings.kt:26`) while `LocaleHelper` reads `ru` from its SharedPreferences cache in every session.

## 1. Mechanism

`SettingsViewModel` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`):

- Line 178-186: `settings` is `combine(repo.getSettings(), _settingsOverride) { persisted, override -> override ?: persisted }.stateIn(.., initialValue = AppSettings())`. Until the first DataStore emission reaches the `stateIn` cache, `settings.value` **is the constructor-default object**.
- Line 172, 213, 321: `_settingsOverride` is written but **never reset to `null`**. Once set, `override ?: persisted` pins `settings.value` to that snapshot for the ViewModel's whole lifetime, so every later DataStore emission - including writes made by another fragment or by `updateSettings { transform }` - is ignored.

15 call sites under `ui/settings` write back with the `updateSettings(settings.value.copy(..))` shape. On SettingsActivity open they fire before the first emission, so `settings.value.copy(..)` is `AppSettings().copy(..)` - the whole stored object is replaced by defaults, one field aside.

Previously harmless: `SettingsRepositoryImpl.updateSettings` (line 640-644) compared the incoming object against the stored one and skipped the write. With the override latch in place the two never match again, so the guard stops firing and every bind-time write lands.

`updateSettings(settings: AppSettings)` persists **every** field of the object (lines 667-847), so a single stale snapshot resets the entire store, not one setting.

S1535 (`bugfix-settings-restart-loop-optimal-cache-size`, Verified 2026-08-08 22:32) introduced `_settingsOverride` and the `initialValue = AppSettings()` seed, and its own KDoc at line 174-177 already names the hazard: "`.value` read during fragment setup can still be the constructor defaults rather than anything stored; never sound for a decision that compares against a stored value". The call sites were not migrated to `awaitPersistedSettings()`.

## 1a. Compounding mechanisms found alongside

- **Silent reset on DataStore corruption.** `core/di/AppModule.kt:118-121` builds the store with `ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() })`, and `SettingsRepositoryImpl.getSettings()` (`:290-296`) catches read failures into `emptyPreferences()` as well. Either path snaps every setting to its class default with no user-visible signal - indistinguishable from the race above when the owner reports it.
- **`language` has two independent stores that can permanently disagree.** `SettingsViewModel.resetGeneralSection()` (`:332-365`) writes `language = "en"` into DataStore, while `SettingsRepositoryImpl.updateSettings` deliberately never syncs `LocaleHelper.saveLanguage()` (`:649-651`). `LocaleHelper` keeps its own `SharedPreferences("app_settings")` entry, and on API 33+ consults `LocaleManager.applicationLocales` first. This is exactly the split in the log: DataStore dump `language : en`, `LocaleHelper: Read language from cache: ru`.
- **The unsafe overload has no mutex.** `updateSettings(settings: AppSettings)` (`:633`) writes without a lock; the safer `updateSettings(transform)` overload (`:850-855`) holds `transformMutex` and rebases the transform on a fresh persisted read. The burst of ~25 concurrent whole-object writes runs entirely on the unlocked path.

Reinstall-time loss has a separate cause - see S1552 (`data_extraction_rules.xml` has the wrong root element, so Android 12+ backs up and restores the settings DataStore against the project's stated intent).

## 2. Scope

- `ui/settings/SettingsViewModel.kt` - override lifetime and the defaults seed.
- The 15 `updateSettings(settings.value.copy(..))` call sites under `ui/settings/`.
- `data/repository/SettingsRepositoryImpl.kt` - whole-object write with no per-field intent.

## 3. Open questions

- Clear `_settingsOverride` once the persisted flow catches up, or drop the override entirely in favour of the field-level `updateSettings { transform }` path?
- Should `updateSettings(settings: AppSettings)` stay public at all, given a whole-object write from a UI snapshot is unsafe by construction? Field-level `updateSettings { it.copy(..) }` under `transformMutex` (line 850) has none of these failure modes.
- Should the store refuse a write whose incoming object equals `AppSettings()` while the stored one does not, as a backstop?
- Should `language` collapse into a single source of truth, or should `updateSettings` sync `LocaleHelper.saveLanguage()` on every change of that field?
- Should the corruption handler surface something to the user instead of silently producing empty preferences?
- No unit tests were found for `SettingsRepositoryImpl`, `SettingsViewModel` or `LocaleHelper`; confirm with a `Glob` of `app_v2/src/test/**/*Settings*` before implementation.
