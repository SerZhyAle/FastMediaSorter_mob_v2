# Phase 01 - Audio empty-state resilience & memory hygiene

**Strategic spec:** [`../S0407_audio-player-extra-visualizations.md`](../S0407_audio-player-extra-visualizations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Make the audio video-background controller degrade to "no background" (black + static note) instead of an alternative animation when clips are absent/broken, and fully release media resources on each per-track clip re-pick so a long play queue cannot exhaust memory.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] No dependency phases.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt` | Modified | ≤ 380 |

> File is < 500 LOC (currently ~343) - no backup step required. No landscape layout involved.

---

## Steps

### Step 01.1 - Degrade to "no background" instead of animated bars

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the three VISUALIZATION fallback paths - `showVideo()` when `getRandomBackgroundFile()` returns null, the `MediaPlayer` `setOnErrorListener`, and the `startMediaPlayer` `catch` block - replace the call to `showBars()` with `showStaticNote()` so absence/corruption renders as "no background" (black + static music note), per strategic §3.2 and §6.10. Keep `videoView.isVisible = false` in each path. Do not add a toast, dialog, or rethrow - the existing silent fallback is required; the only change is the fallback target. Keep the existing `Timber.i` (no-clips) and `Timber.e` (decode error) logs; do not embed a ticket id in them.

**Verification:**

- `Grep` - `showStaticNote()` is called from all three VISUALIZATION fallback sites (null file, `setOnErrorListener`, `startMediaPlayer` catch): `Grep -n "showStaticNote()"` returns ≥ 3 hits.
- `Grep` - no `showBars()` call remains on a VISUALIZATION fallback path (the `file == null`, error-listener, and catch branches reference `showStaticNote`, not `showBars`).
- `Grep` - `Toast` does not appear in the file (`Grep -n "Toast"` returns zero hits).
- `/build` compiles (run via `/build`, not gradle directly).

**Status:** `[x] done`

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS (showStaticNote() ×6, no showBars on fallback paths, Toast 0, Log.d 0). Build deferred to Phase Done Criteria. Files: AudioEmptyStateController.kt.

---

### Step 01.2 - Release the render Surface on every teardown

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `private var currentSurface: Surface? = null`. In `startMediaPlayer`, after a `Surface` is successfully attached, assign it to `currentSurface`; on the `catch` path keep releasing the local surface. In `releaseMediaPlayer()`, after releasing the `MediaPlayer`, also release and null `currentSurface` (`currentSurface?.release(); currentSurface = null`). This guarantees the previous frame's surface is freed on each per-track re-pick (strategic §6.12), not just the player. Do not change the per-track re-pick cadence - `show()` is still called per track by the cover-art loader.

**Verification:**

- `Grep` - `private var currentSurface: Surface?` matches once.
- `Grep` - `currentSurface?.release()` present inside the file.
- `Grep` - `currentSurface = surface` (or equivalent assignment) present in `startMediaPlayer`.
- `/build` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS (currentSurface decl ×1, release ×1, assign ×1). Build deferred to Phase Done Criteria. Files: AudioEmptyStateController.kt.

---

### Step 01.3 - Drop the stale SurfaceTexture listener on teardown

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> When the surface is not yet available, `showVideo()` installs a `videoView.surfaceTextureListener` that captures the picked `file`. On a subsequent per-track re-pick this can fire for a stale file. In `release()` and at the start of a fresh `showVideo()` re-pick, clear the listener (`videoView.surfaceTextureListener = null`) before installing a new one, so only the current pick's callback is live. Keep behaviour identical when the surface is already available.

**Verification:**

- `Grep` - `videoView.surfaceTextureListener = null` matches ≥ 1 time.
- `Grep` - `Log\.d\(` returns zero hits in the file (Timber-only).
- `/build` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-13 - Verification 2/2 PASS (surfaceTextureListener=null ×2, Log.d 0). Build deferred to Phase Done Criteria. Files: AudioEmptyStateController.kt.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (Kotlin-only change).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `AudioEmptyStateController.kt` (post-change.ps1).
- [x] Catalog regenerated via post-change.ps1 (1784 records); neuroslop + ticket-log gates PASS.

---

## Handoff Notes to Next Phase

The controller now renders "no background" on missing/broken clips and frees `MediaPlayer` + `Surface` + listener on every re-pick. Phase 02 (settings gate) is independent and may run in any order relative to this phase.

---

## Rollback Plan

Revert the phase commit - no data migration or persisted state changed; behaviour reverts to the prior CANVAS_BARS fallback.
