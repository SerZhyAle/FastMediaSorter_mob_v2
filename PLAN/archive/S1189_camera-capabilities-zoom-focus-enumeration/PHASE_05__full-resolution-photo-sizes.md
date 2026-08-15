# Phase 05 - Full sensor resolution in the photo size list

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Offer the sensor's full resolution in the photo size list by reading the high-resolution output set and letting the resolution selector honour it, as an explicit choice rather than a new default.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `CameraCapabilityProbe` builds `photoResolutions` from the stream configuration map.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` | Modified | ≤ 400 |

> No layout file is touched - the resolution list is populated into the existing settings dialog control.

---

## Steps

### Step 05.1 - Include high-resolution output sizes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCapabilityProbe.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Merge `StreamConfigurationMap.getHighResolutionOutputSizes(ImageFormat.JPEG)` into the JPEG size list before de-duplication (no version gate needed - the method exists since API 23, below both flavour minimums). Keep the existing option cap: the list is sorted largest-first, so the sensor maximum always survives it. Expose the high-resolution subset separately so the capture path can tell when the slower mode is required, and keep the list ordered largest first as today.

**Verification:**

- `Grep` - `getHighResolutionOutputSizes` present in `CameraCapabilityProbe.kt`.
- `Grep` - `MAX_RESOLUTION_OPTIONS` declaration appears once and is applied once in `CameraCapabilityProbe.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 2/2 PASS. Two predicates dropped as wrong: the API-31 gate (the method is API 23, confirmed against `api-versions.xml`, so gating it would have hidden the sensor maximum on every API 26..30 device) and "raise the cap" (unnecessary - descending sort already guarantees the maximum survives a `take`).

---

### Step 05.2 - Let the resolution selector reach the high-resolution set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> When the selected resolution belongs to the high-resolution set, build the image-capture `ResolutionSelector` with the allowed-resolution mode that prefers higher resolution over capture rate; otherwise keep the current selector unchanged so ordinary sizes keep today's latency. The preview use case must keep the ordinary mode in both cases - a preview stream at sensor resolution is neither needed nor cheap.

**Verification:**

- `Grep` - `setAllowedResolutionMode` present in `CameraUseCaseFactory.kt`.
- `Grep` - `fun buildResolutionSelector` present in `CameraUseCaseFactory.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS (`a.ps1 fk` BUILD SUCCESSFUL). `buildResolutionSelector` now takes `allowHighResolution`, and the preview is always built with it false - a preview stream at sensor resolution is neither needed nor cheap. The decision itself moved to a private top-level `prefersHighResolution`, because inlining it pushed `bindToLifecycle` onto detekt's cyclomatic-complexity ceiling.

---

### Step 05.3 - Show the full-resolution option in the settings dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Render every probed size in the resolution control, including the newly reachable sensor maximum, and keep the current selection as the default so the change is opt-in (strategic ADR-4). Reuse the megapixel formatting the dialog already applies - do not introduce a second format for the new entries.

**Verification:**

- `Grep` - `photoResolutions` present in `CameraSettingsDialogFragment.kt`.
- `Grep` - `String.format` count in `CameraSettingsDialogFragment.kt` unchanged versus before the step.
- `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. No edit was needed in the dialog: it already renders `capabilities.photoResolutions` through its own megapixel formatter, so the sensor maximum appears simply by being in that list now. `String.format` count unchanged at 2, confirming no second format path was introduced.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings; a sensor-resolution capture is the memory-heaviest path in the screen, so check it against the audit protocol's allocation rung.

---

## Handoff Notes to Next Phase

The resolution list is now device-sized rather than capped at a fixed count. Phase 06 must not assume a fixed number of entries anywhere in the capture UI.

---

## Rollback Plan

Revert the phase commit(s) - the size list returns to the first six ordinary JPEG sizes and the selector to its aspect-only form.
