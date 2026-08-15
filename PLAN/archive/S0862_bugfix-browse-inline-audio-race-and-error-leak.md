# Спецификация (compact bugfix): S0862 - BrowseInlineAudioManager - гонка публикации player + утечка на error-пути

**Ticket:** S0862
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, both findings (2026-07-02, dedicated skeptic). (1) player is a plain var (:49) assigned in scope.launch(Dispatchers.IO) (:144, scope = viewModelScope); creation :160-175, assignment :176, start() :181 use BLOCKING prepare() (not prepareAsync - contrast AudioEmptyStateController.kt:307, PlaybackHealthHelper.kt:163); zero isActive/ensureActive in the file -> past the last suspension point the coroutine is uncancellable. inlineStop() (:86-94) runs on main from BrowseViewModel.onCleared():668 (BEFORE super.onCleared() :672 - scope not yet cancelled) and BrowseActivity :403/:418/:639; independent stop triggers confirmed: BrowseFileOpenManager.openFile:92, BrowseManagerInitializer :181/:202/:289, inlinePlayToggle :78-81. Trigger: SMB audio with multi-second download + any stop/switch during prepare window -> playing MediaPlayer with no live reference, audio audibly continues. (2) Inner try (:161-172) wraps only setDataSource; prepare() (:173) is outside; outer catch (:185-188) logs and resets state only - exactly ONE .release() exists in the file (:89, inlineStop on the field) and player=newPlayer (:176) never ran before the throw -> just-built MediaPlayer provably unreachable+unreleased; broad catch also swallows CancellationException; truncated SMB cache file (download deletes cache only on explicit SmbResult.Error :309, not on interrupted write) makes prepare() throw realistically.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:176** - Unsynchronized cross-thread 'player' publication: in-flight inlineStart can start an orphaned, never-released MediaPlayer after inlineStop/onCleared (contract items 1, 2, 7)
  - Evidence: Line 49: 'private var player: MediaPlayer? = null' - plain var, no @Volatile/Mutex. inlineStart runs the whole create-assign-start sequence on an IO thread: line 144 'scope.launch(Dispatchers.IO) {', lines 160-181 'val newPlayer = MediaPlayer().apply { .. prepare() .. }' then 'player = newPlayer' (176) and 'newPlayer.start()' (181). There is no suspension point between the coroutine's last resume (for local files: none at all after launch; for SMB: return from downloadSmbAudioToCache) and start(), so cooperative cancellation of viewModelScope is never observed in that window. inlineStop (lines 86-94, 'player?.let { .. it.release() }; player = null') runs on the main thread (BrowseActivity.onStop:639 'viewModel.inlineStop()', BrowseViewModel.onCleared:668, back-nav BrowseActivity:403/418). Runtime path A (screen leave): tap an SMB audio file (multi-second download) -> press Back/Home before prepare completes -> onStop + onCleared call inlineStop() which sees player==null and releases nothing -> the IO coroutine, already past its last suspension point, executes 'player = newPlayer; newPlayer.start()' on the field of the dead ViewModel -> audio keeps playing with no remaining code path that can release it (saveResumeState/prefetch launches on the cancelled scope are no-ops). Runtime path B (rapid track switch): 'else -> { inlineStop(); inlineStart(file) }' (lines 78-81) - tapping track B while track A's IO coroutine is between resolveLocalPath and line 176 produces two live MediaPlayers; both assign 'player' last-writer-wins and both call start() -> the losing instance is never released and two audio streams overlap. Not P0 only because the manager holds @ApplicationContext (BrowseViewModel.kt:60), so no Activity/View is retained.
  - Fix hint: Confine player mutation to one thread or guard with a generation token: capture an epoch/stop counter before launching inlineStart, and before 'player = newPlayer; start()' re-check it (under a Mutex or on Dispatchers.Main); if superseded or scope-inactive, release newPlayer immediately instead of assigning; also check 'isActive' after the blocking prepare().
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt:185** - Error path leaks the freshly created MediaPlayer: prepare()/setDataSource failure reaches catch that resets state but never calls release() (contract item 7)
  - Evidence: Lines 160-175: 'val newPlayer = MediaPlayer().apply { try { setDataSource(localPath) } catch (e: java.io.IOException) { .. reset(); setDataSource(context, ..) .. else { throw e } } prepare(); setOnCompletionListener { inlinePlayNext() } }'. The inner try covers only the first setDataSource; 'prepare()' (line 173), the content-URI fallback 'setDataSource(context, ..)' (line 168), and the rethrow at line 170 all propagate to the outer handler, lines 185-188: 'catch (e: Exception) { Timber.e(e, "InlinePlayer: failed to start .."); _inlinePlayerState.value = InlinePlayerState() }' - no release() of the already-constructed native MediaPlayer, and 'player' was never assigned so inlineStop() can never reach it. Concrete trigger: a truncated/corrupt inline_audio cache file (e.g. produced by an interrupted earlier download) or an unsupported codec makes prepare() throw IOException -> one native MediaPlayer (codec/AudioTrack handles) is orphaned per failed tap, held until non-deterministic finalization; repeated retries on the same broken file accumulate unreleased players. Same catch also swallows CancellationException from the download suspension (broad 'catch (e: Exception)').
  - Fix hint: Wrap creation in try/catch that calls newPlayer.release() on any failure before rethrowing/logging (or use runCatching { .. }.onFailure { newPlayer.release() }); rethrow CancellationException separately.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

BrowseInlineAudioManager - гонка публикации player + утечка на error-пути. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`BrowseInlineAudioManager` содержит два независимых дефекта release-контракта вокруг встроенного `MediaPlayer`:

1. `player` - обычный `var` без синхронизации. `inlineStart()` запускает создание+подготовку+публикацию+`start()` в `scope.launch(Dispatchers.IO)` без единой точки приостановки после `resolveLocalPath()` (для локальных файлов - вообще без приостановок), поэтому кооперативная отмена `viewModelScope` не наблюдается в окне между построением плеера и публикацией. `inlineStop()` выполняется на главном потоке (`BrowseActivity.onStop`, `BrowseViewModel.onCleared` - ДО отмены scope) и просто читает текущее значение `player`. Два конкретных сценария гонки: (A) пользователь покидает экран во время долгой SMB-загрузки - `inlineStop()` видит `player == null` и ничего не освобождает, а IO-корутина, уже пройдя точку приостановки, всё равно публикует и запускает плеер на мёртвой ViewModel; (B) быстрое переключение трека (`inlineStop(); inlineStart(file)`) - пока корутина трека A ещё строит плеер, стартует корутина трека B; обе публикуют `player` (последняя запись выигрывает), проигравший экземпляр никогда не освобождается - два одновременных аудиопотока.
2. Внутренний `try` в блоке `MediaPlayer().apply { .. }` покрывает только первый `setDataSource()`; `prepare()`, fallback-`setDataSource(context, ..)` и повторный `throw e` пробрасываются во внешний `catch (e: Exception)`, который лишь логирует и сбрасывает состояние - ни разу не вызывая `release()` на уже сконструированном нативном `MediaPlayer` (единственный `release()` во всём файле - в `inlineStop()`, на поле `player`, которое ещё не было присвоено). Тот же широкий `catch (e: Exception)` перехватывает и `CancellationException` из точки приостановки в `downloadSmbAudioToCache`, нарушая контракт структурной конкурентности.

---

## 3. Исправление

1. Добавлено поле `@Volatile private var playGeneration = 0` - инкрементируется в начале `inlineStop()` и захватывается локальной переменной `myGeneration` в `inlineStart()` СИНХРОННО на потоке вызывающего (до `scope.launch`), то есть до того, как могла бы стартовать конкурирующая загрузка.
2. Непосредственно перед `player = newPlayer` в IO-корутине добавлена проверка `if (myGeneration != playGeneration)` - если публикация устарела (сработал `inlineStop()` или другой `inlineStart()` успел инкрементировать счётчик), только что построенный `newPlayer` освобождается через `release()` и функция завершается без публикации, вместо того чтобы осиротить его или конкурировать за поле `player`.
3. Конструирование `MediaPlayer` вынесено из `.apply { .. }` в обычные вызовы на объекте `newPlayer`, обёрнутые в try/catch, который вызывает `newPlayer.release()` перед повторным `throw` при любой ошибке `setDataSource`/`prepare` - до этого фикса ни один путь ошибки не освобождал уже созданный нативный `MediaPlayer`.
4. Внешний catch-блок `inlineStart()` теперь сначала перехватывает `kotlinx.coroutines.CancellationException` и пробрасывает её дальше (не трогая состояние), и только затем общий `catch (e: Exception)` логирует и сбрасывает `_inlinePlayerState` - широкий catch больше не глушит отмену корутины.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (release-контракт + гонка публикации плеера), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: `playGeneration` инкрементируется в `inlineStop()` и захватывается перед `scope.launch` в `inlineStart()`; проверка поколения стоит непосредственно перед `player = newPlayer`; ошибки создания плеера освобождают `newPlayer` перед пробросом; `CancellationException` пробрасывается отдельно от общего `catch`.
- Ручная device-проверка (BlockNeedUserTest, опционально): открыть SMB-аудио с многосекундной загрузкой и уйти с экрана Browse (Back/Home) до завершения подготовки - ожидание: звук не продолжает играть после ухода; быстро переключить между двумя аудиофайлами подряд (двойной тап track A -> track B) - ожидание: слышен только последний трек, без наложения звука.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `@Volatile playGeneration` field present, incremented in `inlineStop()` - PASS. `inlineStart()` captures `myGeneration` before `scope.launch` - PASS. Publish-point re-check `if (myGeneration != playGeneration)` releases and returns without publishing - PASS. `buildInlineMediaPlayer()` releases `newPlayer` on any construction failure before rethrowing (extraction also keeps `inlineStart` under detekt `ThrowsCount`) - PASS. Outer `catch (CancellationException)` rethrows before the generic `catch (Exception)` resets state - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate - PASS. Dev log entry present (S0862 @ 17:21:08) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Open an SMB audio file with a multi-second download and leave the Browse screen (Back/Home) before prepare completes - expect audio does not keep playing after leaving. Rapidly switch between two audio files (double-tap track A then track B) - expect only the last track audible, no overlap.

