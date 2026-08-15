# Phase 03 - Headless lens parity

**Strategic spec:** [`../S1478_bugfix-headless-capture-ignores-camera-settings.md`](../S1478_bugfix-headless-capture-ignores-camera-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Make the headless path choose its lens by the same rule the on-screen path uses, so both capture routes open the same camera on a multi-lens device.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt` | Modified | ≤ 220 |

---

## Steps

### Step 03.1 - Replace `resolveSelector` with the shared initial-lens rule

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete `resolveSelector(provider)` and its `DEFAULT_BACK_CAMERA` / `DEFAULT_FRONT_CAMERA` branches. Enumerate lenses with `CameraLensEnumerationManager`'s existing expand-from-provider function, pick the entry at `initialLensIndex(entries)`, and turn it into a `CameraSelector` with `CameraUseCaseFactory.selectorFor(entry)`. `initialLensIndex` is a pure function over the entry list and needs no Activity. Keep a fallback that throws the existing `IllegalStateException("No camera available")` when enumeration yields no entry.

**Why:**

Strategic §1 records that the on-screen path deliberately steers the initial choice off the ultra-wide lens (S1261 defect D1) while the headless path takes CameraX's convenience constant, so the two routes can open different lenses on the same device.

**Verification:**

- `Grep` - `HeadlessPhotoCapturer.kt` contains zero occurrences of `DEFAULT_BACK_CAMERA` and `DEFAULT_FRONT_CAMERA`.
- `Grep` - `initialLensIndex` is referenced in `HeadlessPhotoCapturer.kt`.
- `Grep` - `selectorFor` is referenced in `HeadlessPhotoCapturer.kt`.
- `Grep` - `No camera available` still present.

**Status:** `[x] done`

---

### Step 03.2 - Carry the chosen physical lens id into the use case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> When the selected entry names a physical sub-lens, pass its id into the Phase 01 factory call so the capture is taken on that lens. A `CameraSelector` binds the logical camera only; the physical id travels on the use-case builder. When the entry is logical, pass null and the factory's existing guard makes it a no-op.

**Why:**

Strategic §3.3 sets the criterion as both routes selecting one lens, and selecting the entry without carrying its physical id would bind the logical camera while shooting on whichever sub-lens the device defaults to - the S1189 arrangement the factory already implements for the on-screen path.

**Verification:**

- `Grep` - the physical-camera-id argument is supplied at the factory call site in `HeadlessPhotoCapturer.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `HeadlessPhotoCapturer.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Field-of-view equality between the two routes is NOT claimed here - it is device-only.

---

## Step Log

- 2026-08-07 - Step 03.1 Verification 4/4 PASS. `resolveSelector` replaced by `resolveLens`, which runs the same `expand` -> `select` -> `initialLensIndex` rule the camera screen uses; zero `DEFAULT_BACK_CAMERA` / `DEFAULT_FRONT_CAMERA` occurrences remain (the KDoc names the constant in prose, not as a symbol); `No camera available` still thrown when enumeration is empty.
- 2026-08-07 - Step 03.2 Verification 2/2 PASS. `physicalCameraId = lens.physicalCameraId` supplied at the factory call, so a chosen sub-lens is shot on that lens rather than on whatever the logical camera defaults to.
- Compile: `.\a.ps1 fk` exit 0.
- Field-of-view equality between the two routes is NOT claimed here - device-only.