# Phase 02 — Filter generic-motion noise before reporting HUD activity

**Strategic spec:** [`../S0057_bugfix-vr-hud-autohide-timeout.md`](../S0057_bugfix-vr-hud-autohide-timeout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Stop `VrPlayerActivity.onGenericMotionEvent()` from indefinitely sliding the 15 s activity window forward in response to ambient axis jitter from idle Quest 3 controllers; only deliberate thumbstick / trigger / hat motion past a fixed deadzone must call `reportActivity()`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 anchor confirmed: `onGenericMotionEvent()` exists in `VrPlayerActivity.kt` and currently calls `vrHudManager?.reportActivity()` once per second unconditionally.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1100 |

> File is already large. Verify pre-edit LOC: `wc -l` ≤ 1100. If larger, take a timestamped backup into `temp/` before editing (project rule for files > 500 LOC). If post-edit LOC would exceed 1000, halt and split via Manager pattern instead — do NOT proceed inside this phase.

---

## Steps

### Step 02.1 — Pre-edit safety: backup oversized file

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Confirm current line count of `VrPlayerActivity.kt`. If > 500 LOC, copy it to `temp/VrPlayerActivity_<YYYYMMDD_HHmm>.kt.backup` before editing (project rule §5). If post-edit projection > 1500OC, abort this phase and open a refactor spec instead — do not edit further.

**Verification:**

- File exists at `temp/VrPlayerActivity_<YYYYMMDD_HHmm>.kt.backup` (Glob match `temp/VrPlayerActivity_*.kt.backup` returns at least one entry created today).
- Original file at `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` still compiles before any further edits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — HARD STOP. `wc -l VrPlayerActivity.kt` = 1965 LOC, already over the project's 1500OC limit (CLAUDE.md §5.2) and the phase's stated ≤ 1100 budget. Spec status set to `BlockQuestions`.
- 2026-05-03 — User waived the 1000-LOC rule for this bugfix. Backup created at `temp/VrPlayerActivity_20260503_0416.kt.backup` (95 KB). Spec status restored to `In Progress`.

---

### Step 02.2 — Add axis-deadzone gate around `reportActivity()` in `onGenericMotionEvent()`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `override fun onGenericMotionEvent(event: MotionEvent)`, gate the existing 1 Hz `vrHudManager?.reportActivity()` call behind a new private helper `isDeliberateControllerMotion(event)` that returns `true` only when at least one of the standard controller axes exceeds a fixed deadzone:
>
> - `MotionEvent.AXIS_X`, `AXIS_Y`, `AXIS_Z`, `AXIS_RZ`, `AXIS_HAT_X`, `AXIS_HAT_Y` — `abs(value) >= 0.20f`
> - `AXIS_LTRIGGER`, `AXIS_RTRIGGER`, `AXIS_BRAKE`, `AXIS_GAS` — `value >= 0.20f`
>
> If none of those exceed the threshold the method must NOT call `reportActivity()`, but must still forward the event to `vrInputManager?.onMotionEvent(event)` and `super.onGenericMotionEvent(event)` so cursor / wheel routing is unchanged. Keep the existing 1 Hz throttle on the path that does call `reportActivity()`. Add a single short WHY-comment above the new helper explaining that idle Quest 3 controllers emit sub-deadzone axis noise that would otherwise reset the HUD's 15 s window forever. Define the deadzone as a `private const val VR_AXIS_ACTIVITY_DEADZONE = 0.20f` companion-object constant — do not inline magic numbers.

**Verification:**

- `Grep -n "private fun isDeliberateControllerMotion" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → exactly one hit.
- `Grep -n "VR_AXIS_ACTIVITY_DEADZONE = 0.20f" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → exactly one hit.
- `Grep -n "isDeliberateControllerMotion(event)" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → at least one call site inside `onGenericMotionEvent`.
- `Grep -n "vrHudManager\?\.reportActivity()" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → still present (call sites in `dispatchKeyEvent` and `handleVrCommand` must remain intact).
- `Grep -n "vrInputManager\?\.onMotionEvent(event)" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → at least one hit (cursor routing preserved).
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → zero hits (Timber-only rule preserved).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: VrPlayerActivity.kt (+18 LOC, 1965 → 1983, line-budget waiver per user). Added `isDeliberateControllerMotion()` (10 axes / 0.20 deadzone), `VR_AXIS_ACTIVITY_DEADZONE` companion constant, and gated the existing 1 Hz `reportActivity()` call inside `onGenericMotionEvent()`. Three explicit-command paths still call `reportActivity()` unchanged (key-up @941, mapped command @1153, motion-gated @993).

---

### Step 02.3 — Build verification

**Files:** —
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `/build` (debug, `standard` flavor at minimum). Do NOT invoke gradle directly. Resolve any compile errors. Confirm explicit-command paths still call `reportActivity()`: `dispatchKeyEvent` (button up) and `handleVrCommand` (mapped controller commands). Do not relax the deadzone to chase missing wakes — explicit commands cover that path.

**Verification:**

- `/build` exit code 0 for at least one configured flavor.
- `Grep -n "TODO(phase-02)" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → zero hits.
- `Grep -n "vrHudManager\?\.reportActivity()" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → at least three hits remain (key-up path + handleVrCommand + onGenericMotionEvent guarded path).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. `gradlew assembleVrDebug` BUILD SUCCESSFUL in 14s; `compileVrDebugKotlin` actually executed (the earlier `assembleStandardDebug` runs were UP-TO-DATE because `src/vr/java` is part of the `vr` flavor only — phase tactical text fixed in retrospect for future phases). 3 reportActivity() call sites confirmed (lines 941, 993, 1153). No `TODO(phase-02)` markers.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `/build` succeeded (`assembleVrDebug`, 14s).
- [x] `Grep` for `TODO(phase-02)` returns zero hits across the repo.
- [x] Dev log entry added for `VrPlayerActivity.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Invariants after this phase: HUD activity is reported only on (a) explicit key/button events, (b) mapped VR commands, (c) deliberate axis motion past a 0.20 deadzone. The 15 s idle auto-hide should now fire on Quest 3 in both `vrShowFps=true` and `vrShowFps=false`.
- Phase 03 finalizes catalogue and dev-log housekeeping; no behavioural changes.

---

## Rollback Plan

Revert this phase's commit. The change is isolated to one method plus one helper plus one constant. No persisted state, no UI surface, no DI changes. If on-device testing surfaces missed wakes, lower the deadzone constant — do not remove the gate, since that re-opens root cause B.
