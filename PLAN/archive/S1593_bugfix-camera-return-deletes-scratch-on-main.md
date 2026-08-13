# Спецификация (compact bugfix): S1593 - возврат из хоста камеры удаляет черновые файлы на главном потоке

**Ticket:** S1593
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Текст:**

Returning from the in-app camera host deletes scratch files on the main thread - two StrictMode DiskWriteViolation (~4 ms each), not covered by any existing ticket.

Found during the S1579 device verification on RFCR110NBQJ (Samsung Galaxy S21+, SM-G996U1, Android 15 / SDK 35), standard debug 2.60.8120.227-DEBUG. Out of scope for S1579, whose boundary section names only the camera bring-up path.

Evidence: temp/S1579/run4_control.log, 08-12 02:36:31.697 and .698, pid == tid == 17016 (main thread).
Stack: MainCameraCaptureManager.clearPending(MainCameraCaptureManager.kt:167) and :168, called from MainCameraCaptureManager.handleResult(MainCameraCaptureManager.kt:117).

Source: clearPending() calls File(dir, "$base.jpg").delete() and File(dir, "$base.mp4").delete() inline; handleResult reaches it on the multiCapture return path, and three catch blocks in the launch path plus two more early returns in handleResult call it too. The class already owns a coroutineScope (used a few lines below for saveCapturedMedia), so the deletion has an off-main home available.

Dedup checked: search.ps1 on clearPending and MainCameraCaptureManager returns nothing; the neighbouring main-thread tickets S1480, S1569, S1573, S1517, S1324 and S1579 itself each name other sites.

**Захвачено во время:** S1579 (device verification, `/spec-do`)

---

## 1. Проблема / симптом

- Возврат из хоста внутренней камеры на главный экран даёт две StrictMode DiskWriteViolation подряд, ~4 мс каждая.
- Устройство: RFCR110NBQJ (Samsung Galaxy S21+, SM-G996U1), Android 15 / SDK 35, standard debug 2.60.8120.227-DEBUG.
- Эвиденс: `temp/S1579/run4_control.log`, 08-12 02:36:31.697 и .698, pid == tid == 17016 (главный поток).
- Оба нарушения приходят из очистки черновых файлов сессии захвата, вызываемой прямо из колбэка результата.

---

## 2. Корневая причина

- `MainCameraCaptureManager.clearPending()` удалял `$base.jpg` и `$base.mp4` вызовом `File.delete()` inline.
- Все вызывающие находятся на главном потоке: `handleResult()` (путь multiCapture и два ранних выхода) и три catch-блока в `dispatch()`.
- Удаление файла - дисковая запись, поэтому каждый вызов давал по нарушению на путь.
- Класс уже держит `coroutineScope` (`lifecycleScope` активити-хоста), то есть место вне главного потока было доступно и до фикса.

---

## 3. Исправление

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`, метод `clearPending()`.
- Поля `pendingDir` / `pendingBaseName` / `multiCapture` сбрасываются синхронно, до ухода в фон - пути снапшотятся в локальные значения раньше, чем может стартовать новая сессия захвата.
- Два `File.delete()` перенесены в `coroutineScope.launch(Dispatchers.IO + NonCancellable)`.
- `NonCancellable` обязателен: скоуп - `lifecycleScope` активити, и без него destroy сразу после результата отменил бы удаление и оставил мусор в черновой папке, тогда как прежний inline-вызов такую гарантию давал.
- Единственная точка правки покрывает все шесть путей вызова, потому что все они идут через `clearPending()`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1579 (в чьей device-верификации найдено; сам дефект вне его границ), S1608 (тот же класс дефекта в `BrowseCameraCaptureManager`)

---

## 4. Проверка

- Компиляция: `pwsh -NoProfile -File ./a.ps1 fk` - expected: exit 0 | actual: BUILD SUCCESSFUL in 51s, exit 0.
- On-device: открыть камеру с главного экрана, снять кадр, вернуться; в logcat не должно быть DiskWriteViolation со стеком `MainCameraCaptureManager.clearPending`.
- Probe-тег `S1593: clearPending offloading scratch delete` в logcat подтверждает, что поток отработал - без него отсутствие нарушения ничего не доказывает.
- Тот же сценарий с отменой (возврат без съёмки) и с видео - оба идут через `clearPending()`.
- Негативная проверка: черновая папка `Capture` после возврата не накапливает `CAP_*.jpg` / `CAP_*.mp4`, то есть удаление не потерялось при уходе в фон.
