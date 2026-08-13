# Спецификация (compact bugfix): S1535 - Бесконечный цикл перезапусков при входе в настройки (авто-подбор размера кэша)

**Ticket:** S1535
**Status:** Archived
**Priority:** 85
**Date:** 2026-08-08
**Tier:** bugfix

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Текст:**

> изучи последние логи с эмулятора - особенно их конец - какойто цикл перехода к настройкам

---

## 1. Проблема / симптом

Вход в Settings на эмуляторе запускает самоподдерживающийся цикл «перезапуск приложения -> Settings -> перезапуск», период около 500 мс.

Наблюдалось на `sdk_gphone64_x86_64`, Android 15 (SDK 35), `standard` debug (`com.sza.fastmediasorter.debug`).
Свободно на `/data`: ~7.4 GB.

Воспроизводилось четырьмя пачками в одном сеансе логов: 20:35:57-20:36:02 (7 итераций), 20:43:40-20:43:47 (10), 20:48:36-20:48:45 (23), 21:56:16-21:56:19 (7). Цикл обрывается сам - выглядит как гонка, а не как жёсткий детерминизм.

Эвиденс, один виток (logcat, 2026-08-08):

```
21:56:15.929 D/UserAction: TOUCH: action=DOWN x=755 y=203 (MainActivity)
21:56:16.104 I/ActivityTaskManager: START u0 {cmp=.../ui.settings.SettingsActivity}
21:56:16.131 D/BaseActivity: onCreate: SettingsActivity
21:56:16.262 I/ActivityTaskManager: Displayed .../SettingsActivity +160ms
21:56:16.579 D/LocaleHelper: Saving language: en
21:56:16.581 D/LocaleHelper: Set language via LocaleManager: en (system will restart app)
21:56:16.589 I/ActivityTaskManager: START u0 {act=android.intent.action.MAIN cat=[LAUNCHER] ...}
21:56:16.842 D/BaseActivity: onCreate: MainActivity
21:56:16.866 I/ActivityTaskManager: START u0 {cmp=.../ui.settings.SettingsActivity}   <- виток замкнулся
```

Побочные эффекты в том же окне: каждый виток создаёт и уничтожает пару Activity со всеми ViewModel (LeakCanary watch-спам), теряется состояние экрана, пользователю физически не удержать Settings открытыми.

---

## 2. Корневая причина

Четыре звена, каждое проверено по коду рабочего дерева.

- [GeneralSettingsFragment.kt:271](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt#L271) в `onViewCreated` вызывает `cacheHelper.checkAndSuggestOptimalCacheSize()`.
- [GeneralSettingsCacheHelper.kt:33-36](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt#L33-L36) читает `viewModel.settings.value` - а это `StateFlow`, чей `initialValue = AppSettings()` ([SettingsViewModel.kt:174-182](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt#L174-L182)). До первой эмиссии DataStore значение синтетическое: `cacheSizeMb = 2048`, `isCacheSizeUserModified = false` ([AppSettings.kt:47-48](app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt#L47-L48)). Сохранённое значение (1024) при этом читается корректно - оно есть и в `glide_config.xml`, и в `settings.preferences_pb`; сравнение просто идёт не с ним.
- На этом устройстве `CalculateOptimalCacheSizeUseCase` даёт 1024 (доступно 7 GB, ветка `else`). `2048 != 1024` -> срабатывает `showOptimalCacheSizeSuggestion` -> `applyCacheSizeAndRestart(newCacheSizeMb = 1024, isUserModified = false)`.
- [GeneralSettingsCacheHelper.kt:195-197](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt#L195-L197): `saveLanguage(context, getLanguage(context))` пишет тот же самый язык, чтобы через `LocaleManager` получить перезапуск процесса; затем `markReturnToSettings`; затем [MainActivity.kt:343-347](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt#L343-L347) после перезапуска сразу открывает Settings. Цикл замкнут.

Замок не защёлкивается никогда: подсказка пишет `isCacheSizeUserModified = false`, то есть условие `!settings.isCacheSizeUserModified` истинно и на следующем витке.

Отдельно: [LocaleHelper.saveLanguage](app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt#L150-L182) безусловно присваивает `localeManager.applicationLocales`, даже когда язык не менялся - то есть перезапуск процесса тут используется как механизм, а не как следствие смены языка.

---

## 3. Исправление

Два изменения; каждое в одиночку разрывает цикл, вместе они закрывают и гонку, и её последствие.

- **Сравнивать с загруженным значением.** `SettingsViewModel` получает `awaitPersistedSettings()` - приостанавливающий доступ к первой эмиссии из хранилища. `checkAndSuggestOptimalCacheSize` ждёт его вместо чтения `settings.value`. Заглушка `AppSettings()` перестаёт участвовать в сравнении, автоподсказка на уже настроенном устройстве не срабатывает вовсе.
- **Автоматический путь не перезапускает приложение.** Подобранный размер записывается и вступает в силу при следующем обычном запуске - Glide читает `glide_config` при инициализации процесса. Тост показывается сразу, а не после перезапуска. Пользовательский путь (`showCacheSizeRestartDialog` -> кнопка «Перезапустить») перезапуск сохраняет: там перезапуск запрошен явно.

Следствие второго пункта: отложенный тост через `SharedPreferences` остаётся без единого писателя, поэтому `markPendingCacheSizeToast`, `consumePendingCacheSizeToastMb`, ключ `pending_cache_size_toast_mb` и `SettingsActivity.maybeShowPendingCacheSizeToast` удаляются (CLAUDE.md Rule 20). Заодно исчезает чтение с диска в `SettingsActivity.onCreate`, дающее нарушение StrictMode на 11 мс в главном потоке.

Вне охвата: `LocaleHelper.saveLanguage` продолжает безусловно писать в `LocaleManager`. После этого фикса им пользуется только пользовательский путь, где перезапуск процесса и требуется; ограничивать запись «только при смене языка» без разбора `WelcomeActivity` рискованно и к дефекту не относится.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция `standard` проходит.
- `Grep` - `pending_cache_size_toast_mb` не встречается ни в одном `.kt`.
- На эмуляторе с < 10 GB свободного места (`optimal` = 1024, заглушка = 2048) открыть Settings и подождать 10 секунд: в logcat нет ни `Set language via LocaleManager`, ни повторного `START u0 {cmp=.../SettingsActivity}`, экран остаётся открытым.

---

## Phase 01 - Сравнение с загруженными настройками

**Статус:** ✅ Done
**Depends on:** none - foundation phase

### Objective

`checkAndSuggestOptimalCacheSize` сравнивает оптимум с сохранённым размером кэша, а не с конструкторным умолчанием `AppSettings()`.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt` | Modified | ≤ 300 |

### Steps

#### Step 01.1 - Add `awaitPersistedSettings()` to `SettingsViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a suspend function `awaitPersistedSettings(): AppSettings` next to the `settings` StateFlow. Return `_settingsOverride.value` when an optimistic override is pending, otherwise the first emission of `settingsRepository.getSettings()`. Add a KDoc line stating that `settings.value` may still hold the `AppSettings()` seed and must not be used for decisions that compare against stored values.

**Why:**

`settings` is seeded with `initialValue = AppSettings()`, so any caller reading `.value` during fragment setup can compare against defaults the user never chose - the exact read that starts the restart cycle.

**Verification:**

- `Grep` - `suspend fun awaitPersistedSettings` matches exactly once in that file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

#### Step 01.2 - Await loaded settings in the optimal-size check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `checkAndSuggestOptimalCacheSize`, replace `viewModel.settings.value` with `viewModel.awaitPersistedSettings()`. Keep both existing guards unchanged: skip when `isCacheSizeUserModified` is true, and skip when the stored size already equals the optimum.

**Why:**

Comparing the device optimum against the `AppSettings()` seed makes "not optimal" permanently true on any device whose optimum differs from 2048 MB, which is what re-fires the suggestion on every Settings open.

**Verification:**

- `Grep` - `viewModel.settings.value` absent from `checkAndSuggestOptimalCacheSize`.
- `Grep` - `awaitPersistedSettings()` present in that file.

**Status:** `[x]` done

---

### Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes.

---

## Phase 02 - Автоподбор без принудительного перезапуска

**Статус:** ✅ Done
**Depends on:** Phase 01

### Objective

Автоматически подобранный размер кэша применяется без убийства процесса, а осиротевший механизм отложенного тоста удаляется.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 400 |

### Steps

#### Step 02.1 - Apply the automatic size without a restart

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Rewrite `showOptimalCacheSizeSuggestion` so it writes the new size through `viewModel.updateSettings` and the `glide_config` preference, then shows the `cache_size_installed_toast` immediately - no `saveLanguage`, no `markReturnToSettings`, no `restartApp`. Drop the now-unused `showInstalledToastAfterRestart` parameter from `applyCacheSizeAndRestart` and keep that function for the user-confirmed restart path only.

**Why:**

The forced process restart is the engine of the cycle, and it fires for a change the user never requested; the new size only has to survive to the next process start, because Glide reads its disk-cache size when the process initialises.

**Verification:**

- `Grep` - `showInstalledToastAfterRestart` returns zero hits repository-wide.
- `Grep` - `restartApp` absent from `showOptimalCacheSizeSuggestion`.
- `Grep` - `applyCacheSizeAndRestart` still called from the `showCacheSizeRestartDialog` positive button.

**Status:** `[x]` done

---

#### Step 02.2 - Delete the orphaned pending-toast mechanism

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete `markPendingCacheSizeToast`, `consumePendingCacheSizeToastMb` and the `PREF_PENDING_CACHE_SIZE_TOAST_MB` constant from `LocaleHelper`, and `maybeShowPendingCacheSizeToast` plus its call in `onCreate` from `SettingsActivity`. Remove imports left unused by the deletion. Keep the `cache_size_installed_toast` string - Step 02.1 shows it directly.

**Why:**

Step 02.1 removes the only writer of the pending-toast preference, so the reader is dead weight (CLAUDE.md Rule 20), and its `onCreate` read is a main-thread disk access that StrictMode already flags at 11 ms.

**Verification:**

- `Grep` - `pending_cache_size_toast_mb` returns zero hits in `app_v2/src`.
- `Grep` - `maybeShowPendingCacheSizeToast` returns zero hits.
- `Grep` - `cache_size_installed_toast` still present in `GeneralSettingsCacheHelper.kt`.

**Status:** `[x]` done

---

### Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes.
- [x] `post-change.ps1 -ScopeToFile` closes green over the whole changed set.

---

## Last Audit

**Дата:** 2026-08-08
**Вердикт:** Verified

### Что сделано

- `SettingsViewModel.awaitPersistedSettings()` - приостанавливающий доступ к сохранённым настройкам; на `settings` добавлен комментарий, что `.value` может быть конструкторной заглушкой.
- `checkAndSuggestOptimalCacheSize` сравнивает оптимум с ним, а не с `settings.value`.
- Автоматический путь выделен в `applyOptimalCacheSize`: пишет размер, показывает тост, не перезапускает приложение. Пользовательский `applyCacheSizeAndRestart` перезапуск сохранил.
- Удалены `markPendingCacheSizeToast`, `consumePendingCacheSizeToastMb`, ключ `pending_cache_size_toast_mb`, `SettingsActivity.maybeShowPendingCacheSizeToast` и осиротевшие импорты `Toast` / `LocaleHelper` в `SettingsActivity`.

### Находка phase-boundary аудита (исправлена в этом же тикете)

`persistCacheSize` в первой редакции перечитывал `viewModel.settings.value` для `copy()`. `awaitPersistedSettings()` собирает поток DataStore отдельно от `stateIn`, стоящего за `settings`, и порядок между ними не гарантирован - перечитывание могло вернуть заглушку `AppSettings()`, и тогда `copy()` записал бы дефолты поверх всех остальных настроек. Это был бы дефект тяжелее исходного. Базовые настройки теперь передаются аргументом.

### Эвиденс

- `.\a.ps1 fk` - BUILD SUCCESSFUL, exit 0 (дважды: после реализации и после снятия проб).
- `.\a.ps1 d` - BUILD SUCCESSFUL, APK `v2.60.8071.632-DEBUG`, установлен на `emulator-5554`.
- Устройство, состояние «сохранено 1024, оптимум 1024» (то самое, на котором цикл и наблюдался): проба напечатала `persisted cacheSizeMb=1024 userModified=false optimal=1024`; в logcat один `onCreate: SettingsActivity`, ноль `Set language via LocaleManager`, ноль перезапусков; `SettingsActivity` осталась `topResumedActivity`.
- Устройство, состояние «сохранено 2048, оптимум 1024» (исходный триггер, DataStore сброшен и затем восстановлен): проба напечатала `persisted cacheSizeMb=2048 userModified=false optimal=1024`, сработала ветка авто-применения; в logcat один `onCreate: SettingsActivity`, ноль `Set language via LocaleManager`, ноль `onDestroy: SettingsActivity`; `glide_config.xml` = 1024; `app_restart_state.xml` пуст.
- `Grep` - `pending_cache_size_toast_mb`, `maybeShowPendingCacheSizeToast`, `showInstalledToastAfterRestart` дают ноль совпадений в `app_v2/src`.
- Пробы `Timber.d("S1535:` удалены, ноль совпадений.

### Осталось

Ничего блокирующего. Вне охвата тикета: `LocaleHelper.saveLanguage` по-прежнему пишет в `LocaleManager` безусловно - после фикса это трогает только явный пользовательский перезапуск.
