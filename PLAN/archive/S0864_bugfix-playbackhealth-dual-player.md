# Спецификация (compact bugfix): S0864 - PlaybackHealthHelper - fallback не освобождает ExoPlayer (двойное воспроизведение)

**Ticket:** S0864
**Status:** Archived
**Priority:** 65
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, both findings (2026-07-02, dedicated skeptic). (1) playWithMediaPlayer() (:115-127) never pauses/stops/releases manager.exoPlayer; exactly two call sites (checkPlaybackHealth :81, VideoPlayerErrorHandler.kt:169), neither stops ExoPlayer first; checkPlaybackHealth only fires while player.isPlaying==true -> ExoPlayer is guaranteed actively decoding when MediaPlayer.start() fires in onPrepared (:126) -> two live decoders until releasePlayer/onDestroy. Trigger: local .flac/.ac3/.eac3/.wv with stuck ExoPlayer decode -> 2 stuck health polls (~4s, 2000ms x2, VideoPlayerManager.kt:204-205) -> dual audio. (2) Set-then-reset ordering: isUsingMediaPlayer=true (:118) then releaseMediaPlayer() (:119) whose body (:178-189) unconditionally ends mediaPlayer=null; isUsingMediaPlayer=false - flag never re-set after the new MediaPlayer is built (:121-164) -> stays false for the whole fallback session. All routing keys off the stale flag: VideoPlaybackControlsHelper.pause/play (:111-121) else-branch touches only exoPlayer; VideoPlayerLifecycleHelper.onPause (:55-58) same - fallback MediaPlayer keeps playing audibly with app backgrounded until onDestroy (which is flag-independent and releases both - sole delayed backstop). Position-restore guard !isUsingMediaPlayer (VideoPlayerManager.kt:659) permanently true post-fallback (dead guard). Fix shape: stop/release ExoPlayer on fallback engage; move the flag set AFTER releaseMediaPlayer() (or make releaseMediaPlayer not clear it when re-entered from playWithMediaPlayer).

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt:115** - MediaPlayer fallback never stops or releases the still-playing ExoPlayer - two concurrent active players
  - Evidence: playWithMediaPlayer() (lines 115-127) does: `cancelPlaybackHealthCheck(); isUsingMediaPlayer = true; releaseMediaPlayer(); mediaPlayer = MediaPlayer().apply { .. prepareAsync() }` - it never pauses, stops, or releases manager.exoPlayer. Its health-check trigger guarantees the ExoPlayer is actively playing at that moment: checkPlaybackHealth() line 56 `if (!player.isPlaying || player.playbackState != Player.STATE_READY) { .. return }` and line 73-81 posts `playWithMediaPlayer(currentFilePath!!)` only after two stuck polls. Runtime path: play a local FLAC/AC3/EAC3/WV file whose decode is stuck emitting white noise -> after 2 polls (~4 s) the fallback MediaPlayer starts() in onPrepared while the ExoPlayer keeps decoding and outputting white noise (it also still holds audio focus via handleAudioFocus=true, which MediaPlayer never requests). Result: two live decoders and doubled audible output until the next releasePlayer()/onDestroy. Violates contract items 1 (one active player) and 7 (failure path releases too).
  - Fix hint: In playWithMediaPlayer(), stop and release (or at minimum pause+stop) the ExoPlayer before starting MediaPlayer, e.g. exoPlayer?.stop() plus releasePlayer()-style teardown of the exo instance while keeping currentFilePath state.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackHealthHelper.kt:119** - isUsingMediaPlayer set-then-reset ordering bug: releaseMediaPlayer() clears the flag, so the fallback MediaPlayer is uncontrollable and keeps playing in background
  - Evidence: Lines 118-119: `isUsingMediaPlayer = true` then `releaseMediaPlayer()`; releaseMediaPlayer() line 187-188 unconditionally ends with `mediaPlayer = null; isUsingMediaPlayer = false` and the flag is never set true again after the new MediaPlayer is created. All routing reads this flag: VideoPlaybackControlsHelper.pause() lines 111-118 `if (manager.isUsingMediaPlayer) { .. mediaPlayer?.pause() } else { manager.exoPlayer?.pause() }`, play() line 121, and the lifecycle edge VideoPlayerLifecycleHelper.onPause() lines 55-58 `wasPlayingBeforePause = manager.exoPlayer?.isPlaying == true || (manager.isUsingMediaPlayer && manager.mediaPlayer?.isPlaying == true); manager.pause()`. Runtime path: after the white-noise fallback engages, the user presses pause or backgrounds the activity (ON_PAUSE) -> pause() routes to exoPlayer only, mediaPlayer keeps playing audio with the screen off/app in background until Activity onDestroy finally releases mediaPlayerToRelease (VideoPlayerLifecycleHelper.kt:110-122). Also breaks playVideo()'s `!isUsingMediaPlayer` position-restore guard.
  - Fix hint: Reorder: call releaseMediaPlayer() first, then set isUsingMediaPlayer = true after constructing the new MediaPlayer (or make releaseMediaPlayer() not touch the flag and set it explicitly at both call sites).

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

PlaybackHealthHelper - fallback не освобождает ExoPlayer (двойное воспроизведение). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`PlaybackHealthHelper.playWithMediaPlayer()` содержит два независимых дефекта release-контракта при переключении на fallback:

1. Функция никогда не останавливает и не освобождает `manager.exoPlayer` перед запуском fallback `MediaPlayer`. Триггер срабатывает только пока `checkPlaybackHealth()` видит `player.isPlaying == true` - значит в момент вызова `playWithMediaPlayer()` ExoPlayer гарантированно ещё активно декодирует. `MediaPlayer.start()` в `onPrepared` запускается поверх него - два живых декодера одновременно выводят звук (двойное воспроизведение), пока `releasePlayer()`/`onDestroy` не освободит оба.
2. Порядок `isUsingMediaPlayer = true` (:118) затем `releaseMediaPlayer()` (:119) - `releaseMediaPlayer()` безусловно завершается `mediaPlayer = null; isUsingMediaPlayer = false` (:187-188), а флаг больше нигде не выставляется обратно в `true` после создания нового `MediaPlayer`. Весь маршрутизирующий код (`VideoPlaybackControlsHelper.pause/play`, `VideoPlayerLifecycleHelper.onPause`) читает именно этот флаг для выбора между `exoPlayer` и `mediaPlayer` - после fallback он навсегда остаётся `false`, поэтому pause/play/backgrounding продолжают адресоваться к уже освобождённому `exoPlayer` и никогда не трогают реально играющий `mediaPlayer`, который продолжает звучать в фоне вплоть до `onDestroy`.

---

## 3. Исправление

1. Вызов `releaseMediaPlayer()` перемещён в начало функции (до присвоения флага) - освобождает любой зависший от предыдущей fallback-попытки `MediaPlayer` первым, не затирая ещё не выставленный флаг.
2. Перед созданием `MediaPlayer` добавлена остановка и освобождение `exoPlayer`: `player.removeListener(playerListener); player.stop(); player.release()`, затем `exoPlayer = null` и `currentPlayerView?.player = null` - зеркалирует ExoPlayer-специфичную часть `VideoPlayerLifecycleHelper.releasePlayer()`, но НЕ трогает остальное состояние сессии (`currentFilePath`, position-save loop, `activeResourceKey`/throttle-режим), поскольку воспроизведение того же файла продолжается через fallback-движок, а не завершается.
3. `isUsingMediaPlayer = true` перенесён на момент ПОСЛЕ создания `MediaPlayer` (после `.apply { .. prepareAsync() }`) - флаг выставляется только когда fallback-плеер реально существует и ExoPlayer уже освобождён, закрывая окно, когда флаг `true`, но ни один живой плеер за ним не стоит.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (release-контракт + флаг маршрутизации плеера), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard) - PASS.
- Статический ре-обзор: `releaseMediaPlayer()` вызывается до присвоения флага; `exoPlayer` останавливается/освобождается перед созданием `MediaPlayer`; `isUsingMediaPlayer = true` выставляется после создания `MediaPlayer`, а не до.
- Ручная device-проверка (BlockNeedUserTest, опционально): открыть локальный `.flac`/`.ac3`/`.eac3`/`.wv` файл со сломанным декодированием (white noise), дождаться срабатывания fallback (~4 c, 2 застрявших опроса) - ожидание: только один звучащий плеер (MediaPlayer), pause/resume и уход приложения в фон корректно управляют именно им.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `playWithMediaPlayer()` calls `releaseMediaPlayer()` before the flag is touched (:118) - PASS. `exoPlayer` stopped, listener removed, released, and nulled before `MediaPlayer` construction (:120-130) - PASS. `isUsingMediaPlayer = true` set after `MediaPlayer` construction, not before (:177-179) - PASS. Exception path still sets `isUsingMediaPlayer = false` on failure (:172) - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate - PASS. Dev log entry present (S0864 @ 17:14:13) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Open a local .flac/.ac3/.eac3/.wv file with broken decode (white noise), wait for fallback to engage (~4s, 2 stuck polls) - expect exactly one audible player (MediaPlayer), pause/resume and backgrounding correctly control it.

