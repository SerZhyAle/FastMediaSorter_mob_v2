# Phase 01 - Media3 MIDI Backend

**Strategic spec:** [`../S0305_mid-audio-playback-support.md`](../S0305_mid-audio-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02 - MIME Routing Policy
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Add the Media3 MIDI extension and route both Activity and service ExoPlayer instances through extension-capable renderers.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or intentionally dirty with unrelated changes documented.
- [ ] Branch is a development branch, not `main`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackRenderersFactory.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` | Modified | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 35 |

---

## Steps

### Step 01.1 - Add Media3 MIDI Dependency

**Files:** `app_v2/build.gradle.kts`  
**Depends on:** start of phase

**Prompt for developer:**

> Add `androidx.media3:media3-exoplayer-midi:1.2.1` to every app flavor that supports audio playback: `standard`, `noLegal`, `legacy`, `vr`, and `lite`. Do not add it to `photos`, because `SUPPORT_AUDIO=false` there. Keep the version equal to the existing Media3 1.2.1 pin.

**Verification:**

- `Grep` - `"standardImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")` exists in `app_v2/build.gradle.kts`.
- `Grep` - `"noLegalImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")` exists in `app_v2/build.gradle.kts`.
- `Grep` - `"legacyImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")` exists in `app_v2/build.gradle.kts`.
- `Grep` - `"vrImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")` exists in `app_v2/build.gradle.kts`.
- `Grep` - `"liteImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")` exists in `app_v2/build.gradle.kts`.
- `Grep` - `photosImplementation"("androidx.media3:media3-exoplayer-midi` returns zero matches in `app_v2/build.gradle.kts`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 6/6 PASS. Added `media3-exoplayer-midi:1.2.1` to standard, noLegal, lite, legacy, and vr only. Dev log recorded.

---

### Step 01.2 - Add Shared Playback Renderers Factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackRenderersFactory.kt`  
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a small helper that returns a `DefaultRenderersFactory` configured with decoder fallback and extension renderer mode. Use `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` so the MIDI renderer and existing extension renderers can be discovered when present on the classpath. Do not add persistent `S0305` text to production log messages.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackRenderersFactory.kt` exists.
- `Grep` - `fun createPlaybackRenderersFactory` exists in `PlaybackRenderersFactory.kt`.
- `Grep` - `setEnableDecoderFallback(true)` exists in `PlaybackRenderersFactory.kt`.
- `Grep` - `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` exists in `PlaybackRenderersFactory.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Added shared `createPlaybackRenderersFactory()` helper. Dev log recorded.

---

### Step 01.3 - Wire Activity ExoPlayer Factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt`  
**Depends on:** Step 01.2

**Prompt for developer:**

> Replace the local `DefaultRenderersFactory(context)` construction in `createPlayer()` with `createPlaybackRenderersFactory(context)`. Preserve existing `PrefetchLoadControlFactory`, audio attributes, callbacks, video effects, and lifecycle behavior.

**Verification:**

- `Grep` - `createPlaybackRenderersFactory(context)` exists in `PlayerSetupHelper.kt`.
- `Grep` - `DefaultRenderersFactory(context)` returns zero matches in `PlayerSetupHelper.kt`.
- `Grep` - `player.addListener(playerListener)` still exists in `PlayerSetupHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Foreground ExoPlayer now uses `createPlaybackRenderersFactory(context)`. Dev log recorded.

---

### Step 01.4 - Wire Audio Service ExoPlayer Factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt`  
**Depends on:** Step 01.2

**Prompt for developer:**

> Update `AudioPlaybackService.onCreate()` so the service ExoPlayer is built with `createPlaybackRenderersFactory(this)`. Preserve audio attributes, noisy handling, wake mode, `MediaSession`, foreground notification, and position-save logic.

**Verification:**

- `Grep` - `createPlaybackRenderersFactory(this)` exists in `AudioPlaybackService.kt`.
- `Grep` - `.setWakeMode(wakeMode)` still exists in `AudioPlaybackService.kt`.
- `Grep` - `.setHandleAudioBecomingNoisy(true)` still exists in `AudioPlaybackService.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. `AudioPlaybackService` now uses `createPlaybackRenderersFactory(this)`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` or `scripts/post-change.ps1`.

---

## Handoff Notes to Next Phase

Media3 MIDI classes are on the classpath for all audio-capable flavors. Activity and service playback now use extension-capable renderer factories.

---

## Rollback Plan

Revert phase commit(s). No data migration or persisted user state is changed.