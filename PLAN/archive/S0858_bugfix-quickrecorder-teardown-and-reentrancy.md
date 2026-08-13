# Спецификация (compact bugfix): S0858 - QuickAudioRecorderService - reentrancy и teardown-дыры (микрофон/фокус не освобождаются)

**Ticket:** S0858
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: P1 CONFIRMED 2/3 (re-tap during async save starts second recorder). Other items UNVERIFIED.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt:172** - No-arg stopSelf() in save coroutine destroys service mid-recording, orphaning a live MediaRecorder with mic and exclusive audio focus held
  - Evidence: stopAndSave() releases recorder 1 and sets `isRecording = false` (line 142) BEFORE launching the save coroutine: `serviceScope.launch { val result = micRecordingSaver.save(...` (lines 148-153), which suspends for a long network upload (uploadToResource routes to LocalToFtpStrategy/LocalToSmbStrategy/LocalToSftpStrategy/cloudOperationStrategy, lines 183-198). During that window a widget tap dispatches on the stale flag - QuickAudioRecorderLaunchManager.kt line 32 `if (QuickAudioRecorderService.isRecording)` sees false -> `QuickAudioRecorderService.start(activity)` -> ACTION_START -> handleStart(), whose only duplicate guard `if (isRecording) return` (line 84) passes, so a SECOND recorder is created and started (lines 100-119, `isRecording = true`). When the save coroutine then resumes it runs lines 170-172: `outputFile = null / stopForegroundCompat() / stopSelf()`. No-arg `stopSelf()` stops the service even though a newer start command was delivered after the stop command (that is what stopSelf(startId) exists for), and line 170 also clobbers recording 2's `outputFile` set at line 98. onDestroy() (lines 200-203) performs only `serviceScope.cancel()`, so recorder 2 is never stop()/release()d - the microphone stays captured by an unreachable MediaRecorder until finalization, the AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE focus for listener 2 is never abandoned (a subsequent instance's abandonFocus() early-returns at line 255-256 because its `audioFocusListener` field is null), the FGS notification is removed while the mic is live, static `isRecording` sticks at true (widget stuck on the recording icon; the next tap's ACTION_STOP reaches a fresh instance with `recorderStarted=false`/`outputFile=null` and just toasts an error), and recording 2's audio is never saved. Violates contract items 1 (orphan creation path), 5 (focus not abandoned on this path) and 7 (early-exit path without release); player instance survives host teardown unreleased = P1 minimum.
  - Fix hint: Use stopSelf(startId) captured per command (or check that no new recording started before stopping), and do not null shared fields (`outputFile`) from a stale save coroutine; guard handleStart while a save is in flight.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt:174** - Second ACTION_STOP during in-flight save cancels the upload via onDestroy scope cancel - captured recording is never delivered (data loss)
  - Evidence: During the save window the FGS notification with its Stop action (PendingIntent.getService ACTION_STOP, lines 285-301) is still posted - it is removed only at line 171/177 after the save finishes. A second Stop tap re-enters stopAndSave(): `captured` is computed only inside `if (recorderStarted)` (lines 129-139) and `recorderStarted` was already reset to false by the first pass (line 218 in releaseRecorder()), so `captured` stays false and the else branch runs (lines 174-178): `toast(getString(R.string.quick_recorder_error)) / outputFile = null / stopForegroundCompat() / stopSelf()`. stopSelf() -> onDestroy() -> `serviceScope.cancel()` (line 201) aborts the first save coroutine mid-`micRecordingSaver.save(...)` (line 149) - the clip is never written to any destination (MicRecordingSaver's local-fallback guarantee never executes because the coroutine itself is cancelled), no success/fallback notice is shown (only a generic error toast), and `file.delete()` (line 155) never runs so the orphan REC_*.m4a stays in app-private getExternalFilesDir(Music) (line 95), which the user cannot browse on API 30+ scoped storage. Deterministic user path: record -> stop -> upload to a slow/unreachable network destination in flight -> tap the notification's Stop action again. Violates contract item 7 (failure/early-exit path must not skip proper completion/cleanup of the in-flight save).
  - Fix hint: Track an in-flight save state: a duplicate ACTION_STOP while saving should be a no-op (or await the save) instead of stopping the service; alternatively make the save non-cancellable (NonCancellable/WorkManager handoff) so scope cancel cannot lose a captured clip.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt:200** - Restart during async save orphans an active MediaRecorder (mic stays on); onDestroy never releases recorder or audio focus
  - alt wording: onDestroy() is not a release point: real teardown edge never releases MediaRecorder, abandons audio focus, or resets static isRecording
  - Evidence: stopAndSave() flips all state to idle BEFORE the suspend save finishes: `releaseRecorder(); abandonFocus(); isRecording = false; QuickAudioRecorderWidgetProvider.updateAllWidgets(this, false)` (lines 140-143), then launches `serviceScope.launch { micRecordingSaver.save(... upload = { ... uploadToResource ... }) ... stopForegroundCompat(); stopSelf() }` (lines 148-173) - the upload path goes through LocalToFtp/Smb/Sftp/cloud strategies, so this window lasts seconds. During it a widget tap re-enters onStartCommand -> handleStart(); the only guard is `if (isRecording) return` (line 84), which is now false, so a NEW MediaRecorder is created and started (lines 100-119, isRecording=true). When the first save completes, its coroutine unconditionally calls `stopSelf()` (line 172; no-arg stopSelf ignores newer startIds) -> onDestroy runs, which is only `serviceScope.cancel(); super.onDestroy()` (lines 200-203) - no releaseRecorder(), no abandonFocus(). The second session's native MediaRecorder keeps capturing the microphone with no owner: the static `isRecording` stays true, and the next ACTION_STOP spawns a fresh service instance whose `mediaRecorder` is null and `recorderStarted` false, so stopAndSave() releases nothing and just toasts an error. Result: microphone + audio focus held until process death, second recording lost. Violates protocol Layer 3 'player instances are always released' / P1 'unreleased heavy resource'.
  - Fix hint: Guard handleStart() with an isFinalizing/save-in-progress flag (or use stopSelf(startId)), and make onDestroy() call releaseRecorder() + abandonFocus() and reset the static isRecording as a last-resort teardown.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

QuickAudioRecorderService - reentrancy и teardown-дыры (микрофон/фокус не освобождаются). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`stopAndSave()` синхронно переводит состояние сервиса в "не записывает" (`releaseRecorder()`, `abandonFocus()`, `isRecording = false`) ДО запуска асинхронного сохранения (`micRecordingSaver.save(..)`, которое может уйти в сетевую загрузку и занять секунды). На это окно нет никакой блокировки:

1. Пока сохранение выполняется, `isRecording == false`, поэтому повторный тап по виджету проходит guard `if (isRecording) return` в `handleStart()` и создаёт ВТОРОЙ `MediaRecorder`, стартует его и перезаписывает поля `mediaRecorder`/`outputFile` (сервис - singleton в рамках процесса, поля общие).
2. Когда первое сохранение завершается, его корутина безусловно вызывает `outputFile = null` (затирает поле второй записи) и no-arg `stopSelf()` - последний не учитывает, что после команды сохранения пришла более новая команда старта; сервис уничтожается, второй `MediaRecorder` никогда не получает `stop()/release()`, микрофон и `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` остаются захваченными до смерти процесса.
3. Повторный тап Stop во время сохранения повторно входит в `stopAndSave()`: `recorderStarted` уже `false` (первый проход его сбросил), поэтому `captured` остаётся `false`, срабатывает "ничего не захвачено" ветка -> `stopSelf()` -> `onDestroy()` -> `serviceScope.cancel()` - это отменяет ПЕРВУЮ (ещё выполняющуюся) корутину сохранения прямо посреди `micRecordingSaver.save(..)`, готовый клип теряется без какого-либо fallback-уведомления.
4. `onDestroy()` содержал только `serviceScope.cancel()` - не последняя линия обороны: если какой-либо путь оставляет `mediaRecorder`/`recorderStarted` не освобождёнными к моменту уничтожения сервиса, ресурс утекает без второго шанса на очистку.

Объединяющая причина - отсутствие флага "сохранение ещё не завершено", которым можно было бы заблокировать и повторный старт, и повторный стоп на время асинхронного окна.

---

## 3. Исправление

1. Добавлено поле `isSaving: Boolean` - true с момента запуска корутины сохранения до её завершения (`finally`-блок).
2. `handleStart()`: guard расширен до `if (isRecording || isSaving) return` - блокирует создание второго `MediaRecorder`, пока предыдущий клип ещё сохраняется.
3. `stopAndSave()`: ранний выход `if (isSaving) return` в начале функции - повторный Stop-тап во время сохранения теперь no-op вместо вызова `stopSelf()`/отмены scope.
4. Корутина сохранения обёрнута в `try/finally`: `finally` сбрасывает `isSaving = false`, затем условно очищает `outputFile` (`if (outputFile === file) outputFile = null` - защита от затирания более новой записи, даже если гонка всё же произойдёт), затем `stopForegroundCompat()`/`stopSelf()` - вынесены из-под условия успеха/неудачи, чтобы выполниться на любом выходе из корутины.
5. `onDestroy()`: добавлена последняя линия обороны - если `mediaRecorder != null || recorderStarted` на момент уничтожения сервиса, вызываются `releaseRecorder()`, `abandonFocus()`, `isRecording = false` перед `super.onDestroy()`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика foreground-сервиса (reentrancy-guard, release-контракт), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: `handleStart()` блокирует старт при `isRecording || isSaving`; `stopAndSave()` no-op при `isSaving`; async-ветка сохранения оборачивает `micRecordingSaver.save(..)` в `try/finally`, сбрасывающий `isSaving` и условно очищающий `outputFile` на любом выходе; `onDestroy()` подстраховывает освобождение при незавершённой записи.
- Ручная device-проверка (BlockNeedUserTest, опционально): начать запись через виджет, тапнуть Stop, во время (сетевой) загрузки быстро тапнуть виджет ещё раз - ожидание: тап игнорируется, вторая запись не стартует, значок виджета не мигает в "запись". Тапнуть кнопку Stop в уведомлении повторно во время сохранения - ожидание: клип всё равно сохраняется (нет тоста об ошибке из-за отменённой корутины).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Checks: (1) `handleStart()` guard extended to `if (isRecording || isSaving) return` - PASS. (2) `stopAndSave()` early-returns `if (isSaving) return` before any state mutation - PASS. (3) async save branch sets `isSaving = true` before `serviceScope.launch` and wraps the save in `try/finally`, resetting `isSaving = false` and running `stopForegroundCompat()`/`stopSelf()` unconditionally on exit - PASS. (4) `outputFile` cleared only via `if (outputFile === file) outputFile = null` guard, not unconditionally - PASS. (5) `onDestroy()` releases recorder + abandons focus + resets `isRecording` when `mediaRecorder != null || recorderStarted` before `super.onDestroy()` - PASS. (6) `.\a.ps1 fk` compile - PASS, BUILD SUCCESSFUL. (7) `post-change.ps1 -ScopeToFile` - PASS (dev-log, catalog-sync, ticket-log-audit, flavor-flag, neuroslop, fgs-notification, deprecated-pm-flags, detekt all PASS; listener-symmetry SKIP as project-wide advisory, not attributed to this file). MANUAL: on-device reentrancy verification deferred (no device online this session).

### Manual / on-device

- [ ] Start recording via widget, tap Stop, quickly tap widget again during (network) save - tap is ignored, no second recording starts, widget icon does not flicker back to recording.
- [ ] Tap the notification's Stop action a second time during save - clip still saves successfully (no error toast from a cancelled save coroutine).

