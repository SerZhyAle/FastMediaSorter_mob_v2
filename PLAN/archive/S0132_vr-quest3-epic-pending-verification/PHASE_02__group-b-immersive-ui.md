# Phase 02 — Group B: Immersive UI

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Verify controller ray + cursor (ex-S0065), HUD indicators (ex-S0009), HUD swapchain adaptive size (ex-S0080), and the full interactive control panel (ex-S0008) on Quest 3. All four sub-tasks have code committed; this phase is on-device acceptance.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] VR build installed with all Phase 01 code changes.
- [ ] Quest 3 with controllers and hand-tracking enabled.
- [ ] A 4K stereo test file available for interactive panel FPS check.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| *(none — verification-only phase; no code changes expected)* | — | — |

> If a defect is found during on-device testing, create a bug fix commit in a sub-step (e.g., 02.1-fix) and add the affected file to this table before committing.

---

## Steps

### Step 02.1 — Verify controller ray visual and cursor on Quest 3

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt` (read), `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHandRayManager.kt` (read)
**Depends on:** — start of phase

**Prompt for developer:**

> Open any stereo file in immersive mode. Point the controller at the HUD — a thin semi-transparent ray line and a small disc cursor must be visible. Aim away from all registered planes — the ray should be truncated at ≤ 5 m or hidden. Switch to hand-tracking mode and confirm the same visual is drawn for the hand aim ray. Confirm FPS ≥ 72 throughout (use FPS counter or ADB overlay).

**Verification:**

- On-device observation: ray line visible from controller tip to HUD hit point.
- On-device observation: cursor disc visible at HUD intersection point, angular size ≥ 5° at 2 m distance.
- On-device observation: hand-tracking aim produces the same ray/cursor primitive.
- FPS ≥ 72 during normal use with ray enabled.

**Status:** `[ ]` not done

---

### Step 02.2 — Verify HUD indicators (8 indicators + idle + swapchain race)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudIndicatorManager.kt` (read), `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` (read)
**Depends on:** Step 02.1

**Prompt for developer:**

> In immersive mode, trigger each of the 8 HUD indicators in sequence: pause, seek, volume change, zoom change, file name display, recenter, mode change, repeat toggle. Each must appear on its respective command and auto-hide after its timer. Confirm:
> 1. Pause indicator: visible ~3 s on pause, then hides.
> 2. Progress bar: visible on seek/file change, auto-hides ~3 s.
> 3. Idle state: ADB logcat for 60 s of no activity — the string `hud_swapchain submission` must NOT appear (no idle HUD composition layer submitted).
> 4. Exit-to-phone and re-enter: open immersive, switch to phone layout, re-enter immersive — HUD state is clean (no duplicate indicators, no missing state).
> 5. Race check: watch logcat at immersive exit for `createHudSwapchain(1024×256) returned false` — note whether a visual artifact (flicker/black frame) is visible at the moment this log line appears.

**Verification:**

- On-device observation: all 8 indicators appear and auto-hide on correct commands.
- On-device observation: no indicator duplication after phone→immersive transition.
- On-device observation: race log line (if present) — record whether artifact is visible; document finding in a Phase 06 dev log comment.

**Status:** `[ ]` not done

---

### Step 02.3 — Verify HUD swapchain adaptive size

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` (read), `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` (read)
**Depends on:** Step 02.2

**Prompt for developer:**

> In immersive mode, capture logcat at session start. Find the `HUD swapchain: NxM` log line. Confirm N > 1024 and M > 256. In the headset, read the HUD text (file name, indicator labels) from typical HUD distance — text must be legible without squinting. Confirm FPS ≥ 72.

**Verification:**

- Logcat: line matching `HUD swapchain: \d+x\d+` where first value > 1024 and second > 256.
- On-device observation: HUD text is legible at typical viewing distance.
- FPS ≥ 72.

**Status:** `[ ]` not done

---

### Step 02.4 — Verify interactive control panel (ex-S0008)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt` (read), `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt` (read)
**Depends on:** Step 02.1 (ray must be visible to interact), Step 02.3

**Prompt for developer:**

> While playing a 4K stereo file in immersive mode:
> 1. Press the "Open controls" controller button — the floating control panel appears in the lower FOV.
> 2. Verify all elements are clickable via ray: pause/play, seek forward/back, seek slider (trigger-drag), volume, brightness, audio track selector, subtitle selector, playback speed, stereo format indicator + manual switch, exit immersive.
> 3. Drag the seek slider: hold trigger, move ray along slider, release — position updates to new time.
> 4. Change stereo format via the manual switch — format changes immediately without exit.
> 5. Wait 10 s without interaction — panel auto-hides.
> 6. FPS during open panel + 4K playback: ≥ 72.
> 7. Interactive zone size: all buttons must register clicks without precise aiming (angular size ≥ 5° at 2 m).

**Verification:**

- On-device observation: panel opens on button press; all 11 elements listed above respond to ray interaction.
- On-device observation: seek slider drag updates playback position.
- On-device observation: panel auto-hides after 10 s of inactivity.
- FPS ≥ 72 with open panel during 4K playback.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` if any defect fix was committed.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file modified (if any fixes committed) via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if any `.kt` changed.

---

## Handoff Notes to Next Phase

- Phase 04 (`panel-3d-flow`) depends on this phase and Phase 03. Both must be ✅ before Phase 04 starts.
- If step 02.2 race check finds a visible artifact, log the finding in Phase 06 and open a follow-up ticket before marking this epic Verified.

---

## Rollback Plan

Verification-only phase — no code changed under the normal path. If a defect fix was committed, revert that commit. No data migration.
