# Спецификация (compact bugfix): S0874 - StreamInlineAudioManager - stale play()-callback после stop() (осиротевший service player)

**Ticket:** S0874
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-02
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1 (2026-07-02, dedicated skeptic; orphan is real but BOUNDED - explicitly noted). Mechanics: play() (:115-129) runs stopPlaybackKeepingController() sync, then async playAudioWithMetadata callback assigns player (:126-129); AudioServiceController connect callback unconditionally setMediaItem+prepare+play+onPlayerReady (AudioServiceController.kt:148-152) with zero staleness token. Trigger: double-tap the same AUDIO row inside the connect window (StreamsActivity.onPlay :441-450 -> stop() :448) - player still null, stop clears currentSource/usingService/miniControl but cannot cancel the in-flight future; late callback re-populates player with all ownership flags cleared -> isServiceAudioActive=false -> AudioExitBehaviorResolver returns FINISH (never stops it), onStop (:731-735) by design skips service mode -> audibly playing service player with NO reachable UI stop. Bounded by: next play() (stopPlaybackKeepingController :116) or Activity onDestroy -> release() -> player?.stop() (:183); sub-case connect-after-destroy is neutralized by MediaController.releaseFuture cancelling the future. Fix shape: request-token/staleness guard in the play callback (compare currentSource captured at request time; if stale -> stop the just-started player immediately).

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt:126** - Service-mode play() callback has no staleness guard: playback starts after stop(), leaving an orphaned playing service player with all inline-UI state cleared
  - Evidence: play() service branch: `audioController.playAudioWithMetadata(Uri.parse(source.url), source.title) { startedPlayer -> player = startedPlayer; startedPlayer.addListener(playerListener) }` (lines 126-129). The controller connect is asynchronous (`MediaController.Builder(context, sessionToken).buildAsync()` + `future.addListener(...)`, AudioServiceController.kt:63-77) and the connect callback unconditionally starts playback: `player.setMediaItem(mediaItem) ... player.prepare(); player.play(); onPlayerReady(player)` (AudioServiceController.kt:148-152). Neither stop() nor a new play() cancels the pending connect, and the onPlayerReady lambda never checks that its source is still current. Concrete path (double-tap toggle, service cold): tap an AUDIO row -> play(bg=true) sets currentSource/playingId synchronously and starts the async connect; tap the same row again inside the connect window -> StreamsActivity.onPlay sees `inlineAudio.playingId == source.id` and calls stop() -> stopPlaybackKeepingController() runs with `player == null` so `player?.stop()` is a no-op (lines 174-190), state cleared (`currentSource = null`, `usingService = false`), mini-control hidden. The connect then completes -> the foreground service starts audible playback AFTER the user stopped it, and the manager re-enters `player = startedPlayer; startedPlayer.addListener(playerListener)` with `playingId == null`, `isServiceAudioActive == false`, `miniControl.isVisible == false`: an actively-streaming player with no visible stop control and no state flag claiming ownership (isLocalPlaybackActive and isServiceAudioActive are both false, so host onStop does nothing). It keeps playing until the user starts another stream or the Activity is destroyed (release() -> player?.stop()). Violates contract items 1 (orphan creation path) and 7 (stop/early-exit path does not cover the pending async start). Unowned actively-playing heavy resource = P1.
  - Fix hint: Guard the callback with a generation/source token: capture `source` (or a monotonically increasing request id) at play() and inside onPlayerReady bail out - `if (currentSource?.id != source.id) { startedPlayer.stop(); return }` - and/or add an AudioServiceController.cancelPendingConnect() invoked from stopPlaybackKeepingController().

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

StreamInlineAudioManager - stale play()-callback после stop() (осиротевший service player). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

- `play()` (service-ветка) синхронно вызывает `stopPlaybackKeepingController()`, затем регистрирует async connect-callback. Сам connect (`AudioServiceController.connect` -> `MediaController.buildAsync` future) безусловно делает `setMediaItem/prepare/play/onPlayerReady` без токена свежести.
- `onPlayerReady` безусловно выполняет `player = startedPlayer; addListener(..)`.
- Триггер (double-tap той же AUDIO-строки внутри connect-окна): `StreamsActivity.onPlay` видит `playingId == source.id` -> `stop()`. `player` ещё `null`, поэтому `player?.stop()` - no-op; чистятся `currentSource`/`usingService`/mini-control, но in-flight future не отменяется.
- Поздний callback переустанавливает `player` со всеми флагами владения сброшенными: `isServiceAudioActive=false` -> `AudioExitBehaviorResolver` -> host `onStop` по дизайну пропускает service-режим -> звучащий service-player без достижимого UI-стопа (P1, unowned playing heavy resource).
- Bounded: следующий `play()` (`stopPlaybackKeepingController`) или Activity `onDestroy` -> `release()` -> `player?.stop()` его останавливают.

---

## 3. Исправление

Single edit in `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`, service branch of `play()`.

1. At the top of the `playAudioWithMetadata` callback, before assigning `player`, bail if the request is stale: `if (currentSource?.id != source.id) { startedPlayer.stop(); return@playAudioWithMetadata }`.
   - `source` is captured at request time (lambda closure); `currentSource` is nulled by `stop()` and reassigned by a newer `play()`, so a superseded request is detected.
   - Verification: after a `stop()` inside the connect window, the late callback stops the just-started shared controller instead of leaving an orphaned playing player.

Note: `AudioServiceController` shares one `MediaController`, so the guard stops the shared controller only when the request is superseded. In the normal directExecutor registration order a following `play(B)` callback re-establishes playback after the stale `play(A)` callback stops it. The pathological unspecified-order case (B before A) is pre-existing non-determinism and out of scope. No `AudioServiceController` API change needed.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard Kotlin compile) - PASS.
- Static gates `.\a.ps1 fg` (neuroslop, pm-flags, listener, flavor, ticket-log) - PASS.
- Optional on-device (deferred, not a merge gate): Background playback ON, double-tap the same AUDIO stream row within the service connect window; confirm no audio keeps playing after the mini-control hides.

---

## Last Audit

**Date:** 2026-07-02
**Verdict:** Verified
**Method:** static - `compileStandardDebugKotlin` + scoped gates + concurrency inspection (CODE_AUDIT_PROTOCOL callback/async trigger). On-device double-tap regression optional, not a merge gate.

- Fix present in `StreamInlineAudioManager.play()`: the `playAudioWithMetadata` callback returns early (stopping `startedPlayer`) when `currentSource?.id != source.id`.
- Reasoning:
  - Confirmed scenario (double-tap same source -> `stop()` sets `currentSource=null`): callback is stale -> shared controller stopped, orphan eliminated.
  - `play(A)` then `play(B)`: in registration order the stale `A` callback stops, then `B` re-establishes playback -> plays `B`.
  - Guard uses only existing state (`currentSource`, captured `source`); no new field, no `AudioServiceController` change.
- Shared-controller correctness: `startedPlayer` is the single shared `MediaController`; `stop()` keeps it connected for the next `play()` (matches `stopPlaybackKeepingController` contract). No leak.

