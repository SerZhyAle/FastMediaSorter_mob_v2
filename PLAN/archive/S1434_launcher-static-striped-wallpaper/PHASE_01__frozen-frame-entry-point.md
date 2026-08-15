# Phase 01 - Frozen-frame entry point on the branded view

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Give `AudioWaveParticleView` a public `showFrozenFrame()` that re-rolls the session parameters and paints one settled frame without ever starting the animator, and keep that frame alive across layout changes.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt` | Modified | ≤ 500 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). This file measures 447 lines, so no backup step is required; keep the phase's additions under the 500-line mark.
>
> **Flavor placement.** The view lives in `src/main` and is shared with the audio player, so nothing here is flavor-specific.

---

## Steps

### Step 01.1 - Add the frozen-mode state fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two private fields next to `pendingStart`: `frozenMode`, true while the host wants a still frame instead of the animation, and `pendingFrozenFrame`, true when a frozen frame was requested before the view had a size. Give each a one-line KDoc naming what it guards.

**Why:**

Strategic §7 lists "the entry point is called before layout and the frame never draws" as a medium-probability risk whose mitigation is the deferred-start mechanism the animated path already uses, and that mechanism needs its own pending flag because `pendingStart` means "start the animator", which the frozen path must never do.

**Verification:**

- `Grep` - `private var frozenMode` matches exactly once.
- `Grep` - `private var pendingFrozenFrame` matches exactly once.

**Status:** `[ ]` not done

---

### Step 01.2 - Add `showFrozenFrame()` and its private painter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun showFrozenFrame()` to the public API block: cancel the animator, set `frozenMode` true, call `randomizeParams()`, and when `width` or `height` is not positive set `pendingFrozenFrame` and return, otherwise clear `pendingFrozenFrame` and call a new private `drawFrozenFrame()`. `drawFrozenFrame()` re-inits particles for the current size, resets `time` and `startupFrameCount`, repaints the off-screen buffer with `bufferFillColor`, sets `wavePaint.strokeWidth` from the rolled `waveStrokeWidth`, and calls the existing `renderStaticFrame()`. Neither function starts the animator and neither writes anything to disk.

**Why:**

Strategic §5.1 requires the new entry point to be a composition of the existing private "re-roll parameters" and "render the settled frame" operations rather than new graphics code, and ADR-2 states that a second drawing path would drift from the animation the mode is named after.

**Verification:**

- `Grep` - `fun showFrozenFrame()` matches exactly once.
- `Grep` - `private fun drawFrozenFrame()` matches exactly once.
- `Grep` - `animator.start()` returns no hit inside `showFrozenFrame` or `drawFrozenFrame`.
- `Grep` - `renderStaticFrame()` is called from `drawFrozenFrame`.

**Status:** `[ ]` not done

---

### Step 01.3 - Repaint the frozen frame on size change

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `onSizeChanged`, after the off-screen bitmap is recreated and before the existing `animatorsDisabled()` branch, handle the frozen case: when `pendingFrozenFrame` or `frozenMode` is set, clear `pendingFrozenFrame`, call `drawFrozenFrame()` and return. Add a comment stating that the recreated buffer is blank, so a frozen frame that is not repainted here leaves the desktop a flat fill after a rotation.

**Why:**

Strategic §2 requires the frame to stay on screen for as long as the user works with the launcher, and a rotation recreates the off-screen buffer without any animator tick to repaint it, which would blank the wallpaper.

**Verification:**

- `Grep` - `drawFrozenFrame()` appears inside `onSizeChanged`.
- `Grep` - `pendingFrozenFrame = false` matches at least twice (deferred path plus size-change path).

**Status:** `[ ]` not done

---

### Step 01.4 - Clear frozen mode when the animation resumes ownership

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Clear `frozenMode` and `pendingFrozenFrame` at the top of `startAnimation()` and inside `stopAndReset()`, so a view that was showing a frozen frame animates normally once the host asks it to, and a reset leaves no stale frozen state behind.

**Why:**

Strategic §2 requires the three existing wallpaper options to behave exactly as before, and the launcher reuses one view instance across mode switches, so a sticky `frozenMode` would make the branded animation repaint a still frame on every rotation.

**Verification:**

- `Grep` - `frozenMode = false` matches exactly twice.
- `Grep` - `pendingFrozenFrame = false` present in both `startAnimation` and `stopAndReset`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in `AudioWaveParticleView.kt`.
- [ ] File stays at or below 500 lines, so no Rule 5 backup obligation is created for later phases.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`showFrozenFrame()` is idempotent from the caller's side: each call rolls a new palette and paints exactly one frame, so the host decides how often a new frame appears. The audio player is untouched - it only ever calls `startAnimation`, `pauseAnimation` and `stopAndReset`.

---

## Rollback Plan

Revert phase commit(s) - the additions are new members plus one branch in `onSizeChanged`; no existing caller changes behaviour.
