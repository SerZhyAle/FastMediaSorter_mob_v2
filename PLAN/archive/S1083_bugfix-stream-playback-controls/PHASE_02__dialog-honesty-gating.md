# Phase 02 - Dialog honesty gating

**Strategic spec:** [`../S1083_bugfix-stream-playback-controls.md`](../S1083_bugfix-stream-playback-controls.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-18
**Completed:** 2026-07-18

---

## Objective

Build the control dialog's visible section set from each section's applicability to the active source: hide SPEED for a live source, hide HUE and BRIGHTNESS for a stream source. After this phase the dialog shows no section that silently does nothing.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 900 |

> No layout edit: sections are gated by the existing `activeSections` visibility mechanism (`isVisible`), so `res/layout/dialog_playback_control.xml` and its landscape variant are untouched. No new strings: unavailable sections are hidden, not disabled-with-hint.

---

## Steps

### Step 02.1 - Snapshot source character on dialog open

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Alongside the existing `hasMultipleAudioTracks` / `is3dVrEnabled` snapshots in `onViewCreated`, snapshot two `Boolean`s from the host once: `sourceIsStream = host().activeSourceIsStream` and `sourceIsLive = host().activeSourceIsLive`. Follow the existing snapshot-at-open comment convention (the rail must stay stable for the dialog's lifetime).

**Verification:**

- `Grep` - `activeSourceIsStream` and `activeSourceIsLive` both present in `PlaybackControlDialogFragment.kt`.
- `Grep` - `sourceIsStream` declared as a `private var` in `PlaybackControlDialogFragment.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Gate SPEED, HUE, BRIGHTNESS in the active-section set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the `activeSections` builder (video branch), add `ControlSection.HUE` and `ControlSection.BRIGHTNESS` only when `!sourceIsStream || host().supportsColorAdjustmentForActiveSource` (so a stream drops them until Phase 03 flips the capability), and add `ControlSection.SPEED` only when `!sourceIsLive`. The AUDIO branch is unchanged (radio is out of scope). Keep the existing ordering.

**Verification:**

- `Grep` - `sourceIsLive` guards the `ControlSection.SPEED` add in `PlaybackControlDialogFragment.kt` (read the `activeSections` builder).
- `Grep` - `supportsColorAdjustmentForActiveSource` present in `PlaybackControlDialogFragment.kt`.
- `Grep` - `activeSections.contains` still guards `resolveInitialSection` (a now-hidden section stays unselectable and non-focusable - no code change needed, confirm it holds after the gate edit).
- `Grep -n "Log\.d\("` - zero hits in `PlaybackControlDialogFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-18 - Verification 4/4 PASS. Files: PlaybackControlDialogFragment.kt.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `PlaybackControlDialogFragment.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The dialog is now honest and non-regressing: for a live stream SPEED is gone, for any stream HUE/BRIGHTNESS are gone. This is a complete, shippable end state on its own. Phase 03 (device-gated) re-admits HUE/BRIGHTNESS for streams by flipping `supportsColorAdjustmentForActiveSource` and wiring the effects lifecycle; if §6.2 fails on device, Phase 03 is skipped and this phase stands as final.

---

## Rollback Plan

Revert the phase commit - purely a visibility rule in one fragment; no data or layout change.
