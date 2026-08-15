# Спецификация (compact bugfix): S1650 - Инициализация Glide читает кэш-каталог с главного потока при первой загрузке миниатюр

**Ticket:** S1650
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-14
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1207 (проверка на устройстве после переноса инициализации Cast с главного потока)

**Текст:**

При первом показе сетки миниатюр на экране обзора Glide инициализируется лениво и делает это на
главном потоке: два `DiskReadViolation` по 88 мс каждый. Вне объёма S1207 - другой экран и другая
подсистема, но тот же класс дефекта.

**Условия измерения:** устройство RFCR110NBQJ (Galaxy S21, Android 15), сборка standard-debug
`v2.60.8112.319`. Лог: `temp/S1207/logcat_S1207.txt`, отметка времени 17:26:59.210.
Оба нарушения на `tid 25797`, то есть на главном потоке процесса.

---

## 1. Проблема / симптом

Два `DiskReadViolation` по ~88 мс со стеком
`Glide.initializeGlide` -> `GlideAppModule.applyOptions(GlideAppModule.kt:87)` -> `getCacheDir`,
запущенным из `AdapterThumbnailLoader.loadVideo(AdapterThumbnailLoader.kt:715)` в момент отрисовки
сетки видеофайлов.

Наблюдаемый эффект: первая прокрутка списка ресурсов подтормаживает ровно один раз за процесс -
пока Glide не собран. Дальше стоимость не повторяется, поэтому дефект легко не заметить при
повторных заходах на тот же экран.

---

## 2. Корневая причина

Glide собирается лениво, в потоке, который первым к нему обратился, а его `applyOptions` делает на
этом потоке две дисковые операции: читает `SharedPreferences` с размером кэша и создаёт каталог
`image_cache`. Первым к Glide обращается загрузчик миниатюр при отрисовке сетки - то есть главный
поток.

Прогрев на фоновом потоке в приложении уже есть: S1480 добавил задачу `warm-glide` в
`DeferredStartupWorker`. Дефект не в её отсутствии, а в моменте запуска - воркер ставится в очередь
с `setInitialDelay(30, TimeUnit.SECONDS)`, и сама задача идёт в нём пятой, после уборки временных
файлов и backfill'а SMB. До сетки миниатюр пользователь доходит за секунды, поэтому главный поток
выигрывает эту гонку всегда. Прогрев назначен позже того момента, который он был должен предупредить.

Дополнительное ограничение, которое и держало прогрев так поздно: размер дискового кэша Glide читает
из зеркала в `SharedPreferences`, а пишет это зеркало отдельная стартовая корутина. Тридцатисекундная
задержка гарантировала, что запись успела - но гарантировала случайно, а не по построению.

---

## 3. Исправление

Перенести прогрев из отложенного воркера в стартовый путь приложения, на `Dispatchers.IO`, по
образцу S0869 (там так же прогревается Room в самом начале `onCreate` и по той же причине).

- Прогрев ждёт завершения записи зеркала размера кэша, а не тридцати секунд: иначе Glide рассчитает
  дисковый кэш по устаревшему значению. Ожидание выражено явно, через задание записи.
- Лог состояния дискового кэша остаётся ровно перед сборкой Glide: S1322 наблюдает состояние
  каталога **до** того, как Glide его тронет, и перестановка сломала бы смысл этого замера.
- Из `DeferredStartupWorker` обе задачи убираются - у них теперь один владелец.
- Остаточная гонка принимается той же формулировкой, что в S0869: главный поток, успевший обратиться
  к Glide раньше прогрева, блокируется на его внутреннем замке, но дисковую работу уже не делает сам.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1648 (тот же класс дефекта на открытии плеера), S1207 (в его проверке находка сделана), S1480 (добавил прогрев, который этот тикет ставит на работающее место), S0869 (образец прогрева Room), S1322 (замер каталога кэша до сборки Glide).

---

## 4. Проверка

Холодный старт на устройстве, затем открыть ресурс с сеткой видеофайлов. В логе:

- `AppStartupInitializer: deferred task complete - warm-glide` появляется до первой загрузки миниатюры;
- `GlideAppModule: *** CACHE CONFIGURED ***` не сопровождается `DiskReadViolation` со стеком
  `Glide.initializeGlide` на главном потоке.

---

## Phase 01 - Move the Glide warm-up onto the startup path

**Status:** ✅ Done
**Depends on:** none - single-phase fix
**Steps done:** 3 / 3

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeferredStartupWorker.kt` | Modified | ≤ 100 |

### Steps

#### Step 01.1 - Give `AppStartupInitializer` a warm-up that waits for the cache-size mirror

**Files:** `core/init/AppStartupInitializer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Keep the `Job` returned by the launch inside `syncCacheSizeToSharedPreferences()` in a private field. Add a `suspend fun warmGlide()` that joins that job, then runs the existing cache-status log and `Glide.get(context)` as two `runDeferredTask` entries, in that order.

**Why:**

Glide reads the disk-cache size from the `SharedPreferences` mirror that this class writes, so a warm-up that does not wait for the write would size the cache from a stale value - a guarantee the thirty-second worker delay used to provide by accident.

**Verification:**

- `Grep` - `suspend fun warmGlide` present in the file.
- `Grep` - `cacheSizeMirrorSync` assigned in `syncCacheSizeToSharedPreferences` and joined in `warmGlide`.

**Status:** `[x] done`

#### Step 01.2 - Launch the warm-up from `Application.onCreate`

**Files:** `FastMediaSorterApp.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Right after `startupInitializer.get().initialize()`, launch `warmGlide()` on `applicationScope` with `Dispatchers.IO`. Do not gate it on `firstFrameSignal`: the first image load can happen on the very first screen, which is what the warm-up has to beat.

**Why:**

The warm-up only prevents the main-thread disk work if it starts before the first image load, and every existing deferred path in this class fires after the first frame or later.

**Verification:**

- `Grep` - `warmGlide()` called inside a `Dispatchers.IO` launch in `onCreate`.
- `Grep` - no `firstFrameSignal.await` in that block.

**Status:** `[x] done`

#### Step 01.3 - Remove both tasks from `DeferredStartupWorker`

**Files:** `worker/DeferredStartupWorker.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Delete the `log-glide-disk-cache-status` and `warm-glide` tasks together with the now-unused `Glide` and `CacheStatusHelper` imports. Leave one comment naming S1650 and the new owner of the pair.

**Why:**

Two owners of one warm-up would build Glide twice in a race and leave the S1322 status log measuring a directory Glide has already created.

**Verification:**

- `Grep` - `warm-glide` and `CacheStatusHelper` return zero hits in the worker.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

### Phase Done Criteria

- [x] Every step above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, затем `.\a.ps1 d` BUILD SUCCESSFUL.
- [x] Dev log entry added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Rollback Plan

Revert the three-file change - the warm-up returns to the deferred worker, where it is late but harmless.

---

## Last Audit

**Дата:** 2026-08-14
**Вердикт:** Verified

**Проверка на устройстве** (RFCR110NBQJ, SM-G996U1, Android 15, standard debug `v2.60.8112.319`, холодный старт после `force-stop` и очистки буфера logcat):

- `20:55:17.751` `CacheStatusHelper: === GLIDE DISK CACHE STATUS AT STARTUP ===`, следом `deferred task complete - log-glide-disk-cache-status` - лог состояния каталога по-прежнему идёт до сборки Glide.
- `20:55:17.762` `GlideAppModule: Read cache_size_mb from SharedPreferences: 8192MB` - не значение по умолчанию 2048, то есть ожидание записи зеркала сработало.
- `20:55:17.766` `*** CACHE CONFIGURED *** Memory=8MB, Disk=8192MB` и `20:55:17.774` `deferred task complete - warm-glide`. Все четыре строки на `tid 19660` при `pid 19638` - фоновый поток, не главный.
- `20:55:29.548` сетка миниатюр биндится через `AdapterThumbnailLoader.loadVideo(AdapterThumbnailLoader.kt:715)` -> `Glide.with` на главном потоке `tid 19638` - тот самый вызов из §1 - и ни одного нарушения StrictMode рядом.
- Во всём захвате `initializeGlide` не встречается ни в одном стеке нарушений: `grep -c initializeGlide` = 0. До правки этот стек давал два `DiskReadViolation` по 88 мс.

**Найдено попутно, вне объёма:** на главном потоке остаётся `DiskReadViolation` длительностью 258 мс со стеком `BrowseFileTransferCoordinator.<init>(BrowseFileTransferCoordinator.kt:47)` -> `getSharedPreferences`, выполняемым при Hilt-инъекции `MainActivity`. Другой класс, другая подсистема - припаркован отдельным тикетом.
