Выбор цвета для получателя

В портретном режиме выводить не более двух элементов в строчку, в ландшафтном - не более 3-4 (в зависимости от размера экрана). В остальных случаях - по одному элементу на строку. Это позволит избежать проблем с разными размерами экранов и обеспечит удобство использования приложения.

## Задача: Добавить виртуальную папку "Недавние файлы" в предложения

**Цель:** Добавить пункт "Недавние медиа" в список локальных папок (где сейчас предлагаются WhatsApp, Telegram и т.д.). Этот пункт должен открывать список последних файлов на устройстве, отсортированных по дате изменения.

**Техническая реализация:**

1.  **Backend - MediaStoreRepository**:
    -   Модифицировать интерфейс `MediaStoreRepository` и реализацию `MediaStoreRepositoryImpl`:
        -   Добавить метод `suspend fun getRecentFiles(limit: Int, allowedTypes: Set<MediaType>): List<MediaFile>`.
        -   Реализация: Запрос к `MediaStore.Files.getContentUri("external")`.
        -   Сортировка: `${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC`.
        -   Лимит: например, 1000 файлов.
        -   Фильтрация по типам медиа (как в `getFoldersWithMedia`).

2.  **Logic - LocalMediaScanner**:
    -   Определить константу для виртуального пути, например: `const val VIRTUAL_PATH_RECENT = "virtual://recent"`.
    -   В методе `scanFolder()` добавить проверку: если `path == VIRTUAL_PATH_RECENT`, вызывать новый метод `scanRecentFiles(...)`, который делегирует поиск в репозиторий.
    -   Аналогично обновить `getFileCount()` и `isWritable()` (вернуть false, так как это виртуальная папка).

3.  **UseCase - ScanLocalFoldersUseCase**:
    -   В методе `invoke()` вручную создавать `MediaResource` для "Недавних файлов".
    -   Path: `VIRTUAL_PATH_RECENT`.
    -   Name: Локализованная строка "Recent Media" / "Недавние".
    -   Type: `ResourceType.LOCAL`.
    -   Добавлять этот ресурс в список возвращаемых папок.

4.  **UI/Resources**:
    -   Добавить строки в `strings.xml`.


