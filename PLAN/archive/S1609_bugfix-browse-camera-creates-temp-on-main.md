# Спецификация (compact bugfix): S1609 - browse-камера создаёт временный файл на главном потоке

**Ticket:** S1609
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Текст:**

Found while fixing S1608 (main-thread temp deletes in the same class). `BrowseCameraCaptureManager.createTemp()` runs on the main thread and performs two disk operations there: `activity.getExternalFilesDir(..)` (which mkdirs the directory on first use) and `File(dir, "CAP_$timestamp$ext").also { it.createNewFile() }`. Both are StrictMode DiskWriteViolations on every single photo or video capture launch, not just on abandon paths. File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`, `createTemp()` around line 594, called from `launch()` and `launchVideo()`.

Same defect class as S1579, which fixed exactly this for the main-screen manager: `MainCameraCaptureManager.createScratchDir()` was moved into `withContext(Dispatchers.IO)` and the intent dispatch was resumed on the main dispatcher inside a `coroutineScope.launch { .. }`. The browse-side counterpart was never converted.

Out of scope for S1608, whose captured text names only the `delete()` sites, and non-trivial: `launch()` / `launchVideo()` are synchronous today and their whole bodies (temp creation, FileProvider URI, intent build, `launcher.launch`, plus every catch block that resets the pending fields) would have to move inside a coroutine, preserving the ordering that `pendingTempFile` is set before the launcher fires.

A second, smaller site in the same class: `localVideoFallbackTarget()` calls `it.mkdirs()`, and it is reached from the save flow - verify whether that path is already off-main before deciding it needs the same treatment.

Dedup: `search.ps1` on `createTemp` and on `createNewFile` returns no records.

**Захвачено во время:** S1608 (`/skill-fix`)

---

## 1. Проблема / симптом

- Каждый запуск камеры с экрана browse - фото и видео - делает дисковую запись на главном потоке, то есть даёт StrictMode DiskWriteViolation. Не на путях отмены, как в S1608, а на самом обычном сценарии.
- Второй след того же класса: при съёмке видео с недоступным сетевым назначением создание публичной папки Movies тоже шло на главном потоке.
- Эвиденс класса дефекта - S1579 на RFCR110NBQJ (Samsung Galaxy S21+, Android 15): там `createScratchDir()` главного экрана давал ровно такое нарушение, и фикс был именно уходом в `Dispatchers.IO`.
- Отдельного device-лога по browse-стороне нет: дефект найден чтением кода при фиксе S1608, поэтому проверка ниже - прогон этих путей на устройстве.

---

## 2. Корневая причина

- `createTemp()` был обычной функцией и вызывался прямо из `launch()` / `launchVideo()`, то есть на главном потоке.
- Внутри две дисковые операции: `getExternalFilesDir(..)` создаёт каталог при первом обращении, `createNewFile()` пишет inode.
- Browse-менеджер не был переведён на схему S1579: там `createScratchDir()` уже ушёл в `withContext(Dispatchers.IO)`, а диспетчеризация интента вернулась на главный поток внутри `coroutineScope.launch { .. }`.
- Отдельная причина у второго следа: весь save-путь диспетчеризуется из `lifecycleScope.launch` без диспетчера, то есть `Main.immediate`. `CameraCaptureSaver.save()` делает собственный `withContext(Dispatchers.IO)` и потому безопасен, а `localVideoFallbackTarget()` вызывается до него и свой `mkdirs()` выполнял на главном потоке.

---

## 3. Исправление

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`.
- `createTemp()` стал `private suspend fun` с `withContext(Dispatchers.IO)` внутри - хоп спрятан в самой функции, а не в двух местах вызова, поэтому следующий вызывающий не сможет его забыть.
- Тела `launch()` и `launchVideo()` начиная с создания временного файла завёрнуты в `coroutineScope.launch { .. }`; продолжение возобновляется на главном диспетчере хоста, так что диалоги, snackbar и `launcher.launch(intent)` остались на главном потоке.
- Синхронная часть обеих функций сохранена намеренно: проверка камеры и `pendingResource` / `pendingIsVideo` выставляются до ухода в корутину, а `pendingTempFile` присваивается до `launcher.launch`, поэтому результат не может прийти без своего временного файла.
- `localVideoFallbackTarget()` стал `suspend`, `mkdirs()` ушёл в `withContext(Dispatchers.IO)`. Оба вызывающих (`resolveVideoSaveTarget`, `save`) уже были suspend, сигнатуры наружу не поменялись.
- Побочное следствие переноса: существовавшие широкие `catch` (`Throwable`, `Exception`) оказались внутри корутины и стали глотать `CancellationException`. Во все пять цепочек добавлена первая ветка `catch (e: CancellationException) { throw e }` - требование S1363, поймано гейтом `swallowed-cancellation`.
- Сознательно вне правки: `CameraCaptureSaver.save()` - у него уже есть собственный `withContext(Dispatchers.IO)`, проверено чтением.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1579 (same fix already applied to the main-screen manager), S1608 (sibling defect in the same class)

---

## 4. Проверка

- Компиляция: `pwsh -NoProfile -File ./a.ps1 fk` - expected: exit 0 | actual: BUILD SUCCESSFUL in 37s, exit 0.
- On-device, экран browse: снять фото в папку и сохранить - в logcat не должно быть DiskWriteViolation со стеком `BrowseCameraCaptureManager`.
- Probe-тег `S1609: createTemp on thread=` печатает имя потока: без `main` в нём отсутствие нарушения ничего не доказывает.
- То же для записи видео (`.mp4`-ветка `createTemp`).
- Второй след: записать видео при недоступном сетевом назначении - тег `S1609: localVideoFallbackTarget mkdirs on thread=` тоже не должен показывать `main`.
- Регресс порядка: камера открывается по одному тапу, снятый файл сохраняется в нужную папку, snackbar появляется - асинхронный старт не должен ронять или задваивать запуск.
- Регресс отмены: пути отказа из S1608 по-прежнему удаляют временный файл, `Android/data/<pkg>/files/Pictures` и `.../Movies` не накапливают `CAP_*`.
