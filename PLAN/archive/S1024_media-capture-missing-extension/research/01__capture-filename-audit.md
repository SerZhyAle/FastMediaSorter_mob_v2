# S1024 - Media-capture filename/extension audit

**Дата:** 2026-07-15
**Метод:** read-only обход всех точек записи медиа в `app_v2` (catalog + Grep + Read).

## Вывод

Все точки **происхождения** захвата (скриншоты, камера, аудио, видео, кадры, кроп, PDF) генерируют имя с корректной маской и расширением. Репортовый баг «скриншот → редакция → файл без расширения» рождается **ниже по потоку** - в naming-коде Draw-редактора «Save as..», который переиспользует общий анти-паттерн: проверка `'.' in name` как признак «есть расширение». Имя с хвостовой точкой (`foo.`) проходит эту проверку, и расширение не дописывается → файл сохраняется без расширения (в т.ч. в `MediaStore.DISPLAY_NAME`).

## Дефекты

### DEFECT #1 (High) - Draw «Save as..» может сохранить без расширения
- `ui/player/helpers/ImageEditorFileNamer.kt:25-28` `buildName()` - при `ext == ""` даёт хвостовую точку (общий билдер CROP/COMPRESS/DRAW).
- `ui/player/helpers/ImageDrawOverlayManager.kt:184-190` `handleSaveRequest` - `extNoDot` без фолбэка (пусто, если у источника нет расширения) → prefill диалога уже с хвостовой точкой.
- `ui/player/helpers/PlayerDrawingSaveHelper.kt:442` `shareDrawingBytes` и `:583-585` `setupDrawOverlaySaveCallback` - `!filename.contains('.')`.
- `ui/player/standalone/StandaloneDrawSaveHelper.kt:123-124` `save()` - `chosen.contains('.')`; пишет в `MediaStore.Images.Media.DISPLAY_NAME` (точное совпадение с репортом: MIME корректен, DISPLAY_NAME с хвостовой точкой).

Два независимо-достаточных триггера: (1) источник без расширения → prefill уже сломан; (2) пользователь при переименовании стирает суффикс до точки - оба обходят `contains('.')`.

### DEFECT #2 (Low, dormant) - `domain/usecase/SaveDrawingUseCase.kt:229-234`
`normalizeName`: `if ('.' in trimmed)` - хвостовая точка возвращается как есть. Сейчас недостижимо (единственный вызыватель предзаполняет `currentFile.name` с `.jpg`), но тот же корень.

### DEFECT #3 (Low, edge) - `domain/usecase/CreateDrawingUseCase.kt:141-143`
`ensureJpegExtension`: `if (name.contains('.'))` - вручную введённое имя с хвостовой точкой сохраняется как есть.

## Все прочие семейства - OK

- Скриншоты: `SaveScreenshotUseCase` (`screenshot_<ts>.png`, PNG, DISPLAY_NAME+MIME согласованы), оба движка (`ScreenCaptureService` MediaProjection; `ScreenshotAccessibilityService` noLegal), `ScreenshotGestureActionDispatcher.stageOcrSourceFile` (`ocr_<ms>.png`). OK.
- Кроп / OCR-изображение: `ImageCropManager` (везде `substringAfterLast('.', "jpg")`, формат из того же ext), `CameraOcrStorageManager` (`CAP_/OCR_IMG_/OCR_TXT_<ts>.<ext>`). OK.
- Кадры/GIF/stream: `SaveVideoFrameManager` (ext и формат из одного `useJpeg`), `SaveGifFirstFrameUseCase` (`_first_frame.png`), `StreamFramePersistentStore` (`<hash>.jpg`, приватный кэш). OK.
- Аудио: `QuickAudioRecorderService`, `BrowseMicRecordingManager` (`REC_<ts>.m4a`, MPEG_4/AAC), `MicRecordingSaver` (имя от вызывателя). OK.
- Видео: `ScreenVideoRecordingService` (`SCR_<ts>.mp4`). OK.
- Камера: `BrowseCameraCaptureManager`/`MainCameraCaptureManager`/`widget/Camera*`/`CameraCaptureFlowManager`/`CameraCaptureSaver` (`CAP_<ts>.jpg|.mp4`, rename через безопасный `withExt`/`withCapturedExt` = `endsWith(".<ext>")`). OK.
- PDF/отчёты: `PdfExportHelper` (`_page_N.jpg`), `StatisticsReportShareManager` (`statistics_report.txt`). OK.

## Общие точки записи (не источник дефекта)

- `LocalDestinationClassifier` + `MediaStoreLocalDestinationWriter` - общий конвейер MediaStore/legacy; переносит `displayName`/`mimeType` от вызывателя дословно (корректность гейтится вызывателем).
- Отдельного общего билдера capture-имён нет: каждое место инлайнит свой `SimpleDateFormat(..)+prefix+ext` (внутренне согласованно).

## Безопасный шаблон (эталон в коде)

- `ImageCropManager`: `sourceFile.name.substringAfterLast('.', "jpg")` (дефолт никогда не пуст).
- `BrowseCameraCaptureManager.withExt` / `CameraQuickCaptureLaunchManager.withCapturedExt`: `name.endsWith(dotExt, ignoreCase = true)` против известного-корректного расширения.

Фикс: заменить `contains('.')` на `substringAfterLast('.', "").isBlank()` и консолидировать UI-потребителей в один чистый `ImageEditorFileNamer.ensureExtension(name, fallbackExt)`; домен-usecase'ы починить inline (слой не зависит от ui).

## /spec-draft кандидат (вне объёма)

- Мёртвая обвязка `ImageDrawOverlayManager.inPlaceSaveCallback` / `DrawOverlayInPlaceSaveCallback`: полностью подключена `PlayerDrawingSaveHelper.setupDrawOverlayInPlaceSaveCallback()` (`:480-558`), но 0 вызывателей в `src/main` (кнопка `btn_draw_save` зовёт `actionCallback.onSaveRequested`). ~80 LOC недостижимой проводки (наследие «S0192 Phase 06»). Rule 20 dead-weight - отдельный тикет.
