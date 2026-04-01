# Детализированное техзадание: Включение расписаний по умолчанию (готово для разработчика)

**Обновлено**: 01.04.2026 — корректировка по результатам анализа реального кода.

## 1. Цель задачи
Сделать функционал автоматизации более доступным "из коробки". Значение по умолчанию для активации планировщика должно быть `true` (Включено), чтобы пользователь сразу видел соответствующие пункты меню и мог создавать задачи без предварительного похода в настройки.

## 2. Область изменений (задокументированы точные файлы)

| Файл | Изменение | Строка |
|------|-----------|--------|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Изменить дефолт DataStore с `false` на `true` | ~300 |
| `app_v2/build.gradle.kts` | Изменить `buildConfigField ENABLE_SCHEDULED_OPERATIONS` в **release** variant с `"false"` на `"true"` | ~236 |

> **ВНИМАНИЕ**: Изменение одного лишь DataStore-дефолта НЕ ДОСТАТОЧНО.
> В release-сборке флаг жёстко закрыт на compile-time (`BuildConfig.ENABLE_SCHEDULED_OPERATIONS = false`).
> Вся UI-логика работает через двойной гейт:
> ```kotlin
> // BrowseActivity.kt
> val isScheduleEnabled = BuildConfig.ENABLE_SCHEDULED_OPERATIONS && viewModel.scheduledOperationsEnabled
> // FastMediaSorterApp.kt
> if (BuildConfig.ENABLE_SCHEDULED_OPERATIONS && settings.enableScheduledOperations) { ... }
> ```
> Без снятия compile-time блокировки в release пользователи feature не увидят.

## 3. Архитектурные требования (Strict Rules)
- **Архитектура**: Clean Architecture (слой Data + Build Config).
- **Стык**: `androidx.datastore.preferences.core` (DataStore) + BuildConfig (`app_v2/build.gradle.kts`).
- **Инъекция зависимостей**: Hilt (не касается данной задачи, существующая структура не меняется).
- **Логирование**: Использовать `Timber`.

## 4. Точная логика изменений

### 4.1 DataStore: изменение дефолта
**Файл**: `data/repository/SettingsRepositoryImpl.kt`

Фактическое имя ключа (не `PREF_IS_SCHEDULE_ENABLED` — это некорректное имя):
```kotlin
// строка ~122
private val KEY_ENABLE_SCHEDULED_OPERATIONS = booleanPreferencesKey("enable_scheduled_operations")
```

Место чтения дефолта (строка ~300):
```kotlin
// ДО:
enableScheduledOperations = preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: false,
// ПОСЛЕ:
enableScheduledOperations = preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: true,
```

### 4.2 BuildConfig: снятие compile-time блокировки в release

**Файл**: `app_v2/build.gradle.kts`

```kotlin
// ДО (release buildType, строка ~236):
buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "false")
// ПОСЛЕ:
buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
```

> **Примечание**: debug-вариант уже установлен в `"true"` (строка ~224) — не трогать.

### 4.3 Обработка миграции (поведение DataStore — ОК из коробки)
DataStore в Kotlin использует `?: defaultValue` при чтении — это означает:

- Ключ отсутствует (первый запуск / чистая установка) → вернёт новый дефолт `true` ✓
- Ключ есть и равен `false` (пользователь явно выключил) → вернёт сохранённое `false` ✓
- Ключ есть и равен `true` → вернёт сохранённое `true` ✓

**Миграции не требуется** — `DataStore` обеспечивает правильное поведение автоматически.

## 5. Требования к UI (Presentation Layer)
Изменения в UI-коде не требуются. Существующая логика корректно реагирует на изменение флагов:
- `BrowseActivity.kt` — перечитывает `viewModel.scheduledOperationsEnabled` при каждом открытии меню.
- `ResourceOpsMenuManager.kt` — показывает/скрывает `action_automate_resource` через `isScheduleEnabled`.
- `SettingsActivity` — отображает Switch, связанный с DataStore через `SettingsRepositoryImpl`.

## 6. Пошаговый план имплементации (Checklist)

### Шаг 1: DataStore — дефолт
- [x] Открыть `app_v2/src/.../data/repository/SettingsRepositoryImpl.kt`.
- [x] Найти строку `preferences[KEY_ENABLE_SCHEDULED_OPERATIONS] ?: false`.
- [x] Заменить `?: false` на `?: true`.

### Шаг 2: BuildConfig — release variant
- [x] Открыть `app_v2/build.gradle.kts`.
- [x] В блоке `buildTypes { release { ... } }` найти `buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "false")`.
- [x] Заменить `"false"` на `"true"`.
- [x] Убедиться, что debug-вариант (`"true"`) не изменён.

### Шаг 3: Проверка WorkManager (нет изменений, только проверка)
- [x] Убедиться, что `FastMediaSorterApp.kt` (строка ~198) вызывает `workManagerScheduler.rescheduleAll()` только при наличии активных задач в БД (или rescheduleAll безопасен при пустом списке задач — не создаёт паразитных Workers).
- [x] Включение флага должно открывать UI-возможности, а **не запускать Workers** при отсутствии задач.

Примечание по проверке: `WorkManagerScheduler.rescheduleAll()` загружает только `scheduledOperationRepository.getAllEnabled()` и затем итерирует найденные операции. При пустом списке задач метод фактически является no-op.

### Шаг 4: Верификация
- [x] Собрать **debug** сборку: `.\gradlew.bat assembleStandardDebug`.
- [ ] Удалить приложение с устройства/эмулятора (очистка DataStore).
- [ ] Установить заново.
- [ ] Зайти в `Browse` → открыть меню → убедиться, что пункт "Автоматизация" виден **без захода в настройки**.
- [ ] Зайти в `Settings` → убедиться, что тумблер "Операции по расписанию" включён.
- [ ] Собрать **release**: `.\gradlew.bat assembleStandardRelease`.
- [ ] Установить release APK и повторить проверку пункта меню.

Технический статус: `:app_v2:compileStandardDebugKotlin` прошёл успешно. `:app_v2:compileStandardReleaseKotlin` в текущем состоянии репозитория блокируется несвязанными ошибками по отсутствующим string resources (`unarchive_progress_entry`, `unarchive_error_no_space`) из другой задачи.

### Шаг 5: Локализация & Строки
- [ ] Не требует изменений (строки `settings_schedule_title` уже существуют).

### Шаг 6: Lint и Changelog
- [ ] Запустить `.\gradlew.bat lintStandardDebug` — убедиться, что нет новых предупреждений.
- [x] Лог в `dev/CHANGELOG.md` через `add_to_dev_log.ps1` для каждого изменённого файла.

Технический статус lint: запуск выполнен, но `lintStandardDebug` падает на уже существующей ошибке в `app_v2/src/main/res/layout/bottom_sheet_now_playing.xml` (`BottomSheetBehavior must extend android.view.View`, `Instantiatable`). Ошибка не связана с этой задачей.

## 7. Риски

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| **Регрессия старых пользователей**: пользователи, никогда не трогавшие настройку, обнаружат включённую автоматизацию после обновления | Средняя | Приемлемо — это целевое UX-поведение. DataStore не перезапишет явно сохранённое `false`. |
| **WorkManager при пустом списке задач**: `rescheduleAll()` запускается при старте с включёнными флагами | Низкая | Проверить реализацию `rescheduleAll()` — она должна быть no-op при пустой БД задач. |
| **BuildConfig release = true**: включение в production может обнажить баги автоматизации, скрытые под флагом | Средняя | Провести регрессионное тестирование сценариев автоматизации перед релизом. |

## 8. Затронутые файлы (итог)
1. `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` — строка ~300
2. `app_v2/build.gradle.kts` — строка ~236
