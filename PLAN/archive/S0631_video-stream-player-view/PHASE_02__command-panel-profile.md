# Phase 02 - Command panel stream profile

**Strategic spec:** [`../S0631_video-stream-player-view.md`](../S0631_video-stream-player-view.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

When `state.isLiveVideoStream` is true, the command panel shows ONLY the owner-approved stream set
(send-to, fullscreen, video-control dialog, snapshot, rotation, info, cast) and hides everything else,
in portrait, big-buttons, and landscape. Non-stream files are untouched.

> Owner-approved set mapped to commands: `SEND_TO` (share link), `FULLSCREEN`, `EDIT` (video control
> dialog - `onEditClicked` routes VIDEO to `showPlaybackControlDialog`), `SAVE_FRAME` (snapshot),
> `ROTATION_TOGGLE` (gated by `state.showRotationToggle`), `INFO`, `CAST` (gated by `supportsCast` +
> Wi-Fi). PiP is a separate overlay button in `custom_player_controls*.xml` (already video+API31 gated) -
> NOT a command-panel item, so it is unchanged by this phase. No `res/layout*` XML is edited - this phase
> only toggles visibility of existing views; landscape parity is handled in `applyLandscapeLayout` code.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`PlayerState.isLiveVideoStream` exists).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 360 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt` | Modified | ≤ 600 |

---

## Steps

### Step 02.1 - Stream-video allowlist in `buildActiveCommands`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> At the top of `buildActiveCommands(..)`, after resolving `file`, add an early return when
> `state.isLiveVideoStream`: build and return (sorted by priority) ONLY these commands -
> `PlayerCommand.SEND_TO`, `PlayerCommand.FULLSCREEN`, `PlayerCommand.EDIT`, `PlayerCommand.SAVE_FRAME`,
> `PlayerCommand.INFO`, plus `PlayerCommand.ROTATION_TOGGLE` only when `state.showRotationToggle`, plus
> `PlayerCommand.CAST` only when `mediaCapabilities.supportsCast && isWifiConnected`. Do not add any other
> command for a live video stream. Leave the existing non-stream build path unchanged below the guard.

**Verification:**

- `Grep` - `state.isLiveVideoStream` matches in `CommandPanelLayoutPlanner.kt`.
- `Grep` - the early-return block lists `PlayerCommand.SEND_TO` and `PlayerCommand.SAVE_FRAME` and `PlayerCommand.EDIT`.
- `Grep` - `PlayerCommand.DELETE` and `PlayerCommand.FAVORITE` do NOT appear inside the stream branch (still only in the non-stream path).

**Status:** `[x]` done

---

### Step 02.2 - Stream-video branch in `applyLandscapeLayout`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> At the start of `applyLandscapeLayout(..)`, add `if (state.isLiveVideoStream) { .. return }`. Inside it,
> make visible ONLY the stream-allowed landscape binding buttons: `binding.btnInfoCmd`,
> `binding.btnFullscreenCmd`, `safeViews.btnSaveFrameCmd`, `safeViews.btnEditCmd` (set its
> contentDescription to `R.string.control`), and `safeViews.btnCastCmd` gated exactly as today
> (`mediaCapabilities.supportsCast && getCastMediaManager()?.isCastAvailable == true && isWifiConnected(..)`).
> Set every other command button hidden (`isVisible = false`): favorite, slideshow, delete, rename, undo,
> black-screen, lyrics, youtube-music, all pdf/text/epub/image/office buttons, print, open-in-separate-window,
> crop/crop-to-file/compress/draw. For the overflow, reuse the existing pattern
> `planner.buildActiveCommands(..).filter { !it.barCapable }` and set `btnOverflowMenu` visible iff non-empty
> (this surfaces `SEND_TO` as the share-link entry). Then `return` before the normal landscape body runs.

**Verification:**

- `Grep` - `if (state.isLiveVideoStream)` matches in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - inside that branch, `binding.btnFavorite.isVisible = false` (or equivalent favorite-hide) present.
- `Grep` - `safeViews.btnSaveFrameCmd.isVisible = true` reachable in the stream branch.

**Status:** `[x]` done

---

### Step 02.3 - Suppress slideshow + favorite for stream-video in `update`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `update(..)`, derive `val isStreamVideo = state.isLiveVideoStream` near the media-type flags and
> change `showSlideshow` to `(isImage || isVideo) && !isStreamVideo` so the slideshow button is hidden for a
> live video stream in portrait and big-buttons modes (landscape is already handled by step 02.2). In the
> async favorite coroutine, gate `shouldShowFavorite` with `&& !state.isLiveVideoStream` so favorite never
> re-appears for a stream after the layout pass. No other state change.

**Verification:**

- `Grep` - `!isStreamVideo` (or `!state.isLiveVideoStream`) appears in the `showSlideshow` computation.
- `Grep` - `shouldShowFavorite` line includes `!state.isLiveVideoStream`.

**Status:** `[x]` done

---

### Step 02.4 - Planner test for the stream-video allowlist

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlannerTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a test that builds a `PlayerState` whose `resource.id == SyntheticResourceIds.STREAM` and
> `currentFile.type == MediaType.VIDEO`, calls `buildActiveCommands(..)`, and asserts the result is exactly
> the stream allowlist (order by priority: `SEND_TO`, `FULLSCREEN`, `SAVE_FRAME`, `CAST` when wifi+cast,
> `ROTATION_TOGGLE` when toggle on, `INFO`, `EDIT`) and contains none of `DELETE`/`FAVORITE`/`SLEEP_TIMER`/
> `RANDOM`. Add one assertion that a non-stream VIDEO state still yields the full set (regression guard).

**Verification:**

- `Grep` - a new `@Test fun` referencing `isLiveVideoStream` or `SyntheticResourceIds.STREAM` exists in `CommandPanelLayoutPlannerTest.kt`.
- `Grep` - the test asserts absence of `PlayerCommand.DELETE` for the stream state.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `CommandPanelLayoutPlannerTest` passes - run the single class via `--tests` (do not run the full suite).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The stream profile now governs which controls appear. The `SEND_TO` command stays visible for a stream but
still shares the file path; Phase 03 fixes its payload to share the URL.

---

## Rollback Plan

Revert phase commit(s) - pure visibility gating behind `isLiveVideoStream`; no data migration. Non-stream
behavior is unchanged, so revert restores prior stream behavior (full control set) without side effects.
