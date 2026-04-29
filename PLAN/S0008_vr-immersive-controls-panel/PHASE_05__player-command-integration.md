# Phase 05 — Player Command Integration

**Strategic spec:** [`../spec_vr-immersive-controls-panel.md`](../spec_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Wire every panel zone click to its corresponding `PlaybackCommand`. Implement seek-drag debounce (Research Q4). Confirm the auto-hide timer is 10 s. Feed live playback state (volume, brightness, speed, track label, stereo format) from `PlayerViewModel` / `VrHudSink` into `VrInteractivePanelDriver` so the panel always shows current values.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (panel visible, all zones drawn).
- [ ] Phase 04 is ✅ Done (hover highlighting and ZONE_EXIT wired).
- [ ] Research Q4 resolved: seek debounce threshold determined (if SMB latency > 500 ms, debounce at 300 ms; else no debounce needed).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ current + 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt` | Modified | ≤ 280 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt` | Modified | ≤ 80 |

---

## Steps

### Step 5.1 — Build full zone → PlaybackCommand dispatch table

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `VrPlayerActivity` (or its controller-input helper), replace the Phase 04 placeholder in the trigger-click handler with the full dispatch table. Use the zone ID constants from `VrInteractivePanelComposer`:
>
> | Zone ID constant | PlaybackCommand |
> |-----------------|-----------------|
> | `ZONE_PREV` | `PlaybackCommand.PreviousFile` (or equivalent) |
> | `ZONE_NEXT` | `PlaybackCommand.NextFile` |
> | `ZONE_PLAY_PAUSE` | `PlaybackCommand.TogglePlayPause` |
> | `ZONE_SEEK_BACK` | `PlaybackCommand.SeekBackward` |
> | `ZONE_SEEK_FWD` | `PlaybackCommand.SeekForward` |
> | `ZONE_SEEK_SLIDER` | handled via drag (see Step 5.2) — no click action |
> | `ZONE_VOL_DOWN` | `PlaybackCommand.VolumeDown` |
> | `ZONE_VOL_UP` | `PlaybackCommand.VolumeUp` |
> | `ZONE_BRIGHT_DOWN` | `PlaybackCommand.BrightnessDown` |
> | `ZONE_BRIGHT_UP` | `PlaybackCommand.BrightnessUp` |
> | `ZONE_SPEED` | `PlaybackCommand.SetPlaybackSpeed(nextSpeed())` |
> | `ZONE_TRACK` | `PlaybackCommand.CycleAudioTrack` |
> | `ZONE_FORMAT` | `PlaybackCommand.CycleStereoFormat` |
> | `ZONE_EXIT` | `PlaybackCommand.ExitImmersive` (or equivalent close command) |
>
> `nextSpeed()` cycles {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f} — share with `VrControlOverlayManager.nextSpeed()` (or extract to a utility). After each command dispatch, call `panelDriver.scheduleAutoHide()` to reset the 10 s timer.
>
> Use Timber. No `Log.d`.

**Verification:**

- `Grep` — `ZONE_PLAY_PAUSE` referenced in `VrPlayerActivity.kt` (or its delegate).
- `Grep` — `ZONE_FORMAT` → `CycleStereoFormat` referenced in that file.
- `Grep` — `ZONE_EXIT` dispatch still present (no regression from Phase 04).
- `Grep` — `scheduleAutoHide` called after dispatch.
- `Grep` — `Log\.d(` returns zero hits in touched file.

**Status:** `[ ]` not done

---

### Step 5.2 — Implement seek-drag → SeekTo with debounce

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> In the trigger-hold path (trigger down + pointer moving + zone is `ZONE_SEEK_SLIDER`):
>
> - While trigger is held and `seekFraction >= 0f`: call `panelDriver.updateSeekDrag(seekFraction)` every frame to update the visual slider.
> - Debounce the actual `PlaybackCommand.SeekTo(positionMs)` dispatch: emit `SeekTo` only when `seekFraction` has not changed for `SEEK_DEBOUNCE_MS` (use 300 ms if Research Q4 confirmed SMB latency > 500 ms; otherwise 0 ms / immediate). Define `SEEK_DEBOUNCE_MS` as a constant.
> - On trigger release: emit one final `SeekTo` at the current fraction × totalMs, then call `panelDriver.updateSeekDrag(-1f)` to clear the drag indicator.
>
> The debounce must NOT block the UI thread — use `Handler.postDelayed` and cancel on each new fraction update.

**Verification:**

- `Grep` — `SEEK_DEBOUNCE_MS` constant defined in the file.
- `Grep` — `SeekTo` dispatched in the seek-drag path.
- `Grep` — `updateSeekDrag(-1f)` called on trigger release.
- `Grep` — `Log\.d(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 5.3 — Add panel state feed methods to VrHudSink

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt`
**Depends on:** — start of phase (parallel with Step 5.1)

**Prompt for developer:**

> `VrHudSink` is the interface through which `VrPlayerActivity` / ViewModel pushes HUD state. Add new default methods so the panel driver receives live values:
>
> ```kotlin
> fun updatePanelVolume(percent: Int) {}
> fun updatePanelBrightness(percent: Int) {}
> fun updatePanelSpeed(speed: Float) {}
> fun updatePanelTrackLabel(label: String) {}
> fun updatePanelFormatLabel(label: String) {}
> fun showPanel() {}
> fun hidePanel() {}
> fun togglePanel() {}
> ```
>
> Default implementations are empty — backwards-compatible. `VrInteractivePanelDriver` will implement these (Step 5.4). Do not add logic here.

**Verification:**

- `Grep` — `fun updatePanelVolume` in `VrHudSink.kt`.
- `Grep` — `fun updatePanelBrightness` in `VrHudSink.kt`.
- `Grep` — `fun updatePanelSpeed` in `VrHudSink.kt`.
- `Grep` — `fun showPanel()` in `VrHudSink.kt`.
- File size — `VrHudSink.kt` ≤ 80 lines.

**Status:** `[ ]` not done

---

### Step 5.4 — Implement VrHudSink panel methods in VrInteractivePanelDriver

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt`
**Depends on:** Step 5.3

**Prompt for developer:**

> Make `VrInteractivePanelDriver` implement `VrHudSink` (add `... : VrHudSink` to the class declaration, or add the interface implementation separately if already implementing another interface).
>
> Override:
> - `updatePanelVolume(percent)` → `updateVolume(percent)`.
> - `updatePanelBrightness(percent)` → `updateBrightness(percent)`.
> - `updatePanelSpeed(speed)` → `updateSpeed(speed)`.
> - `updatePanelTrackLabel(label)` → `updateTrackLabel(label)`.
> - `updatePanelFormatLabel(label)` → `updateFormatLabel(label)`.
> - `showPanel()` → `show()`.
> - `hidePanel()` → `hide()`.
> - `togglePanel()` → `toggle()`.
>
> All overrides simply delegate to the existing methods in `VrInteractivePanelDriver`. No new logic.

**Verification:**

- `Grep` — `VrHudSink` in `VrInteractivePanelDriver.kt` class declaration.
- `Grep` — `override fun updatePanelVolume` in `VrInteractivePanelDriver.kt`.
- `Grep` — `override fun showPanel` in `VrInteractivePanelDriver.kt`.
- `Grep` — `Log\.d(` returns zero hits.
- File size — `VrInteractivePanelDriver.kt` ≤ 280 lines.

**Status:** `[ ]` not done

---

### Step 5.5 — Push live state from VrPlayerActivity into panel driver

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 5.4

**Prompt for developer:**

> In `VrPlayerActivity`, wherever the existing HUD sink receives state updates, also forward the equivalent update to `panelDriver` (via the `VrHudSink` interface methods added in Step 5.3):
>
> - On volume change: call `panelDriver.updatePanelVolume(percent)`.
> - On brightness change: call `panelDriver.updatePanelBrightness(percent)`.
> - On playback speed change: call `panelDriver.updatePanelSpeed(speed)`.
> - On audio track change: call `panelDriver.updatePanelTrackLabel(label)`.
> - On stereo format change: call `panelDriver.updatePanelFormatLabel(label)`.
> - On progress update: call `panelDriver.updateProgress(positionMs, bufferedMs, totalMs)`.
>
> These calls must happen on the main thread. If existing calls happen on a background thread, wrap in `runOnUiThread { … }`.
>
> Open-controls button (existing controller button X handler): replace `vrControlOverlayManager.toggle()` with `panelDriver.togglePanel()` (or keep both if fallback is needed and `panelDriver` may be null).

**Verification:**

- `Grep` — `panelDriver.updatePanelVolume` called in `VrPlayerActivity.kt`.
- `Grep` — `panelDriver.updatePanelTrackLabel` called.
- `Grep` — `panelDriver.updateProgress` called (or `updateProgress` called via the `VrHudSink` reference).
- `Grep` — `Log\.d(` returns zero hits in touched file.

**Status:** `[ ]` not done

---

### Step 5.6 — Verify auto-hide delay is 10 s and panel stays open on hover

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt`
**Depends on:** Step 5.4

**Prompt for developer:**

> Confirm that `AUTO_HIDE_DELAY_MS = 10_000L` in `VrInteractivePanelDriver`.
>
> Ensure `scheduleAutoHide()` is cancelled and rescheduled on every call to any `update*` method while the panel is visible. The implementation should use a single `Handler` and a single pending `Runnable` (cancel old → schedule new):
>
> ```kotlin
> private fun scheduleAutoHide() {
>     handler.removeCallbacks(autoHideRunnable)
>     handler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
> }
> ```
>
> And `autoHideRunnable` calls `hide()`.
>
> Call `scheduleAutoHide()` at the end of `show()`, `updateHoverZone()`, `updateVolume()`, `updateBrightness()`, `updateSpeed()`, `updateTrackLabel()`, `updateFormatLabel()`. Do NOT call it in `updateProgress()` — progress updates are too frequent and should not reset the timer.

**Verification:**

- `Grep` — `AUTO_HIDE_DELAY_MS = 10_000L` in `VrInteractivePanelDriver.kt`.
- `Grep` — `handler.removeCallbacks(autoHideRunnable)` in `VrInteractivePanelDriver.kt`.
- `Grep` — `handler.postDelayed(autoHideRunnable` in `VrInteractivePanelDriver.kt`.
- `Grep` — `scheduleAutoHide()` NOT called inside `updateProgress`.
- `Grep` — `Log\.d(` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] All 14 zone IDs have either a `PlaybackCommand` dispatch or an explicit "no click action" comment (ZONE_SEEK_SLIDER).
- [ ] `AUTO_HIDE_DELAY_MS = 10_000L` confirmed by Grep.
- [ ] `Grep` for `Log\.d(` in every touched Kotlin file returns zero hits.
- [ ] On device (Quest 3): Play/Pause, Volume, Track, Format, Exit all work from the panel without exiting VR. Seek slider drag seeks the video. Panel hides after 10 s of no interaction. (Manual test — document in Blockers Log if unavailable.)
- [ ] Dev log entries:

  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "feature" "Phase 05: full zone→PlaybackCommand dispatch table; seek-drag debounce; live state feed to panel"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt" "feature" "Phase 05: implement VrHudSink panel methods; confirm 10 s auto-hide"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudSink.kt" "feature" "Phase 05: add panel state feed default methods"
  ```

---

## Handoff Notes to Next Phase

- All panel interactions are wired. Phase 06 updates feature docs and regenerates the catalog.
- `SEEK_DEBOUNCE_MS` value is documented in the source — if Research Q4 outcome changes the value, update the constant and re-run Phase 06 dev log.
- `VrInteractivePanelDriver` now implements `VrHudSink` — the catalog must reflect this new interface relationship.

---

## Rollback Plan

Revert phase commits. `VrPlayerActivity` command table reverts to Phase 04 placeholder (ZONE_EXIT only). `VrHudSink` new methods are default-empty — backwards-compatible, safe to remove. `VrInteractivePanelDriver` interface implementation removal requires removing the override methods.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 1 (MD031 blank line before dev-log powershell block)
  - REVIEW applied: 0
  - DISCUSS proposed: 0
