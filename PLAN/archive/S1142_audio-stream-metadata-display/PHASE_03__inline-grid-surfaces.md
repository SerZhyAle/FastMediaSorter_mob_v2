# Phase 03 - Inline single-string format + grid active-card now-playing

**Strategic spec:** [`../S1142_audio-stream-metadata-display.md`](../S1142_audio-stream-metadata-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Switch the inline mini-control to the shared single-string formatter, make it show the live track in background (service) mode too, and surface the current track on the active channel's grid tile. Delivers strategic criteria 3 and 4.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`NowPlayingMetadata`, `StreamTitleFormatter.nowPlayingLine`).
- [ ] Phase 02 ✅ Done (service pushes combined metadata -> controller `onMediaMetadataChanged` carries the live track).
- [ ] Backup taken for `StreamsActivity.kt` (>500 LOC - Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1040 |

> No layout XML edited (the grid cell reuses `tvTitle`; the mini-control reuses `tvMiniTitle`) - landscape parity not applicable this phase.
> `StreamsActivity.kt` is 1003 LOC - take a timestamped backup under `temp/S1142/` before editing (Rule 5).

---

## Steps

### Step 03.1 - Structured `nowPlaying` + formatter render + background-mode observe

**Files:** `ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `nowPlaying` from `MutableStateFlow<String?>` to `MutableStateFlow<NowPlayingMetadata?>` (import `com.sza.fastmediasorter.core.playback.NowPlayingMetadata`). In `onMetadata` (local/OFF path raw ICY) set `nowPlaying.value = entry.title?.takeIf { it.isNotBlank() }?.let(NowPlayingMetadata::parse)`.
>
> Add `override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata)` to `playerListener`: this is the background/service path where the controller receives the combined metadata Phase 02 pushes. Build a candidate `title = mediaMetadata.title?.toString()`, `artist = mediaMetadata.artist?.toString()`; treat it as a real track only when `title` is non-blank AND differs from the current station name (`currentSource?.title`) - otherwise set `nowPlaying.value = null` (pre-track state). When it is a track, set `nowPlaying.value = NowPlayingMetadata(artist = artist?.takeIf { it.isNotBlank() }, title = title!!)`.
>
> Rewrite `renderTitle()` to use `StreamTitleFormatter.nowPlayingLine(currentSource?.title ?: return, nowPlaying.value)` for the base line, keeping the existing `noSignalVisible` suffix wrap unchanged.
>
> Add a constructor param `onNowPlayingChanged: (track: String?) -> Unit = {}`. In the existing `collectOnLifecycle(nowPlaying)` block, after `renderTitle()`, also call `onNowPlayingChanged(nowPlaying.value?.trackLine())`. Import `androidx.media3.common.MediaMetadata`. Do not touch recovery/buffering paths (criterion 5).

**Verification:**

- `Grep` - `MutableStateFlow<NowPlayingMetadata?>` present.
- `Grep` - `override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata)` present.
- `Grep` - `StreamTitleFormatter.nowPlayingLine(` referenced in `renderTitle`.
- `Grep` - `onNowPlayingChanged` declared as a constructor param and invoked in the collect block.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Grep 4/4 PASS. Files: StreamInlineAudioManager.kt (structured nowPlaying + onMediaMetadataChanged + formatter render + onNowPlayingChanged). Compile at phase-end build.

---

### Step 03.2 - Grid active-tile now-playing (`StreamGridAdapter`)

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `private var playingId: String? = null` and `private var nowPlayingLine: String? = null` plus `fun setNowPlaying(id: String?, track: String?)`. It returns early when both are unchanged; otherwise it records the previous playing index (by id in `currentList`), stores the new `id`/`track`, and calls `notifyItemChanged` for the previous and the new playing positions only (mirror `StreamSourceAdapter.setPlayingId` - no full rebind). In `VH.bind`, when `source.id == playingId` and `nowPlayingLine` is non-blank set `binding.tvTitle.text = "${StreamTitleFormatter.display(source.title)} - $nowPlayingLine"` and append the track to the tile `contentDescription` (TalkBack, §3.2 accessibility); otherwise keep the current `StreamTitleFormatter.display(source.title)` line. Inactive tiles show station name only (ADR-4).

**Verification:**

- `Grep` - `fun setNowPlaying(id: String?, track: String?)` present.
- `Grep` - `playingId` and `nowPlayingLine` referenced in `bind`.
- `Grep` - `notifyItemChanged` present in `setNowPlaying`.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Grep 4/4 PASS. Files: StreamGridAdapter.kt (setNowPlaying + active-tile track + CD). Compile at phase-end build.

---

### Step 03.3 - Wire the manager callback to the grid adapters (`StreamsActivity`)

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> First back up `StreamsActivity.kt` to `temp/S1142/StreamsActivity.<yyyymmdd-HHmmss>.kt.bak` (Rule 5). In `setupViews`, pass `onNowPlayingChanged = { track -> gridAdapter.setNowPlaying(inlineAudio.playingId, track); pinnedGridAdapter.setNowPlaying(inlineAudio.playingId, track) }` to the `StreamInlineAudioManager` constructor. Extend the existing `onPlayingChanged` lambda to also reset the grid now-playing on a play/stop change: `gridAdapter.setNowPlaying(id, null); pinnedGridAdapter.setNowPlaying(id, null)` (so a new station starts with station-only until its first ICY frame). Update the S1141 comment that claimed "Grid adapters carry no playing indicator" - they now carry the now-playing track for the active tile (S1142).

**Verification:**

- `Grep` - `onNowPlayingChanged =` present in `StreamsActivity.kt`.
- `Grep` - `gridAdapter.setNowPlaying(` and `pinnedGridAdapter.setNowPlaying(` both present.
- `Glob` - `temp/S1142/StreamsActivity.*.kt.bak` exists.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Grep 4/4 PASS (backup + onNowPlayingChanged + grid fan-out). Compile at phase-end build.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - the added `onMediaMetadataChanged` override reuses the already-registered `playerListener` (no new listener register/remove asymmetry); no P0/P1.

---

## Handoff Notes to Next Phase

All four now-playing surfaces (notification/lock-screen via Phase 02; inline control + grid active tile via this phase) share `NowPlayingMetadata` + `StreamTitleFormatter`. Phase 04 regenerates the catalog and dev log; the notification headline is device-verified in `/spec-test-device`.

---

## Rollback Plan

Revert phase commit(s) - three UI-layer file changes, no schema/DI/surface-navigation change. Restore `StreamsActivity.kt` from `temp/S1142/` if needed.
