# Phase 03 - Streams Background Gate

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Gate inline stream audio on the background-playback setting: when ON keep the current `AudioServiceController` background path; when OFF play through a local in-app `ExoPlayer` that does not start a foreground service and stops when the screen is left or backgrounded - a faithful mirror of local audio.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`StreamsViewModel.settings` available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 300 |

> The `enablePersistentAudioPlayback && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` capability read in `src/main` mirrors the existing local-audio gate in `PlayerMediaLoaderManager` (a capability flag, not an `IS_<flavor>` guard - allowed by CLAUDE.md Rule 14).

---

## Steps

### Step 03.1 - Add a background-service flag to play()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `fun play(source: StreamSourceEntity)` to `fun play(source: StreamSourceEntity, useBackgroundService: Boolean)`. Keep the existing `AudioServiceController.playAudioWithMetadata(..)` path for `useBackgroundService == true`. Store the active mode so `stop()`/`release()`/`stopPlaybackKeepingController()` know which player to tear down. Do not change the ICY-metadata listener contract (`onMetadata` -> `nowPlaying`) or the error contract (`onPlayerError` -> `stop()` + `onError`).

**Verification:**

- `Grep` - `fun play(source: StreamSourceEntity, useBackgroundService: Boolean)` present.
- `Grep` - `audioController.playAudioWithMetadata(` still present (ON path intact).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Files: StreamInlineAudioManager.kt. play() takes useBackgroundService; usingService + localPlayer track active mode.

---

### Step 03.2 - Add the local in-app ExoPlayer path for the OFF case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> When `useBackgroundService == false`, play through a local `androidx.media3.exoplayer.ExoPlayer` built from `miniControl.context` (default extractors so `IcyInfo` metadata still arrives), instead of `AudioServiceController`. Set `MediaItem.fromUri(source.url)`, `prepare()`, `playWhenReady = true`; attach the same `playerListener` (ICY title + error). Assign it to the existing `player` field so the mini-control state stays consistent. In `stopPlaybackKeepingController()` and `release()`, `release()` the local ExoPlayer (an in-app ExoPlayer must be released, not just `stop()`-ed) and null it out; only call `audioController.release()` when the service path was used. The local player must never be handed to `AudioPlaybackService`, so no foreground service and no media notification appear when background playback is OFF.

**Verification:**

- `Grep` - `ExoPlayer.Builder(` present in `StreamInlineAudioManager.kt`.
- `Grep` - `IcyInfo` still referenced (metadata path preserved).
- `Grep -n "Log\.d\("` on the file returns zero hits.
- `Grep` - no `GlobalScope` introduced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Files: StreamInlineAudioManager.kt. OFF path builds an in-app ExoPlayer with Icy-MetaData header; released (not stopped) on teardown.

---

### Step 03.3 - Pass the flag from StreamsActivity and stop local audio on background

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `onPlay`, compute `useBackgroundService = viewModel.settings.value.enablePersistentAudioPlayback && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` and pass it to `inlineAudio.play(source, useBackgroundService)`. Apply the same value to the retry call in `showStreamUnavailable`. Add `override fun onStop()` that, when the active stream was started in local (non-service) mode, stops inline audio so OFF-mode playback does not survive the screen going to background - mirroring local-audio's no-background contract. When the service path is active (ON), `onStop` must not stop playback (the service owns background continuation).

**Verification:**

- `Grep` - `inlineAudio.play(source, useBackgroundService)` (or equivalent flag-passing call) present.
- `Grep` - `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` referenced in `StreamsActivity.kt`.
- `Grep` - `override fun onStop()` present.
- Compile: `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Files: StreamsActivity.kt. onPlay/retry pass the gate; onStop stops OFF-mode audio on background. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the phase.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if the manager's public signature changed.

---

## Handoff Notes to Next Phase

`StreamInlineAudioManager` now has two playback modes and exposes `playingId`. Phase 04 uses `playingId != null` plus the ON/OFF mode to decide whether the streams-exit dialog is relevant, and routes the exit decision through the Phase 01 reuse units.

---

## Rollback Plan

Revert the phase commit(s); `play()` reverts to the single service path. No persisted data or schema changed.
