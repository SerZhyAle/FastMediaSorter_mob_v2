# Phase 01 - Sensor availability and measurement sources

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce the readings layer: one answer to "does this device have that sensor", and three cold Flow sources - orientation, location, step counter - that run only while collected. No UI and no persistence yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] No UI decision is open here - this phase adds no view, no layout and no string.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorReading.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorCapability.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SensorAvailabilityRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/DeviceSensorAvailabilityRepositoryImpl.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/OrientationReadingSource.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/MotionReadingSource.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/StepCountReadingSource.kt` | New | ≤ 170 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/SensorModule.kt` | New | ≤ 80 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). Every file in this phase is new, so no backup sub-step applies.
>
> **Flavor placement.** Every file lands in `src/main/java/`, carries no `BuildConfig.IS_*` guard and names no gadget. These are device capabilities, not launcher features - a future non-launcher surface reads the same sources.
>
> **Landscape parity.** No layout in this phase.

---

## Steps

### Step 01.1 - Add the reading models and the capability enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorReading.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/sensors/SensorCapability.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `SensorReading.kt` add three immutable data classes carrying values already in display units, plus the timestamp each was taken at: `CompassReading` (`azimuthDegrees: Float`, `accuracy: SensorAccuracy`, `altitudeMeters: Double?`), `MotionReading` (`speedKmh: Float?`, `altitudeMeters: Double?`, `distanceDeltaMeters: Double`, `takenAtMillis: Long`) and `StepReading` (`stepsSinceBoot: Long`, `takenAtMillis: Long`). Add a `SensorAccuracy` enum with `UNRELIABLE`, `LOW`, `MEDIUM`, `HIGH` mapping the `SensorManager.SENSOR_STATUS_*` constants. In `SensorCapability.kt` add an enum with `COMPASS`, `LOCATION`, `STEP_COUNTER` and nothing else. Convert metres per second to kilometres per hour here, in the model's factory, not in a view.
>
> The reading types carry `null` for a value the device did not supply on that sample - an absent altitude fix is not zero, and a view that cannot tell the two apart will draw sea level over a mountain.

**Why:**

Strategic ADR-1 requires the readings layer to hand gadgets finished values in display units so five gadgets cannot disagree about the same sample, and §3.1.2 fixes kilometres per hour as the speed unit.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `data class CompassReading`, `data class MotionReading`, `data class StepReading` each match exactly once.
- `Grep` - `enum class SensorCapability` matches exactly once and the file contains `COMPASS`, `LOCATION`, `STEP_COUNTER`.
- `Grep` - `speedKmh` present; `speedMs` and `metersPerSecond` return zero hits across both files.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 12\12 PASS. Files: domain/model/sensors/SensorReading.kt (+90 LOC, new), domain/model/sensors/SensorCapability.kt (+14 LOC, new). Dev log recorded.

---

### Step 01.2 - Add the availability repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/SensorAvailabilityRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/DeviceSensorAvailabilityRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare `SensorAvailabilityRepository` in `domain/repository/` with one member: `fun isAvailable(capability: SensorCapability): Boolean`. Implement it in `data/sensors/DeviceSensorAvailabilityRepositoryImpl` as an `@Singleton` taking `@ApplicationContext Context`, resolving each capability once and caching the result - hardware does not appear at run time. Resolve `COMPASS` as `SensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null`, falling back to both `TYPE_ACCELEROMETER` and `TYPE_MAGNETIC_FIELD` being present. Resolve `LOCATION` as `PackageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)`. Resolve `STEP_COUNTER` as `Build.VERSION.SDK_INT >= 29 && getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null`. Answer only the hardware question - whether a permission was granted is a separate axis this repository must not fold in, or a denied grant would delete the gadget from the picker instead of showing an empty state.

**Why:**

Strategic ADR-3 makes "no sensor - no gadget in the list" one rule of the availability layer rather than five copies inside five gadgets, and §3.2 puts the step counter behind API 29 because that is where `ACTIVITY_RECOGNITION` became a runtime permission.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `interface SensorAvailabilityRepository` matches exactly once.
- `Grep` - `TYPE_ROTATION_VECTOR`, `TYPE_STEP_COUNTER`, `FEATURE_LOCATION` each present in the impl.
- `Grep` - `SDK_INT >= 29` or `SDK_INT >= Build.VERSION_CODES.Q` present in the impl.
- `Grep` - `checkSelfPermission` returns zero hits in both files - availability is hardware only.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 7\7 PASS. Files: domain/repository/SensorAvailabilityRepository.kt (+17 LOC, new), data/sensors/DeviceSensorAvailabilityRepositoryImpl.kt (+53 LOC, new). Resolved eagerly behind `by lazy` (SYNCHRONIZED) rather than a mutable cache map, because a `@Singleton` read from several gadget views concurrently would race on `getOrPut`. Dev log recorded.

---

### Step 01.3 - Add the orientation source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/OrientationReadingSource.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `OrientationReadingSource` as an `@Singleton` exposing `fun readings(): Flow<CompassReading>` built with `callbackFlow`. Register a `SensorEventListener` on `TYPE_ROTATION_VECTOR` at `SENSOR_DELAY_UI` inside the builder and call `unregisterListener` in `awaitClose`, so the pair cannot be split. Convert the rotation vector with `SensorManager.getRotationMatrixFromVector` then `getOrientation`, normalise the azimuth into 0..360 and emit it with the accuracy reported by `onAccuracyChanged`. Emit `SensorAccuracy.UNRELIABLE` until the first accuracy callback arrives. Apply `conflate()` so a slow collector drops stale samples instead of queueing them, and leave `altitudeMeters` null - altitude comes from the location source, not from orientation.

**Why:**

Strategic §3.2 requires every subscription to be paired with its unsubscribe or the `assert-listener-symmetry` gate refuses the build, and §7 names a sensor subscription outliving the desktop as this feature's most likely battery defect; `awaitClose` is the only place that pairing cannot drift apart.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class OrientationReadingSource` matches exactly once.
- `Grep` - `callbackFlow` present.
- `Grep` - `\.registerListener\(` and `\.unregisterListener\(` each match exactly once. Anchored on the call, because a bare `registerListener` also matches inside `unregisterListener`.
- `Grep` - `awaitClose` present.
- `Grep` - `conflate()` present.
- `Grep` - `GlobalScope` returns zero hits.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8\8 PASS. Files: data/sensors/OrientationReadingSource.kt (+84 LOC, new). Absent sensor returns `emptyFlow()` from outside the builder rather than closing inside it, because a `callbackFlow` block that returns without reaching `awaitClose` throws.
- 2026-08-06 - CLOSURE FAIL. `post-change` exit 1: `assert-detekt` scoped - `ReturnCount - Function readings has 3 return statements which exceeds the limit of 2` at OrientationReadingSource.kt:32. The step's own predicates passed; the closure gate did not, so the step is back to `[~]` - flipping it to done on passing predicates alone would have recorded a step whose closure never went green. Fix: collapse the two guard returns and the builder return into one expression. Predicates also tightened this run - `registerListener` matched inside `unregisterListener`, so both are now anchored on the call.
- 2026-08-06 - Re-closed after the fix: `readings()` is now one `return` over an `if/else`, and `post-change` exits 0 with a bare `post-change: PASS`. Dev log recorded.

---

### Step 01.4 - Add the motion source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/MotionReadingSource.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `MotionReadingSource` as an `@Singleton` exposing `fun readings(): Flow<MotionReading>` built with `callbackFlow`, mirroring the provider handling in `CameraLocationProvider` - platform `LocationManager` only, no GMS, `GPS_PROVIDER` then `NETWORK_PROVIDER`, every platform call wrapped so a provider that refuses cannot crash the collector. Call `requestLocationUpdates` on start and `removeUpdates` in `awaitClose`. Emit `speedKmh` from `Location.speed` only when `hasSpeed()` is true and `altitudeMeters` from `Location.altitude` only when `hasAltitude()` is true, leaving each null otherwise. Compute `distanceDeltaMeters` as `previous.distanceTo(current)` against the previous emitted fix, emitting 0 for the first fix, and discard a delta whose fix has `accuracy` worse than 50 metres so a stationary device does not accumulate drift as distance. Take the permission grant as the caller's guarantee and annotate the registration `@SuppressLint("MissingPermission")`, as `CameraLocationProvider` does. Never request background location and never hold a fix in a field outside the flow.

**Why:**

Strategic §5.2 routes speed and altitude out of one location stream so the two gadgets cannot disagree, §6 item 5 forbids any collection that would pull in a background-location declaration, and §7 names GPS drift accumulating as distance while parked as the failure that would make the trip meter untrustworthy.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class MotionReadingSource` matches exactly once.
- `Grep` - `callbackFlow` and `awaitClose` each present.
- `Grep` - `\.requestLocationUpdates\(` and `\.removeUpdates\(` each match exactly once. Anchored on the call, because the failure log names the same method in a string literal.
- `Grep` - `hasSpeed()` and `hasAltitude()` each present.
- `Grep` - `distanceTo` present.
- `Grep` - `ACCESS_BACKGROUND_LOCATION` and `FusedLocationProviderClient` each return zero hits.
- `Grep` - `Log\.d\(` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 9\9 PASS. Files: data/sensors/MotionReadingSource.kt (+110 LOC, new). Two returns exactly, at detekt's `ReturnCount` limit, after the step 01.3 finding. A fix coarser than 50 m contributes zero distance rather than being dropped entirely, so a poor fix still updates speed and altitude while only its distance is distrusted. `post-change: PASS WITH ADVISORIES (1)` - `detekt-preflight` (lexical) reported `MagicNumber` on the two `-> 0.0` arms at lines 103-104; the authoritative `assert-detekt` gate passed on the same file, and `0.0` there is the identity "no distance travelled", so naming it as a constant would add noise without adding meaning. Advisory read and dismissed, not silently ignored. Dev log recorded.

---

### Step 01.5 - Add the step source and the Hilt module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/StepCountReadingSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/SensorModule.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `StepCountReadingSource` as an `@Singleton` exposing `fun readings(): Flow<StepReading>` over `TYPE_STEP_COUNTER` at `SENSOR_DELAY_NORMAL`, registered inside `callbackFlow` and unregistered in `awaitClose`. Emit the counter value the platform reports verbatim as `stepsSinceBoot` - it is steps since last reboot and the caller decides what to subtract; computing a daily total here would need persistence this source must not own. Yield `emptyFlow()` when `SensorAvailabilityRepository.isAvailable(STEP_COUNTER)` is false or the sensor is absent, so a caller on a device without the sensor gets no emissions rather than an exception.
>
> Write the three guards - unavailable capability, no `SensorManager`, no sensor - as **one** `return` over an `if/else`, not as three early returns. detekt's `ReturnCount` caps a function at 2 and this exact shape failed closure on `OrientationReadingSource` in step 01.3. Add `SensorModule` in `di/`, `@InstallIn(SingletonComponent::class)`, binding `SensorAvailabilityRepository` to `DeviceSensorAvailabilityRepositoryImpl` with `@Binds`; the three sources need no `@Provides` because each is an `@Singleton` with an `@Inject constructor`.

**Why:**

Strategic §6 item 2 fixes the system counter as the only step source and forbids an accelerometer algorithm of our own, and §3.2 states a device without the sensor must degrade to nothing offered rather than to a broken gadget.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class StepCountReadingSource` matches exactly once.
- `Grep` - `TYPE_STEP_COUNTER` present in the source.
- `Grep` - `registerListener` and `unregisterListener` each match exactly once in the source.
- `Grep` - `emptyFlow` present in the source.
- `Grep` - `TYPE_ACCELEROMETER` returns zero hits in the source - no algorithm of our own.
- `Grep` - `@Binds` and `SensorAvailabilityRepository` each present in `SensorModule.kt`.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 8\8 PASS. Files: data/sensors/StepCountReadingSource.kt (+64 LOC, new), di/SensorModule.kt (+26 LOC, new). Written in the same CODE.LOCK window as the step 01.3 fix - the queue wait was seven minutes and both edits were ready - but verified and closed separately, so each step's verdict still stands on its own run. Guards written as one `if/else` from the start, applying the step 01.3 `ReturnCount` finding. `post-change: PASS`. Dev log recorded.

---

### Step 01.6 - Add the observe use cases

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ObserveCompassUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ObserveMotionUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/sensors/ObserveStepCountUseCase.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add one use case per source, each an `@Inject constructor` class with a single `operator fun invoke()` returning the source's flow unchanged: `ObserveCompassUseCase` over `OrientationReadingSource`, `ObserveMotionUseCase` over `MotionReadingSource`, `ObserveStepCountUseCase` over `StepCountReadingSource`. Add no logic - these exist so a gadget view depends on `domain/usecase` rather than reaching into `data/sensors`, which is the layering every other gadget already follows.

**Why:**

Added by the Phase 01 boundary audit. CLAUDE.md section 8 fixes the chain as `UI -> ViewModel -> UseCase -> Repository -> DataSource`, and both gadget precedents obey it - `WeatherGadget` takes `GetLauncherWeatherUseCase`, and S1178's accepted plan injects its four use cases into `TechnicalGadget`. Without this step, Phases 03 to 05 would have gadget views collecting a `data/sensors` source directly, skipping two layers.

**Verification:**

- `Glob` - all three files exist.
- `Grep` - `operator fun invoke` present in each.
- `Grep` - `OrientationReadingSource`, `MotionReadingSource`, `StepCountReadingSource` each appear in exactly one use case.
- `Grep` - `data.sensors` returns zero hits under `app_v2/src/launcherEnabled/` once Phases 03 to 05 land.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3\3 PASS (the fourth predicate is deferred by construction - it cannot run until Phases 03 to 05 exist). Files: domain/usecase/sensors/ObserveCompassUseCase.kt, ObserveMotionUseCase.kt, ObserveStepCountUseCase.kt (+19 LOC each, new). Importing a `data` class from a `domain` use case looked like a second layering violation, so it was checked rather than assumed: 87 existing use cases in this repo do exactly that, so these three follow the codebase rather than inventing a stricter rule for one feature. `post-change: PASS`. Dev log recorded.

---

## Phase-Boundary Audit (2026-08-06)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 4 not applicable, this phase touches no Room surface.

- **P1 - layering, fixed here as step 01.6.** The phase's three sources are correct data-layer components, but the plan had Phases 03 to 05 collecting them straight from a gadget view - `UI -> DataSource`, skipping UseCase and Repository. Both in-repo precedents go through a use case. Fixed by adding step 01.6 and by rewriting the Phase 03/04/05 prompts to name the use cases.
- **P3 - sensor callbacks land on the main thread.** `OrientationReadingSource` registers without a `Handler`, so `SensorManager` delivers on the main looper and the rotation-matrix maths runs there at `SENSOR_DELAY_UI`; `MotionReadingSource` passes `Looper.getMainLooper()` explicitly, as `CameraLocationProvider` does. The per-event cost is a 9-element matrix multiply and one `distanceTo`, which is not measurable against a home screen's frame budget, and moving to a background `HandlerThread` would add a thread to own and shut down. Left as is deliberately; revisit only if the desktop shows jank with a compass tile placed.
- Layer 2 otherwise clean: no `GlobalScope`, no hardcoded dispatcher, no blocking call, every subscription paired with its unsubscribe inside `awaitClose`, `conflate()` on all three sources, and the one piece of shared mutable state (`availability`) is behind `by lazy` in SYNCHRONIZED mode rather than a racy `getOrPut`.
- Layer 3 clean: sources hold `@ApplicationContext` only, no view or activity reference, the two reused `FloatArray` buffers are per-subscription and die with the flow, and no cache is introduced.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `BUILD SUCCESSFUL in 1m 30s`, run after step 01.6. `hiltJavaCompileStandardDebug` is in that run, so the new `@Binds` is graph-validated rather than merely compiled.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `assert-listener-symmetry` reports `new imbalance 0` on every step's changed-file delta, including all three sources.
- [x] Dev log entry added via `post-change.ps1` per step. Note: the facade writes one changelog row per invocation naming the first file, while its gates judge every file passed - so the six rows cover the phase, not one row per file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` ran inside `post-change` (2497 records).
- [x] Phase-boundary audit run - see "Phase-Boundary Audit" above. One P1 found and fixed in-phase as step 01.6; one P3 recorded as a deliberate choice.

---

## Handoff Notes to Next Phase

- Every source is cold: nothing is registered until a collector arrives, and everything is unregistered when the last one leaves. A consumer that wants a reading while invisible has to break this contract deliberately, which no phase of this ticket does.
- Availability answers hardware only. Permission state is a second, independent axis handled in Phase 03 and Phase 05.
- `MotionReading.distanceDeltaMeters` is a per-sample delta, not a running total. Phase 02 owns the accumulation.

---

## Rollback Plan

Revert phase commit(s) - every file is new, no existing class changes behaviour, no data migration and no user-facing surface is touched.
