# Спецификация (compact bugfix): S0863 - AudioEmptyStateController - осиротевший фоновый MediaPlayer + start() в Error-состоянии

**Ticket:** S0863
**Status:** Archived
**Priority:** 75
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED (2026-07-02, dedicated skeptic); finding 2 ESCALATED to P0. (1) P1: show() (:74-95) -> hideAll() (:168-173) flips isVisible only; none of the non-video show* paths call releaseMediaPlayer() (only hide() :102, release() :159, re-entrant startMediaPlayer() :252 do). Mode switch away from video leaves the looping muted player (start :278, isLooping :266) decoding behind GONE TextureView (View visibility does not tear down SurfaceTexture); onPause (:130-139) and onIsPlayingChanged (:110-127) gate on currentMode == MODE_VISUALIZATION/MODE_GIF_LOOP which no longer matches -> orphan unreachable by backgrounding. Trigger: change audioEmptyStateMode in Settings mid-track (AudioSettingsFragment.kt:174), next track's show(<non-video>) lands over the live player with no hide() between (AudioCoverArtLoader call sites re-read the setting per track). (2) P0: setOnErrorListener body (:284-302) hides the view, showStaticNote(), returns true - never releaseMediaPlayer(), isPrepared stays true (reset only at :358); start sites are UNGUARDED - onIsPlayingChanged :120 and onResume :150 'if (isPrepared) mediaPlayer?.start()' - while pause sites :122-124/:135-137 ARE try/catch-wrapped (asymmetry). PlayerActivityLifecycleBridge.kt:80 -> PlayerActivity.kt:989 calls onResume() unwrapped -> IllegalStateException on Error-state player propagates uncaught on main = crash. Fix shape: release (or reset+isPrepared=false) in the error listener; symmetric try/catch or state-machine guard on start sites; stop video player on mode switch in show().

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:87** - show() with a non-video mode never stops a live video MediaPlayer - orphaned muted player keeps looping and decoding behind a GONE TextureView, unreachable even by onPause
  - Evidence: show() (lines 85-94) does 'currentMode = mode; hideAll(); when (mode) { MODE_NONE -> showStaticNote() .. }'. hideAll() (lines 168-173) only toggles visibility, and none of showStaticNote()/showPulseNote()/showBars()/showWaves() call releaseMediaPlayer() - only hide() (line 102), release() (line 159) and a video re-entry via startMediaPlayer() (line 252) do. So show(video) followed by show(non-video) leaves mediaPlayer non-null, prepared, isLooping=true and started (onPrepared line 278 'mp.start()'), rendering into a now-GONE TextureView. Worse, once currentMode is non-video the player becomes unpausable: onPause() line 134 gates on 'if (currentMode == MODE_VISUALIZATION || currentMode == MODE_GIF_LOOP)' and onIsPlayingChanged's when(currentMode) (line 110) no longer matches the video branch - so backgrounding the app does NOT pause the orphan; it decodes video continuously until a cover-art track triggers hide() or the activity is destroyed. Runtime path: audioEmptyStateMode is a live user setting written at runtime (AudioSettingsFragment.kt:174 'viewModel.updateSettings(current.copy(audioEmptyStateMode = selectedKey))') and AudioCoverArtLoader re-reads it fresh on every track (AudioCoverArtLoader.kt:121, 178) and calls show(mode). A PlayerActivity stays alive while settings change e.g. via the S0184 tear-off window (PlayerActivity.kt:822-835 launches a duplicate player with FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_MULTIPLE_TASK 'while keeping the source player alive' - the other window can navigate to Settings). Next track -> show(BARS) over the live VISUALIZATION player -> hardware video decoder loops invisibly (muted, battery/CPU + a contended codec instance) for the rest of the session, including while backgrounded.
  - Fix hint: At the top of show(): if currentMode.isVideoMode() && !mode.isVideoMode() -> releaseMediaPlayer() and clear videoActive (mirror what hide() does) before switching branches.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt:284** - MediaPlayer error handler releases nothing and leaves isPrepared=true; later unguarded start() on the Error-state player throws uncaught IllegalStateException
  - Evidence: setOnErrorListener (lines 284-302) ends with only: 'videoActive = false / videoView.isVisible = false / showStaticNote() / true' - it never calls releaseMediaPlayer() and never resets isPrepared, so after an error the dead player, its Surface (currentSurface) and isPrepared=true are all retained (isPrepared is cleared only in releaseMediaPlayer(), line 358). Both start paths are unguarded: onIsPlayingChanged line 120 'if (isPrepared) mediaPlayer?.start()' and onResume line 150 'if (isPrepared) mediaPlayer?.start()' - while the pause paths deliberately catch IllegalStateException (lines 122-124, 135-137). Runtime path: audio track with VISUALIZATION background is prepared and looping (isPrepared=true) -> an async error arrives mid-playback (MediaPlayer.MEDIA_ERROR_SERVER_DIED on mediaserver death, or an IO/decode error on the looping clip - the handler explicitly maps these, lines 286-296) -> player enters Error state per the MediaPlayer state machine -> user later toggles play from the media notification (service Player.Listener pipes it in via PlayerManagerInitializer.kt:773) or returns to the activity (PlayerActivityLifecycleBridge.kt:80 -> onResume) -> mediaPlayer.start() on an Error-state player throws IllegalStateException on the main thread -> crash. Secondary cost: until that point the errored player + surface stay allocated behind the static-note fallback (contract item 7: failure path does not release).
  - Fix hint: In onError call releaseMediaPlayer() (which also resets isPrepared) before hiding the view; wrap both start() call sites in the same try/catch(IllegalStateException) guard already used for pause().

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

AudioEmptyStateController - осиротевший фоновый MediaPlayer + start() в Error-состоянии. Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

Два независимых дефекта в `AudioEmptyStateController`, оба - неполный release-контракт вокруг `MediaPlayer` в VISUALIZATION/GIF_LOOP режиме:

1. `show(mode)` переключает `currentMode` и вызывает `hideAll()`, который только скрывает View (`isVisible = false`). Ни один из non-video веток (`showStaticNote`/`showPulseNote`/`showBars`/`showWaves`) не вызывает `releaseMediaPlayer()` - только `hide()`, `release()` и повторный вход в `startMediaPlayer()` это делают. После переключения с video на non-video режим `mediaPlayer` остаётся живым, `isLooping=true`, декодирует за невидимым `TextureView`. Хуже - `onPause()`/`onIsPlayingChanged()` матчатся по `currentMode == MODE_VISUALIZATION`, который уже сменился, поэтому осиротевший плеер не сворачивается даже при уходе приложения в фон.
2. `setOnErrorListener` при ошибке скрывает view и показывает static note, но никогда не вызывает `releaseMediaPlayer()` и не сбрасывает `isPrepared` (сбрасывается только внутри `releaseMediaPlayer()`). По официальному контракту `MediaPlayer.OnErrorListener` объект переходит в Error-состояние и требует `reset()` перед повторным использованием. Оба места `start()` (`onIsPlayingChanged`:120, `onResume`:150) были без try/catch, в отличие от симметричных `pause()`-веток - вызов `start()` на Error-плеере кидает необработанный `IllegalStateException` на главном потоке.

---

## 3. Исправление

1. `show(mode)`: в начале функции, если `currentMode.isVideoMode() && !mode.isVideoMode()` - вызвать `releaseMediaPlayer()` и `videoActive = false` (по аналогии с тем, что уже делает `hide()`), ДО присвоения `currentMode = mode` и `hideAll()`.
2. `setOnErrorListener`: добавлен вызов `releaseMediaPlayer()` (сбрасывает `isPrepared`, вызывает `reset()`+`release()` на самом MediaPlayer - штатная реакция по контракту `OnErrorListener`) перед `videoActive = false; videoView.isVisible = false; showStaticNote()`.
3. `onIsPlayingChanged()` и `onResume()`: оба вызова `mediaPlayer?.start()` обёрнуты в `try/catch (IllegalStateException)` симметрично уже существующим `pause()`-веткам; catch логирует через `Timber.w(e, ..)` и вызывает `releaseMediaPlayer()` как safe-recovery (defense-in-depth поверх пункта 2 - `isPrepared` теперь и так сбрасывается в `onError`, но try/catch закрывает любой другой путь попадания в Error-состояние).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none
- Внутренняя механика (release-контракт MediaPlayer), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard).
- `.\a.ps1 d` - debug-сборка проходит.
- Статический ре-обзор: `show()` освобождает плеер при уходе с video-режима; `onError` вызывает `releaseMediaPlayer()`; оба сайта `start()` обёрнуты в try/catch симметрично `pause()`.
- Ручная device-проверка (BlockNeedUserTest, опционально): переключить audioEmptyStateMode с VISUALIZATION на любой другой режим mid-track - ожидание: видео-плеер освобождается (не декодирует в фоне); спровоцировать ошибку MediaPlayer (например, повреждённый background-клип) - ожидание: приложение не падает при следующем play/resume.

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `show()` releases video player + clears `videoActive` when leaving video mode, before `currentMode` reassignment (AudioEmptyStateController.kt:90-91) - PASS. `setOnErrorListener` calls `releaseMediaPlayer()` (resets `isPrepared`) + `videoActive = false` before hiding (:326-327) - PASS. `onIsPlayingChanged()` start() site wrapped in try/catch(IllegalStateException) with `releaseMediaPlayer()` recovery (:133-135) - PASS. `onResume()` start() site symmetric guard (:171-173) - PASS. Exception `e` referenced in both new catches via `Timber.w(e, ..)` (no SwallowedException) - PASS. `standard debug` Kotlin compile - PASS. detekt scoped gate - PASS. Dev log entry present (S0863 @ 16:12:52) - PASS. FEATURES trilingual - EXEMPT (internal release-contract fix, no user-visible capability).

### Manual / on-device

- [ ] Switch audioEmptyStateMode away from VISUALIZATION mid-track - expect the video player releases (no background decode); force a MediaPlayer error (corrupt background clip) - expect no crash on next play/resume.

