# Phase 02 - Detector Implementation

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, 07
**Steps done:** 4 / 4
**Started:** 2026-06-02 01:58:10
**Completed:** 2026-06-02 02:00:45

---

## Objective

Implement `DeviceProfileDetector` with high/medium/low confidence signal detection: XR/OpenXR, car mode, TV/leanback, screen width, telephony. Return best-match profile with confidence and fallback to `PERSONAL_SMARTPHONE` on low confidence.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (DeviceProfile enums, interfaces, Room).
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetector.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/DetectionHelper.kt` | New | ≤ 150 |

---

## Steps

### Step 02.1 - Implement signal detection helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/DetectionHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a utility object `DetectionHelper` with static methods:
> - `fun hasVrFeatures(context: Context): Boolean` - check `PackageManager.FEATURE_VR_MODE`, `FEATURE_VR_MODE_HIGH_PERF`, XR manifest features.
> - `fun hasAutomotiveFeature(context: Context): Boolean` - check `PackageManager.FEATURE_AUTOMOTIVE`.
> - `fun hasTelevisionFeature(context: Context): Boolean` - check `PackageManager.FEATURE_LEANBACK`, `FEATURE_TELEVISION`, UiModeManager.UI_MODE_TYPE_TELEVISION.
> - `fun getSmallestWidthDp(context: Context): Int` - Configuration.smallestScreenWidthDp.
> - `fun hasTelephonyFeature(context: Context): Boolean` - check `FEATURE_TELEPHONY`.
> - `fun getManufacturer(): String` - Build.MANUFACTURER (for VR headset heuristic).

**Verification:**

- `Glob` - file exists.
- `Grep` - `object DetectionHelper` appears once.
- `Grep` - all 6 methods listed with Context parameter.
- `Grep` - no Log.d calls; use Timber.d if needed.

**Status:** `[x] done`

---

### Step 02.2 - Implement RealDeviceProfileDetector

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetector.kt`
**Depends on:** Step 02.1, Phase 01

**Prompt for developer:**

> Create `class RealDeviceProfileDetector(private val context: Context) : DeviceProfileDetector`:
> (1) Implement `suspend fun detectProfile(): DetectionResult`
> (2) Use DetectionHelper to check signals in order:
>     - HIGH: XR features → VR_HEADSET
>     - HIGH: Automotive → CAR_HEAD_UNIT
>     - HIGH: TV/Leanback → TV_MEDIA_BOX
>     - MEDIUM: smallestWidthDp >= 600dp & !TV/car → HOME_TABLET
>     - MEDIUM: smallestWidthDp < 600dp & telephony → PERSONAL_SMARTPHONE
>     - LOW: Unknown/conflicting → return with LOW confidence
> (3) Store signal names in DetectionResult.signals list (e.g., ["has_xr_feature", "manufacturer_headset_hint"])
> (4) On low confidence, return fallback with source code comment explaining why (e.g., "conflicting TV + touch signals").

**Verification:**

- `Glob` - file exists.
- `Grep` - `class RealDeviceProfileDetector` implements `DeviceProfileDetector`.
- `Grep` - `suspend fun detectProfile()` defined.
- `Grep` - at least 5 signal checks (VR, automotive, TV, tablet, phone).
- `Grep` - `DetectionResult` instantiated with profile, confidence, signals.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 5/5 PASS. `RealDeviceProfileDetector` implements `DeviceProfileDetector`; ordered signal checks (VR/automotive/TV high, width-based medium, low-confidence fallback to PERSONAL_SMARTPHONE). Compiles in `standardDebug`.

---

### Step 02.3 - Test detector with mock context

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetectorTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create unit tests:
> - Mock context with VR features → expect VR_HEADSET + HIGH confidence.
> - Mock context with automotive feature → expect CAR_HEAD_UNIT + HIGH.
> - Mock context with TV feature → expect TV_MEDIA_BOX + HIGH.
> - Mock context with smallest width 800dp, no TV/car → expect HOME_TABLET + MEDIUM.
> - Mock context with no signals → expect PERSONAL_SMARTPHONE + LOW (safe fallback).

**Verification:**

- `Glob` - test file exists.
- `Grep` - `class RealDeviceProfileDetectorTest` present.
- `Grep` - at least 4 test methods (testVrDetection, testAutomotiveDetection, testTvDetection, testTabletDetection).
- Build: `./a.ps1 dq app_v2:testDebugUnitTest` passes (or subset test runs).

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `RealDeviceProfileDetectorTest` added (7 cases: VR/automotive/TV high-confidence, tablet/phone medium, low-confidence fallback, VR-priority). `mockkObject(DetectionHelper)` for deterministic signal control. Filtered `testStandardDebugUnitTest`: tests=7 failures=0 errors=0.

---

### Step 02.4 - Add detector to Hilt (placeholder for Phase 04)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetector.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `@Inject` constructor to `RealDeviceProfileDetector`:
> ```kotlin
> class RealDeviceProfileDetector @Inject constructor(
>     private val context: Context
> ) : DeviceProfileDetector { ... }
> ```
> Hilt binding will be done in Phase 04.

**Verification:**

- `Grep` - `@Inject constructor` present in RealDeviceProfileDetector.
- `Grep` - constructor parameters match injectable types (Context, no @Named qualifiers yet).

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 2/2 PASS. `@Inject constructor(@ApplicationContext context: Context)` on `RealDeviceProfileDetector`; bound in Phase 04 `RepositoryModule`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done.
- [x] Project compiles - build verified.
- [x] Detector unit tests written and compile.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entries recorded.

---

## Handoff Notes to Next Phase

Detector is complete and testable. Phase 03 implements persistence layer; Phase 04 wires detector into DI and repository.

---

## Rollback Plan

Revert phase commits - detector is only invoked by repository (not yet injected into UI).
