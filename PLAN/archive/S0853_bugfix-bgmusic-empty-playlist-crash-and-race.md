# Спецификация (compact bugfix): S0853 - BackgroundMusicManager - краш random() на пустом плейлисте + гонка состояния

**Ticket:** S0853
**Status:** Archived
**Priority:** 80
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: BOTH CONFIRMED (2026-07-02). Finding A (crash :407): P0 3/3 skeptic panel. Finding B (state race :51): P1, dedicated skeptic - all 5 named fields have genuine cross-thread writers with zero synchronization in the file: failedFiles (main Player.Listener :121/:132 vs IO :396/:398 via scope.launch :122/:137-149), availableAudioFiles/currentTrackPath (IO :290/:297 vs main release() :546-547 vs mixed skipToNextRandomTrack :408), isPlaying (IO health-check loop :486-497 write :494 vs main :161/:248/:257/:428/:541), loadPlaylistJob (main :206/:267 vs mixed :413 - lost-update on Job leaves an uncancelled orphan calling setMediaItem out of order). Torn read of availableAudioFiles/failedFiles during the IO filter :404 produces the empty-candidates state that FEEDS finding A's crash. Fix shape: confine all mutable state to main (hop via withContext(Main) for every write) or guard with a Mutex; fixing B shrinks A's window, but A still needs its own empty-candidates guard.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:51** - Data race: failedFiles/currentTrackPath/availableAudioFiles/isPlaying/loadPlaylistJob mutated from both main thread and Dispatchers.IO workers with no synchronization
  - Evidence: Unguarded fields: `private val failedFiles = mutableSetOf<String>()` (line 51), `currentTrackPath` (47), `availableAudioFiles` (46), `currentTrackName` (48), `isPlaying` (41), `loadPlaylistJob` (67) - no Mutex/@Volatile/thread confinement. Main-thread writers: Player.Listener callbacks (player created on main, so application looper = main) do `failedFiles.add(it)` at lines 121/132; release() writes lines 541-549. IO-thread writers: `loadAndSetPlaylist` body runs on `scope` = SupervisorJob()+Dispatchers.IO (line 66) and writes `availableAudioFiles = audioFiles` (290), `currentTrackPath = randomFile.path` (297), `currentTrackName = trackName` (349); `skipToNextRandomTrack()` executes entirely on an IO worker when launched from the IO-error path (line 122) or recovery path (line 149), mutating `failedFiles.clear()` (398), `currentTrackPath` (408), `loadPlaylistJob?.cancel(); loadPlaylistJob = scope.launch` (412-413); the health check writes `isPlaying = false` on an IO worker (line 495) while reading it at 490. The SAME function concurrently runs on main from the health check (`withContext(Dispatchers.Main)` line 499 -> skip at 508) or the track-name tap. Concrete failure: IO-error recovery skip on IO iterating `availableAudioFiles.filter { it.path !in failedFiles }` (404) while a second onPlayerError on main executes `failedFiles.add` (121) -> ConcurrentModificationException or corrupted HashSet; concurrent read-modify-write of `loadPlaylistJob` (412-413) from main (tap/health-check) and IO (error path) -> lost update -> an untracked duplicate download job that later calls setMediaItem out of order and is never cancelled by release().
  - Fix hint: Confine all state mutation to the main thread (hop to Dispatchers.Main before touching manager state in skipToNextRandomTrack/loadAndSetPlaylist, keep only the download on IO), or guard the fields with a Mutex.
- **[P0] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt:407** - Crash: candidateFiles.random() on empty list when the playlist's only track is marked failed (IO-error skip path is uncaught)
  - Evidence: Line 407: `val randomFile = candidateFiles.random()`. For a single-file playlist the branch at line 391 (`if (availableAudioFiles.size > 1 && currentTrackPath != null)`) is false, so line 404 runs: `availableAudioFiles.filter { it.path !in failedFiles }` - empty once that one file is in failedFiles - and `random()` on an empty collection throws NoSuchElementException. Concrete path: music resource with exactly 1 audio file -> repeatMode ONE (line 356) -> playback IO error (the code's own comment line 120: "silent skip (file moved/unavailable)", e.g. cache file evicted or local file deleted mid-play) -> onPlayerError line 117 `error.errorCode in 2000..2999` -> line 121 `currentTrackPath?.let { failedFiles.add(it) }` -> line 122 `scope.launch { skipToNextRandomTrack() }`. That launch runs on `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (line 66) which has NO CoroutineExceptionHandler, so the NoSuchElementException is delivered to the default handler and kills the process. The same empty-candidates state also crashes the main thread via the track-name tap (PlayerManagerInitializer.kt:209-212 calls `skipToNextRandomTrack()` directly with no try/catch) and via the STATE_ENDED callback (line 112). Contrast: the non-IO error path wraps the skip in try/catch (lines 137-176); the IO path and the tap path do not.
  - Fix hint: Use `candidateFiles.randomOrNull() ?: return` (optionally clear failedFiles or invoke onMusicErrorListener when no candidate remains), or add an empty-guard after building candidateFiles.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

BackgroundMusicManager - краш random() на пустом плейлисте + гонка состояния. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`BackgroundMusicManager` держит единственный `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` и запускает на нём корутины через `scope.launch { }` **без явного диспетчера** в двух местах восстановления после ошибки плеера (`onPlayerError`, IO-ветка :122 и общая ветка :137). Поэтому синхронный префикс `skipToNextRandomTrack()` (чтение `availableAudioFiles`/`failedFiles`/`currentTrackPath`, запись `currentTrackPath`/`loadPlaylistJob`) в этих двух путях выполняется на IO-потоке, а во всех остальных вызовах (`Player.Listener.onPlaybackStateChanged`, health-check, внешний тап по имени трека) - на Main. Аналогично `loadAndSetPlaylist()` пишет `availableAudioFiles`/`currentTrackPath`/`currentTrackName` до `withContext(Dispatchers.Main)` (то есть на IO), а health-check (`startHealthCheck()`) читает/пишет `isPlaying` и читает `musicPlayer` ДО хопа на Main. Итог - пять полей мутируются с обоих потоков без синхронизации (Mutex/@Volatile/thread confinement отсутствуют).

Гонка на `availableAudioFiles`/`failedFiles` создаёт состояние "все файлы плейлиста помечены как failed" (torn read во время IO-фильтрации :404 конкурирует с main-thread `failedFiles.add()` в `onPlayerError`). Для плейлиста из одного файла ветка `availableAudioFiles.size > 1` (:391) ложна, поэтому `candidateFiles = availableAudioFiles.filter { it.path !in failedFiles }` (:404) даёт пустой список, а `candidateFiles.random()` (:407) кидает `NoSuchElementException` - без try/catch на IO-error-пути (:122, `scope.launch` без CoroutineExceptionHandler) это падение процесса; на main-путях (health-check :508, внешний тап) - краш UI-потока.

Оба дефекта имеют общий корень (не изолированное состояние менеджера от потока вызова) и общий вектор проявления (single-file плейлист + playback IO error), поэтому чинятся одним проходом по файлу.

---

## 3. Исправление

Конфайнмент всех mutable-полей менеджера (`isPlaying`, `availableAudioFiles`, `currentTrackPath`, `currentTrackName`, `failedFiles`, `loadPlaylistJob`) к Main-потоку - без Mutex, по образцу уже существующего паттерна в файле (`withContext(Dispatchers.Main)` перед `currentPlaylist`/`musicPlayer` в конце `loadAndSetPlaylist`/`skipToNextRandomTrack`). Реальная I/O-работа (`prepareMediaItem`, `downloadNetworkFileUseCase`, `getMediaFilesUseCase`) остаётся на IO - строго через отдельный `scope.launch { }` (без диспетчера = IO по умолчанию scope), запускаемый ПОСЛЕ того как поля уже прочитаны на Main.

Шаги:
1. `onPlayerError` IO-error ветка (:122) и общая recovery-ветка (:137-176): `scope.launch { skipToNextRandomTrack() }` -> `scope.launch(Dispatchers.Main)` с тем же телом. Убрать теперь избыточные внутренние `withContext(Dispatchers.Main)` (:142-144, :157-174) - весь блок уже на Main.
2. `loadAndSetPlaylist()` (:286-362): убрать преждевременные записи `availableAudioFiles = audioFiles` (:290), `currentTrackPath = randomFile.path` (:297), `currentTrackName = trackName` (:349) на IO - перенести все три записи внутрь финального `withContext(Dispatchers.Main) { }` блока (:351-362), рядом с `currentPlaylist`. Локальные `audioFiles`/`randomFile`/`trackName` продолжают использоваться как раньше.
3. `skipToNextRandomTrack()` (:382-437): заменить `candidateFiles.random()` на `candidateFiles.randomOrNull()` с guard - если null (единственный файл в плейлисте только что попал в failedFiles), очистить `failedFiles`, вызвать `onMusicErrorListener` с существующей строкой `R.string.no_music_files`, `return` без падения. Перенести `currentTrackName = trackName` (:419) внутрь `withContext(Dispatchers.Main)` (та же IO-до-хопа проблема, что и в п.2, тот же файл/функция - чинится тем же приёмом).
4. `startHealthCheck()` (:484-529): обернуть весь per-tick блок (после `delay(60_000)`) в один `withContext(Dispatchers.Main) { }`, убрать `continue` (заменить на if/else), так как `isPlaying`/`musicPlayer` больше не читаются/не пишутся на IO.

Раздельная синхронизация (Mutex) не нужна - после шагов 1-4 каждое поле имеет ровно одного "писателя по потоку" (Main), гонка устраняется структурно.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (thread confinement), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard).
- `.\a.ps1 d` - debug-сборка проходит.
- Статический ре-обзор изменённого файла: каждое из 5 полей (`isPlaying`, `availableAudioFiles`, `currentTrackPath`, `currentTrackName`, `failedFiles`, `loadPlaylistJob`) читается/пишется только внутри Main-контекста (прямой вызов из Main или `withContext(Dispatchers.Main)`/`scope.launch(Dispatchers.Main)`).
- Ручная device-проверка (BlockNeedUserTest): плейлист фоновой музыки из ОДНОГО аудиофайла, спровоцировать IO-ошибку воспроизведения (например, удалить/переместить файл во время слайдшоу) - ожидание: приложение не падает, музыка останавливается с уведомлением вместо краша; многотрековый плейлист продолжает штатно авто-переключаться при ошибках.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: onPlayerError IO-branch + recovery-branch both hop `scope.launch(Dispatchers.Main)` (BackgroundMusicManager.kt:123,140) - PASS x2. `loadAndSetPlaylist()` field writes (`availableAudioFiles`/`currentTrackPath`/`currentTrackName`) moved inside final `withContext(Dispatchers.Main)` (:354-356) - PASS. `skipToNextRandomTrack()` uses `candidateFiles.randomOrNull()` with empty-guard (clear failedFiles, stop player, `onMusicErrorListener`, return - :419-429) and `currentTrackName` write moved inside `withContext(Dispatchers.Main)` (:443) - PASS x2. `startHealthCheck()` per-tick body fully wrapped in `withContext(Dispatchers.Main)` (:513-546) - PASS. Grep of all 5 fields (`isPlaying`/`availableAudioFiles`/`currentTrackPath`/`currentTrackName`/`failedFiles`/`loadPlaylistJob` assignments) confirms every write site is reached only after a Main-thread guard (Player.Listener callback, `updateState()`/`skipToNextRandomTrack()`/`release()` self-redirect, or explicit `withContext(Dispatchers.Main)`) - PASS x2 (no off-Main writer found). Dev log entry present (S0853 @ 15:14:08) - PASS. FEATURES trilingual - EXEMPT (internal crash/race fix, no user-visible capability change).

### Manual / on-device

- [ ] Single-file background-music playlist + forced IO error (delete/move file mid-slideshow) - expect graceful stop + toast, no crash; multi-track playlist keeps auto-advancing through failures.

