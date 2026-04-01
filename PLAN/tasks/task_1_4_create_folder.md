# Детализированное техзадание: Создать каталог в ресурсе (готово для разработчика)

## 1. Цель задачи
Реализовать функцию создания новой папки (директории) внутри текущего ресурса в `BrowseActivity`. Опция должна быть доступна для локальных и сетевых ресурсов (SMB/SFTP/FTP/Cloud) при условии поддержки навигации по подпапкам.

## 2. Местоположение в UI
- **Экран**: `BrowseActivity` (список файлов).
- **Точка входа**: Кнопка `Resource Operations` (в тулбаре) -> `ResourceOpsMenuManager` -> пункт `Создать каталог`.
- **Логика видимости**:
  - Пункт меню отображается **только**, если у ресурса (`MediaResource`) флаг `showSubfoldersAsItems == true`.
  - Если ресурс только для чтения (`isReadOnly == true`) — пункт скрыт.

## 3. Архитектурные требования (Strict Rules)
- **Архитектура**: MVVM + Clean Architecture.
- **Слой данных**: Использовать `UnifiedFileOperationHandler` для оркестрации.
- **Инъекция зависимостей**: Hilt.
- **Логирование**: Использовать `Timber`.

## 4. Слой данных (Data Layer)
**Статус**: Полностью реализовано. Ничего не требует изменений.

> `FileTransferProvider.createDirectory(path: String): Result<String>` — уже присутствует в интерфейсе.  
> `LocalTransferProvider.createDirectory()` — реализован (SAF + legacy File API).  
> `SmbTransferProvider.createDirectory()` — реализован через `SmbClient`.  
> `UnifiedFileOperationHandler.executeCreateDirectory(path)` — реализован.  

**[FIX] `LocalTransferProvider.createDirectory()` — добавить вызов `MediaStoreNotifier.notifyFile()` после создания папки на обычном Storage (не SAF), чтобы папка сразу появилась в MediaStore.**

## 5. Бизнес-логика (Domain Layer)
**Статус**: Полностью реализовано. `CreateDirectoryUseCase.kt` существует.

> `invoke(resource, parentPath, folderName): Result<String>` — реализован с полной валидацией:
> - проверка `isReadOnly`, trim, проверка пустой строки, запрещённые символы (`/ \ : * ? " < > |`), лимит 255 символов.
> - Делегирует в `UnifiedFileOperationHandler.executeCreateDirectory(fullPath)`.

Ничего не требует создания.

## 6. Презентационный слой (UI Layer)
**Частично реализовано.** Следующее уже готово:
- `BrowseViewModel.createFolder(name)` — полностью реализован (вызов UseCase + `ShowMessage`/`ShowError` events).
- `ResourceOpsMenuManager.showCreateFolderDialog()` — базовая реализация есть, **но без live-валидации**.
- `menu_resource_ops.xml` — пункт `action_create_folder` уже присутствует.
- Строки EN/RU/UK — все есть.

### [FIX-1] Видимость пункта меню (`ResourceOpsMenuManager.showMenu()`)
Текущий код НЕ скрывает пункт `action_create_folder`. Нужно:
- Скрывать `action_create_folder`, если `resource.showSubfoldersAsItems == false`.
- Скрывать `action_create_folder`, если `resource.isReadOnly == true`.
- Ресурс получать через `viewModel.state.value.resource`.

### [FIX-2] Live-валидация в диалоге (`showCreateFolderDialog()`)
Текущий код использует голый `EditText`. Нужно:
- Заменить на `TextInputLayout` + `TextInputEditText`.
- Кнопка "OK" (`android.R.string.ok`) **disabled**, пока поле пустое или содержит недопустимые символы.
- Отображать `error_invalid_folder_name` через `TextInputLayout.error` при вводе недопустимых символов.

### [FIX-3] После создания (уже работает) — обновление списка
Уже реализовано через `loadResource()` и `BrowseEvent.ShowMessage`.

## 7. Пошаговый план имплементации (Checklist)
- [x] `FileTransferProvider.createDirectory()` — уже готово
- [x] `LocalTransferProvider.createDirectory()` — SAF + File API реализованы
- [x] `SmbTransferProvider.createDirectory()` — реализован
- [x] `UnifiedFileOperationHandler.executeCreateDirectory()` — реализован
- [x] `CreateDirectoryUseCase` — реализован с валидацией
- [x] `BrowseViewModel.createFolder()` — реализован
- [x] Пункт `action_create_folder` в `menu_resource_ops.xml` — есть
- [x] Строки локализации EN/RU/UK — есть
- [x] **[FIX-1]** `ResourceOpsMenuManager.showMenu()`: скрывать `action_create_folder` если `!showSubfoldersAsItems` или `isReadOnly`
- [x] **[FIX-2]** `ResourceOpsMenuManager.showCreateFolderDialog()`: `TextInputLayout` + live-валидация + кнопка OK disabled
- [x] **[FIX-3]** `LocalTransferProvider.createDirectory()`: добавить `MediaStoreNotifier.notifyFile()` для legacy paths

## 8. Риски
- **Права доступа (SAF)**: создание может завершиться ошибкой, если нет прав на запись в родительскую папку (обработать `Result.failure`).
- **Синхронизация MediaStore**: после создания папки на локальном диске необходимо уведомить систему (вызвать `MediaStoreNotifier`).
