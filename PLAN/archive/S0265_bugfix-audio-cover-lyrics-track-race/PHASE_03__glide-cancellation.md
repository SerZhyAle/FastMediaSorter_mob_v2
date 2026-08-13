# Phase 03 - Glide Cancellation on Track Switch

**Strategic spec:** [`../S0265_bugfix-audio-cover-lyrics-track-race.md`](../S0265_bugfix-audio-cover-lyrics-track-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Abort the pending Glide bitmap load for the previous track at the start of every new `loadAudioCoverArt` call. The Phase 02 guard prevents a late-arriving bitmap from poisoning state, but it does **not** prevent the bitmap from briefly appearing in the `audioCoverArtView` ImageView before the new track's `Glide.into()` is reached. This phase closes that visual leak by explicitly clearing Glide on the target view. Implements strategic §5.1 pillar C.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done. Stale-result guards are wired.
- [ ] Build is green on `standard` flavor.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 525 |

> No new files. No other files touched.

---

## Steps

### Step 03.1 - Clear Glide on `audioCoverArtView` at the start of `loadAudioCoverArt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inside `fun loadAudioCoverArt(file: MediaFile)`, after the existing `coverArtJob?.cancel(); coverArtJob = null` (around line 87-88) and **before** any branching on `isNetworkFile / isCloudFile / local`, add an explicit Glide clear:
>
> ```kotlin
> // S0265: abort any pending Glide request from the previous track so a late-arriving
> // bitmap cannot momentarily appear in the ImageView before the new load reaches into().
> Glide.with(binding.audioCoverArtView.context).clear(binding.audioCoverArtView)
> ```
>
> Place the call before the early-return seek-skip guard moved further up - the order is `seek-skip check → reset state → cancel job → clear Glide → branching`. Do not introduce `try/catch` around the clear - it is safe under Glide's contract.
>
> Important - keep the existing early-return guard (`file.path == coverArtDisplayedForPath && audioCoverArtView.isVisible`) **above** the cancel and clear: returning early without cancellation is the intended fast path for ExoPlayer seek re-triggers on the same file.

**Verification:**

- `Grep` - `Glide\.with\(binding\.audioCoverArtView\.context\)\.clear\(binding\.audioCoverArtView\)` returns 3 matches (1 new at top of `loadAudioCoverArt` line ~92 + 2 pre-existing inside embedded-artwork branches lines ~127 and ~202).
- `Grep` - `coverArtJob\?\.cancel\(\)` followed within 5 lines by `Glide.+\.clear\(binding\.audioCoverArtView\)` (use `Grep` with `-A 5` then visually confirm in tool output, or run `Grep` for the exact text block).
- `Grep` - the seek-skip early return `cover already displayed for` returns 1 match (unchanged, must remain). Line number of seek-skip `return` (~85) is **less than** line number of new Glide clear (~92).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: AudioCoverArtLoader.kt. New clear inserted at line 92 right after `coverArtJob?.cancel()` and after the seek-skip early return at line 85. Pre-existing clears at lines 127, 202 unchanged.

---

### Step 03.2 - Verify project builds and confirm seek-skip fast path intact

**Files:** none (build only)
**Depends on:** Step 03.1

**Prompt for developer:**

> Trigger a debug build of the `standard` flavor via `/build`. Re-read the first ~20 lines of `loadAudioCoverArt` to confirm the order: seek-skip early return → state reset → job cancel → Glide clear → network/cloud branch. If the Glide clear is above the early-return, ExoPlayer seeks within the same track would blank the cover - that is a regression.

**Verification:**

- Build exits with code 0.
- `Grep` - `loadAudioCoverArt: cover already displayed for` returns 1 match (the seek-skip log line, unchanged).
- `Grep` - `Glide\.with\(.+\)\.clear\(binding\.audioCoverArtView\)` returns 1 match - and its line number is **greater** than the line of the seek-skip `return` statement (verify by reading 20 lines around `fun loadAudioCoverArt`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Build SUCCESSFUL (assembleStandardDebug, 35s, exit 0). APK v2.60.5201.208. Seek-skip return at line 85 precedes new Glide.clear at line 92 - fast path for ExoPlayer seeks intact.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `AudioCoverArtLoader.kt`.

---

## Handoff Notes to Next Phase

- Cover ImageView is now reset to managed state at every track change. Late-arriving Glide loads are aborted before they can paint.
- Media-session notification guard remains the last gap - Phase 04 hardens it.

---

## Rollback Plan

Revert the phase commit. The Glide-clear call disappears; behaviour falls back to Phase 02's "ImageView may briefly show stale bitmap, but state and notification are protected".
