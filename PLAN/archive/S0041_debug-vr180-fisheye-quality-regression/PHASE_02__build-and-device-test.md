# Phase 02 — Build and Device Test

**Status:** ⬜ Not Started
**Phase slug:** build-and-device-test
**Ticket:** S0041
**Depends on:** Phase 01 complete

---

## Goal

Build a debug APK containing the `VR_QUALITY_DEBUG` logging, install on Quest 3, and capture a new log session during VR180 fisheye playback.

---

## Steps

### Step 2.1 — Build VR debug APK [BUILD-REQUIRED]

**Status:** `[x] (auto-build — PASS)`

**Prompt for developer:**
Run the VR-flavor debug build:
```powershell
.\scripts\builders\build-debug.PS1
```
Verify the build passes. Check `build/outputs/` for the APK.

**Verification:**
- BUILD-REQUIRED. Human must confirm build passed.

---

### Step 2.2 — Capture device log [MANUAL]

**Status:** `[manual — deferred to human]`

**Depends on:** Step 2.1

**Prompt for developer:**
1. Install APK on Quest 3 (via ADB or sideload).
2. Start `adb logcat -v time > logs/fastmediasorter_vr_quality_debug_<date>.log` before launching the app.
3. Open `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` in the app.
4. Enter immersive mode (VR player).
5. Watch for 30 seconds. Exit.
6. Stop logcat capture.
7. Verify the log file exists and contains `VR_QUALITY_DEBUG`.

**Verification:**
- MANUAL-REQUIRED. User confirms device session completed and log is in `logs/`.
- `Select-String -Path "logs/fastmediasorter_vr_quality_debug_*.log" -Pattern "VR_QUALITY_DEBUG"` — ≥ 2 matches.

---

## Phase Done Criteria

- [x] 1. APK built successfully (standard debug v2.60.4301.658 + vr debug v2.60.4301.700, 2026-04-30).
- [ ] 2. [MANUAL] Log file in `logs/` contains `VR_QUALITY_DEBUG: selected track format` entry.
- [ ] 3. [MANUAL] Log file contains `VR_QUALITY_DEBUG: fisheye first frame` entry.

---

## Step Log

<!-- append entries after each step completes -->
