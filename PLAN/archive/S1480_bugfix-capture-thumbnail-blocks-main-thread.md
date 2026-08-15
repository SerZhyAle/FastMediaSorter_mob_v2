# Спецификация (compact bugfix): S1480 - Сохранение снимка блокирует главный поток на десятки миллисекунд

**Ticket:** S1480
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1457

**Текст:**

Найдено при device-тесте S1457 на emulator-5554 (Android 15, standard debug). После каждого спуска затвора StrictMode фиксирует дисковые операции на главном потоке из пути показа миниатюры результата:

```
08-07 16:36:53.071 D/StrictMode: StrictMode policy violation; ~duration=41 ms: android.os.strictmode.DiskWriteViolation
08-07 16:36:53.072 D/StrictMode: StrictMode policy violation; ~duration=27 ms: android.os.strictmode.DiskReadViolation
08-07 16:36:53.072 D/StrictMode:  at com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureResultManager.showGalleryThumbnail(CameraCaptureResultManager.kt:85)
08-07 16:36:53.072 D/StrictMode:  at com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureResultManager.access$showGalleryThumbnail(CameraCaptureResultManager.kt:35)
08-07 16:36:53.072 D/StrictMode:  at com.sza.fastmediasorter.ui.cameracapture.helpers.CameraCaptureResultManager$persistMultiCapture$1.invokeSuspend(CameraCaptureResultManager.kt:67)
```

41 мс записи и 27 мс чтения на главном потоке приходятся ровно на момент, когда пользователь ждёт готовности к следующему кадру, - это заметная задержка серийной съёмки, а не только замечание StrictMode.

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

Воспроизводится каждым спуском затвора на экране съёмки. Эвиденс выше собран на эмуляторе; на реальном аппарате дисковые операции обычно дороже, поэтому задержка ожидается не меньше.

---

## 2. Корневая причина

- Glide инициализируется лениво, при первом `Glide.get()` / `Glide.with()` в процессе, и его `applyOptions()` выполняется **синхронно на потоке вызывающего**.
- В `applyOptions()` (`di/GlideAppModule.kt`) лежат ровно две дисковые операции нужного порядка: чтение размера кэша из `SharedPreferences("glide_config")` и `File(cacheDir, "image_cache").mkdirs()`, после которого Glide создаёт журнал `DiskLruCache`. Это чтение и запись, которые StrictMode и показал.
- Экран съёмки оказывается этим первым вызывающим, когда приложение запущено прямо в камеру (виджет, краевой жест, ярлык): `Glide.with(` во всём `ui/cameracapture/**` встречается ровно один раз - `CameraCaptureResultManager.kt:85`, и до него никто в процессе Glide не трогает.
- Прогрева нет ни на одном пути запуска. `DeferredStartupWorker` вызывает `CacheStatusHelper.logGlideDiskCacheStatus()`, но тот намеренно смотрит на каталог обычным `java.io.File` и Glide не поднимает (S1322). `AppStartupInitializer.initialize()` лишь синхронизирует размер кэша в те самые `SharedPreferences`, API Glide не касаясь.
- Отсюда цена приходится на первый спуск затвора: `persistMultiCapture` уже вернулся на главный поток (сохранение честно уходит в `Dispatchers.IO` внутри `CameraCaptureSaver.writeToDevice`), и следом на главном потоке поднимается весь Glide.

Что этим **не** объясняется и остаётся гипотезой: захват в §0 утверждает «после каждого спуска», а разовая инициализация может стоить только первого. Второй вероятный вклад - чтение `File.lastModified()` для ключа кэша на каждой загрузке, но он на порядок дешевле и один лог-фрагмент их не разделяет. Проверка §4 построена так, чтобы это разделить, а не замолчать.

---

## 3. Исправление

Поднять Glide заранее и не на главном потоке. Две точки, потому что путей запуска два.

1. **Общий путь.** `DeferredStartupWorker` - это `CoroutineWorker`, его `doWork()` уже идёт вне главного потока. Добавить задачу `warm-glide` (`Glide.get(applicationContext)`) сразу **после** `log-glide-disk-cache-status`, чтобы наблюдение S1322 за гонкой за каталог осталось верным: сперва замер, потом прогрев. Это снимает ту же цену со всех экранов, а не только со съёмки - первый, кто позовёт Glide, платил её везде одинаково.
2. **Холодный запуск прямо в камеру.** Виджет и краевой жест открывают экран съёмки раньше, чем отложенный воркер успевает отработать, поэтому экран прогревает Glide сам: `CameraCaptureResultManager` в `init` запускает `Glide.get()` на `Dispatchers.IO`. Пользователь в это время кадрирует, так что к затвору инициализация уже позади. Владелец прогрева - тот же класс, что платит цену; активити не трогается вовсе (Rule 3).

`Glide.get(context)` для этого и годится: он потокобезопасен и не требует главного потока, в отличие от `Glide.with(activity)`.

**Из объёма исключено:** `.signature()` / `.diskCacheStrategy()` на строке 85. Имена файлов серийной съёмки уникальны по метке времени, устаревшего кэша сегодня не бывает, и добавление к этому тикету защиты от чужой проблемы только запутает проверку.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1457 - расхождение результата съёмки с видоискателем, при device-тесте которого находка и всплыла; ни один открытый тикет не описывает блокировку главного потока на пути миниатюры (проверено `search.ps1` по «StrictMode» и «thumbnail»).

---

## 4. Проверка

Воспроизводится на эмуляторе - эвиденс §0 оттуда и снят, так что проверка не требует живого аппарата.

1. `.\a.ps1 fk` - компиляция standard.
2. Статически: `Glide.get(` вызывается в `DeferredStartupWorker` после задачи `log-glide-disk-cache-status`, и в `init` `CameraCaptureResultManager` под `Dispatchers.IO`. Ни один `Glide.with(` на экране съёмки не предшествует прогреву.
3. На эмуляторе (standard debug, StrictMode включён только в debug): холодный старт приложения, открыть экран съёмки, спустить затвор. В logcat не должно быть `DiskWriteViolation` / `DiskReadViolation` со стеком через `CameraCaptureResultManager.showGalleryThumbnail`.
4. **Разделяющий опыт, обязателен.** В той же сессии спустить затвор второй и третий раз и посмотреть, был ли до правки дефект разовым. Если на старой сборке нарушение фиксируется только на первом кадре, а на новой не фиксируется вовсе - причина §2 подтверждена целиком. Если на старой оно фиксируется на каждом кадре, а на новой исчезает только с первого - остаточная стоимость приходится на ключ кэша (`File.lastModified()` на каждую загрузку), правка закрывает лишь большую половину, и это надо записать отдельным тикетом, а не выдать за полный фикс.
5. Регресс: миниатюра последнего снимка по-прежнему появляется и обновляется после каждого кадра, вспышка обводки работает.

---

## Last Audit

### Manual (device test 2026-08-11 19:24-19:32)

**Verdict: PASS.**

Device: RFCR110NBQJ - Samsung Galaxy S21+ (SM-G996U1), Android 15 (SDK 35), real hardware, not the
emulator the capture in §0 came from. Build `v2.60.8111.809-DEBUG` (standard debug, installed 18:10:53),
app pid 20957 for the whole run, logcat buffer never rotated so the capture covers the session end to end.
Path: cold start (force-stop, then launch) -> MainActivity -> Programs -> Camera -> shutter.

**§4.3 - no violation through `showGalleryThumbnail`: PASS.**

- expected: 0 `DiskWriteViolation` / `DiskReadViolation` whose stack passes through
  `CameraCaptureResultManager.showGalleryThumbnail` | actual: 0, on every shot.
- 19 StrictMode violations fired in the session; not one names `showGalleryThumbnail`. Confirmed twice
  over - by walking each violation to its first `com.sza.fastmediasorter` frame, and by a raw grep of the
  whole capture for `D StrictMode:.*showGalleryThumbnail`.

**§4.4 - the discriminating experiment: PASS, and it did not need the old build.**

- Six shutters in one session, violations through `showGalleryThumbnail` counted per shot: shot 1 = 0,
  shot 2 = 0, shot 3 = 0, and shots 4, 5, 6 = 0 as well.
- The comparison against the old build existed only to separate "fixed" from "merely quieter". The
  per-shot breakdown separates them on this build alone: a surviving per-load cost - the `File.lastModified()`
  cache-key stat §2 names as the second candidate - would have to bill every subsequent load, so it would
  appear on shots 2 and 3. It appears on neither, nor on three further shots. The residual hypothesis is
  excluded by measurement rather than by comparison, so the root cause in §2 is confirmed whole and no
  follow-up ticket for a per-load residual is warranted.

**Probe: fired once, off the main thread, before any shot.**

- `08-11 19:25:11.162 20957 20991 D CameraCaptureResultManager: S1480: Glide warmed off the main thread`
- tid 20991 != pid 20957, so the warm-up genuinely ran off the main thread, which is the point of the fix
  and not merely that it ran.
- Exactly one occurrence in the session, on entering the camera screen, 59 s before the first shutter
  (`shot-1-begin` marker at 19:26:10.097).

**Positive control - StrictMode was demonstrably live.**

- The camera-open window holds 14 violations, all main-thread (pid == tid == 20957), so a zero elsewhere in
  this session is a real zero and not a session where StrictMode never armed.
- Those 14 belong to S1579, not here: `CameraCaptureSessionManagerKt.offeredExtensions` (7),
  `CameraCaptureFlowManager.resolveOutput` (2), `MainCameraCaptureManager.createScratchDir` (3),
  `CaptureDestinationPolicy.resolveCameraDirectory` (2). This APK predates the S1579 fix, so their presence
  is expected and is not a finding against this ticket.

**§4.5 regression: PASS on all three legs.**

- Appears: `btnGalleryThumbnail` is absent from the UI dump taken before the first shot and present after it.
- Refreshes per frame: all six shots saved (`CAP_20260811_1926..` through `CAP_20260811_193153_6.jpg`), and
  switching the lens between shots changed the thumbnail's pixel content (interior mean 77,79,78 -> 65,67,66,
  different pixel hash), so the view reloads rather than holding the first frame.
- Stroke flashes: a ten-frame screencap burst around one shutter caught the flash in flight - 3149
  accent-blue pixels on the thumbnail's border ring in frame 2, and exactly 0 in the other nine frames.

Evidence in `temp/S1480/`: `logcat_final.txt` (full session capture), `analyze.ps1` (per-shot attribution,
buckets each violation by its first app frame and by pid == tid), `ring.ps1` and `frames.ps1` (stroke-flash
measurement), `frames/` (burst and lens-switch screenshots), `ui_camera.xml` and
`ui_camera_after_shot1.xml` (thumbnail before and after the first shot).
