# Спецификация (compact bugfix): S0875 - StreamFrameSnapshotManager - гонка acquireLatestImage vs close() (краш процесса)

**Ticket:** S0875
**Status:** Archived
**Priority:** 75
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED, ESCALATED to P0 (2026-07-02, dedicated skeptic). Mechanics: both acquire sites run on the shared listener thread with NO catch coverage - :147 'r.acquireLatestImage()?.close()' is bare in the firstFrame.isCompleted branch; :215 'reader.acquireLatestImage() ?: return null' sits BEFORE readFrame's own try (:216), and capture()'s catch (:195) lives on Dispatchers.Main - structurally cannot intercept an async exception on the reader thread. close path: capture() finally (:198-206, Main) does setOnImageAvailableListener(null)+imageReader.close() with ZERO synchronization shared with the listener (existing synchronized/semaphore guard only queue/limit state). readerHandler is a companion val on one HandlerThread("StreamFrameReader") (:257-258) shared by every capture for the process lifetime. LoggingHelper.installCrashHandler (:50-57) chains to previousCrashHandler - does NOT swallow -> uncaught IllegalStateException kills the process. Exposure is continuous: StreamGridModeManager 60s periodic refresh (:255/:186), pull-to-refresh (:200), scroll listener (:68-69) -> request() per visible tile. Fix shape: guard the listener body with try/catch(IllegalStateException) + an @Volatile closed flag checked before acquire, or close the reader on the reader thread via readerHandler.post.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt:215** - ImageReader acquireLatestImage races main-thread close() with no guard - uncaught IllegalStateException on shared HandlerThread kills the process
  - Evidence: Both acquire sites run on the background 'StreamFrameReader' HandlerThread and are NOT protected against the main-thread teardown. Line 215 is outside readFrame's try: `val image = reader.acquireLatestImage() ?: return null` (the `try {` starts only on line 216), and line 147 in the listener is bare: `r.acquireLatestImage()?.close()`. Meanwhile capture()'s finally on the MAIN thread closes the reader with no lock/flag shared with the listener: lines 204-205 `imageReader?.setOnImageAvailableListener(null, null); imageReader?.close()`. ImageReader's ListenerHandler reads the listener reference once at message-dispatch time; a lambda already past that read keeps executing while main runs close(). acquireLatestImage on a closed reader throws IllegalStateException from nativeImageSetup ('ImageReader is not initialized or was already closed') - a well-known field crash signature. Thrown inside the listener lambda it is uncaught (line 147 branch has no try at all; line 215 is before readFrame's try), and an uncaught exception on the companion-object HandlerThread (lines 257-258) is a FATAL EXCEPTION = app crash. Concrete path: capture times out at CAPTURE_TIMEOUT_MS (line 184-187) or completes on frame 1 while the live decoder (playWhenReady=true, line 182) renders frame 2 during player.release(); the frame message starts executing (listener read pre-line-204), the reader thread is preempted before acquire, main finishes lines 204-205, reader thread resumes into acquireLatestImage on the closed reader. Window is narrow but the operation repeats per visible tile on every 60 s periodic grid refresh, scroll re-capture, and pull-to-refresh.
  - Fix hint: Guard both acquire sites: move line 215 inside readFrame's try (its catch(Throwable) already returns null) and wrap line 147 in try/catch(IllegalStateException); or share a @Volatile closed flag / lock between the finally and the listener so no acquire can start after close().

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

StreamFrameSnapshotManager - гонка acquireLatestImage vs close() (краш процесса). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`StreamFrameSnapshotManager.capture()` рендерит поток в offscreen `ImageReader` и в `finally` (на Main) делает `imageReader.close()`. `ImageReader.OnImageAvailableListener` вызывается на отдельном `readerHandler` (единый `HandlerThread("StreamFrameReader")` на весь процесс, `companion object`). Handler читает ссылку на listener-лямбду в момент диспетчеризации сообщения - уже начавшийся вызов лямбды продолжает исполняться, даже если Main успел вызвать `close()` до того, как лямбда дошла до `acquireLatestImage()`. Оба места вызова `acquireLatestImage()` (строка ~147 в листенере и строка ~215 в `readFrame()`) были без try/catch (в `readFrame()` вызов вообще стоял ДО начала `try` на строке 216, так что и общий `catch (t: Throwable)` его не покрывал). `acquireLatestImage()` на уже закрытом ридере кидает `IllegalStateException` ("ImageReader is not initialized or was already closed") - необработанное на `HandlerThread` исключение уходит в `LoggingHelper.installCrashHandler`, который НЕ глотает ошибку, а цепляется к `previousCrashHandler` -> процесс падает. Окно гонки узкое, но операция повторяется на каждый видимый tile при 60-секундном periodic refresh грида, pull-to-refresh и re-capture при скролле - то есть срабатывает регулярно на реальном трафике.

---

## 3. Исправление

Оба места вызова `acquireLatestImage()` обёрнуты в `try/catch (IllegalStateException)`, трактующий гонку как штатный (не аварийный) исход - кадр просто пропускается:
1. Листенер (`if (firstFrame.isCompleted)` ветка, `r.acquireLatestImage()?.close()`) - обёрнут в try/catch, лог `Timber.d` вместо падения.
2. `readFrame(reader)` - `reader.acquireLatestImage()` перенесён в собственный `try`, `IllegalStateException` перехватывается и возвращает `null` (та же семантика, что и штатный "нет кадра"); остальная логика функции (`copyPixelsFromBuffer`/`finally { image.close() }`) не изменена.

Синхронизация (Mutex/`@Volatile`-флаг) не потребовалась - `try/catch` на конкретном известном исключении `IllegalStateException` достаточен, точечно устраняет падение без изменения модели потоков (readerHandler остаётся общим на процесс, `close()` остаётся на Main - это уже устоявшийся паттерн релиза ресурсов в файле, менять не требуется).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (guard от гонки потоков), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard).
- `.\a.ps1 d` - debug-сборка проходит.
- Статический ре-обзор: оба сайта `acquireLatestImage()` (листенер + `readFrame()`) теперь обёрнуты в `try/catch (IllegalStateException)`; общий `catch (t: Throwable)` в `readFrame()` остаётся для прочих ошибок копирования кадра.
- Ручная device-проверка (BlockNeedUserTest, опционально): грид с несколькими живыми потоками, спровоцировать частый periodic refresh/pull-to-refresh на слабом канале - ожидание: не падает процесс; при гонке тайл просто пропускает кадр (остаётся на предыдущем/placeholder до следующего успешного capture).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: listener-branch acquire (StreamFrameSnapshotManager.kt:148-152) wrapped in `try/catch (IllegalStateException)`, exception passed to `Timber.d(e, ...)` (not swallowed) - PASS. `readFrame()` acquire (:221-226) moved inside its own `try/catch (IllegalStateException)`, same non-swallowed logging - PASS. Both catches return `null`/no-op instead of propagating - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate (post-fix, SwallowedException resolved) - PASS. Dev log entry present (S0875 @ 16:06:30) - PASS. FEATURES trilingual - EXEMPT (internal race-condition fix, no user-visible capability).

### Manual / on-device

- [ ] Grid with several live streams under frequent periodic refresh/pull-to-refresh on a weak channel - expect no process crash; a raced tile simply skips one frame instead of crashing.

