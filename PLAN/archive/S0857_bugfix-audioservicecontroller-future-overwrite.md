# Спецификация (compact bugfix): S0857 - AudioServiceController - перезапись in-flight controllerFuture без release

**Ticket:** S0857
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic + 1 prior panel vote). Note: the two findings below are ONE defect (line 63 builds the future, line 64 stores it). Key confirmed mechanics: isConnected is the only guard and stays false for the whole buildAsync window; PlayerManagerInitializer.kt:715 injects the SAME AudioServiceController instance into NowPlayingManager (onStart -> connectForStatus on every resume) and PlayerMediaLoaderManager (connect() on audio playback) - two uncoordinated callers race the unguarded field; release() (:307-314) releases only the current field; no ServiceConnection/unbindService exists anywhere in ui/player, so the orphaned bound MediaController pins AudioPlaybackService (ExoPlayer+MediaSession) indefinitely - MediaSessionService.onDestroy cannot run while any client is bound, and @Volatile isRunning stays true, which MainActivity.kt:335-349 restore logic and PlayerLifecycleManager.kt:448-469 exit gates read verbatim.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt:63** - connect()/connectForStatus() have no in-flight guard: overlapping calls overwrite controllerFuture and orphan the earlier MediaController, which is never released
  - Evidence: The guard checks only a COMPLETED connection: lines 39-40 `val isConnected: Boolean get() = mediaController?.isConnected == true`; connect() line 51 `if (isConnected) { ... return }` ignores a pending controllerFuture. Lines 63-64 then run `val future = MediaController.Builder(context, sessionToken).buildAsync(); controllerFuture = future`, and connectForStatus() repeats the same pattern at 257-258 - each call overwrites the previous future. release() (311) only releases the CURRENT field: `controllerFuture?.let { MediaController.releaseFuture(it) }`, so an overwritten in-flight future's MediaController completes its bind, gets assigned to mediaController by its listener (line 69, itself overwriting a possibly-live prior controller without releasing it), and is later shadowed by the second future's listener - no code path ever calls releaseFuture/release on it. Each MediaController is a bound ServiceConnection to AudioPlaybackService created with the host Activity context (StreamsActivity.kt:181 `AudioServiceController(this)`, PlayerManagerInitializer.kt:715 `AudioServiceController(activity)`, MainStreamsInlineAudioManager.kt:35 `AudioServiceController(binding.root.context)`). Concrete runtime paths that overlap inside the buildAsync connect window (service cold start builds notification channel + ExoPlayer + MediaSession first, AudioPlaybackService.onCreate:153-368): (a) Streams screen - StreamInlineAudioManager.play() line 126 calls audioController.playAudioWithMetadata -> connect() per channel tap, and stopPlaybackKeepingController() (174-190) only stops the player, never the connection: tapping channel A then channel B before the first connect completes creates two futures, orphaning the first controller; same engine on the MainActivity streams panel. (b) PlayerActivity - NowPlayingManager.updateBarVisibility line 155 calls connectForStatus on every resume (PlayerActivityLifecycleBridge.kt:81 nowPlayingManager.onStart) while PlayerMediaLoaderManager's audio-play flow calls connect() via playAudioPlaylistWithMetadata; when a photo screen with running background audio is resumed and the user immediately opens an audio file, both run before the first future resolves. (c) Reconnect after service auto-stop: connect() line 69 `mediaController = controller` overwrites the prior disconnected MediaController instance without releasing it. Violates contract items 1 and 7; per taxonomy this is an unreleased heavy resource (player-connection) that survives until Activity destroy force-unbind ('ServiceConnection leaked' log) and keeps the bound service from stopping.
  - Fix hint: Track the pending future: if controllerFuture is non-null and not done, chain the new onConnected callback onto it instead of building a second controller; release any previous mediaController/future before assigning a new one.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt:64** - Pending MediaController future overwritten without release - orphaned bound controller pins AudioPlaybackService (and its ExoPlayer) past stopSelf, so onDestroy/release can never run
  - alt wording: connect() overwrites an in-flight controllerFuture: rapid second play() creates a duplicate MediaController that is never released and keeps playerListener attached forever
  - alt wording: connect()/connectForStatus() overwrite controllerFuture without releasing the in-flight future, so one of two interleaved connects leaves a MediaController that release() can never free
  - Evidence: connect() guards only on completed connections: `if (isConnected)` (line 51) where `val isConnected: Boolean get() = mediaController?.isConnected == true` (lines 39-40) is false for the whole async connect window. It then unconditionally overwrites the stored future: `val future = MediaController.Builder(context, sessionToken).buildAsync(); controllerFuture = future` (lines 63-64); connectForStatus() does the same at lines 257-258. release() releases only the LAST stored future: `controllerFuture?.let { MediaController.releaseFuture(it) }` (line 311). Concrete path: PlayerActivity resumes on a photo while background audio runs -> NowPlayingManager.updateBarVisibility calls `audioServiceController.connectForStatus { .. }` (NowPlayingManager.kt:155) -> future F1 pending; user navigates to an audio file within the connect window (on cold service start the window spans service onCreate incl. ExoPlayer build) -> PlayerMediaLoaderManager -> `audioServiceController.playAudioPlaylistWithMetadata(items, startIndex, onPlayerReady)` (NowPlayingManager.kt:98) -> connect() sees isConnected==false -> builds F2, `controllerFuture = F2`, F1 is orphaned. F1 completes and its MediaController stays bound to AudioPlaybackService for the process lifetime (never passed to MediaController.releaseFuture; PlayerLifecycleManager.kt:190 `activity.audioServiceController?.release()` releases only F2). Android does not destroy a service with bound clients: after playback ends, AudioPlaybackService.autoStopRunnable `stopSelf()` (AudioPlaybackService.kt:87) and MainActivity `stopService(serviceIntent)` (MainActivity.kt:1423-1428) never reach onDestroy, so `mediaSession?.run { player.release(); release() }` (AudioPlaybackService.kt:417-420) never executes - the ExoPlayer and MediaSession stay alive unreleased and `isRunning` remains true (misdrives MainActivity restore-player at MainActivity.kt:335-349 and exit logic). Violates contract items 2/7 (player instance survives its teardown edge unreleased) and the unit-note 'binder clients not retaining the service'.
  - Fix hint: In connect()/connectForStatus(), when a previous connect is in flight, attach the callback to the existing pending future instead of building a new one; if replacement is intended, call MediaController.releaseFuture(previousFuture) before overwriting controllerFuture.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

AudioServiceController - перезапись in-flight controllerFuture без release. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`connect()`/`connectForStatus()` проверяли только УЖЕ завершённое подключение (`isConnected` = `mediaController?.isConnected == true`), которое остаётся `false` на весь период асинхронного `buildAsync()`. Оба метода безусловно создавали новый `controllerFuture`, перезаписывая предыдущий - если второй вызов приходил до резолва первого (два экрана/колбэка используют один и тот же `AudioServiceController`: `NowPlayingManager.updateBarVisibility` на каждом resume + `PlayerMediaLoaderManager` при старте аудио-плейбека), первый future терял единственную ссылку на себя, его `MediaController` резолвился и биндился к `AudioPlaybackService`, но никогда не освобождался - `release()` освобождает только ТЕКУЩЕЕ поле. Осиротевший bound-клиент не даёт `MediaSessionService.onDestroy` выполниться (Android не уничтожает сервис с активными bind-клиентами), поэтому ExoPlayer/MediaSession сервиса никогда не освобождаются, а `isRunning` остаётся `true` вечно.

---

## 3. Исправление

Добавлен `controllerLock` (`synchronized`) и явный state-machine поверх `controllerFuture`/`mediaController`:
1. `getOrCreateControllerFuture(reason)`: если уже подключены - вернуть `Connected`. Если есть in-flight future (`controllerFuture != null && mediaController == null`) - переиспользовать его (`FutureRequest`), НЕ создавая новый. Если найденный future уже устарел (мёртвое соединение) - явно `MediaController.releaseFuture()` перед созданием нового.
2. `storeResolvedController(future, controller)`: при резолве future проверяет, остаётся ли он ТЕКУЩИМ отслеживаемым (`controllerFuture === future`) - если нет (успел смениться более новым запросом), возвращает `Stale`, и вызывающий код (`connect`/`connectForStatus`) освобождает только что резолвленный `controller.release()` вместо того, чтобы его сохранить.
3. `clearFutureOnFailure(future)`: при ошибке подключения сбрасывает `controllerFuture` только если это всё ещё тот же future и ничего не сохранено - не затирает более новый future, если ошибка пришла от уже вытесненного.
4. `connect()`/`connectForStatus()` теперь используют `getOrCreateControllerFuture` вместо прямого `buildAsync()`, оба ветвления (`Connected`/`FutureRequest`) обработаны единообразно.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (устранение гонки future/controller), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- Статический ре-обзор: `getOrCreateControllerFuture`/`storeResolvedController`/`clearFutureOnFailure` все под одним `controllerLock`; ни один путь не перезаписывает `controllerFuture` без либо переиспользования, либо явного `releaseFuture()`.
- Dev-log подтверждает применение фикса (S0857 @ 2026-07-02 15:30:27); фикс уже был реализован и собран до начала этого аудита - повторная сборка не требуется (файл не менялся в рамках audit-прохода).
- Ручная device-проверка (необязательно, вне BlockNeedUserTest): resume PlayerActivity на фото с активным фоновым аудио сразу с открытием другого аудиофайла (гонка `connectForStatus` vs `connect`) - ожидание: только один живой `MediaController`, сервис корректно останавливается после завершения воспроизведения (нет "ServiceConnection leaked" в логах).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `getOrCreateControllerFuture()` reuses an in-flight future instead of overwriting (AudioServiceController.kt:339-343) - PASS. Stale future explicitly released before creating a new one (:345-349) - PASS. `storeResolvedController()` detects a superseded future and signals `Stale` so the caller releases the orphaned controller (:363-376, consumed at :65-69 and :110-115) - PASS. `clearFutureOnFailure()` only clears the still-current future (:378-384) - PASS. All mutation sites under `controllerLock` (`synchronized`) - PASS. Dev log entry present (S0857 @ 15:30:27) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Resume PlayerActivity on a photo with background audio active while immediately opening another audio file (connectForStatus vs connect race) - expect exactly one live MediaController, service stops cleanly after playback ends, no "ServiceConnection leaked" in logs.

