# Дизайн решения: распаковка ZIP по клику в Browse

Дата: 2026-04-01

## 1) Контекст (C4 – Context)
- Пользователь просматривает файлы в Browse.
- Требуется возможность распаковки ZIP по одному клику на архив.
- Поддержка: локальные ресурсы и SD-карта (SAF/content://).
- Ограничение: сеть/облако не поддерживаются.

## 2) Контейнеры (C4 – Container)
- UI: BrowseActivity + диалог подтверждения + Snackbar.
- ViewModel: BrowseViewModel (события + state для прогресса).
- Domain: ExtractArchiveUseCase (ZipInputStream, защита Zip Bomb, path traversal).
- Data: LocalTransferProvider / SafHelper (для SAF/content:// записи).

## 3) Компоненты (C4 – Component)
### UI
- BrowseActivity:
  - В showBinaryFileMenu() добавить ветку для MediaType.BINARY_ARCHIVE.
  - Показывать UnarchiveConfirmDialog.
  - Показывать прогресс (на основе нового ExtractProgress → FileOperationProgressDialog/простая AlertDialog).
  - По успеху: Snackbar с action "Open" → navigateToFolder(targetPath).

### ViewModel
- BrowseState расширить ExtractionState (isExtracting, currentEntry, done/total, progress, targetPath).
- BrowseEvent расширить:
  - ShowExtractConfirmDialog(file, targetDirName)
  - ExtractionProgress(entry, done, total, percent)
  - ExtractionSuccess(targetPath)
  - ExtractionFailed(message)
- Методы:
  - prepareExtraction(file) — расчет targetDirName
  - extractArchive(file)
  - cancelExtraction()

### Domain
- ExtractArchiveUseCase (новый):
  - Input: archivePath, targetDirPath, onCancel
  - Output: Flow<ExtractProgress>
  - ZipInputStream, потоковая запись.
  - Защита Zip Bomb: лимиты (2 GB, 100k entries, depth 10).
  - Path traversal: normalize + ensure inside targetDir.
  - Кодировка: UTF-8 с fallback на CP866.
  - SAF: если targetDirPath content:// → создавать выходные файлы через ContentResolver/DocumentFile.

### Data
- Использовать существующие helpers:
  - LocalTransferProvider.createDirectory() для SAF/локальных путей.
  - SafHelper.getDocumentFileFromUri() для доступа к дереву.
  - ContentResolver.openOutputStream() для записи.

## 4) Поток данных (Data Flow)
1) Пользователь кликает на ZIP (BINARY_ARCHIVE).
2) BrowseActivity -> viewModel.prepareExtraction(file)
3) ViewModel → BrowseEvent.ShowExtractConfirmDialog
4) Пользователь подтверждает → viewModel.extractArchive(file)
5) ViewModel запускает ExtractArchiveUseCase (IO) и коллекцию Flow
6) UI получает прогресс → обновляет диалог (процент + имя текущего файла)
7) Success → Snackbar + action "Open" → navigateToFolder(targetPath)

## 5) API/Contracts
### ExtractProgress
- Started(totalEntries)
- EntryDone(entryName, done, total, percent)
- Success(extractedCount, targetPath)
- Failure(error)

## 6) ADR (решения и причины)
1) **Без WorkManager в v1**: пользователь ожидает немедленный результат, процесс контролируется в текущем экране. WorkManager можно добавить в v2 при необходимости фоновой распаковки.
2) **Поддержка SAF/SD**: запись через ContentResolver, чтобы обеспечить Android 11+ доступ к SD.
3) **Суффикс при конфликте**: всегда _1.._99 без диалогов.
4) **Только ZIP**: ZipInputStream нативно поддерживается, остальные форматы требуют сторонних библиотек (вне задачи).

## 7) Обработка ошибок
- Network/Cloud ресурсы → сообщение "Недоступно для сетевых источников".
- Zip Bomb / path traversal → Abort + user-friendly error string.
- Нет места → отдельное сообщение.
- Cancel → тихое завершение.

## 8) Тестирование
### Unit
- ExtractArchiveUseCase:
  - success
  - zip bomb (size/entries/depth)
  - traversal ("../")
  - cancel
  - CP866 name

### Manual
- Local ZIP (кириллица), проверка прогресса и перехода.
- SAF/SD (content://): распаковка в выбранную папку.

## 9) Обновления документации
- После реализации добавить фичу в:
  - docs/FEATURES.md
  - docs/FEATURES_RU.md
  - docs/FEATURES_UK.md
