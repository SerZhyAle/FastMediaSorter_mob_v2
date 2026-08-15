# Phase 02 - Service-side live ICY metadata into MediaSession

**Strategic spec:** [`../S1142_audio-stream-metadata-display.md`](../S1142_audio-stream-metadata-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Inside `AudioPlaybackService`, react to the service `ExoPlayer`'s raw ICY `onMetadata`, parse it, and push artist/title into the current MediaItem's metadata via `replaceMediaItem` (mirroring the video path) so the system notification / lock screen reflect the live track. Delivers strategic criteria 1 and 2.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`NowPlayingMetadata.parse` available).
- [ ] Backup taken for `AudioPlaybackService.kt` (>500 LOC - Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | ≤ 940 |

> `AudioPlaybackService.kt` is 899 LOC - take a timestamped backup under `temp/S1142/` before editing (Rule 5). Added surface ~30 LOC keeps it under the 1500 split ceiling.

---

## Steps

### Step 02.1 - Backup `AudioPlaybackService.kt`

**Files:** `temp/S1142/`
**Depends on:** - start of phase

**Prompt for developer:**

> The file exceeds 500 LOC. Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` to `temp/S1142/AudioPlaybackService.<yyyymmdd-HHmmss>.kt.bak` before editing.

**Verification:**

- `Glob` - `temp/S1142/AudioPlaybackService.*.kt.bak` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Backup created (temp/S1142/AudioPlaybackService.20260723-103100.kt.bak). PASS.

---

### Step 02.2 - Push live ICY track into the current MediaItem metadata

**Files:** `ui/player/AudioPlaybackService.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the service `ExoPlayer` listener (the anonymous `Player.Listener` added at the `exoPlayer.addListener(...)` call), add `override fun onMetadata(metadata: Metadata)` that extracts the first `IcyInfo.title` (iterate `metadata.get(i)`, `is IcyInfo`, take non-blank `title`), and delegates to a new private `fun applyLiveIcyMetadata(rawIcyTitle: String)`.
>
> `applyLiveIcyMetadata` must: (a) guard against churn - keep a `private var lastIcyTitle: String? = null` field and return early when `rawIcyTitle == lastIcyTitle` (strategic §7 anti-flicker); (b) parse via `NowPlayingMetadata.parse(rawIcyTitle)` (return early on null); (c) read `player` (the service `ExoPlayer`) and its `currentMediaItem` + `currentMediaItemIndex`, return early if null; (d) rebuild the item preserving URI/extras/mimeType: `current.buildUpon().setMediaMetadata(current.mediaMetadata.buildUpon().setTitle(parsed.title).setArtist(parsed.artist).setStation(current.mediaMetadata.title).build()).build()` - carry the original static station name into `setStation` so it is not lost; (e) `player.replaceMediaItem(index, updated)` inside a `try/catch (e: IllegalStateException)` logging `Timber.w(e, ..)` (mirror `StreamPlaybackHelper.updateNowPlayingTitle`); (f) set `lastIcyTitle = rawIcyTitle` on success.
>
> Reset `lastIcyTitle = null` on `onMediaItemTransition` (new station) so a repeat title on a different station is not swallowed. Add imports `androidx.media3.common.Metadata`, `androidx.media3.extractor.metadata.icy.IcyInfo`, `com.sza.fastmediasorter.core.playback.NowPlayingMetadata`. Keep any new log line `<= 120` chars (Rule 19). This runs only for AUDIO/radio (the service is audio-only); no flavor guard needed.

**Verification:**

- `Grep` - `override fun onMetadata(metadata: Metadata)` present in `AudioPlaybackService.kt`.
- `Grep` - `fun applyLiveIcyMetadata(` present.
- `Grep` - `player.replaceMediaItem(` present.
- `Grep` - `lastIcyTitle` referenced in both `applyLiveIcyMetadata` and `onMediaItemTransition`.
- `Grep -n "Log\.d\("` in the file returns zero hits.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Grep 5/5 PASS (onMetadata, applyLiveIcyMetadata, replaceMediaItem, lastIcyTitle x4, no Log.d). Compile at phase-end build. Files: AudioPlaybackService.kt (+~35 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `AudioPlaybackService.kt`.
- [ ] Phase-boundary audit - listener symmetry unaffected (no new register/unregister; reused existing listener), no P0/P1.

---

## Handoff Notes to Next Phase

After this phase the service updates the current MediaItem's combined metadata on each ICY change. That propagates to any connected `MediaController` via `onMediaMetadataChanged`, which Phase 03 consumes to show the live track in the inline control (background mode) and in the grid active card. The device-verifiable headline (notification/lock-screen updates) is proven in `/spec-test-device`.

---

## Rollback Plan

Revert phase commit(s) - single-file change adding one listener override + one private method; no schema/DI/surface change. Restore from `temp/S1142/` backup if needed.
