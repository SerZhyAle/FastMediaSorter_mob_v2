# Phase 02 - Save Routing

**Strategic spec:** [../S0375_video-recording-destination-resource.md](../S0375_video-recording-destination-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Route video capture through its own destination contract without regressing current-resource saves or explicit-target entry points.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is on a feature branch.
- [x] Backups exist for large touched files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/CaptureDestinationPolicy.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` | Modified | ≤ 600 |

---

## Steps

### Step 02.1 - Add a dedicated video fallback helper

**Files:** `CaptureDestinationPolicy.kt`
**Depends on:** 01.3
**Prompt for developer:** Introduce a `resolveVideoDestination` helper parallel to the existing microphone and camera helpers. The helper must resolve to the selected usable target when present and otherwise fall back to the public device `Movies` directory.
**Verification:** `CaptureDestinationPolicy` exposes a dedicated video resolver and mentions `Movies` in its contract.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added `resolveVideoDestination(..)` with a public `Movies` fallback to `CaptureDestinationPolicy`.

### Step 02.2 - Route video saves through a dedicated resolver

**Files:** `BrowseCameraCaptureManager.kt`
**Depends on:** 02.1
**Prompt for developer:** Split the current shared camera/video destination routing so that video capture uses a dedicated resolver: usable current resource stays primary, otherwise use the configured video destination when valid, otherwise fall back to the device `Movies` directory. Do not change explicit-target widget flows; this step only affects the shared browse capture path.
**Verification:** `save(..)` no longer routes video capture through the camera destination setting, and the video path references the new dedicated video destination setting/fallback.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Split browse camera/video save routing so video now uses its own destination contract.

---

## Phase Done Criteria

- [x] Video routing uses its own destination resolver.
- [x] A usable current resource remains the primary video target.
- [x] Invalid or empty video destination falls back to `Movies`.
- [x] Camera routing remains unchanged.