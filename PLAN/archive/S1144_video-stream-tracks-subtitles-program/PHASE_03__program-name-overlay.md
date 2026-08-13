# Phase 03 - Live program-name overlay for video streams

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none (independent of the DB work; consumes S1142's shared metadata model)
**Blocks:** -
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Show the live program name (ICY `player.mediaMetadata`) as an on-screen overlay in the video player, mirroring the radio inline "Title - Track" pattern (Q1/ADR-5), reusing S1142's `NowPlayingMetadata`/`StreamTitleFormatter`. ICY-only (Q2); ID3/EMSG out of scope.

---

## Prerequisites

- [ ] S1142 present in code (`core/playback/NowPlayingMetadata.kt`, `StreamTitleFormatter.nowPlayingLine`) - it is (BlockNeedUserTest).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VideoPlayerManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/*player* (overlay TextView)` + `res/layout-land/` counterpart | Modified | - |

> Confirm the exact video-player layout(s) that host `tvFileNameOverlay` (research 01) and edit BOTH portrait and landscape variants (Rule 11) - list the concrete file(s) in this step before editing.

---

## Steps

### Step 03.1 - Add a program-name overlay view (portrait + landscape)

**Files:** video player layout(s) under `res/layout/` + `res/layout-land/`
**Depends on:** - start of phase

**Prompt for developer:**

> Locate the video player layout(s) hosting the existing `tvFileNameOverlay`. Add a sibling `TextView` `tvStreamProgramOverlay` positioned like the radio inline control (bottom band, single line, ellipsize end), initially `visibility="gone"`. Use `?attr/` / `@color/` tokens only (no hardcoded hex - Rule 19). Edit the `res/layout-land/` counterpart identically. No hardcoded user text - the string is bound at runtime.

**Verification:**

- `Grep` - `tvStreamProgramOverlay` present in both the portrait and landscape layout files.
- `Grep -n "=\"#"` on the edited layouts returns zero hardcoded-hex hits.

**Status:** `[ ]` not done

---

### Step 03.2 - Bind the overlay to live ICY metadata (mirror radio)

**Files:** `ui/player/helpers/VideoPlayerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `VideoPlayerManager` add a `Player.Listener.onMediaMetadataChanged(mediaMetadata)` (or extend the existing listener) that, only for a STREAM item, builds `NowPlayingMetadata(artist = mediaMetadata.artist?.toString(), title = mediaMetadata.title?.toString())` and renders `StreamTitleFormatter.nowPlayingLine(<channel/station title>, meta)` into `tvStreamProgramOverlay`, toggling visibility GONE when the title is blank or equals the static channel name (pre-program state). Reuse the existing overlay show/hide/auto-hide behaviour of `tvFileNameOverlay` if present. Do not add a new listener register without a symmetric removal (extend the already-registered listener). ICY-only - no ID3/EMSG parsing (ADR-5).

**Verification:**

- `Grep` - `onMediaMetadataChanged` and `tvStreamProgramOverlay` referenced in `VideoPlayerManager.kt`.
- `Grep` - `StreamTitleFormatter.nowPlayingLine(` referenced.
- `/build` -> `standard debug` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug`.
- [ ] Portrait + landscape layout parity confirmed.
- [ ] Dev log entry per file.
- [ ] Phase-boundary audit (Layer 3 listener ownership): the metadata listener reuses an already-registered listener or has a symmetric removal.

---

## Handoff Notes to Next Phase

The video player now shows the live program name for streams. Final phase covers docs/catalog/inventory.

---

## Rollback Plan

Revert the overlay view + binding - the player loses the program-name line; no data/schema impact.
