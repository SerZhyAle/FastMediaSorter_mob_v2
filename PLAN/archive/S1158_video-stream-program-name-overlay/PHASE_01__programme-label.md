# Phase 01 - Programme label

**Strategic spec:** [`../S1158_video-stream-program-name-overlay.md`](../S1158_video-stream-program-name-overlay.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Carry the already-captured ICY programme name to a visible label in the video player, in both
orientations.

---

## Prerequisites

- [x] On a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | - |
| `app_v2/src/main/res/values/dimens.xml` | Modified | +1 line |

Both orientations are listed, as CLAUDE.md Rule 11 requires - the landscape file is not optional here.

---

## Steps

### Step 01.1 - Declare the callback

**Files:** `VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun onStreamProgramName(name: String?) {}` to `PlayerCallback`, next to `onStreamWaitPhase` and
> with the same empty default - only the full player UI renders it, and every other `PlayerCallback`
> implementation must stay compilable without knowing about it.
>
> In `playVideo`, right after the entry log, call `playerCallback.onStreamProgramName(null)`. Every new
> file and every new channel passes through here, which makes it the one point where the previous
> programme name is guaranteed stale.

**Verification:**

- `Grep` - `fun onStreamProgramName` matches exactly once in `VideoPlayerManager.kt`.
- `Grep` - `onStreamProgramName(null)` appears inside `playVideo`.

**Status:** `[x]` done

---

### Step 01.2 - Raise it from the ICY branch

**Files:** `StreamPlaybackHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the stream listener's `onMetadata`, in the `is IcyInfo` branch, the non-blank now-playing title is
> already extracted into `nowPlaying` and passed to `updateNowPlayingTitle`. Add
> `playerCallback.onStreamProgramName(nowPlaying)` alongside it.
>
> Do not add a second log line - the branch already logs the value at `Timber.i`.

**Verification:**

- `Grep` - `onStreamProgramName(nowPlaying)` matches exactly once.
- `Grep` - the `is IcyInfo` branch still calls `updateNowPlayingTitle`.

**Status:** `[x]` done

---

### Step 01.3 - Declare the label in both orientations

**Files:** `layout/activity_player_unified.xml`, `layout-land/activity_player_unified.xml`, `values/dimens.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `@+id/streamProgramLabel` directly after `tvFileNameOverlay` in **both** layouts, styled to match
> it - same start margin, same rounded badge background, single line with end ellipsis, `gone` by
> default - and offset below it by a new dimen `stream_program_label_margin_top`.
>
> `tvFileNameOverlay` uses `layout_gravity` in both files, so the same declaration is valid in each -
> unlike `streamWaitLabel`, which strategic §5 warns is gravity-placed in portrait and constrained in
> landscape. Verify that by looking at the actual parent of `tvFileNameOverlay` in the landscape file
> before copying the block, rather than trusting this note.
>
> No hardcoded colours (CLAUDE.md Rule 19) - reuse the colour resources `tvFileNameOverlay` already
> references.

**Verification:**

- `Grep` - `streamProgramLabel` matches exactly once in each layout file.
- `Grep` - no `="#` hex literal on the added lines.
- `Grep` - `stream_program_label_margin_top` defined exactly once in `values/dimens.xml`.

**Status:** `[x]` done

---

### Step 01.4 - Render it

**Files:** `PlayerPlaybackCallbackImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Override `onStreamProgramName`: a null or blank name hides the label, anything else sets the text
> (trimmed) and shows it.
>
> Do **not** add a clear next to either of the two places that hide `streamWaitLabel`. Both fire when
> playback becomes ready or buffering ends, and a programme name has to survive its own stream
> starting to play - clearing there would blank the label the moment the picture appeared. The reset
> lives in `playVideo` (Step 01.1) instead.

**Verification:**

- `Grep` - `override fun onStreamProgramName` matches exactly once.
- `Grep` - `streamProgramLabel` does not appear in either `streamWaitLabel` clearing block.
- `.\a.ps1 fc` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The signal is end-to-end. What only a device can answer: whether the label is legible over live video
and whether it collides with the file-name overlay on a long channel name.

---

## Rollback Plan

Remove the callback declaration and its three call sites, plus the two layout blocks and the dimen.
