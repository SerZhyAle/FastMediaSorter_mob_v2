# Phase 05 - Signal sampling and charts

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06, Phase 08

---

## Objective

Sample the four live signal sources into a bounded in-memory window and render each as one compact chart with an accessible text summary. No new dependency and no new drawing class: the chart is the shared `SensorSeriesChartView`, which S1446 extended to cover this case.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/networkmonitor/SignalSeries.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/WifiSignalSampler.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/CellularSignalSampler.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/BluetoothRssiSampler.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/TrafficRateSampler.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/networkmonitor/SignalSeriesTest.kt` | New | ≤ 200 |

> No chart class and no chart attributes are authored here. `SensorSeriesChartView` in `ui/common/chart/` already draws this shape and, since S1446, carries the labelled value axis and the numeric summary a full-screen section needs. The consumer that hosts the section binds it; the file that does so belongs to the phase that builds that section.

---

## Steps

### Step 05.1 - Model the bounded sample window

**Files:** `domain/model/networkmonitor/SignalSeries.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `SignalSeries` as a fixed-capacity ring of timestamped samples covering roughly two minutes, with derived `last`, `min`, `max` and a trend direction. Adding beyond capacity drops the oldest sample. Provide no persistence API of any kind.

**Why:**

Strategic §3.2 bounds the chart window in memory and forbids writing signal history to disk, so the absence of a persistence path is the constraint rather than a decision left to the caller.

**Verification:**

- `Grep` - a capacity constant is declared.
- `Grep` - `last`, `min`, `max`, `trend` all exposed.
- `.\a.ps1 fu` - `SignalSeriesTest` passes, including an overflow test.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS. `SAMPLE_CAPACITY` declared; `last`, `min`, `max`, `trend` exposed; targeted `check-standard-fast.ps1 -Mode Unit -Tests SignalSeriesTest` BUILD SUCCESSFUL, XML `tests=9 failures=0 errors=0`. Files: `domain/model/networkmonitor/SignalSeries.kt` (new, 73 LOC), `src/test/.../SignalSeriesTest.kt` (new, 107 LOC). The window bounds by sample count, not by age - `SAMPLE_CAPACITY = 120` is two minutes at the one-per-second cadence `ConnectivitySnapshotDataSource.RESAMPLE_INTERVAL_MS` already imposes. Dev log recorded.

---

### Step 05.2 - Sample Wi-Fi and traffic rate

**Files:** `data/networkmonitor/WifiSignalSampler.kt`, `data/networkmonitor/TrafficRateSampler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Emit the connected network's RSSI and link speed as a flow, and the device's up and down traffic rate from the Android traffic counters as a second flow. Both register on collection and stop on cancellation. Neither may call a Wi-Fi scan API.

**Why:**

Research artifact 02 permits the chart only for the connected Wi-Fi network and forbids presenting a scan as a passive signal monitor, and strategic §3.2 stops all observation when the screen is not visible.

**Verification:**

- `Grep` - `startScan` returns zero hits in both files.
- `Grep` - `awaitClose` present in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `startScan` 0 hits across both files; `awaitClose` present in both. Files: `data/networkmonitor/WifiSignalSampler.kt` (new, 118 LOC), `data/networkmonitor/TrafficRateSampler.kt` (new, 106 LOC). The Wi-Fi sampler reads `WifiInfo` itself instead of borrowing `ConnectivitySnapshotDataSource`, which re-enumerates every visible network and its link properties per tick for two numbers this chart needs. The traffic sampler measures its interval on `SystemClock.elapsedRealtime` and stamps the sample with the wall clock, so a clock correction cannot produce a negative or spiked rate; the first tick emits nothing because a rate needs two counter reads. First draft of the Wi-Fi KDoc named `startScan` in prose and so failed its own zero-hit predicate - reworded, the predicate now measures the code rather than its own comment. Dev log recorded.

---

### Step 05.3 - Sample cellular signal per subscription

**Files:** `data/networkmonitor/CellularSignalSampler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Register one `TelephonyCallback.SignalStrengthsListener` per visible subscription on API 31+ and the legacy `PhoneStateListener` equivalent below it, emitting one series per SIM. Emit `SectionAvailability.NoPermission` when `READ_PHONE_STATE` is missing and omit a series entirely for an absent SIM - never emit zero-filled samples. Unregister every callback when collection ends.

**Why:**

Research artifact 02 requires a separate callback and a separate series per subscription, and states an unavailable source must render as an explicit unavailable state, because a zero-filled series reads as "no signal" when the truth is "no data".

**Verification:**

- `Grep` - `SignalStrengthsListener` present.
- `Grep` - `unregister` present.
- `Grep` - `NoPermission` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - AUDIT-FIX: a per-SIM registration refused with `SecurityException` escaped the `callbackFlow` builder, so `awaitClose` never ran and every callback registered before it stayed live. Registration now returns a no-op teardown on a refusal. Verification 3/3 re-run PASS; `a.ps1 fk` exit 0. The same pass dropped the deprecated `PhoneStateListener` import - an import cannot carry the function's `@Suppress`, so the class is named in full inside the legacy branch and the compile is now warning-free.
- 2026-08-09 - Verification 3/3 PASS, each on a code hit rather than a comment. `SignalStrengthsListener` at line 143, `unregister` at 81/149/224, `NoPermission` at 99. File: `data/networkmonitor/CellularSignalSampler.kt` (new, 236 LOC). Registration is symmetrical through a `SignalRegistration` seam, so the API 31+ `TelephonyCallback` and the legacy `PhoneStateListener` close the same way and `awaitClose` needs no branch. An unknown ASU and `CellInfo.UNAVAILABLE` both map to null and skip the sample, so an unreporting SIM contributes no series instead of a zero-filled one. Dev log recorded.

---

### Step 05.4 - Sample the selected connected Bluetooth device

**Files:** `data/networkmonitor/BluetoothRssiSampler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Read RSSI only for a device the user explicitly selected from the connected set, through its GATT connection, requiring `BLUETOOTH_CONNECT` on API 31+. Emit nothing at all when no device is selected. Do not scan.

**Why:**

Research artifact 02 excludes advertising-device RSSI because it requires a scan, and requires the series to be omitted rather than blank when no device is selected, so the selection is a precondition of the sampler and not a UI filter.

**Verification:**

- `Grep` - `startScan`, `BluetoothLeScanner`, `startDiscovery` return zero hits.
- `Grep` - `BLUETOOTH_CONNECT` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `startScan` / `BluetoothLeScanner` / `startDiscovery` 0 hits; `BLUETOOTH_CONNECT` referenced at line 184. File: `data/networkmonitor/BluetoothRssiSampler.kt` (new, 199 LOC). No selection returns `emptyFlow()`, so the precondition is expressed by opening no connection at all rather than by filtering afterwards. The device is matched against `getConnectedDevices(GATT)` rather than resolved from the address, because `getRemoteDevice` answers for any well-formed address and connecting to an absent one would hang pending instead of reporting `NoNetwork`. Dev log recorded.

---

### Step 05.5 - Bind the shared chart to a `SignalSeries`

**Files:** `domain/model/networkmonitor/SignalSeries.kt`, `src/test/.../networkmonitor/SignalSeriesTest.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Do not author a chart. Give `SignalSeries` a mapping to `List<SensorSeriesPoint>` - each sample becomes one point with its timestamp and its value as `primaryValue`, `secondaryValue` null - so the shared `SensorSeriesChartView` renders it unchanged. The section that hosts the chart sets `showValueAxis = true` and builds its `contentDescription` from `summary()`, which returns `last`, `min`, `max` and a trend as numbers; the wording and the units are the consumer's, because the view holds no strings. Rate-limit at the source: emit at most 4 samples per second rather than throttling redraws, since the view redraws only when it is given new points. Add no charting dependency.

**Why:**

Strategic §3.2 requires a text equivalent for every chart and a bounded redraw rate to protect the battery; S1446 §9 ADR-2 rules that a second chart class would diverge from the first at its first change, and its research artifact `PLAN/S1446_reconcile-s1433-charts-with-s1179/research/01__landed-s1179-vs-planned-s1433.md` establishes that the landed S1179 view already draws this point shape.

**Verification:**

- `Grep` - no feature-local chart class and no feature-local chart attributes file are created anywhere in `app_v2/src` by this phase.
- `Grep` - `SensorSeriesPoint` referenced in `SignalSeries.kt`.
- `Grep` - no new dependency line added to `app_v2/build.gradle.kts` by this phase.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS. `SensorSeriesPoint` referenced in code at `SignalSeries.kt:86`; the only chart class and styleable in the tree are the pre-existing `SensorSeriesChartView` / `attrs_sensor_series_chart.xml` and the S1179 gadget, none of them authored here; `app_v2/build.gradle.kts` is not in this phase's changed set, so no dependency line was added. `SignalSeriesTest` re-run after the change: `tests=14 failures=0 errors=0`. Files: `domain/model/networkmonitor/SignalSeries.kt` (+61 LOC, 134 total), `src/test/.../SignalSeriesTest.kt` (rewritten, 165 LOC).
- 2026-08-09 - Plan corrected, one defect, before the edit: the step listed only `SignalSeries.kt` under Files, but the 4-samples-per-second cap is enforced in `append`, which is exactly what `SignalSeriesTest` pins - the test file was added to the step's Files rather than left to fail outside the step's scope. The cap lives in the window and not in the view because all four sources pass through `append`, so a later sampler cannot outrun it; the previous behaviour of accepting every sample is gone, and the test now spaces its fixtures by `MIN_SAMPLE_INTERVAL_MS`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `check-standard-fast.ps1 -Mode Unit` compiled `compileStandardDebugKotlin` green with all six files in the tree (BUILD SUCCESSFUL, 2026-08-09 10:32), and `a.ps1 fk` then reported that task `UP-TO-DATE` with exit 0.
- [x] `Grep` for `TODO(phase-05)` returns zero hits - 0 occurrences across `app_v2/src`.
- [x] `assert-neuroslop` - PASS on every step closure, all dimensions at or below baseline, zero new occurrences over the changed files.
- [x] Dev log entry added for the phase - five entries, one per step.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See the audit note below.

## Phase-boundary audit - 2026-08-09

Layers 1, 2 and 3 of `docs/CODE_AUDIT_PROTOCOL.md` (no Room surface in this phase, so Layer 4 does not apply).

- **P1, fixed here** - `CellularSignalSampler` registered one callback per SIM inside the `callbackFlow` body with no guard. A `SecurityException` from the second subscription would have escaped the builder, so `awaitClose` would never have run and the first SIM's callback would have stayed registered for the life of the process. Registration now returns a no-op teardown on a refusal, which is the same "refused despite a granted permission" shape the rest of the class already uses.
- **P2, recorded not fixed** - no sampler sets a dispatcher. That matches `ConnectivitySnapshotDataSource` and the layering: `NetworkMonitorRepositoryImpl` is where `.flowOn(Dispatchers.IO)` belongs. Carried into the handoff notes so a section does not collect binder IPC on the main thread.
- Layer 1 - every file sits in its layer (`domain/model` for the window, `data/networkmonitor` for the four samplers), all are far below the line budget, and none reads a `BuildConfig` flavor flag.
- Layer 3 - teardown is symmetrical in all four samplers: tickers cancelled, telephony callbacks unregistered, the GATT client disconnected and closed, and nothing is retained by the singleton between collections - the per-collection state lives inside the flow.

---

## Handoff Notes to Next Phase

`SensorSeriesChartView` is the single chart widget for every section, including the GNSS charts in Phase 06 - shared with the S1179 launcher gadgets rather than owned by this feature (S1446). Every sampler is cold: nothing runs until a screen collects it.

- Every sampler here is a data source and applies no `flowOn` of its own, matching `ConnectivitySnapshotDataSource`. The dispatcher is the consumer's decision and `NetworkMonitorRepositoryImpl.observeSnapshot` already sets the precedent - it ends in `.flowOn(Dispatchers.IO)` because every read behind these flows is binder IPC. A section in Phase 07 or 08 that collects a sampler directly on the main thread would put that IPC on the main thread once a second.
- The four-per-second cap lives in `SignalSeries.append`, not in the view and not in the samplers. A future source needs no throttle of its own; it also cannot bypass this one.
- `BluetoothRssiSampler.observe` takes the selected device address and returns an empty flow when it is null, so Phase 08 must pass the user's selection rather than filter afterwards.

---

## Rollback Plan

Revert phase commit(s) - new samplers and a mapping onto an existing view, not yet referenced by any layout.
