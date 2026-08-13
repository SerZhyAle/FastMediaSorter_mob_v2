# Phase 01 - Preview-free photo use case

**Strategic spec:** [`../S1478_bugfix-headless-capture-ignores-camera-settings.md`](../S1478_bugfix-headless-capture-ignores-camera-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Give `CameraUseCaseFactory` an entry point that builds only `ImageCapture`, and route `HeadlessPhotoCapturer` through it so both capture paths share one definition of output geometry.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - none exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt` | Modified | ≤ 220 |

---

## Steps

### Step 01.1 - Add a photo-only entry point to `CameraUseCaseFactory`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a function that returns a single configured `ImageCapture` without building `Preview`, `ViewPort` or `UseCaseGroup`. It reuses the existing private `buildResolutionSelector(preferHighResolution)` and `applyPhysicalCameraId()` and sets `targetRotation` from the constructor property, exactly as the `imageCapture` branch of `create()` does. Do not duplicate that builder chain - if the two now differ in any way, that is the defect this ticket exists to remove. Leave `create(previewView)` and its callers untouched.

**Why:**

Strategic §2 records that the factory's KDoc claims to keep output geometry in one place, but its only entry point demands a `PreviewView`, so the headless path could not enter it and grew a second, divergent builder.

**Verification:**

- `Grep` - `CameraUseCaseFactory.kt` contains a function returning `ImageCapture` whose body calls `buildResolutionSelector`.
- `Grep` - that function does not reference `PreviewView`, `ViewPort` or `UseCaseGroup`.
- `Grep` - `fun create(previewView: PreviewView)` still present, unchanged signature.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 3/3 PASS. Files: `CameraUseCaseFactory.kt` (+13 LOC). `create()`'s photo branch now calls the new `createPhotoCapture()` rather than repeating the chain, so the two routes share one builder. Mechanical closure deferred to Step 05.1, which the plan defines as the single batched `post-change.ps1` run for this ticket.

---

### Step 01.2 - Build the headless `ImageCapture` through the factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the inline `ImageCapture.Builder().setCaptureMode(..).build()` in `capture()` with a `CameraUseCaseFactory` instance configured for photo mode, and take its photo-only `ImageCapture`. Keep `CAPTURE_MODE_MINIMIZE_LATENCY` only if the factory path can express it; if it cannot, set it on the returned use case or extend the factory entry point to carry it - do not fork the builder again. Pass `videoMode = false`, no selected resolution, and the rotation value the class holds today (`Surface.ROTATION_0` until Phase 02 supplies a real one).

**Why:**

Strategic §3.1 requires the headless path to stop owning its own builder, since a second builder is what let rotation, resolution strategy and physical-lens id silently diverge between the two capture routes.

**Verification:**

- `Grep` - `HeadlessPhotoCapturer.kt` contains zero occurrences of `ImageCapture.Builder(`.
- `Grep` - `HeadlessPhotoCapturer.kt` references `CameraUseCaseFactory`.
- `Grep -n "Log\.d\("` returns zero hits in `HeadlessPhotoCapturer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-07 - Verification 3/3 PASS. Files: `HeadlessPhotoCapturer.kt` (+6 LOC net). `CAPTURE_MODE_MINIMIZE_LATENCY` is expressed by the factory entry point itself, so nothing was set on the returned use case and no second builder survives. Rotation still the `Surface.ROTATION_0` placeholder Phase 02 replaces.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No method was added to `CameraCaptureSessionManager` - it sits on detekt's `TooManyFunctions` ceiling (strategic §3.1).
