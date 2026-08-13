# Спецификация (compact bugfix): S0856 - AudioToggleTileService - утечка MediaController future мимо release

**Ticket:** S0856
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: P1 CONFIRMED 3/3 by skeptic panel.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/core/AudioToggleTileService.kt:123** - Pending MediaController buildAsync future not released on onStopListening - connection completing after the release edge yields an orphaned, never-released MediaController with a stuck Player.Listener and a leaked ServiceConnection that keeps AudioPlaybackService alive
  - alt wording: Pending MediaController future escapes onStopListening teardown; late completion registers a listener past the symmetric edge and the next connect orphans the unreleased controller
  - alt wording: Pending MediaController future never released; stop-before-connect orphans a connected controller (contract items 1 and 7)
  - Evidence: connectToSession() discards the future and the completion callback has no released/still-listening guard: ```kotlin private fun connectToSession() {     val token = SessionToken(this, ComponentName(this, AudioPlaybackService::class.java))     val future = MediaController.Builder(this, token).buildAsync()   // line 75: future is a local, never retained     future.addListener({         try {             mediaController = future.get()                            // line 78: unconditional field assignment             mediaController?.addListener(tilePlayerListener)          // line 79 ``` releaseController() only handles an already-assigned controller - it cannot release an in-flight build because the field is still null and there is no MediaController.releaseFuture() call: ```kotlin private fun releaseController() {          // line 123     mediaController?.removeListener(tilePlayerListener)     mediaController?.release()     mediaController = null } ``` Concrete runtime path (all steps on the main looper, so it is a message-ordering hole, not a visibility race): (1) QS shade opens while audio plays -> onStartListening (line 31) -> connectToSession() starts the async bind/handshake to AudioPlaybackService; (2) user closes the shade before the handshake finishes -> onStopListening (line 40) -> releaseController() no-ops (field null, no future to release); (3) the connection-complete message runs the callback -> a live connected MediaController is stored in the field and tilePlayerListener is added, on a tile that is no longer listening; (4) user reopens the shade -> onStartListening -> connectToSession() runs again unconditionally (no existing-controller check, lines 31-33) and its callback overwrites the field at line 78 WITHOUT releasing the previous instance. The first controller is now permanently orphaned: release() is never called on it, tilePlayerListener is never removed (contract items 1, 4, 7), and its internal ServiceConnection stays bound to AudioPlaybackService, preventing the MediaSessionService from stopping itself after playback ends (service + session linger) until the TileService itself is destroyed and the system force-cleans the leaked connection (logcat 'ServiceConnection leaked'). This is an unreleased heavy player resource surviving the host teardown edge = P1. The project already has the correct pattern in the two sibling controller hosts, which this file skips: NowPlayingViewModel.kt lines 62/106/143 (`controllerFuture?.let { MediaController.releaseFuture(it) }`) and AudioServiceController.kt line 311 (`controllerFuture?.let { MediaController.releaseFuture(it) }`).
  - Fix hint: Retain the future in a field (private var controllerFuture: ListenableFuture<MediaController>? = null; controllerFuture = future in connectToSession). In releaseController() call controllerFuture?.let { MediaController.releaseFuture(it) }; controllerFuture = null before nulling mediaController - releaseFuture releases the controller even if the future completes after the call, mirroring NowPlayingViewModel.disconnect() and AudioServiceController.release(). Optionally also guard the callback: if (future !== controllerFuture) { future.get().release(); return }, and skip connectToSession() when mediaController?.isConnected == true (as NowPlayingViewModel.connect() line 101 does).

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

AudioToggleTileService - утечка MediaController future мимо release. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

В `AudioToggleTileService` при запуске подключения (`connectToSession`) создается `ListenableFuture<MediaController>`, которая не сохраняется в полях класса. Если пользователь закрывает Quick Settings шторку до завершения асинхронного подключения:
1. Вызывается `onStopListening() -> releaseController()`.
2. Поле `mediaController` всё еще `null`, поэтому метод ничего не освобождает.
3. По завершении подключения колбэк `future.addListener` выполняется безусловно, присваивая подключенный `MediaController` в `mediaController` и добавляя `tilePlayerListener`.
4. При следующем открытии шторки (`onStartListening() -> connectToSession()`) старый контроллер перезаписывается новым без вызова `release()`, оставаясь навсегда утекшим (орфанизированным) вместе со своим `ServiceConnection` и `tilePlayerListener`. Это предотвращает остановку `AudioPlaybackService` и вызывает предупреждение о `ServiceConnection leaked`.

---

## 3. Исправление

1. Добавить импорт `com.google.common.util.concurrent.ListenableFuture`.
2. Добавить поле `private var controllerFuture: ListenableFuture<MediaController>? = null` для сохранения ссылки на текущий in-flight future.
3. В `connectToSession()` проверять `if (mediaController?.isConnected == true) return` для предотвращения повторного подключения при уже живом соединении.
4. В колбэке `addListener` добавить проверку соответствия текущей future: `if (future !== controllerFuture)` для корректного освобождения поздних соединений, и сбрасывать `controllerFuture = null` в блоке `finally` при успехе/ошибке текущего подключения.
5. В `releaseController()` добавить вызов `controllerFuture?.let { MediaController.releaseFuture(it) }` с последующим обнулением ссылки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

1. Успешная компиляция проекта через `.\a.ps1 fk`.
2. Прохождение Detekt и линтера на измененном файле с помощью `scripts/post-change.ps1` с флагом `-ScopeToFile`.
3. Сборка debug APK (`.\a.ps1 d`).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `controllerFuture` field present (AudioToggleTileService.kt:28) - PASS. `connectToSession()` guards both `isConnected` and in-flight `controllerFuture` before creating a new future (:76-77) - PASS. Callback checks `future !== controllerFuture` and releases the stale resolved controller instead of storing it (:88-95) - PASS. `finally` clears `controllerFuture` only if still current (:104-107) - PASS. `releaseController()` calls `MediaController.releaseFuture()` on the pending future in addition to releasing the resolved controller (:150-151) - PASS. `onDestroy()` also calls `releaseController()` for final teardown symmetry (:154-157, beyond the fix hint's minimum) - PASS. Dev log entry present (S0856 @ 15:23:37) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Open Quick Settings shade with audio playing, close it before the MediaController handshake completes, reopen - expect exactly one live controller, no "ServiceConnection leaked" in logcat.

