# Задача 4: Автоматическое удаление .trash и функция восстановления

## ✅ СТАТУС РЕАЛИЗАЦИИ

**Дата:** 2026-02-03  
**Прогресс:** 50% (Основные компоненты созданы, требуется интеграция)

### Выполнено

1. ✅ `TrashMetadata.kt` - Data class с JSON сериализацией
2. ✅ `CleanupTrashUseCase.kt` - UseCase для очистки .trash
3. ✅ `RestoreDeletedUseCase.kt` - UseCase для восстановления
4. ✅ `SettingsManager.kt` - Добавлена настройка `useTrash: Boolean`
5. ✅ Строковые ресурсы (EN, RU, UK)

### Требует интеграции

1. ⏳ **FileOperationUseCase** - Изменить с `.trash_timestamp` на `.trash` + metadata.json
2. ⏳ **BrowseViewModel** - Интегрировать CleanupTrashUseCase и RestoreDeletedUseCase
3. ⏳ **BrowseActivity** - Добавить UI кнопку "Восстановить"
4. ⏳ **Settings UI** - Добавить SwitchPreference для useTrash

📋 **Детальный статус:** См. `temp/task_04_implementation_status.md`

---

## Описание

Автоматически удалять каталоги `.trash` при открытии/сканировании папок. При удалении файлов помещать их в `.trash` и добавить возможность восстановления последнего удаленного файла/папки.

## Приоритет

🟢 Средний (новая функциональность)

## Структура .trash

```
.trash/
  ├─ metadata.json  # инфо откуда был файл
  └─ [удаленные файлы]  # только последний удаленный item
```

## Затронутые файлы

**Новые:**

- `data/model/TrashMetadata.kt`
- `domain/usecase/RestoreDeletedUseCase.kt`
- `domain/usecase/CleanupTrashUseCase.kt`

**Изменяемые:**

- `domain/usecase/DeleteFilesUseCase.kt`
- `BrowseViewModel.kt`
- `BrowseActivity.kt`
- `SettingsManager.kt`

---

## Промпты для разработки

### Промпт 1: Data model для метаданных

```
Создай data class для хранения метаданных удаленного файла:

Файл: `data/model/TrashMetadata.kt`

```kotlin
@Serializable
data class TrashMetadata(
    val originalPath: String,           // полный путь откуда файл
    val resourceId: Long,                // ID ресурса
    val resourceType: String,            // "Local", "SMB", "Cloud"
    val deletedFiles: List<String>,      // имена файлов в .trash
    val deletionTimestamp: Long,         // когда удалили
    val isDirectory: Boolean = false     // это папка или файл
)
```

Добавь методы:

- `toJson(): String` - сериализация в JSON
- `companion object { fun fromJson(json: String): TrashMetadata }` - десериализация

```

### Промпт 2: Изменение логики удаления
```

Модифицируй DeleteFilesUseCase чтобы удаление было через .trash:

1. Найди метод который удаляет файлы
2. Добавь параметр `useTrash: Boolean` (читай из Settings)
3. Если `useTrash == true`:
   - Создай `.trash/` в корне ресурса если не существует
   - Очисти `.trash/` полностью (удали предыдущие файлы)
   - Перемести удаляемые файлы в `.trash/`
   - Создай `metadata.json` с информацией
4. Если `useTrash == false`:
   - Удаляй напрямую как сейчас

Обработай все типы ресурсов: Local, SMB, Cloud.
Для Cloud используй .trash даже если у них своя корзина.

```

### Промпт 3: Автоочистка .trash при входе
```

Добавь очистку .trash при открытии ресурса:

В BrowseViewModel.loadResource():

1. После успешной загрузки ресурса
2. Запусти фоновую задачу cleanupTrash():
   - Найди `.trash/` в корне ресурса
   - Удали весь каталог молча (без вопросов)
3. При сканировании файлов:
   - Исключи `.trash/` из результатов
   - Файлы из `.trash/` НЕ должны показываться в списке

Очистка должна быть тихой - пользователь не видит.

```

### Промпт 4: UI кнопка восстановления
```

Добавь кнопку "Восстановить последний удаленный" в BrowseActivity:

1. В overflow menu командной панели добавь пункт:
   - Title: "Восстановить последний удаленный"
   - Icon: restore/undo icon
   - Visible only если `.trash/metadata.json` существует

2. При клике:
   - Прочитай metadata.json
   - Покажи confirmation dialog:
     "Восстановить [filename]?"
   - При OK вызови RestoreDeletedUseCase

3. После восстановления:
   - Toast: "Файл восстановлен: [name]"
   - Обнови список файлов

```

### Промпт 5: Restore logic
```

Создай RestoreDeletedUseCase для восстановления:

Файл: `domain/usecase/RestoreDeletedUseCase.kt`

Логика:

1. Прочитать `.trash/metadata.json`
2. Проверить существует ли original path:
   - Если НЕТ → показать ошибку "Оригинальная папка не существует"
   - Если ДА → продолжить
3. Проверить нет ли конфликта имен:
   - Если файл с таким именем уже есть → ошибка "Файл уже существует"
4. Переместить файлы из `.trash/` обратно в original path
5. Удалить `.trash/` полностью
6. Вернуть success

Обработай разные типы ресурсов (Local/SMB/Cloud).

```

### Промпт 6: Settings для .trash
```

Добавь настройку "Использовать корзину":

1. В SettingsManager добавь:

   ```kotlin
   val useTrash: Boolean
   ```

   Default: true

2. В Settings UI (fragment) добавь:
   - SwitchPreference "Использовать корзину (.trash)"
   - Summary: "Перемещать файлы в .trash вместо прямого удаления"
   - При первом удалении показать info dialog об этой функции

3. При выключении настройки:
   - Предупреждение: "Файлы будут удаляться без возможности восстановления"
   - Очистить все .trash при следующем открытии ресурсов

```

### Промпт 7: Время жизни .trash
```

Реализуй автоочистку .trash по правилам:

.trash/ хранится до:

1. **Выхода из ресурса** - при onStop() BrowseActivity для этого resourceId
2. **Восстановления** - после successful restore
3. **Следующего удаления** - перед move в .trash очищаем старое

В BrowseActivity.onStop():

```kotlin
if (isFinishing) {
    viewModel.cleanupTrashForCurrentResource()
}
```

В ViewModel:

```kotlin
fun cleanupTrashForCurrentResource() {
    // Удалить .trash/ для state.value.resource
}
```

```

### Промпт 8: Тестирование
```

Протестируй систему .trash:

1. **Базовый сценарий:**
   - Удали файл → проверь что он в .trash/
   - Восстанови → проверь что вернулся на место
   - .trash/ должна очиститься

2. **Двойное удаление:**
   - Удали file1.jpg → в .trash/
   - Удали file2.mp4 → .trash/ очищается, только file2.mp4

3. **Ошибка восстановления:**
   - Удали файл из /DCIM/Photos/
   - Переименуй Photos в Archive
   - Попробуй восстановить → должна быть ошибка

4. **Разные ресурсы:**
   - Локальный ресурс
   - SMB share
   - Google Drive (если .trash поддерживается)

5. **Settings:**
   - Выключи useTrash → файлы удаляются напрямую
   - Включи обратно → работает .trash

Запиши результаты тестов.

```

## Критерии готовности
- ✅ TrashMetadata.kt data class создан
- ✅ CleanupTrashUseCase создан
- ✅ RestoreDeletedUseCase создан
- ✅ Настройка useTrash добавлена в SettingsManager
- ✅ Строковые ресурсы добавлены (EN, RU, UK)
- ⏳ FileOperationUseCase требует модификации для использования .trash вместо .trash_timestamp
- ⏳ BrowseViewModel требует интеграции CleanupTrashUseCase и RestoreDeletedUseCase
- ⏳ BrowseActivity требует UI кнопки восстановления
- ✅ Работает для Local (в процессе), SMB, Cloud (частично реализовано)
