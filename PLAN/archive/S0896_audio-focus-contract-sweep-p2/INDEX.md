# Tactical Plan: S0896 - audio-focus-contract-sweep-p2

**Strategic spec:** [`../S0896_audio-focus-contract-sweep-p2.md`](../S0896_audio-focus-contract-sweep-p2.md)
**Research inputs:** none (research folded into strategic §0 findings + live-code re-verification below)
**Feature:** Audio focus contract - hosts that never request or never abandon focus
**Tier:** 3 - Moderate
**Priority:** 40
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-03

> Scope: tactical, English, developer handoff. Every finding was re-checked against live code before implementing.

---

## Pre-flight re-verification (skeptic pass over the 8 static findings)

1. `BackgroundMusicManager.kt:32` (`@Singleton` annotation) - CONFIRMED. `release()` unconditionally nulls `musicPlayer` and both listener fields with no ownership tracking. `initialize()`/`release()` are each called exactly once per `PlayerActivity` instance (`ensureAudioBackgroundManagersConfigured()` gate + `PlayerLifecycleManager.releaseResources()`), so a plain acquire/release reference count is sufficient - no other call sites exist.
2. `BackgroundMusicManager.kt:97` - CONFIRMED. `ExoPlayer.Builder(context).build()` has no `.setAudioAttributes(..)` call, unlike the canonical pattern in `PlayerSetupHelper.kt:39-48` (`AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()` + `.setAudioAttributes(audioAttributes, true)`).
3. `BackgroundMusicManager.kt:372` - CONFIRMED. `catch (e: Exception)` in `loadAndSetPlaylist()`'s launched job catches `CancellationException` (a subtype) and logs it as `Timber.e`. Same shape found in `skipToNextRandomTrack()`'s launched job (~line 456) guarding the same `loadPlaylistJob` field - fixed both, since both are cancelled via the identical `loadPlaylistJob?.cancel()` call sites and share the identical symptom. Project convention confirmed via grep (`ArchiveFilesUseCase.kt`, `DownloadNetworkFileUseCase.kt`, etc.): `catch (e: CancellationException) { throw e }` before the broad catch.
4. `BrowseInlineAudioManager.kt:86` - CONFIRMED. No `AudioManager`/`AudioFocusRequest` usage anywhere in the file; `android.media.MediaPlayer` has no ExoPlayer-style `handleAudioFocus`. Reused the existing `AudioFocusManager` helper (`ui/player/helpers/AudioFocusManager.kt`, already used by `StandaloneViewManager`) instead of hand-rolling a second manual implementation - it already encodes the project's ADR-2 contract (pause on transient loss, stop on permanent loss, no auto-resume on regain) and the API23-26 fork.
5. `BrowseMicRecordingManager.kt:90` - CONFIRMED. `audioFocusListener` field is set before the grant result is known; the `if (!focusGranted)` branch never clears it. `stopRecording()`'s `pendingTempFile == null && pendingResource == null` guard returns before reaching `abandonAudioFocus()` - `release()`'s guard (`isRecorderStarted || mediaRecorder != null || pendingTempFile != null`) has the same gap. Fix: call the existing `abandonAudioFocus()` inside the denial branch (reuses existing cleanup method instead of duplicating a manual null-out).
6. `MainVoiceCaptureManager.kt:212` - CONFIRMED. Identical shape to #5 in `actuallyStart()`'s `if (!requestAudioFocus())` branch; `release()`'s guard (`isRecorderStarted || pendingTempFile != null || recordingDialog != null`) has the same gap since none of those three are set yet at denial time. Same fix.
7. `StreamInlineAudioManager.kt:136` - CONFIRMED. The OFF-mode local `ExoPlayer.Builder` has no `.setAudioAttributes(..)` and no `.setHandleAudioBecomingNoisy(true)`, unlike the canonical pattern and unlike its own service-mode branch (`audioController.playAudioWithMetadata`, which goes through `AudioPlaybackService`'s properly-configured player).
8. `WearAppModule.kt:68` - CONFIRMED. `provideExoPlayer()` only sets `setHandleAudioBecomingNoisy(true)`, no `setAudioAttributes(..)`. Wear-scoped; only this one function touched, rest of `wear/` is S0902's scope.

---

## Phase Overview

- Phase 01 - `BackgroundMusicManager.kt`: multi-window ownership ref-count, audio attributes, CancellationException rethrow (findings 1-3). Self-contained, single file.
- Phase 02 - `BrowseInlineAudioManager.kt`: manual audio focus via `AudioFocusManager` (finding 4). Self-contained, single file.
- Phase 03 - `BrowseMicRecordingManager.kt` + `MainVoiceCaptureManager.kt`: focus-denial cleanup (findings 5-6). Independent, mechanical, identical fix pattern in both files.
- Phase 04 - `StreamInlineAudioManager.kt` + `WearAppModule.kt`: missing audio attributes (findings 7-8). Independent, mechanical, config-only.

All four phases touch disjoint file sets - no cross-phase symbol dependency, order is arbitrary (chosen by finding order in the strategic spec).

---

## Phase 01 - BackgroundMusicManager.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BackgroundMusicManager.kt`

**Steps:**

1. Add `private var activeHostCount = 0` field with a WHY comment (multi-window: `@Singleton` backs every `PlayerActivity` window). Increment at the top of `initializeInternal()` (always reached via `initialize()`'s Main-thread hop, so mutation stays Main-confined like the rest of the class). Decrement in `release()` after its own Main-thread hop, `coerceAtLeast(0)`; early-return with a `Timber.d` (and, this ticket being `BlockNeedUserTest`, an `S0896:` probe) when the count is still `> 0` - skip the player/listener teardown entirely.
2. In `initializeInternal()`, build `AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()` and chain `.setAudioAttributes(audioAttributes, true)` onto the `ExoPlayer.Builder` (matches `PlayerSetupHelper.kt`'s canonical pattern exactly).
3. Add `import kotlinx.coroutines.CancellationException`. In both `loadAndSetPlaylist()`'s and `skipToNextRandomTrack()`'s launched-job catch blocks, add `catch (e: CancellationException) { throw e }` immediately before the existing `catch (e: Exception)`.

**Verification:** `.\a.ps1 fc` PASS. Grep `activeHostCount` (3 occurrences: field + increment + decrement). Grep `setAudioAttributes` (1 occurrence). Grep `catch (e: CancellationException)` (2 occurrences).

**Status:** Done.

---

## Phase 02 - BrowseInlineAudioManager.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt`

**Steps:**

1. Import `com.sza.fastmediasorter.ui.player.helpers.AudioFocusManager`. Add a field `audioFocusManager = AudioFocusManager(context) { isPermanent -> .. }`: permanent loss calls `inlineStop()`; transient loss pauses (`player?.pause()` + state `copy(status = PAUSED)`).
2. `inlineStart()`: request focus **synchronously on the caller's thread, before launching the `Dispatchers.IO` coroutine** (not inside it) - ordering must match `inlineStop()`'s synchronous `releaseFocus()` so a rapid track-switch (stop+start pair) cannot race a deferred in-coroutine request against a newer call's request/release on the same shared `AudioFocusManager` instance. Gate on `audioFocusManager.hasFocus`; deny -> return (no player built). `inlineStop()` already reset `_inlinePlayerState` before every `inlineStart()` call site (`inlinePlayToggle`, `inlinePlayNext`), so no extra state reset needed on denial.
3. In the coroutine's `localPath == null` branch and the outer `catch (e: Exception)` branch, call `audioFocusManager.releaseFocus()` **only if `myGeneration == playGeneration`** - if superseded, a newer `inlineStart()` call already holds its own valid focus grant on the same shared instance and releasing here would wrongly abandon it.
4. The generation-supersede branch (`myGeneration != playGeneration`, existing S0862 guard) does **not** call `releaseFocus()` - same reasoning as #3.
5. `inlineStop()`: call `audioFocusManager.releaseFocus()` (harmless no-op if focus was never held - both the API26+ and legacy `abandonAudioFocus` paths in `AudioFocusManager` guard on their own state).

**Verification:** `.\a.ps1 fc` PASS. Grep `AudioFocusManager` present. Grep `myGeneration == playGeneration` in the two guarded release sites.

**Status:** Done.

**Known residual (not fixed here, out of scope):** `_inlinePlayerState.value = InlinePlayerState()` on the `localPath == null` and `catch (e: Exception)` paths is unconditional (no `myGeneration` guard), pre-existing before this ticket - a stale coroutine's failure path can theoretically clobber a newer generation's in-progress state. Unrelated to the audio-focus contract; parked as **S0909**.

---

## Phase 03 - BrowseMicRecordingManager.kt + MainVoiceCaptureManager.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt`

**Steps:**

1. `BrowseMicRecordingManager.startRecording()`: call `abandonAudioFocus()` as the first statement inside `if (!focusGranted) { .. }`.
2. `MainVoiceCaptureManager.actuallyStart()`: call `abandonAudioFocus()` as the first statement inside `if (!requestAudioFocus()) { .. }`.

Both reuse the file's own existing private `abandonAudioFocus()` method - no new code, just an added call site. Both methods already null-check defensively (`audioFocusListener ?: return`), so calling them when nothing was truly granted is a safe no-op that only clears the dangling field.

**Verification:** `.\a.ps1 fc` PASS. Grep `abandonAudioFocus()` inside each denial branch.

**Status:** Done.

---

## Phase 04 - StreamInlineAudioManager.kt + WearAppModule.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`

**Steps:**

1. `StreamInlineAudioManager.play()`'s OFF-mode branch: import `androidx.media3.common.AudioAttributes` + `androidx.media3.common.C`, build the same canonical `audioAttributes` as Phase 01, chain `.setAudioAttributes(audioAttributes, true)` and `.setHandleAudioBecomingNoisy(true)` onto the local `ExoPlayer.Builder`.
2. `WearAppModule.provideExoPlayer()`: same two calls, fully-qualified (`androidx.media3.common.AudioAttributes`/`C`) to match this file's existing no-import, fully-qualified-reference style for Media3 types.

**Verification:** app_v2 - `.\a.ps1 fc` PASS. wear - `.\gradlew.bat :wear:compileDebugKotlin` PASS. Grep `setAudioAttributes` in both files.

**Status:** Done.

---

## Completion Gate

- All four phases Done.
- `docs/FEATURES*.md` - skip (internal contract fix, no user-visible feature copy).
- `dev/CHANGELOG.md` - one batched entry via `add_to_dev_log.ps1`.
- `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/wear.jsonl` regenerated.
- Multi-window ownership and audio-ducking behavior are not provable by build alone - `BlockNeedUserTest` at spec close, `S0896:` probes inserted at the 3 highest-value observation points (multi-window release guard, inline-audio focus grant/deny, inline-audio focus-loss reaction).

---

## Rollback Plan

Low-risk: revert the 6 touched files. No Room schema, no Hilt graph, no new classes (reuses existing `AudioFocusManager`). Behavior changes (ducking, multi-window survival) are additive - previously-silent audio-focus contract violations become correct; no existing passing behavior is removed.
