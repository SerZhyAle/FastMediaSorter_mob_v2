# Phase 02 - Rotating note in the background-playback bar

**Strategic spec:** [`../S1382_background-audio-note-stream-icons.md`](../S1382_background-audio-note-stream-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Make the bar's fallback note rotate while audio plays, freeze on pause, and stop whenever the bar goes away - artwork stays still.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `InlinePlaybackAnimator` accepts a turn duration and exposes `pauseNote()` / `resumeNote()` (Step 01.2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt` | Modified | ≤ 400 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). The file is 277 LOC, so no backup sub-step is required.
>
> No layout file is touched: all three visual states reuse the existing `miniArtwork` view, and `view_mini_now_playing.xml` has no `layout-land` twin.

---

## Steps

### Step 02.1 - Hold one animator for `miniArtwork`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a companion object with `private const val NOTE_TURN_MS = 3000L`. Add a property `private val noteAnimator: InlinePlaybackAnimator? = miniBar?.let { InlinePlaybackAnimator(it.miniArtwork, NOTE_TURN_MS) }` and a property `private var noteShown = false` recording whether the artwork slot currently holds the fallback note. Import `com.sza.fastmediasorter.ui.browse.InlinePlaybackAnimator`.

**Why:**

Strategic §5 requires one animator bound to the single existing `miniArtwork` view rather than a new one per `populateBarContent()` call, because re-creating it on every track change would restart the turn from zero.

**Verification:**

- `Grep` - `NOTE_TURN_MS = 3000L` present in `NowPlayingManager.kt`.
- `Grep` - `private val noteAnimator` matches exactly once.
- `Grep` - `private var noteShown` matches exactly once.
- `Grep` - `import com.sza.fastmediasorter.ui.browse.InlinePlaybackAnimator` present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. Files: ui/player/helpers/NowPlayingManager.kt (+12 LOC).

---

### Step 02.2 - Split `populateBarContent` into a still branch and a note branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extract the artwork handling of `populateBarContent` into two private methods. `showStillArtwork(artworkUri: Uri)` keeps the current Glide load, calls `noteAnimator?.stopNote()` first so the view is not left tilted, and sets `noteShown = false`. `showRotatingNote(isPlaying: Boolean)` clears any pending Glide request on `miniArtwork` via `Glide.with(activityBinding.root.context).clear(..)`, sets `R.drawable.ic_music_note`, sets `noteShown = true`, then calls `noteAnimator?.startNote()` followed by `noteAnimator?.resumeNote()` when `isPlaying` is true and `noteAnimator?.pauseNote()` when it is false. Have `populateBarContent` pick the branch on `meta.artworkUri` exactly as it does today, passing `player.isPlaying`.

**Why:**

Strategic §6 item 1 rules that only the fallback note rotates while album art stays still, so the two states need separate handling and the still branch has to cancel the rotation instead of inheriting it.

**Verification:**

- `Grep` - `private fun showStillArtwork` matches exactly once.
- `Grep` - `private fun showRotatingNote` matches exactly once.
- `Grep` - `noteAnimator?.stopNote()` present inside `showStillArtwork`.
- `Grep` - `setImageResource(R.drawable.ic_music_note)` matches exactly once in the file.
- `Grep` - `Log\.d\(` returns zero hits in `NowPlayingManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 5\5 PASS. Files: ui/player/helpers/NowPlayingManager.kt (+22 LOC).

---

### Step 02.3 - Freeze and resume the note with playback state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `playerListener.onIsPlayingChanged`, after the existing `updateMiniPlayPauseIcon(isPlaying)` call, drive the rotation: when `noteShown` is true, call `noteAnimator?.startNote()` then `noteAnimator?.resumeNote()` for `isPlaying == true`, and `noteAnimator?.pauseNote()` for `isPlaying == false`. Do nothing when `noteShown` is false. Reuse the same private helper `showRotatingNote` uses rather than repeating the three calls - extract it as `private fun applyNoteRotation(isPlaying: Boolean)` and call it from both places.

**Why:**

Strategic §6 item 2 makes rotation mean "playing right now" and requires it to stop on pause and continue from where it stopped, which is why the freeze goes through `pauseNote()` rather than `stopNote()`.

**Verification:**

- `Grep` - `private fun applyNoteRotation` matches exactly once.
- `Grep` - `applyNoteRotation(` matches at least three times (declaration plus two call sites).
- `Grep` - `pauseNote()` present in `NowPlayingManager.kt`.
- `Grep` - `resumeNote()` present in `NowPlayingManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. Files: ui/player/helpers/NowPlayingManager.kt (+9 LOC).

---

### Step 02.4 - Route every hide path through one teardown

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/NowPlayingManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `private fun hideBar()` that sets `miniBar?.root?.isVisible = false`, calls `noteAnimator?.stopNote()`, sets `noteShown = false` and calls `detachListener()`. Replace every existing hide site with a call to it: the four early returns in `updateBarVisibility` (video/audio media type, panel setting off, service not running), both hide branches inside the `connectForStatus` callback, and the `STATE_ENDED` / `STATE_IDLE` branch of `onPlaybackStateChanged`. Also call `noteAnimator?.stopNote()` and set `noteShown = false` in `onStop()` so no Choreographer frames are requested while the activity is off-screen. Keep the existing Timber lines where they are.

**Why:**

Strategic §3 makes an infinite animation in a permanently visible bar a leak risk that must stop together with the bar, and strategic §7 records this exact defect shape from S1302; a single teardown point is what keeps a future hide branch from silently missing the stop.

**Verification:**

- `Grep` - `private fun hideBar()` matches exactly once.
- `Grep` - `isVisible = false` matches exactly once in `NowPlayingManager.kt` (only inside `hideBar`).
- `Grep` - `hideBar()` matches at least seven times in the file.
- `Grep` - `stopNote()` present inside `fun onStop()`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 4\4 PASS. Files: ui/player/helpers/NowPlayingManager.kt. Six hide sites collapsed into one `hideBar()`; `onStop()` also drops the animator.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1` - verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same facade run.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase notes:**

- UI-phase screenshot gate (S1338): placement decision recorded (strategic §6 "Quiz decisions (2026-08-05)"). Screenshot deferred to Phase 03 - that phase changes the same view again, so one build-install-capture cycle covers both rather than two. This phase's own Done Criteria do not demand a shot.

---

## Handoff Notes to Next Phase

`showStillArtwork` is the single place that puts a motionless image into `miniArtwork`, and `hideBar()` is the single teardown. Phase 03 adds the channel-icon state by calling into the same still path, so it inherits the rotation stop for free.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no layout change; the bar falls back to its previous static note.
