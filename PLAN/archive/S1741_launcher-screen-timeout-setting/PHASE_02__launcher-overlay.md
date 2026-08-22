# Phase 02 - Launcher Overlay

**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Completed:** 2026-08-17

## Objective

Show and dismiss an app-private blackout overlay after the configured inactivity interval.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherScreenBlackoutManager.kt` | New | ≤ 250 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1,500 |

## Steps

### Step 02.1 - Create lifecycle-bound blackout manager

**Files:** `LauncherScreenBlackoutManager.kt`

**Prompt for developer:**

> Add a manager that observes the timeout, schedules only while the launcher is started, mounts an opaque in-app overlay, and consumes the first activity input that dismisses it.

**Why:**

The strategic choice requires a private visual blackout, not a system timeout, permission, lock, or system-bar change.

**Verification:**

- Manager contains no `Settings.System`, `WRITE_SETTINGS`, `DevicePolicyManager`, or system-bar mutation. (PASS)

**Status:** `[x]` done

### Step 02.2 - Route launcher lifecycle and input

**Files:** `LauncherHomeActivity.kt`

**Prompt for developer:**

> Attach the manager after views exist; route touch, mouse, keyboard and D-pad input to it before the desktop; stop it when the activity stops and release the overlay on destruction.

**Why:**

The input contract requires first input to wake the launcher without activating an underlying cell.

**Verification:**

- Touch and key dispatch call the manager before `super`. (PASS)
- `onStart`, `onStop`, and `onDestroy` forward symmetric lifecycle edges. (PASS)

**Status:** `[x]` done
