# Детализированное техзадание: Печать документов из интерфейса (готово для разработчика)

> **Ревизия**: 2026-04-01. Исправлены: имена MediaType, класса-менеджера, пути меню; добавлены флаг флейвора, риски сетевых источников, очистка кэша.

## 1. Цель задачи
Обеспечить пользователям возможность распечатывать PDF, текстовые файлы и изображения непосредственно из приложения во время просмотра, используя стандартную системную службу печати Android (`android.print.PrintManager`).

## 2. Местоположение в UI
- **Экран**: `PlayerActivity` (экран просмотра — документы рендерятся через менеджеры в `ui/player/helpers/`, отдельного `DocumentViewerFragment` нет).
- **Точка входа**: Overflow-меню тулбара → пункт `На печать` (`action_print`).
- **Условие отображения** (на основе реальных значений `MediaType`):
  - Пункт **виден**: `MediaType.PDF`, `MediaType.TEXT`, `MediaType.IMAGE`.
  - Пункт **скрыт**: `VIDEO`, `AUDIO`, `GIF`, `EPUB`, `BINARY_*` и все прочие типы.
  - Дополнительно: пункт **скрыт**, если `BuildConfig.SUPPORT_DOCUMENTS == false` (флейворы `lite`, `photos`).

## 3. Архитектурные требования (Strict Rules)
- **Архитектура**: MVVM + Clean. Вся логика печати инкапсулирована в `DocumentPrintManager` (по аналогии с уже существующим `PlayerShareManager`). Никакой логики печати в `PlayerActivity` напрямую.
- **Имя класса**: `DocumentPrintManager` (не `PrintManager` — конфликт с системным `android.print.PrintManager`).
- **Расположение**: `ui/player/helpers/DocumentPrintManager.kt`.
- **Системный API**: `android.print.PrintManager` (доступен с API 19; minSdk проекта = 26, проверки `SDK_INT` **не требуются**).
- **DI**: Hilt — `@ActivityScoped` бин через существующий `PlayerModule` или отдельный `PlayerPrintModule`.
- **Логирование**: Только `Timber`. `Log.d()` запрещён.

## 4. Логика и функции (`DocumentPrintManager`)

### 4.1 Публичный API
```kotlin
fun printCurrentFile(activity: Activity, uri: Uri, mediaType: MediaType, fileName: String)
```
Единая точка входа; диспетчеризация по `mediaType` внутри.

### 4.2 Стратегии по типу файла

| `MediaType` | Стратегия |
|---|---|
| `PDF` | Собственный `PdfPrintDocumentAdapter` (читает байты через `ContentResolver.openInputStream`; реализует `PrintDocumentAdapter`). |
| `IMAGE` | `PrintHelper.printBitmap()` — стандартный Jetpack-хелпер, одно действие. |
| `TEXT` | Конвертировать текст в `PrintedPdfDocument` постранично через `Canvas`; или завернуть в HTML и напечатать через `WebView.createPrintDocumentAdapter()`. |

### 4.3 Файлы из сетевых источников (SMB / SFTP / FTP / Cloud)
Если `uri` не является `content://` или `file://` с прямым доступом ОС:
1. Скачать файл во временный файл: `File(context.cacheDir, "print_tmp_${System.currentTimeMillis()}")`.
2. Передать `Uri.fromFile(tempFile)` в адаптер.
3. **После завершения задания** (в `onFinish()` адаптера) — удалить `tempFile`.
4. Если загрузка не удалась — показать `Snackbar` с строкой `error_print_unavailable` (не Toast).

### 4.4 Параметры печати
- Дефолтный размер страницы: `PrintAttributes.MediaSize.ISO_A4`.
- Ориентация: выбор через системный диалог (не задавать хардкодом).

### 4.5 Проверка готовности сервиса
```kotlin
val pm = activity.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
if (pm == null) { showSnackbar(R.string.error_print_unavailable); return }
```

## 5. Требования к UI (Presentation Layer)

### 5.1 Меню
- Файл: `app_v2/src/main/res/menu/overflow_menu_player.xml` (существующий файл).
- Добавить элемент:
  ```xml
  <item
      android:id="@+id/action_print"
      android:title="@string/action_print"
      android:icon="@drawable/ic_print"
      app:showAsAction="never" />
  ```

### 5.2 Видимость пункта меню
- Управление через `PlayerUiStateCoordinator` или `PlayerDialogAndUiStateManager` (согласовать с командой по аналогии с `action_share`).
- Логика: наблюдать `PlayerViewModel.currentMediaType: StateFlow<MediaType?>`, устанавливать видимость при `PREPARE_OPTIONS_MENU` или через `invalidateOptionsMenu()`.

### 5.3 Обработчик клика
- В `PlayerActivity.onOptionsItemSelected` добавить `R.id.action_print` → вызов `documentPrintManager.printCurrentFile(...)`.
- Не блокировать UI вручную — системный диалог печати является модальным сам по себе.

## 6. Пошаговый план имплементации (Checklist)

### Шаг 1: Вспомогательные ресурсы
- [ ] Добавить строки в `strings.xml` (все три локали `values/`, `values-ru/`, `values-uk/`):
  - `action_print` — "Print" / "Печать" / "Друк"
  - `error_print_unavailable` — "No print service available" / "Службы печати недоступны" / "Служби друку недоступні"
  - `print_job_label` — "Printing: %s" / "Печать: %s" / "Друк: %s"
- [ ] Добавить иконку `ic_print` (взять из Material Icons или использовать `@drawable/ic_share` как шаблон).

### Шаг 2: Core — `DocumentPrintManager`
- [ ] Создать `ui/player/helpers/DocumentPrintManager.kt`.
- [ ] Реализовать `printCurrentFile(activity, uri, mediaType, fileName)`.
- [ ] Реализовать внутренний `PdfPrintDocumentAdapter : PrintDocumentAdapter` с корректным закрытием стримов в `onFinish()`.
- [ ] Реализовать логику кэширования сетевых файлов + очистку temp-файла в `onFinish()`.
- [ ] Реализовать ветку `TEXT` (через `WebView.createPrintDocumentAdapter()` — проще и надёжнее ручного Canvas).

### Шаг 3: DI
- [ ] Добавить `@Provides @ActivityScoped fun provideDocumentPrintManager(...)` в `PlayerModule.kt` (или создать `PlayerPrintModule.kt`).

### Шаг 4: UI — интеграция в PlayerActivity
- [ ] Добавить `action_print` в `overflow_menu_player.xml`.
- [ ] В `PlayerUiStateCoordinator` (или `PlayerDialogAndUiStateManager`) — управление видимостью пункта меню.
- [ ] В `PlayerActivity.onOptionsItemSelected` — обработчик `R.id.action_print`.
- [ ] Инжектировать `DocumentPrintManager` в `PlayerActivity` через `@Inject`.

### Шаг 5: Проверка и линт
- [ ] Убедиться, что пункт скрыт на `lite`/`photos` флейворах (`BuildConfig.SUPPORT_DOCUMENTS`).
- [ ] Проверить на PDF-файле через виртуальный принтер Android (Save as PDF).
- [ ] Проверить на файле с SMB-источника (temp download + cleanup).
- [ ] Разрешить все lint-предупреждения в затронутых файлах.
- [ ] Запустить `.\gradlew.bat lintStandardDebug` и `testStandardDebugUnitTest`.
- [ ] Залогировать изменения: `.\scripts\add_to_dev_log.ps1 ...` для каждого изменённого файла.

## 7. Риски и решения

| Риск | Решение |
|---|---|
| Файл на SMB/SFTP/FTP/Cloud — `PrintManager` не имеет доступа к URI провайдера | Скачать во `context.cacheDir` перед передачей адаптеру; удалить в `onFinish()` |
| `WebView` для TEXT не инициализирован на момент печати | Создавать `WebView` динамически только для задачи печати, не в layout |
| Утечка `InputStream` в `PdfPrintDocumentAdapter` | Закрывать в `finally` блоке в `onWrite()` и в `onFinish()` |
| `EPUB` — сложный HTML-рендеринг, неоднозначный результат | EPUB исключён из области задачи; при необходимости — отдельная задача |
| Флейвор `lite`/`photos` — `SUPPORT_DOCUMENTS = false` | Скрывать `action_print` через `BuildConfig.SUPPORT_DOCUMENTS` проверку |
| Большой PDF (>50 MB) — OOM при загрузке в кэш для сетевого файла | Добавить размерный лимит (напр. 30 MB); при превышении — Snackbar с предупреждением |
