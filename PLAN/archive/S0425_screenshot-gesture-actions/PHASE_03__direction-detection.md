# Phase 03 - Direction detection

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Rework `ScreenGestureOverlayManager` so a matched gesture reports its direction (DOWN / RIGHT / UP) through the callback, classified from the drag angle into three non-overlapping windows from the left-edge strip. Update both instantiation sites to accept the direction. No dispatch yet - callbacks keep their current bodies, just receive the new argument. (ADR-2.)

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`ScreenshotGestureDirection` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 200 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenshotAccessibilityService.kt` | Modified | ≤ 230 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Modified | ≤ 200 |

> Flavor placement: `ScreenGestureOverlayManager` + `OverlayHostService` live in the shared `screenCapture` source set; `ScreenshotAccessibilityService` in `noLegal`. The direction enum is in `src/main`. No `src/main` flavor guards added.

---

## Steps

### Step 03.1 - Classify drag angle into three directions

**Files:** `screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the constructor callback to `onGestureMatched: (direction: ScreenshotGestureDirection) -> Unit`. In `handleTouch` ACTION_MOVE: require `dx > 0` (rightward drag from the left strip) and distance ≥ `GESTURE_DISTANCE_PX`, but allow `dy` of either sign (drop the `dy <= 0` rejection so upward gestures match). Compute `angle = atan2(dy, dx)` in degrees and bucket: UP when `angle in -70.0..-20.0`, RIGHT when `angle in -20.0..20.0`, DOWN when `angle in 20.0..70.0`; angles outside all three windows return false (unmatched). Replace the single `MIN/MAX_MATCH_ANGLE` constants with the three window bounds as named companion constants. On a match set `gestureTriggered`, call `onGestureMatched(direction)`, `view.performClick()`, return true. Windows are non-overlapping by construction; exact bounds are a device-test tuning item.

**Verification:**

- `Grep` - `onGestureMatched: (direction: ScreenshotGestureDirection) -> Unit` (or `(ScreenshotGestureDirection) -> Unit`) present.
- `Grep` - `ScreenshotGestureDirection.UP`, `.RIGHT`, `.DOWN` each referenced.
- `Grep` - `dy <= 0f` returns zero hits (upward gestures no longer rejected).
- `Grep` - `import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection` present.

**Status:** `[ ]` not done

---

### Step 03.2 - Update accessibility-service strip instantiation

**Files:** `screencapture/ScreenshotAccessibilityService.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `showStrip()`, change the `onGestureMatched = { captureNow() }` lambda to `onGestureMatched = { direction -> captureNow(direction) }`. Update `captureNow()` signature to `captureNow(direction: ScreenshotGestureDirection)`; for this phase store the direction in a field but keep behaviour unchanged (capture as before). The action gate + dispatch is added in Phase 05. Import the enum.

**Verification:**

- `Grep` - `captureNow(direction` present.
- `Grep` - `onGestureMatched = { direction` present.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 03.3 - Update overlay-host strip instantiation

**Files:** `screencapture/OverlayHostService.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `onCreate()`, change `onGestureMatched = { launchConsentActivity() }` to `onGestureMatched = { direction -> launchConsentActivity(direction) }`. Update `launchConsentActivity` to accept `direction: ScreenshotGestureDirection`; for this phase keep the body unchanged (the direction is threaded into the consent intent in Phase 05). Import the enum.

**Verification:**

- `Grep` - `launchConsentActivity(direction` present.
- `Grep` - `onGestureMatched = { direction` present.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `Log\.d\(` in all three files returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The overlay manager now emits `ScreenshotGestureDirection`. Both services receive it but still behave as before (capture on any matched direction). Phase 05 adds the per-direction action gate (incl. DO_NOT_USE skip) and post-save dispatch.

---

## Rollback Plan

Revert phase commit. No persisted state changed - overlay geometry only.
