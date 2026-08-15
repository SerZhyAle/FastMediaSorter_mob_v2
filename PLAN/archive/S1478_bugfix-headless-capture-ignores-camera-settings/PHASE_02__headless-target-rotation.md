# Phase 02 - Headless target rotation

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

Feed the headless capture the same device-angle rotation signal the on-screen path uses, so the saved file's orientation matches how the device was held.

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

### Step 02.1 - Own a `CameraOrientationManager` for the capture's lifetime

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Construct a `CameraOrientationManager` from the class's `Context`, passing an empty icon-rotation callback, and call `enable()` at the start of `capture()` - before awaiting the camera provider, so the sensor has the provider's binding latency to report at least one reading. Call `disable()` from `release()`. Comment the empty icon callback: the headless path draws no overlay, so there is nothing to keep upright - without that note the empty lambda reads as an oversight.

**Why:**

Strategic §2 establishes that the on-screen path takes rotation from `OrientationEventListener`, not from the display, and that this is precisely why the symptom appears with auto-rotate off - the display stays at `ROTATION_0` while the sensor still reports the angle.

**Verification:**

- `Grep` - `HeadlessPhotoCapturer.kt` references `CameraOrientationManager`.
- `Grep` - `enable()` is called inside `capture(` and `disable()` inside `release(`.
- `Grep` - the empty icon-rotation lambda carries an adjacent comment line.

**Status:** `[x] done`

---

### Step 02.2 - Apply the rotation to the use case and keep it current until the shutter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Pass the manager's current rotation bucket into the Phase 01 factory call instead of the placeholder, and keep assigning `targetRotation` on the built `ImageCapture` from the manager's callback until `takePicture` fires, so a device turned between bind and shutter still records correctly. Stop updating once the shot is in flight.

**Why:**

Strategic §3.2 requires the headless path to inherit both the sensor signal and the S1457 no-sensor fallback, which `CameraOrientationManager` already carries; reading it once at construction would reintroduce the stale-rotation case that fix closed.

**Verification:**

- `Grep` - `targetRotation` is assigned in `HeadlessPhotoCapturer.kt`.
- `Grep` - no `Surface.ROTATION_0` literal is passed as the factory's rotation argument.
- `Grep -n "Log\.d\("` returns zero hits in `HeadlessPhotoCapturer.kt`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Orientation correctness itself is NOT claimed here - it is device-only (INDEX "Verification reachable statically vs only on device").

---

## Step Log

- 2026-08-07 - Step 02.1 Verification 3/3 PASS. `HeadlessPhotoCapturer` owns a `CameraOrientationManager`, `enable()` at the top of `capture()` before the provider is awaited, `disable()` in `release()`. The empty icon callback carries its reason in the property KDoc directly above it.
- 2026-08-07 - Step 02.2 Verification 3/3 PASS. Factory rotation now `orientationManager.rotationBucket.value`; zero `Surface.ROTATION_0` literals remain. `rotatingCapture` follows the device until `takePicture` clears it, so a turn during the exposure cannot re-target the frame in flight.
- Compile: `.\a.ps1 fk` exit 0 (single run covering phases 01-03; a per-phase re-run over an unchanged toolchain would prove nothing new).
- Orientation correctness itself is NOT claimed here - device-only, per INDEX.