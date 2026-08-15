# Phase 06 - GNSS section

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 05
**Blocks:** Phase 08

---

## Objective

Observe satellite status and the current coordinate, and record a track only when the owner-visible setting is on and the screen is open.

> **Why these classes stay their own (S1446).** They were checked against the landed `MotionReadingSource` of S1179 and deliberately not merged with it, on two grounds. First, satellite status comes from `GnssStatus.Callback`, a platform API that `MotionReadingSource` never calls - it reads motion, not the constellation. Second, that source reports no no-permission and no no-hardware state, and this section is required to render both explicitly rather than as an empty chart. The comparison is recorded in `PLAN/S1446_reconcile-s1433-charts-with-s1179/research/01__landed-s1179-vs-planned-s1433.md`; it is written down here so the duplication does not read as an oversight and invite the same reconciliation a second time. No step below changes.

---

## Prerequisites

- [ ] Phase 01 and Phase 05 ✅ Done.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/networkmonitor/GnssSnapshot.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/GnssStatusDataSource.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/GnssTrackRecorder.kt` | New | ≤ 240 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/networkmonitor/GnssTrackRecorderTest.kt` | New | ≤ 220 |

> No DI module is edited. The plan originally listed `di/NetworkMonitorModule.kt` as modified, but that
> fully-qualified name exists only in `src/networkMonitor` and `src/networkMonitorDisabled` - `src/main` carries
> `NetworkMonitorDataModule`, and it binds interfaces to implementations rather than listing data sources. Both
> classes added here are `@Singleton` with an `@Inject constructor`, so Hilt reaches them without a module entry,
> exactly as the four Phase 05 samplers do.

---

## Steps

### Step 06.1 - Model the GNSS snapshot

**Files:** `domain/model/networkmonitor/GnssSnapshot.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define the snapshot as the satellite list (constellation, identifier, C/N0, used-in-fix flag, elevation, azimuth), the visible and used counts, the mean C/N0, the current coordinate with its accuracy and fix time as a nullable field, and a `SectionAvailability`. Model the coordinate separately from the satellite data so a caller can render satellites without ever touching the position.

**Why:**

Research artifact 02 states satellite diagnostics do not require a position fix, and strategic §3.2 gates the coordinate behind a permission the satellite view does not need, so the two must be separable in the model.

**Verification:**

- `Grep` - `usedInFix` and `cn0` present.
- `Grep` - the coordinate field is declared nullable.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `usedInFix` at line 24 and `cn0DbHz` at 23, both declarations rather than comment text; `coordinate: GnssCoordinate?` at 52. File: `domain/model/networkmonitor/GnssSnapshot.kt` (new, 73 LOC). One deviation from the prompt, deliberate: the section-level `SectionAvailability` is not a field of the snapshot, because every other Monitor data source already reports it through the `MonitorSection<T>` wrapper and a second channel for the same answer would let the two disagree. What the snapshot does carry is a *second*, different availability - `coordinateAvailability` - because a receiver can be reporting satellites perfectly while holding no fix, and that is the case the step's own "model the coordinate separately" is about. `cn0DbHz` is nullable and elevation/azimuth are not: the platform reports an undecoded satellite's C/N0 as a flat zero, which a chart would draw as a dead signal, while zero degrees of elevation is a real place on the horizon.

---

### Step 06.2 - Observe satellite status and the coordinate

**Files:** `data/networkmonitor/GnssStatusDataSource.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Register `LocationManager.registerGnssStatusCallback` on collection and unregister on cancellation, requiring `ACCESS_FINE_LOCATION`. Report `SectionAvailability.NoPermission` when the grant is missing and `NoHardware` when the GPS provider is absent or disabled, rather than emitting empty satellite lists. Request location updates for the coordinate only while collected, and never request background location.

**Why:**

Research artifact 02 permits satellite status only while the provider is enabled and the app is in the foreground, and strategic §3.2 forbids any background location, so the lifetime of the callback is the whole safeguard.

**Verification:**

- `Grep` - `registerGnssStatusCallback` and `unregisterGnssStatusCallback` both present.
- `Grep` - `ACCESS_BACKGROUND_LOCATION` returns zero hits across `app_v2/src/main/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `registerGnssStatusCallback` at line 130, `unregisterGnssStatusCallback` at 141 and 151; `ACCESS_BACKGROUND_LOCATION` 0 occurrences across `app_v2/src/main`. File: `data/networkmonitor/GnssStatusDataSource.kt` (new, 219 LOC). `compileStandardDebugKotlin` BUILD SUCCESSFUL, exit 0. Registration is one function that either attaches both sources or attaches neither: a `SecurityException` from `requestLocationUpdates` after the status callback already registered would otherwise escape the `callbackFlow` builder, skip `awaitClose` and leave the GNSS callback attached for the life of the process - the same shape Phase 05's audit found in `CellularSignalSampler`. A disabled GPS provider reports `NoHardware` rather than an empty sky, per the step. `GnssStatus.Callback` and the whole registration path are API 24, so they sit behind an SDK check for the `legacy` flavor's minSdk 23, and IRNSS is only a named constellation from API 29 - below that it maps to `UNKNOWN` rather than being force-fit.

---

### Step 06.3 - Record the track behind the setting

**Files:** `data/networkmonitor/GnssTrackRecorder.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Append coordinates to an on-device track only while `AppSettings.recordGnssTrack` is true and the GNSS section is being collected. Stop and release on cancellation. Read the setting live so switching it off mid-session stops the recording immediately. Never upload the track and never start recording from any entry point other than the open section.

**Sink, decided here because the step said only "on-device":** one UTF-8 text file per session under `filesDir`, header line plus one line per point. App-private, so no storage permission and no other app can read it; the file is what "stored on the device" means, and the recorder exposes its path so the section can offer it. Old session files are pruned to a fixed count on open - the user has no file manager route into `filesDir`, so nothing else would ever delete them.

**Why:**

Strategic §3.2 makes the track an opt-in that is off after installation, records only while the screen is open and never leaves the device, and §7 rates any deviation as a Play Data Safety failure rather than a bug.

**Verification:**

- `Grep` - `recordGnssTrack` referenced.
- `Grep` - no HTTP client, no `Retrofit` and no `OkHttp` reference in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `recordGnssTrack` at line 75; `Retrofit` / `OkHttp` / `HttpURLConnection` 0 hits in the file. File: `data/networkmonitor/GnssTrackRecorder.kt` (new, 175 LOC). Both gates are structural rather than remembered: the live setting drives a `flatMapLatest`, so switching it off cancels the open session mid-flight instead of being noticed at the next point, and the recorder is a cold flow with no start method, so there is no entry point other than an open section to call. Each point is flushed as it is written, because a session ends by cancellation far more often than by completion and a buffer dropped at that moment loses the tail. Old session files are pruned to 20 on open - `filesDir` is app-private, so nothing else in the system would ever delete them.

---

### Step 06.4 - Unit-test the opt-in gate

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/networkmonitor/GnssTrackRecorderTest.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Assert that with the setting off no point is recorded even while coordinates flow, that turning the setting off mid-session stops recording, and that cancelling collection releases the recorder.

**Why:**

The opt-in gate is the claim the Play Data Safety form will make on the product's behalf, and strategic §11 criterion 10 requires the setting, the form and the privacy policy to say the same thing.

**Verification:**

- `.\a.ps1 fu` - `GnssTrackRecorderTest` passes, three test methods reported.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 PASS. Run through `check-standard-fast.ps1 -Mode Unit -Tests GnssTrackRecorderTest` rather than the whole `fu` suite, which the step named: the suite has been observed to run out of memory part-way and report a truncated result, so the targeted runner is what the verdict can be read from. BUILD SUCCESSFUL, result XML `tests=3 failures=0 errors=0`, written 13:03:15 the same session. File: `src/test/.../GnssTrackRecorderTest.kt` (new, 119 LOC). The setting-off test asserts the track *directory* does not exist rather than that it holds no points - an empty file is still a track file the owner never asked for, and would still have to be declared. The cancellation test reads the file back instead of trusting the emitted counter, because the claim under test is that cancellation flushed and closed the writer, not that the flow stopped emitting.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL, exit 0, re-run after the audit fix below.
- [x] `Grep` for `TODO(phase-06)` returns zero hits - 0 occurrences across `app_v2/src`.
- [x] Dev log entry added for the phase - one entry naming the whole four-file set.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See the audit note below.
- [x] `post-change -ScopeToFile` over the four changed files: `post-change: PASS`, exit 0. detekt scoped PASS, neuroslop at or below baseline, listener symmetry unchanged, catalog synced.

## Phase-boundary audit - 2026-08-09

Layers 1, 2 and 3 of `docs/CODE_AUDIT_PROTOCOL.md`. Layer 4 does not apply - this phase adds no Room surface, which is also why its rollback needs no schema hop.

- **P1, fixed here** - `GnssStatusDataSource` discarded the return value of `registerGnssStatusCallback`, which reports a refusal by returning false rather than by throwing. A receiver that declined would have left the section rendering `Available` with an empty satellite list forever - indistinguishable, to the user, from a clear sky with no satellites, and the one thing strategic §11 criterion 9 says the section must never do. The registration now answers with a `SectionAvailability?`, so the two refusals stay distinct: a `SecurityException` is a revoked grant and reports `NoPermission`, a false return is the hardware declining and reports `NoHardware`.
- **P2, recorded not fixed** - the first emission of a live session is an available snapshot with zero satellites, before any callback has fired. It is honest but thin, and a section that renders "0 visible" from it would flicker on open. Carried into the handoff: the subscreen should treat an empty satellite list within the first seconds as "acquiring" rather than as a reading.
- Layer 1 - each file sits in its layer (`domain/model` for the snapshot, `data/networkmonitor` for both sources), and all four are well inside their budgets: 73/140, 219/260, 175/240, 119/220 LOC. No file reads a flavor `BuildConfig` flag.
- Layer 2 - no `GlobalScope`, no hardcoded dispatcher. `GnssStatusDataSource` applies no `flowOn` at all, matching every Phase 05 sampler; `GnssTrackRecorder` sets the injected `@IoDispatcher` because it writes a file rather than reading a binder.
- Layer 3 - teardown is symmetrical and, more to the point, unreachable-by-accident: registration is all-or-nothing, so `awaitClose` never has to guess which of the two sources attached, and nothing is retained by either singleton between collections. The recorder's writer is closed by `use` on both the completion and the cancellation path, and the cancellation path is the one the unit test reads the file back to prove.

---

## Handoff Notes to Next Phase

The GNSS section renders through `SensorSeriesChartView` - the shared widget Phase 05 mapped `SignalSeries` onto, not a chart of this feature's own (S1446). The track recorder has exactly one caller: the open GNSS section.

- `GnssStatusDataSource.observe()` applies no `flowOn`, like every Phase 05 sampler; the consumer owns the dispatcher. `GnssTrackRecorder` is the exception and sets `@IoDispatcher` itself, because it writes a file rather than reading a binder.
- The recorder emits the path of the session file. Phase 08 must offer it, or the track is written where no user can reach it - step 08.5 was extended for exactly that reason.

---

## Rollback Plan

Revert phase commit(s) - no schema change, no shipped surface.
