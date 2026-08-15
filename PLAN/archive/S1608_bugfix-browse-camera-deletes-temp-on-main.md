# Спецификация (compact bugfix): S1608 - browse-камера удаляет временные файлы на главном потоке

**Ticket:** S1608
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Текст:**

Sibling of S1593: BrowseCameraCaptureManager deletes scratch/temp capture files on the main thread at ~13 call sites. Found while fixing S1593 (MainCameraCaptureManager.clearPending main-thread File.delete, StrictMode DiskWriteViolation on Samsung Galaxy S21+, Android 15). The browse-side manager has the same defect class but a wider spread: tempFile.delete() runs inline on the main thread in launchPhoto/launchVideo catch blocks (ActivityNotFoundException, SecurityException, Throwable - both photo and video paths), in the FileProvider.getUriForFile failure paths, in restoreState when the resource is gone, in handleResult on the null-pendingResource and non-OK-resultCode paths, and in the rename dialog's setNegativeButton / setOnCancelListener callbacks. File: app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt, delete() at lines 142, 149, 171, 177, 183, 232, 253, 260, 267, 303, 334, 349, 388, 389. The class already owns a coroutineScope (used for the save flow), so an off-main home exists. Out of scope for S1593, whose captured text names only MainCameraCaptureManager. Dedup: search.ps1 on BrowseCameraCaptureManager and tempFile returns nothing.

**Захвачено во время:** S1593 (`/skill-fix`)

---

## 1. Проблема / симптом

- Любой путь отказа/отмены захвата с экрана browse удаляет временный файл прямо на главном потоке, то есть даёт StrictMode DiskWriteViolation.
- Путей четырнадцать: catch-блоки `launch()` и `launchVideo()` (FileProvider и `launcher.launch`), `restoreState()` при пропавшем ресурсе, `handleResult()` на null-ресурсе и не-OK результате, обе кнопки отмены диалога имени.
- Эвиденс класса дефекта - S1593 на RFCR110NBQJ (Samsung Galaxy S21+, Android 15): там тот же inline `File.delete()` в колбэке результата дал по нарушению на путь, `temp/S1579/run4_control.log`.
- Отдельного device-лога по browse-стороне нет: дефект найден чтением кода при фиксе S1593, поэтому проверка ниже - именно прогон этих путей на устройстве.

---

## 2. Корневая причина

- `BrowseCameraCaptureManager` удалял `tempFile` вызовом `File.delete()` inline в каждой точке отказа.
- Все эти точки - главный поток: колбэки `ActivityResultLauncher`, catch-блоки синхронного `launch()`, кнопки диалога.
- Удаление файла - дисковая запись, поэтому каждый такой путь давал нарушение.
- Класс уже держит `coroutineScope` (`lifecycleScope` активити-хоста, `BrowseActivity`), то есть место вне главного потока было доступно и до фикса.
- Разброс точек - причина, по которой чинить надо одной воронкой, а не четырнадцатью правками: иначе следующий добавленный путь отказа снова уйдёт inline.

---

## 3. Исправление

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`.
- Добавлен приватный `deleteTempAsync(file: File)` - единственная точка удаления временного файла в классе.
- Внутри: `coroutineScope.launch(Dispatchers.IO + NonCancellable) { file.delete() }`.
- `NonCancellable` обязателен: скоуп - `lifecycleScope` активити, и без него destroy сразу после результата отменил бы удаление и оставил мусор, тогда как прежний inline-вызов такую гарантию давал.
- Все четырнадцать вызовов `tempFile.delete()` / `file.delete()` заменены на `deleteTempAsync(..)`; сброс `pendingTempFile` / `pendingResource` / `pendingIsVideo` остался синхронным, файл снапшотится в локальную переменную до ухода в фон.
- Сознательно вне правки: `createTemp()` делает `createNewFile()` на главном потоке - тот же класс нарушений, но требует перестройки `launch()` в корутину (как S1579 сделал для главного экрана). Вынесено отдельным тикетом.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1593 (same defect class, main-screen camera manager), S1609 (main-thread temp-file creation in the same class)

---

## 4. Проверка

- Компиляция: `pwsh -NoProfile -File ./a.ps1 fk` - expected: exit 0 | actual: BUILD SUCCESSFUL in 23s, exit 0.
- On-device, экран browse: сделать фото в папку и отменить в диалоге имени - в logcat не должно быть DiskWriteViolation со стеком `BrowseCameraCaptureManager`.
- Probe-тег `S1608: deleteTempAsync offloading temp delete` в logcat подтверждает, что воронка отработала - без него отсутствие нарушения ничего не доказывает.
- Тот же сценарий с записью видео и с отменой самого захвата (возврат без съёмки).
- Негативная проверка: `Android/data/<pkg>/files/Pictures` и `.../Movies` после отмен не накапливают `CAP_*.jpg` / `CAP_*.mp4`, то есть удаление не потерялось при уходе в фон.
- Регресс: успешное сохранение по-прежнему кладёт файл в папку и показывает snackbar - удалением временного файла в этом пути владеет `CameraCaptureSaver`, он не тронут.
