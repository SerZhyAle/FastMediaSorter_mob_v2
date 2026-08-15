# Спецификация (compact bugfix): S0861 - Семейство mic-capture - владение MediaRecorder и teardown (Browse/Main)

**Ticket:** S0861
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, all 3 findings (2026-07-02, dedicated skeptic). (1) BrowseActivity: onPause/onStop/onDestroy (:615-654) never touch micRecordingManager; teardown lives only on touch ACTION_UP/CANCEL (BrowseButtonSetupHelper.kt:169-183). Trigger: long-press mic without RECORD_AUDIO -> permission dialog -> grant -> launcher callback (:76-84) auto-starts recording with NO finger down -> Back -> started MediaRecorder + AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE survive destroy, OS mic indicator stays on. (2) BrowseMicRecordingManager.startRecording (:48-123): no reentrancy guard; mediaRecorder/pendingTempFile/audioFocusListener unconditionally overwritten (:61/:70/:105); abandonAudioFocus (:232-245) reads only the current listener -> first session's focus can never be abandoned; reachable via the same auto-start chain + next ACTION_DOWN. (3) MainVoiceCaptureManager: stop() (:134-150) launches save without clearing pendingTempFile; scope = lifecycleScope (survives pause); MainActivity.onPause (:584-588) release() -> cancel() -> pendingTempFile?.delete() on main races tempFile.inputStream().use in withContext(IO) (:188-196) -> sink.abort() = silent loss of the just-saved voice note; path B: second recording's pendingTempFile nulled by the FIRST save's finally (:174-177, no generation check) -> second stop() exits at the ?: return guard (:139), file orphaned. No tests cover either manager.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt:647** - Active MediaRecorder + exclusive audio focus survive BrowseActivity destroy: no lifecycle edge ever stops the mic session
  - Evidence: BrowseMicRecordingManager holds heavy state ('private var mediaRecorder: MediaRecorder? = null', 'private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null', manager L43/L46) and its only release entry points are stopRecording()/cancelRecording(). The host's teardown edges never call them: onPause() L615-632 (mediaStoreObserver/scroll/Glide only), onStop() L634-645 (inlineStop/cancelScan only), onDestroy() L647-654 ('GlideCacheStats.logStats(); .. initializer.mediaStoreObserver.stop(); binding.rvMediaFiles.clearOnScrollListeners()'). The implicit stop via touch-stream CANCEL (BrowseButtonSetupHelper L180-183 'MotionEvent.ACTION_CANCEL -> { callbacks.onMicRecordTouchUp() }') only fires while a touch target exists - but the RECORD_AUDIO permission launcher starts a recording with no gesture in progress: BrowseActivity L78-80 '{ granted -> if (granted) { viewModel.state.value.resource?.let { micRecordingManager.startRecording(it) } }' (the original hold was necessarily ended by ACTION_UP/CANCEL when the permission dialog appeared, and that stopRecording() no-ops via the manager's L126 guard because nothing was pending yet). In that state the user pressing Back destroys the activity while the recorder is still capturing and AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE (manager L71-74) is still held: mic stays open (released only by MediaRecorder's finalizer at some future GC), exclusive transient focus is never abandoned so other apps' audio stays suppressed until process death, and the focus-change lambda (manager L65-69, captures the manager which captures 'private val activity: FragmentActivity', L28) remains registered with AudioManager - the classic unabandoned-focus chain that retains the destroyed Activity (escalates to P0 if that binder-rooted retention is accepted; on S+ 'MediaRecorder(activity)' L101 additionally holds the activity inside the orphaned recorder). Contract items 2 and 7 violated: no release on the real teardown edge; the finish()/Back early-exit path releases nothing.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt:105** - startRecording() has no reentrancy guard: second call overwrites a started MediaRecorder without stop/release (orphaned recorder holding the mic)
  - Evidence: startRecording() (L48) never checks 'mediaRecorder != null' / 'isRecorderStarted'; it unconditionally creates a new recorder and overwrites the field: L100-105 'val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { MediaRecorder(activity) } else { MediaRecorder() }; mediaRecorder = recorder' - the previously started instance loses its only reference and releaseRecorder() (L213-230) can never reach it. The double-call is concretely reachable: after the permission-grant auto-start (BrowseActivity L80) leaves a recording active with no finger down, the user's next press of the mic button dispatches ACTION_DOWN -> onMicRecordTouchDown() (BrowseButtonSetupHelper L169-172) -> BrowseActivity L713 'micRecordingManager.startRecording(resource)' while the first session is live. Collateral confirmed in the same path: L61 'pendingTempFile = tempFile' drops the first temp file reference (orphaned on disk while the orphaned recorder keeps writing to it), and L70 'audioFocusListener = listener' overwrites the first focus listener so abandonAudioFocus() (L232-245, uses only the current 'audioFocusListener') can never unregister the first request - register/unregister asymmetry (contract items 1 and 8). The second exclusive-transient request then preempts the first same-app focus client, whose listener (L66-68) fires cancelRecording() asynchronously and tears down the NEW session (releases the new recorder, deletes the new temp file, resets UI) while the old recorder stays orphaned and started until finalizer GC.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt:149** - pendingTempFile ownership not transferred to async save: onPause cancel() deletes the file mid-save; a new recording's pending ref is clobbered by the old save's finally
  - Evidence: stop() launches the save without clearing the field: `val tempFile = pendingTempFile ?: return` .. `coroutineScope.launch { save(tempFile, tempFile.name) }` (lines 139/149) - pendingTempFile stays non-null until save()'s `finally { tempFile.delete(); pendingTempFile = null }` (lines 174-177) runs after the Dispatchers.IO copy. release() (line 162) checks `if (isRecorderStarted || pendingTempFile != null || recordingDialog != null) { cancel() }` and cancel() (line 156) does `pendingTempFile?.delete()`. Runtime path A (data race, main vs IO thread): user taps Stop in the dialog -> stop() launches save on lifecycleScope (MainActivity.kt:765) and suspends at `withContext(Dispatchers.IO)` (line 189) -> user immediately presses Home / another window takes focus -> MainActivity.onPause (MainActivity.kt:587) -> release() -> cancel() deletes the temp file on the main thread while the IO thread is opening/copying it (`tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }`, line 196); if the unlink lands before inputStream() opens, FileNotFoundException -> sink.abort() -> the explicitly-saved recording is silently discarded. Path B (field clobber): during a still-running save the user starts a new recording (onVoice -> start() -> actuallyStart() sets `pendingTempFile = tempFile`, line 99); the old save's finally then sets `pendingTempFile = null` (line 176), so the new recording's stop() hits `val tempFile = pendingTempFile ?: return` (line 139) and returns after releaseRecorder() - the second recording is never saved and its temp file is orphaned in getExternalFilesDir. Violates contract item 7 (early-exit path must release only what it owns): the manager conflates recording-in-progress with save-in-progress in one unguarded shared field mutated from main (cancel) and observed by the IO save job.
  - Fix hint: Transfer ownership at launch: set pendingTempFile = null in stop() before coroutineScope.launch (the coroutine already holds the File in tempFile), and drop the `pendingTempFile = null` from save()'s finally (or clear only if still identical). release()/cancel() then only ever tear down an actual in-progress recording.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

Семейство mic-capture - владение MediaRecorder и teardown (Browse/Main). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Три независимых дефекта release-контракта в семействе mic-capture (`BrowseMicRecordingManager` + `BrowseActivity`, `MainVoiceCaptureManager`):

1. `BrowseActivity.onPause/onStop/onDestroy` никогда не обращались к `micRecordingManager`. Единственная точка teardown - обработчик touch-жеста `ACTION_UP`/`ACTION_CANCEL` (`onMicRecordTouchUp`). Но запрос разрешения `RECORD_AUDIO` завершает исходное касание (`ACTION_CANCEL` при показе системного диалога) ДО выдачи разрешения; колбэк лаунчера (`recordAudioPermissionLauncher`) после гранта автоматически запускает запись без активного пальца на кнопке - в этом состоянии `MediaRecorder` и эксклюзивный `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` переживают уход с экрана/уничтожение активности: микрофон и фокус звука никогда не освобождаются штатным путём.
2. `BrowseMicRecordingManager.startRecording()` не имел защиты от повторного входа - безусловно перезаписывал `mediaRecorder`/`pendingTempFile`/`audioFocusListener`, если запись уже шла (достижимо через тот же auto-start-после-гранта сценарий плюс следующее нажатие кнопки). Проигравший `MediaRecorder` терял единственную ссылку и никогда не освобождался; `abandonAudioFocus()` читает только ТЕКУЩИЙ `audioFocusListener`, поэтому фокус первой сессии никогда не отзывался (register/unregister асимметрия).
3. `MainVoiceCaptureManager.stop()` запускал асинхронное сохранение (`coroutineScope.launch { save(tempFile, ..) }`), не очищая `pendingTempFile` - поле оставалось ненулевым до `finally` внутри `save()` (после IO-копирования). Гонка A: `onPause()` -> `release()` -> `cancel()` удаляет файл из `pendingTempFile` на главном потоке, пока IO-корутина ещё читает тот же файл - тихая потеря только что сохранённой записи (`sink.abort()`). Гонка B: пока сохранение первой записи ещё выполняется, пользователь начинает вторую - её `pendingTempFile` присваивается, но `finally` ПЕРВОГО сохранения затем обнуляет поле, и `stop()` второй записи натыкается на `?: return` - вторая запись никогда не сохраняется, её временный файл остаётся сиротой на диске.

---

## 3. Исправление

1. `BrowseMicRecordingManager.startRecording()`: в начале функции добавлена проверка `if (isRecorderStarted || mediaRecorder != null || pendingTempFile != null) { cancelRecording() }` - любая уже активная сессия корректно отменяется (recorder stop+release, focus abandon, temp file delete) перед стартом новой, вместо безусловной перезаписи полей.
2. `BrowseMicRecordingManager`: добавлен публичный метод `release()`, зеркалирующий уже существующий `MainVoiceCaptureManager.release()` - если есть активная запись (`isRecorderStarted`/`mediaRecorder != null`/`pendingTempFile != null`), вызывает `cancelRecording()` (отмена, не сохранение - запись, прерванную уходом с экрана, безопаснее отбросить, чем пытаться сохранить незавершённый файл).
3. `BrowseActivity.onPause()`: добавлен вызов `if (::micRecordingManager.isInitialized) micRecordingManager.release()` - первая реальная точка teardown для этого менеджера среди всех lifecycle-методов активности.
4. `MainVoiceCaptureManager.stop()`: `pendingTempFile = null` перенесено на начало функции (сразу после чтения `val tempFile = pendingTempFile ?: return`, до запуска async-сохранения) - владение файлом явно передаётся локальной переменной корутины; `release()`/`cancel()` после этого момента ничего не видят в поле и не могут удалить файл, который уже читает IO-корутина.
5. `MainVoiceCaptureManager.save()`: удалено `pendingTempFile = null` из `finally` - поле больше не трогается сохранением, которое уже не владеет им (переход владения случился в `stop()`); это устраняет обнуление чужого (более нового) `pendingTempFile` второй записи.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (release-контракт + владение временным файлом), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: `startRecording()` отменяет активную сессию перед стартом новой; `release()` присутствует в обоих менеджерах и вызывается из `BrowseActivity.onPause()`; `MainVoiceCaptureManager.stop()` очищает `pendingTempFile` до `launch`; `save()` больше не трогает поле в `finally`.
- Ручная device-проверка (BlockNeedUserTest, опционально): зажать mic-кнопку на Browse без выданного `RECORD_AUDIO`, выдать разрешение через системный диалог (auto-start без пальца на кнопке), сразу нажать Back - ожидание: индикатор микрофона ОС гаснет, фокус звука не блокирует другие приложения. Быстро тапнуть mic дважды подряд (вторая сессия поверх первой) - ожидание: только одна активная запись, без утечки recorder. Quick voice: начать запись, сразу нажать Stop и немедленно свернуть приложение (Home) - ожидание: запись сохраняется полностью, без "silent loss".

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `BrowseMicRecordingManager.startRecording()` reentrancy guard cancels any active session before overwriting fields (:48-55) - PASS. `BrowseMicRecordingManager.release()` present, discards via `cancelRecording()` when a session is active (:185-189) - PASS. `BrowseActivity.onPause()` calls `micRecordingManager.release()` guarded by `isInitialized` (:627) - PASS. `MainVoiceCaptureManager.stop()` clears `pendingTempFile` before `coroutineScope.launch` (:145) - PASS. `MainVoiceCaptureManager.save()` no longer touches `pendingTempFile` in `finally` - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate (3 files; pre-existing `SpacingBetweenDeclarationsWithAnnotations` surfaced by line-shift, fixed) - PASS. Dev log entries present for all 3 files (S0861 @ 17:26-17:30) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Hold the Browse mic button without RECORD_AUDIO granted, grant via the system dialog (auto-start with no finger down), immediately press Back - expect the OS mic indicator turns off and audio focus does not block other apps. Double-tap mic rapidly (second session over first) - expect only one active recording, no leaked recorder. Quick voice: start, immediately tap Stop, immediately background the app (Home) - expect the recording saves completely, no silent loss.

