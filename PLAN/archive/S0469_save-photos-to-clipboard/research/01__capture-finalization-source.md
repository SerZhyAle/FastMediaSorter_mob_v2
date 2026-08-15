# S0469 research 01 - Источник картинки и точка финализации в потоке съёмки фото

## Вопрос (§6 Q1)

Что копировать в буфер - живой декодированный bitmap или сохранённый файл снимка - и что реально доступно в точке финализации потока съёмки фото?

## Находки

- Встроенная съёмка фото - `CameraCaptureActivity` + `CameraCaptureSessionManager` (CameraX `ImageCapture.takePicture(OutputFileOptions(file), ..)`). CameraX пишет JPEG **сразу в файл**; декодированный bitmap потоку никогда не отдаётся. То есть «живого bitmap» в точке финализации нет - есть только файл-снимок.
- `CameraCaptureActivity` - drop-in замена `ACTION_IMAGE_CAPTURE`: снимает в temp-файл и возвращает `RESULT_OK`. Маршрутизацию в целевую папку (DCIM / ресурс / upload) делает единый backend `CameraCaptureSaver.save(tempFile, name, target, upload)`.
- Оба входа - Browse (`BrowseCameraCaptureManager.save`) и виджет (`CameraQuickCaptureLaunchManager.save`) - делегируют ровно в `CameraCaptureSaver.save()`. Это единственная точка, где готовый файл-снимок существует для обоих сценариев, и сразу после попытки сохранения он удаляется (`finally { tempFile.delete() }`).

## Решение

- Источник буфера - **сохранённый файл-снимок** (`tempFile`), не bitmap.
- Точка вызова шага копирования - `CameraCaptureSaver.save()`, до `tempFile.delete()`, под гейтом `MediaType.IMAGE` (отсекает видео-съёмку, которая идёт через тот же saver) и под новым флагом настройки. Запись микрофона идёт мимо этого saver - не затрагивается.
- Роль из S0468 (`ImageClipboardWriter`) расширяется файловым методом `copyImageFile(File)`: копия байтов файла в app-cache + content-URI через FileProvider, без decode→PNG - сохраняет исходный формат/качество (цель 3).

## Статус

Resolved.
