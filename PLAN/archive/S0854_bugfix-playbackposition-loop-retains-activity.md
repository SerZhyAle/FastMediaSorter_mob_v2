# Спецификация (compact bugfix): S0854 - PlaybackPositionHelper - осиротевший save-loop удерживает PlayerActivity

**Ticket:** S0854
**Status:** Archived
**Priority:** 80
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P0 (2:1, 2026-07-02). Vote 1 (panel skeptic): confirmed, runnable self-reposts unconditionally. Vote 2 (refuter): claimed strict stop-before-start pairing - but examined only the sequential path and missed the coroutine interleaving. Tie-break (direct code read): VideoPlayerManager.playVideo runs preflight-stop synchronously (:618) then managerScope.launch (:620) WITHOUT cancelling the previous load job (no job field at all); the coroutine suspends at getPosition (:622) and in protocol helpers before startPositionSaving() (:671). Two rapid playVideo calls interleave as stopA-stopB-startA-startB -> loop A orphaned; nothing ever stops it (onDestroy stops only the current loop; managerScope.cancel does not touch the Handler chain). Retained destroyed PlayerActivity = P0.

- **[P0] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt:23** - startPositionSaving() overwrites the previous PositionSaveLoop without stop() - orphaned self-reposting Handler runnable retains PlayerActivity forever after destroy
  - Evidence: PlaybackPositionHelper.kt:22-41: 'internal fun VideoPlayerManager.startPositionSaving() { positionSaveLoop = PositionSaveLoop(..); positionSaveLoop!!.start() }' - the old loop instance is overwritten, never stopped (KDoc claims "Replaces any previously scheduled save loop" but only the reference is replaced). PositionSaveLoop.start() stops only its own runnable, and the runnable self-reposts unconditionally: PositionSaveLoop.kt:36-38 'override fun run() { saveNow(); handler.postDelayed(this, intervalMs) }'. The sequential path is guarded only because VideoPlaybackPreflightHelper.runPreflight (called synchronously at playVideo entry) does manager.stopPositionSaving() at line 92; but playVideo launches an un-serialized coroutine per call (VideoPlayerManager.kt:620 'managerScope.launch {' with no previous-job cancel) that suspends at least at playbackPositionRepository.getPosition(path) (:622) and credentialsRepository.getByCredentialId (helpers) before reaching startPositionSaving() (:671). If playVideo(B) is dispatched while coroutine A is still in flight, both preflights run before either startPositionSaving(); the second startPositionSaving() then orphans the first loop. Nothing can ever stop the orphan: stopPositionSaving/onPause/onDestroy touch only the current positionSaveLoop (VideoPlayerLifecycleHelper.kt:75), and managerScope.cancel() at onDestroy (:125) does not affect the Handler chain (Handler-based, not coroutine-based). The loop lambdas capture the VideoPlayerManager ('getPath = { currentFilePath }', 'getPositionMs = { exoPlayer?.currentPosition .. }'), and VideoPlayerManager holds 'context = activity' and playerCallback = PlayerPlaybackCallbackImpl(activity = activity, ..) (PlayerViewerFactory.kt:14-17), so the main Looper permanently retains the destroyed PlayerActivity plus its PlayerView, and ticks a runnable every 15 s for the rest of the process lifetime (one more per race occurrence).
  - Fix hint: Make startPositionSaving() call stopPositionSaving() (or positionSaveLoop?.stop()) before creating the new loop, and/or serialize playVideo by cancelling the previous load job; also stop the loop in releasePlayer().

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

PlaybackPositionHelper - осиротевший save-loop удерживает PlayerActivity. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Три независимых механизма складываются в P0:

1. `PlaybackPositionHelper.startPositionSaving()` (PlaybackPositionHelper.kt:22-43) создаёт новый `PositionSaveLoop` и просто перезаписывает `positionSaveLoop`, не останавливая прежний экземпляр. Каждый `PositionSaveLoop` владеет собственным `Handler(Looper.getMainLooper())` (PositionSaveLoop.kt:27) - старый экземпляр никем в классе больше не держится, но его `runnable` уже поставлен в очередь сообщений главного Looper'а (`postDelayed`), и именно эта запись в очереди удерживает объект живым. Лямбды `PositionSaveLoop` замыкают `this@VideoPlayerManager` (через extension-функции), а `VideoPlayerManager` держит `context = activity` и `playerCallback` с `activity` внутри - значит orphaned-loop транзитивно удерживает уничтоженную `PlayerActivity`.
2. `VideoPlayerManager.playVideo()` (VideoPlayerManager.kt:605-684) диспатчит каждый вызов как независимую `managerScope.launch { .. }` корутину (:620) без отслеживаемого/отменяемого Job. Быстрый повторный вызов (свайп на следующий файл, пока предыдущий ещё не прошёл suspend-точки вроде `getPosition()`/сетевого TS-probe) даёт две параллельно летящие корутины; обе рано или поздно доходят до `startPositionSaving()` (:671), и какая финиширует второй - та выигрывает ссылку, осиротив первую через механизм (1).
3. `VideoPlayerLifecycleHelper.releasePlayer()` (VideoPlayerLifecycleHelper.kt:20-50) - вызывается из входа каждого протокольного хелпера (`Cloud/Ftp/Smb/Sftp/Local/Stream PlaybackHelper.play*Video()`) и из `PlayerLifecycleManager.stopVideoPlayback()` (переключение на не-видео файл: изображение/PDF/EPUB/текст) - освобождает ExoPlayer, но никогда не останавливает `positionSaveLoop`. Если после этого `playVideo()` больше не вызывается (пользователь ушёл смотреть картинки и не вернулся к видео в этой сессии), у цикла из механизма (1) не остаётся будущего вызова `startPositionSaving()`, которым его бы заменили - он тикает бессрочно независимо от гонки корутин.

Устройство `PositionSaveLoop.start()` (:32-33) само вызывает `stop()` в начале - но это останавливает только `runnable` **этого же** экземпляра; проблема ровно в том, что старый экземпляр никогда не получает вызов `stop()`, т.к. на него больше нет ссылки в `positionSaveLoop`.

---

## 3. Исправление

Минимальный фикс - defense-in-depth на всех трёх механизмах, без изменения публичного API/поведения:

1. `PlaybackPositionHelper.startPositionSaving()` - остановить существующий `positionSaveLoop` (`positionSaveLoop?.stop()`) перед созданием нового, так что устаревший экземпляр гарантированно отменяется независимо от порядка гонки вызовов. Закрывает механизм (1) напрямую - именно эту находку и описывает P0 в §0.
2. `VideoPlayerLifecycleHelper.releasePlayer()` - добавить `manager.stopPositionSaving()`, чтобы каждый путь освобождения плеера (не только `onPause`/`onDestroy`) останавливал цикл сохранения позиции. Закрывает механизм (3) - утечку при переключении на не-видео файл.
3. `VideoPlayerManager.playVideo()` - завести поле `activeLoadJob: Job?`, отменять его перед запуском нового `managerScope.launch { .. }` и сохранять новый Job в это же поле. Сериализует диспатч плейбека так, что два вызова `playVideo()` никогда не летят параллельно - корневой фикс механизма (2). Это также убирает гонку, которую S0865 описывает как duplicate-player race (тот же корень), но S0865 не закрывается этим тикетом - у него собственный belt-and-braces фикс (re-check-and-release exoPlayer в точке присвоения) и собственная верификация.

Verification predicates:
- `positionSaveLoop?.stop()` присутствует непосредственно перед конструктором `PositionSaveLoop(..)` в `startPositionSaving()`.
- `manager.stopPositionSaving()` присутствует в `VideoPlayerLifecycleHelper.releasePlayer()`.
- `activeLoadJob?.cancel()` присутствует непосредственно перед `managerScope.launch { .. }` в `playVideo()`, и результат `launch` присваивается обратно в `activeLoadJob`.
- `.\a.ps1 dq` (assembleStandardDebug) -> BUILD SUCCESSFUL.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0865 (same root cause - unserialized playVideo coroutine; fix together). This ticket's playVideo serialization fix structurally also closes S0865's race window, but S0865 keeps its own scope (belt-and-braces exoPlayer re-check-and-release) and its own audit/verification pass - not silently closed here.

---

## 4. Проверка

Race-механизм (2) требует точного тайминга (два `playVideo()` в узком окне suspend), поэтому детерминированной unit-проверки нет - фикс проверяется статически (код гарантирует stop-before-overwrite независимо от тайминга) и на устройстве:

- Static: `positionSaveLoop?.stop()` в `startPositionSaving()`, `manager.stopPositionSaving()` в `releasePlayer()`, `activeLoadJob` cancel-then-launch в `playVideo()` - все три предиката из §3 присутствуют в коде.
- Build: `.\a.ps1 dq` (`assembleStandardDebug`) -> BUILD SUCCESSFUL, без новых detekt/neuroslop находок в тронутых файлах.
- On-device (manual): открыть видео-папку, быстро пролистать 3-4 сетевых или крупных локальных файла подряд (эмулирует suspend-окно), затем переключиться на изображение (проверка механизма 3) и уйти из плеера. В logcat после выхода не должно быть повторяющихся тиков `PositionSaveLoop: started`/`saveNow` для файла, который уже не отображается - каждый `startPositionSaving()`/`releasePlayer()` должен сопровождаться `PositionSaveLoop: stopped` для прежнего цикла.

