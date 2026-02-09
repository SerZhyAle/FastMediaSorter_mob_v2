# Стратегический План Разработки - FastMediaSorter v2

> **Версия:** 2.0  
> **Дата создания:** 2026-02-03  
> **Статус:** Утвержден

---

## Обзор

Этот документ содержит стратегический план разработки для исправления известных багов и добавления новых функций в FastMediaSorter v2. Каждая задача описана на высоком уровне со ссылками на детальные тактические инструкции.

## Тактические Задания

Детальные пошаговые инструкции для каждой задачи с промптами для разработки находятся в:

📁 **`dev/tasks/`**

---

## Задачи

### 🔴 Критические Баги

#### [Задача 1: Исправление удаления большого числа локальных файлов](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_01_fix_batch_deletion.md) ✅

**Проблема:** При удалении нескольких локальных файлов за один раз удаляются не все и плохо работает обработка ошибок.

**Цель:** Исправить логику batch deletion, добавить прогресс >2 сек, улучшить обработку ошибок.

**Файлы:** `BrowseViewModel.kt`, `BrowseActivity.kt`, `DeleteFilesUseCase.kt`

**Статус:** ✅ **COMPLETED** (2026-02-04)

**Реализация:**

- Добавлена функция `collectMediaStoreUris()` для сбора всех URI перед удалением
- Batch delete теперь создает ОДИН `PendingIntent` для всех файлов (Android 11+)
- Устранены множественные диалоги разрешений при удалении 10+ файлов
- См. `temp/task_01_batch_deletion_analysis.md`

---

#### [Задача 3: Не все файлы видны даже с включенными фильтрами](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_03_missing_files.md) ✅

**Проблема:** Некоторые файлы не отображаются даже когда включены "показывать скрытые" и "все файлы". Замечено на Android 16.

**Цель:** Найти и исправить логику фильтрации, обеспечить совместимость с Android 16 Storage API.

**Файлы:** `LocalResourceManager.kt`, `MediaFileRepository.kt`, `BrowseViewModel.kt`

**Статус:** ✅ **COMPLETED** (2026-02-04)

**Реализация:**

- Добавлен параметр `showHiddenFiles` в `MediaStoreRepository.getFilesInFolder()`
- Исправлена фильтрация скрытых файлов в `MediaStoreRepositoryImpl` и `LocalMediaScanner`
- Параметр корректно передается через всю цепочку вызовов
- Добавлено логирование для отладки фильтрации
- См. `temp/task_03_missing_files_analysis.md`

---

### 🟡 Важные Улучшения

#### [Задача 2: Connection timeout для папок с >10000 файлов (SMB)](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_02_smb_timeout.md)

**Проблема:** `TimeoutException` при загрузке SMB папок с огромным числом файлов (>10000).

**Цель:** Установить адаптивный timeout (15 сек), показывать прогресс с кнопкой "Прервать", оптимизировать loading.

**Файлы:** `SmbResourceManager.kt`, `BrowseViewModel.kt`, SMB connection configuration

**Статус:** 🟡 Средний приоритет (edge case, но важно)

---

#### [Задача 4: Система .trash и восстановление](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_04_trash_system.md)

**Описание:** Автоматически удалять `.trash` при входе в ресурс. При удалении перемещать файлы в `.trash` и добавить кнопку восстановления последнего удаленного.

**Цель:** Повысить безопасность удаления, добавить возможность отмены.

**Новые файлы:**

- `data/model/TrashMetadata.kt`
- `domain/usecase/RestoreDeletedUseCase.kt`
- `domain/usecase/CleanupTrashUseCase.kt`

**Изменяемые:** `DeleteFilesUseCase.kt`, `BrowseViewModel.kt`, `BrowseActivity.kt`, `SettingsManager.kt`

**Статус:** 🟢 Новая функция

---

### 🟢 Улучшения UX

#### [Задача 5: Режим "Показывать подпапки отдельно"](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_05_show_subfolders.md)

**Описание:** Новый флаг в настройках. Если включено - папки отображаются как элементы списка, клик открывает папку. Если выключено - все файлы из подпапок в общем списке (текущее поведение).

**Особенности:**

- Папки всегда сверху, по алфавиту
- Режим сортировки только для файлов
- Глобальная настройка + per-resource override
- ⚠️ **Требуется БД миграция**

**Файлы:** `MediaFile.kt`, `MediaResource.kt`, `BrowseState.kt`, `BrowseViewModel.kt`, `BrowseActivity.kt`, `MediaFileAdapter.kt`

**Статус:** 🟢 Улучшение UX (требует БД миграцию)

---

#### [Задача 6: Специальные миниатюры для бинарных файлов](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_06_binary_files.md) ✅

**Описание:** Для бинарных файлов (ZIP, RAR, APK, ISO, EXE, DLL и т.д.) показывать генерируемые миниатюры с текстом расширения. При  клике показывать меню вместо открытия в плеере.

**Особенности:**

- Миниатюры генерируются программно (разные цвета для разных типов)
- Bottom Sheet Menu: Share / Open with / Copy / Move / Rename / Delete
- Отображаются только в режиме "All files"

**Новые файлы:**

- `util/BinaryFileTypeDetector.kt`
- `util/BinaryFileThumbnailGenerator.kt`

**Изменяемые:** `MediaType.kt`, `MediaFileAdapter.kt`, `PlayerActivity.kt`

**Статус:** ✅ **COMPLETED** (2026-02-04)

**Реализация:**

- `MediaType` enum уже содержит BINARY_ARCHIVE, BINARY_DISK, BINARY_EXECUTABLE, BINARY_OTHER
- `BinaryFileTypeDetector` реализован с поддержкой 60+ binary расширений
- `BinaryFileThumbnailGenerator` создаёт программные thumbnails с gradient backgrounds
- `MediaFileAdapter` интегрирован с binary thumbnail generation
- `BrowseActivity.showBinaryFileMenu()` показывает Bottom Sheet с 6 действиями
- Binary files показываются только при `allFiles=true` в настройках ресурса
- См. `dev/tasks/task_06_binary_files.md` для полной спецификации

---

### 🔵 Будущее

#### [Задача 7: Операции с папками](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_07_folder_operations.md)

**Описание:** Расширение функциональности работы с папками: выбор, избранное, копирование/перемещение, создание, переименование, удаление.

⚠️ **Зависит от Задачи 5** - реализуется ПОСЛЕ

**Функции:**

- Selection папок (checkbox, multi-select)
- Добавление папок в Favorites
- Копирование/перемещение папок (рекурсивно)
- Создание новой папки
- Переименование папки
- Удаление папки с подтверждением

**Новые файлы:** 5 новых UseCases для folder operations

**Статус:** 🔵 Будущая функциональность

---

#### [Задача 8: Полная поддержка мыши и клавиатуры](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/task_08_keyboard_mouse_support.md) ✅

**Описание:** Реализовать comprehensive поддержку мыши и клавиатуры во всех экранах, списках и activity приложения.

**Функции:**

- **Навигация клавиатурой:** Arrow keys для перемещения по спискам, Tab для переключения фокуса
- **Клавиатурные shortcuts:**
  - `Space` / `Enter` - выбор/открытие файла
  - `Ctrl+A` - выбрать все
  - `Ctrl+C` - копировать
  - `Ctrl+X` - вырезать/переместить
  - `Delete` - удалить
  - `F2` - переименовать
  - `F5` - обновить
  - `Backspace` - назад
  - `Escape` - отменить выбор/закрыть диалог
- **Мышь:**
  - Click - выбор файла
  - Double-click - открыть файл
  - Right-click - контекстное меню
  - Hover effects для всех кнопок и элементов
  - Focus indicators для всех интерактивных элементов
- **Accessibility:** Proper focus indicators, screen reader support

**Созданные файлы:**

- `util/KeyboardShortcutHandler.kt`
- `ui/common/MouseEventHandler.kt`
- `ui/common/FocusManager.kt`
- `res/drawable/item_focus_selector.xml`
- `res/drawable/button_hover_selector.xml`
- `res/menu/context_menu_file.xml`

**Изменённые файлы:**

- `MainActivity.kt`, `BrowseActivity.kt`, `PlayerActivity.kt`
- `MediaFileAdapter.kt`, `ResourceAdapter.kt`
- Layout XML files (item_media_file.xml, item_resource.xml и др.)
- `res/values/colors.xml` (focus/hover colors)
- Documentation (README.md на 3 языках)

**Статус:** ✅ **ВЫПОЛНЕНО** (2026-02-04)

---

## Приоритизация

### ⚠️ Примечание

**Строгой последовательности нет** - задачи выполняются по мере готовности и по ситуации.

### Рекомендуемый порядок

**Sprint 1 - Критические баги:**

1. 🔴 Задача 3 - Пропущенные файлы ⏳ **TODO**
2. 🔴 Задача 1 - Batch deletion ⏳ **TODO**

**Sprint 2 - Важные улучшения:**
3. 🟡 Задача 2 - SMB timeout ⏳ **TODO**
4. 🟢 Задача 4 - .trash система 🔶 **50% (основа готова, нужна интеграция)**

**Sprint 3 - UX улучшения:**
5. 🟢 Задача 5 - Подпапки отдельно (БД миграция!) ⏳ **TODO**
6. 🟢 Задача 6 - Binary файлы ⏳ **TODO**

**Будущее:**
7. 🔵 Задача 7 - Операции с папками (после #5) ⏸️ **ОЖИДАЕТ Задачу 5**
8. ✅ Задача 8 - Полная поддержка мыши и клавиатуры **ЗАВЕРШЕНО (2026-02-04)**

---

## Документация

После реализации каждой задачи обновить:

- **README.md** - новые функции
- **FAQ.md** - если добавляются настройки
- **HOW_TO.md** - инструкции для пользователей
- **Code comments** - для сложной логики

---

## Backwards Compatibility

- Все изменения должны быть обратно совместимы
- **БД миграции обязательны** где меняется схема (Задачи 5, 7)
- Настройки по умолчанию не должны ломать существующий UX
- ⚠️ Приложение уже в Google Play - тестировать миграции тщательно!

---

## Ответы на вопросы (уточнено с заказчиком)

### Задача 1: Удаление файлов

- **Прогресс удаления:** Показывать если операция >2 сек
- **Ошибки:** Использовать существующий диалог детальных ошибок (если настройка включена)

### Задача 2: SMB Timeout

- **Timeout:** 15 секунд если ресурс не отвечает (процесс не идет)
- **Долгая загрузка:** Показывать прогресс с кнопкой "Прервать"
- **Progressive loading:** Желательно, но сложно - использовать текущий механизм

### Задача 3: Скрытые файлы

- **Примеры:** Появятся при тестировании
- **Android версия:** Проблема на Android 16 (новый storage API)
- **Типы ресурсов:** Проверить все (Local, SMB, Cloud)

### Задача 4: .trash

- **Облачные ресурсы:** ✅ Да, .trash для облаков тоже
- **Размер .trash:** Всегда только последний deleted item
- **Время хранения:** До выхода из ресурса / restore / next delete
- **Проблемы с путем:** Показать ошибку если путь не существует

### Задача 5: Подпапки отдельно

- **Сортировка папок:** ✅ Все папки сверху, всегда по алфавиту
- **Операции с папками:** ❌ Отдельная задача (Задача 7)
- **Настройка:** Глобальная + per-resource override
- **БД:** ⚠️ Миграция обязательна

### Задача 6: Binary файлы

- **Расширения:** Стандартный набор
- **Open with:** ✅ Да, меню с Share/Open/Copy/Move/Rename/Delete
- **Отображение:** Только в "All files"

### Общее

- **Последовательность:** Нет строгой, по ситуации
- **Версионирование:** Без изменений
- **Миграции БД:** ⚠️ Обязательны (приложение в Play Store)

---

## Процесс Разработки

Для каждой задачи:

1. **Прочитать тактическое задание** в `dev/tasks/task_XX_name.md`
2. **Следовать промптам** последовательно
3. **Тестировать** после каждого этапа
4. **Логировать прогресс** в комментариях кода
5. **Обновить документацию** после завершения

---

## Контакты

- **Репозиторий:** [FastMediaSorter_mob_v2](https://github.com/SerZhyAle/FastMediaSorter_mob_v2)
- **Todo List:** [dev/todo.md](file:///c:/GIT/FastMediaSorter_mob_v2/dev/todo.md)
- **Tactical Tasks:** [dev/tasks/](file:///c:/GIT/FastMediaSorter_mob_v2/dev/tasks/)
