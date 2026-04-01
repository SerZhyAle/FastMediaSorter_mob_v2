# Детализированное техзадание: Интеграция ресурса в расписание (исправлено)

> **Ревизия**: 2026-04-01 — скорректировано под фактическую архитектуру проекта (Activity-based, без NavController/SafeArgs).

## 1. Цель задачи
Связать функционал просмотра файлов с модулем автоматизации. Пользователь должен иметь возможность в один клик начать создание задачи по расписанию, где текущий открытый ресурс уже выбран в качестве "Источника" (Source).

## 2. Местоположение в UI
- **Экран**: `BrowseActivity` — контекстное меню `Resource Operations` (кнопка `btnResourceOps`).
- **Точка входа**: `ResourceOpsMenuManager.showMenu()` → пункт `Автоматизация..`.
- **Логика видимости**:
  - Пункт отображается **только** если выполнены **оба** условия:
    1. Compile-time флаг `BuildConfig.ENABLE_SCHEDULED_OPERATIONS == true` (false для flavors `lite` и `photos`).
    2. Runtime настройка `AppSettings.enableScheduledOperations == true` (DataStore, ключ `enable_scheduled_operations`).
  - `BrowseViewModel.scheduledOperationsEnabled: Boolean` инкапсулирует проверку runtime-флага через `cachedSettings`.

## 3. Фактическая архитектура проекта (важно!)
- **Нет** Navigation Component / NavController / SafeArgs / `nav_graph.xml` — проект использует **Activity-based навигацию через Intent**.
- **Нет** `BrowseFragment` / `ScheduleCreateFragment` — есть `BrowseActivity` и `SettingsActivity`.
- Диалог создания расписания: `ScheduledOperationDialog` (plain `android.app.Dialog`) — живёт в `ui/dialog/`.
- Управление операциями: `ScheduledOperationsViewModel` (`activityViewModels()` в `OperationsSettingsFragment` внутри `SettingsActivity`).
- **Нет** `ScheduleCreateViewModel` / `SavedStateHandle` — источник передаётся через Intent Extra при старте `SettingsActivity`.

## 4. Логика и навигация (Activity-based)
1. Пользователь нажимает `Автоматизация..` в меню `Resource Operations` в `BrowseActivity`.
2. `BrowseActivity` читает `viewModel.state.value.resource?.id` (Long) и запускает `SettingsActivity` с Intent-extra:
   `EXTRA_SOURCE_RESOURCE_ID = "extra_source_resource_id"` (Long, default -1).
3. `SettingsActivity` обнаруживает extra → переходит сразу на вкладку 3 (`OperationsSettingsFragment`).
4. `OperationsSettingsFragment.onResume()` читает extra из `requireActivity().intent`, потребляет (убирает) его и открывает `ScheduledOperationDialog` с **предзаполненным источником** (параметр `prefilledSourceId`).
5. Если ID не найден среди ресурсов — поле источника остаётся пустым (graceful fallback).

## 5. Требования к UI (Presentation Layer)
1. **Browse Side** (`BrowseActivity` + `ResourceOpsMenuManager`):
   - Добавить `action_automate_resource` в `menu_resource_ops.xml`.
   - `ResourceOpsMenuManager.showMenu()` принимает `isScheduleEnabled: Boolean` и `onAutomateSource: (() -> Unit)?`.
   - Пункт меню: `isVisible = isScheduleEnabled && onAutomateSource != null`.
2. **Settings Side** (`ScheduledOperationDialog` + `OperationsSettingsFragment`):
   - `ScheduledOperationDialog` получает новый необязательный параметр `prefilledSourceId: Long? = null`.
   - При `existing == null && prefilledSourceId != null` — автоматически устанавливает источник в dropdown.
   - Если ресурс не найден по ID — поле пустое (без ошибок).

## 6. Пошаговый план имплементации (Checklist)

### Шаг 1: Ресурсы меню и строки
- [x] В `menu_resource_ops.xml` добавить `<item android:id="@+id/action_automate_resource" .../>`.
- [x] В `strings.xml` (EN/RU/UK) добавить `menu_automate_source`.

### Шаг 2: BrowseViewModel — флаг доступности расписаний
- [x] Добавить `val scheduledOperationsEnabled: Boolean get() = cachedSettings?.enableScheduledOperations == true`.

### Шаг 3: ResourceOpsMenuManager — пункт меню + callback
- [x] Обновить сигнатуру `showMenu()`: добавить `isScheduleEnabled: Boolean` и `onAutomateSource: (() -> Unit)?`.
- [x] `popup.menu.findItem(R.id.action_automate_resource).isVisible = isScheduleEnabled`.
- [x] В `setOnMenuItemClickListener` обработать `R.id.action_automate_resource`.

### Шаг 4: BrowseActivity — запуск SettingsActivity с extra
- [x] Передать в `showMenu()` флаг и lambda, запускающую `SettingsActivity` с `EXTRA_SOURCE_RESOURCE_ID`.

### Шаг 5: SettingsActivity — EXTRA + навигация к вкладке
- [x] Добавить `companion object { const val EXTRA_SOURCE_RESOURCE_ID = "extra_source_resource_id" }`.
- [x] В `setupViews()` при обнаружении extra → `viewPager.post { setCurrentItem(3, false) }`.

### Шаг 6: ScheduledOperationDialog — prefilledSourceId
- [x] Добавить `prefilledSourceId: Long? = null` в конструктор.
- [x] В `onCreate()` после `populateExisting()` вызвать `applyPrefilledSource()`.

### Шаг 7: OperationsSettingsFragment — авто-открытие диалога
- [x] В `onResume()` вызывать `checkAndOpenAutomateDialog()`.
- [x] Метод читает extra, потребляет его (`removeExtra`) и открывает `ScheduledOperationDialog`.

### Шаг 8: CHANGELOG
- [x] Логировать все изменённые файлы через `add_to_dev_log.ps1`.

## 7. Риски
- **Race condition ViewPager**: `OperationsSettingsFragment.onResume()` может быть вызван раньше, чем ViewPager переключится на вкладку 3. Решение: `isResumed && BuildConfig.ENABLE_SCHEDULED_OPERATIONS` как guard в `checkAndOpenAutomateDialog()`.
- **Устаревший cachedSettings**: если `BrowseActivity` открылась, а настройки изменились — меню показывает старое значение. Приемлемо: меню обновится при следующем открытии.
