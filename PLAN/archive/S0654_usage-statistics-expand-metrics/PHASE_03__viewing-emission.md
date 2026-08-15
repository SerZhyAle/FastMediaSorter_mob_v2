# Phase 03 - Viewing Emission

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Wire emission for slideshow activity and GIF first-frame saves.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt` | Modified | ≤ 150 |

---

## Steps

### Step 03.1 - Record slideshow sessions and advances

**Files:** `ui/player/SlideshowController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `SlideshowController`. Emit `StatsEvent.SlideshowStarted` once when a slideshow transitions from inactive to active (not on each resume of the same running session). Emit `StatsEvent.SlideAdvanced` on each slide advance. Keep emission off any synchronous I/O path - `record()` only enqueues, so it is safe in the advance callback.

**Verification:**

- `Grep` - `StatsSink` present in `SlideshowController.kt`.
- `Grep` - `SlideshowStarted` and `SlideAdvanced` both referenced.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 03.2 - Record GIF first-frame saves

**Files:** `domain/usecase/SaveGifFirstFrameUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink` into `SaveGifFirstFrameUseCase`, mirroring how `ExtractGifFramesUseCase` records `FRAMES_EXPORTED`. Emit `StatsEvent.GifFrameSaved` after the save returns success. Note the sibling API inconsistency (no `recordStats` flag here unlike `AdjustImageUseCase`) - emit unconditionally; the sink gate handles opt-in.

**Verification:**

- `Grep` - `StatsSink` present in `SaveGifFirstFrameUseCase.kt`.
- `Grep` - `GifFrameSaved` referenced.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`SLIDESHOW_SESSIONS`, `SLIDESHOW_IMAGES_SHOWN`, `GIF_FRAMES_SAVED` now accrue. Rows rendered in Phase 06.

---

## Rollback Plan

Revert phase commit(s) - emission-only, no data migration or user-facing surface changed.
