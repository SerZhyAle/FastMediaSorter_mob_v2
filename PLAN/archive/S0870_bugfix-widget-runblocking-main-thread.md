# Спецификация (compact bugfix): S0870 - Виджеты - runBlocking(Room+gzip+Gson) на main thread (RandomPhotoFrame, WorkManagerScheduler)

**Ticket:** S0870
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, all 3 findings (2026-07-02, dedicated skeptic; :27/:72 = one defect). (1+2) RandomPhotoFrameWidgetProvider.onUpdate (:20-29) has no goAsync()/dispatch, no android:process override (manifest :408-412) -> main thread; :72 calls RandomPhotoFrameWidgetRefresher.refresh whose body is runBlocking(Dispatchers.IO) (Refresher :23) - main parks on IO. On MediaFilesCacheManager miss: Room getByResourceId + GZIP decompress + gson.fromJson of the full cached list (CachedFileListRepository.kt:71-72) + thumbnail read + File.exists; cache cap = 1,000,000 entries (saveCachedFiles :42) -> worst case far past ANR window. Contrast: ScheduledTasksWidgetProvider.onUpdate uses goAsync()+IO scope (S0727, :46-64) - RandomPhotoFrame lacks the equivalent. Second Main-thread entry: RandomPhotoFrameConfigActivity.updateWidgetAndFinish (:124-133) calls updateAppWidget on Main. (3) WorkManagerScheduler.pauseAll/runAllNow/resumeAll are suspend funs with ZERO withContext (class IO scope :42 unused by them) -> run in caller context = viewModelScope Main.immediate (ScheduledOperationsViewModel :96); :298/:310/:322 call ScheduledTasksWidgetRefresher.refresh -> updateAppWidget companion (:132-141) wraps runBlocking{Room getAll().first() + DataStore getSettings().first()} on Main - BYPASSES the provider's own goAsync fix (Settings path never enters the guarded onUpdate/onReceive). Guaranteed jank per tap with a placed widget; ANR on slow storage. Fix shape: goAsync()+IO in RandomPhotoFrame onUpdate (mirror S0727); withContext(IO) inside scheduler refresh or route Settings path through the guarded entry.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt:27** - AppWidgetProvider.onUpdate blocks the main thread on Room + gzip + Gson + file-stat via runBlocking (no goAsync/IO dispatch, unlike the fixed ScheduledTasksWidgetProvider)
  - Evidence: onUpdate (runs on the process main thread for the APPWIDGET_UPDATE broadcast) lines 26-28: `appWidgetIds.forEach { id -> updateAppWidget(context, appWidgetManager, id) }` with no dispatch; updateAppWidget line 70 `RandomPhotoFrameSnapshotStore.read(context, appWidgetId)` (SharedPreferences) then line 72 `RandomPhotoFrameWidgetRefresher.refresh(context, appWidgetId)`. RandomPhotoFrameWidgetRefresher.kt line 23: `return runBlocking(Dispatchers.IO) {` - the calling main thread parks until Room read + decompress + JSON parse of the full cached file list completes (CachedFileListRepository.kt:71-72 `cachedFileListDao.getByResourceId(resourceId)` + `gson.fromJson(decompress(entity.compressedData), ..)`), plus ThumbnailCacheRepository lookup, `File(path).exists()` (RandomPhotoFrameWidgetRefresher.kt:102) and a SharedPreferences write. Sibling ScheduledTasksWidgetProvider.kt:51-55 documents exactly this hazard and defers to IO with goAsync (S0727); this provider was not given the fix. Same blocking chain also re-runs ON MAIN from RandomPhotoFrameConfigActivity.updateWidgetAndFinish (lines 118-126: withContext(Dispatchers.Main) { updateWidgetAndFinish() } -> updateAppWidget -> refresh). Failure: widget host update / boot / periodic update stalls the app main thread for the whole Room+decode round-trip on a large cached list; >10s blocks the broadcast and triggers an ANR.
  - Fix hint: Mirror S0727: goAsync() + CoroutineScope(SupervisorJob()+Dispatchers.IO).launch in onUpdate, and drop the withContext(Dispatchers.Main) around updateWidgetAndFinish (only setResult/finish need Main).
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt:72** - Widget onUpdate runs runBlocking Room read + GZIP BLOB decompress + Gson parse of the full cached file list on the main thread
  - Evidence: RandomPhotoFrameWidgetProvider.onUpdate (lines 20-29) has no goAsync()/dispatch - AppWidgetProvider.onReceive delivers it on the app main thread - and calls `updateAppWidget(..)` which at line 72 calls `RandomPhotoFrameWidgetRefresher.refresh(context, appWidgetId)`. refresh() is `return runBlocking(Dispatchers.IO) { .. deps.cachedFileListRepository().getCachedFiles(current.resourceId) .. }` (RandomPhotoFrameWidgetRefresher.kt:23-31): the main thread blocks while CachedFileListRepository.getCachedFiles does a Room read of the cached_file_lists row and `gson.fromJson(decompress(entity.compressedData), mediaFileListType)` (CachedFileListRepository.kt:71-72) - a GZIP-compressed JSON blob of the resource's entire file list (saveCachedFiles accepts up to 1,000,000 files, CachedFileListRepository.kt:42), then more Room reads via thumbnailCacheRepository.getCachedThumbnail (ThumbnailCacheDao.getThumbnail + updateAccessTime + deleteThumbnail), File.exists() checks, and a SnapshotStore disk write. Trigger path: home-screen host broadcasts APPWIDGET_UPDATE (widget add, boot, host refresh) -> main-thread stall for the whole pipeline; broadcast ANR window applies. Second main-thread path: RandomPhotoFrameConfigActivity.updateWidgetAndFinish (RandomPhotoFrameConfigActivity.kt:126) calls the same updateAppWidget after withContext(Dispatchers.Main). Contrast: ScheduledTasksWidgetProvider.onUpdate explicitly defers to IO with goAsync() for exactly this reason (S0727 comment, lines 51-63). Violates Layer 4 (h) runBlocking reaching DAOs on a UI path and 'no main-thread disk I/O'.
  - Fix hint: Mirror ScheduledTasksWidgetProvider (S0727): goAsync() + CoroutineScope(SupervisorJob()+Dispatchers.IO).launch in onUpdate; make updateAppWidget accept a precomputed snapshot so the RemoteViews push is the only main-thread work, and keep the config-activity path fully on IO before posting the views.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt:310** - Scheduled-tasks widget refresh executes runBlocking(Room getAll + DataStore getSettings) on the main thread when Run All / Pause / Resume is tapped in Settings
  - Evidence: pauseAll (suspend) line 310: `com.sza.fastmediasorter.widget.ScheduledTasksWidgetRefresher.refresh(context)` (same in runAllNow:298 and resumeAll:322) - refresh runs on the caller's dispatcher. ScheduledOperationsViewModel.kt:94-98 launches these on viewModelScope (Dispatchers.Main.immediate): `fun pauseAll() { viewModelScope.launch { workManagerScheduler.pauseAll() } }`. ScheduledTasksWidgetRefresher.kt:25 then calls `ScheduledTasksWidgetProvider.updateAppWidget(context, manager, id)`, which at ScheduledTasksWidgetProvider.kt:133-141 does `runBlocking { val ops = entryPoint.scheduledOperationRepository().getAll().first(); ..; paused = entryPoint.settingsRepository().getSettings().first().. }` - blocking the main thread on a Room query plus a DataStore disk read per placed widget. The provider's own onUpdate/onReceive were deliberately moved off main for this exact reason (lines 51-55, S0727), but this Settings-UI entry path bypasses that fix. Failure: tapping Pause/Resume/Run All in the scheduled-operations settings screen with the widget placed freezes the UI thread for the Room+DataStore round-trip (jank; ANR on slow storage).
  - Fix hint: Dispatch the refresh to IO inside ScheduledTasksWidgetRefresher.refresh (or make refresh suspend and call it withContext(Dispatchers.IO)) so every caller is safe.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

Виджеты - runBlocking(Room+gzip+Gson) на main thread (RandomPhotoFrame, WorkManagerScheduler). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Три независимых main-thread нарушения вокруг виджетов, все - неполное копирование паттерна `goAsync()+IO`, уже применённого в `ScheduledTasksWidgetProvider` (S0727):

1. `RandomPhotoFrameWidgetProvider.onUpdate` вызывал `updateAppWidget()` напрямую без диспетчеризации - `AppWidgetProvider.onReceive` доставляет `onUpdate` на главном потоке процесса. `updateAppWidget()` при сконфигурированном виджете вызывает `RandomPhotoFrameWidgetRefresher.refresh()`, которая содержит `runBlocking(Dispatchers.IO) { .. }` вокруг Room-чтения, GZIP-декомпрессии и Gson-парсинга всего кэшированного списка файлов (до 1,000,000 записей) - главный поток блокируется на весь раунд-трип.
2. `RandomPhotoFrameConfigActivity.triggerInitialRefresh` уже переключался на `Dispatchers.IO` для первого вызова `refresh()`, но затем оборачивал `updateWidgetAndFinish()` в `withContext(Dispatchers.Main)` - а этот метод внутри снова вызывает `RandomPhotoFrameWidgetProvider.updateAppWidget()`, который делает ВТОРОЙ полный `refresh()`-раунд-трип, на этот раз уже на главном потоке.
3. `WorkManagerScheduler.pauseAll()/runAllNow()/resumeAll()` - suspend-функции без `withContext(IO)` вокруг вызова `ScheduledTasksWidgetRefresher.refresh(context)`, поэтому исполняются в диспетчере вызывающего кода. `ScheduledOperationsViewModel` запускает их через `viewModelScope.launch { .. }` (Dispatchers.Main.immediate по умолчанию) - `refresh()` (не suspend) синхронно вызывает `ScheduledTasksWidgetProvider.updateAppWidget()`, которая содержит `runBlocking { .. }` вокруг Room + DataStore чтения без явного диспетчера - блокирует главный поток при каждом тапе Pause/Resume/Run All в настройках, если виджет размещён на экране.

---

## 3. Исправление

1. `RandomPhotoFrameWidgetProvider.onUpdate`: обёрнут в `goAsync()` + `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { .. }` с `pendingResult.finish()` в `finally` - зеркалирует `ScheduledTasksWidgetProvider.onUpdate` (S0727) один в один.
2. `RandomPhotoFrameConfigActivity.triggerInitialRefresh`: второй вызов `RandomPhotoFrameWidgetProvider.updateAppWidget()` остаётся на `Dispatchers.IO` (внутри того же `lifecycleScope.launch(Dispatchers.IO)` блока); `withContext(Dispatchers.Main)` теперь оборачивает только новый метод `finishWithResult()` (`setResult`+`finish()`) - Activity-методы жизненного цикла остаются на главном потоке, но без Room/gzip/Gson работы внутри этого блока.
3. `ScheduledTasksWidgetRefresher.refresh(context)`: сигнатура изменена с обычной `fun` на `suspend fun`, тело обёрнуто в `withContext(Dispatchers.IO) { .. }` - гарантирует IO-диспетчер независимо от диспетчера вызывающего кода. Все 4 существующих вызывающих места (`WorkManagerScheduler.pauseAll/runAllNow/resumeAll`, `ScheduledOperationsWorker.doWork`) уже являются suspend-функциями - изменение сигнатуры не потребовало правок на местах вызова.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (main-thread диспетчеризация виджетов), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: `RandomPhotoFrameWidgetProvider.onUpdate` использует `goAsync()+IO` (симметрично `ScheduledTasksWidgetProvider`); `RandomPhotoFrameConfigActivity` не делает Room/gzip/Gson работу на Main; `ScheduledTasksWidgetRefresher.refresh` - suspend + `withContext(IO)`, все 4 вызывающих места уже suspend-функции.
- Ручная device-проверка (BlockNeedUserTest, опционально): разместить оба виджета (Random Photo Frame + Scheduled Tasks) с большим кэшем файлов, вызвать обновление хоста/boot и тапнуть Pause/Resume/Run All в настройках - ожидание: отсутствие фриза UI/ANR.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `RandomPhotoFrameWidgetProvider.onUpdate` wraps `appWidgetIds.forEach { updateAppWidget(..) }` in `goAsync()` + `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { .. } finally { pendingResult.finish() }` (:29-41) - PASS. Pattern matches `ScheduledTasksWidgetProvider.onUpdate` (S0727) - PASS. `RandomPhotoFrameConfigActivity.triggerInitialRefresh` keeps both `refresh()` and `updateAppWidget()` calls inside `lifecycleScope.launch(Dispatchers.IO)`, `withContext(Dispatchers.Main)` now wraps only `finishWithResult()` (setResult+finish) - PASS. `ScheduledTasksWidgetRefresher.refresh` signature is `suspend fun refresh(context: Context) = withContext(Dispatchers.IO) { .. }` - PASS. All 4 call sites (`WorkManagerScheduler.pauseAll/runAllNow/resumeAll`, `ScheduledOperationsWorker.doWork`) are pre-existing suspend functions, no call-site changes required - PASS. Import-ordering pre-existing defect (android.widget after androidx.work) fixed alongside the new kotlinx imports - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate (3 files) - PASS. Dev log entries present for all 3 files (S0870 @ 16:46-16:52) - PASS. FEATURES trilingual - EXEMPT (internal main-thread dispatch fix, no user-visible capability).

### Manual / on-device

- [ ] Place both Random Photo Frame and Scheduled Tasks widgets with a large cached file list, trigger a host update/boot and tap Pause/Resume/Run All in Settings - expect no UI freeze/ANR.

