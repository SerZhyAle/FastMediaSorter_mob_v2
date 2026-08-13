# Research 01 - Landed S1179 surface against the planned S1433 phases 05 and 06

**Ticket:** S1446
**Date:** 2026-08-07
**Status:** Resolved
**Method:** read-only read of the landed classes and of both phase files

---

## What S1179 actually landed

**Chart:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/chart/SensorSeriesChartView.kt`, 149 LOC, flavor-neutral.

- Consumer hands it `setPoints(value: List<SensorSeriesPoint>)`.
- `SensorSeriesPoint(id: Long, takenAtMillis: Long, primaryValue: Double, secondaryValue: Double?)` - two series on one time axis, the second omitted when null.
- `var showSecondary: Boolean` - the second line differs by stroke width and dash pattern, not by colour alone.
- `val hasData: Boolean` - the host's empty-state gate, true from two points.
- Styleable `attrs_sensor_series_chart.xml`: `sensorSeriesPrimaryColor`, `sensorSeriesSecondaryColor`, both theme-attr defaulted.
- Absent: a labelled value axis, any `contentDescription` or text summary, any redraw throttling. `onDraw` scales x across the first and last timestamp and scales each series independently to its own range.

**Location source:** `app_v2/src/main/java/com/sza/fastmediasorter/data/sensors/MotionReadingSource.kt`, 116 LOC, `@Singleton`, flavor-neutral.

- `readings(): Flow<MotionReading>` - a cold `callbackFlow` over `LocationManager` GPS and network providers, 2000 ms interval, unregistering in `awaitClose`, conflated.
- `MotionReading(speedKmh, altitudeMeters, distanceDeltaMeters, takenAtMillis)` - no coordinates, no accuracy, no fix time.
- Holds no permission itself; the ask lives in `LauncherSensorPermissionManager` under `src/launcherEnabled`.
- Reports no availability state: a missing manager yields an empty flow and a disabled provider is skipped with a log line.
- Carries no satellite data at all - no `GnssStatus`, no C/N0, no constellation, no used-in-fix.

**Series persistence behind the chart:** `SensorSeriesRepository` (Room-backed) with `RecordSensorSeriesPointUseCase` (720-point cap, halving decimation) and `ResetSensorSeriesUseCase`. The series is deliberately durable across process death and is cleared only by an explicit user action.

---

## What S1433 phases 05 and 06 plan to author

- `SignalSeries` - a fixed-capacity two-minute ring with derived last, min, max and trend, and an explicit instruction to provide no persistence API of any kind.
- `WifiSignalSampler`, `CellularSignalSampler`, `BluetoothRssiSampler`, `TrafficRateSampler` - one cold flow each over its own platform API.
- `SignalChartView` plus `attrs_signal_chart.xml` - a line with a labelled value axis, a `contentDescription` carrying the last, min, max and trend as text, and redraw limited to four per second.
- `GnssSnapshot` - the satellite list with constellation, id, C/N0, used-in-fix, elevation and azimuth, plus visible and used counts, mean C/N0, nullable coordinate, accuracy and fix time, and a section-availability state.
- `GnssStatusDataSource` - `registerGnssStatusCallback` with explicit no-permission and no-hardware states.
- `GnssTrackRecorder` - an on-device track gated by the live `recordGnssTrack` setting.

---

## Verdicts

| Planned S1433 artifact | Verdict | Ground |
|---|---|---|
| `SignalSeries` | Separate | Its phase forbids any persistence API, while the S1179 series is Room-backed and deliberately durable. The two lifetime contracts are opposites, not a parameter. |
| The four samplers | Separate | S1179 wraps `LocationManager` only; there is no counterpart for `WifiManager`, `TelephonyManager`, Bluetooth RSSI or `TrafficStats`. |
| `SignalChartView` | Extend | `setPoints(List<SensorSeriesPoint>)` already draws exactly this shape. Only two named capabilities are missing from the shared view: a rendered value axis with labels, and an auto-generated text summary for `contentDescription`. |
| `attrs_signal_chart.xml` | Reuse | The two colour attributes needed are already declared in `attrs_sensor_series_chart.xml`. A second attrs file only exists if a second view class exists. |
| `GnssSnapshot` | Separate | `MotionReading` carries speed, altitude and distance delta. Nothing in the sensor models describes satellites. |
| `GnssStatusDataSource` | Separate, with a coordinate-shaped extension candidate | `registerGnssStatusCallback` is an API `MotionReadingSource` never calls, and the shared source emits no availability state. Its own ADR argues for one location source rather than one per tile, so widening `MotionReading` with coordinate, accuracy and fix time is a legitimate later move for that half only; the satellite half needs its own type regardless. |
| `GnssTrackRecorder` | Separate | S1179 persists a decimated tile history, never a coordinate track, and has no setting-gated recording to reuse. |

---

## Placement

Both landed classes sit in `src/main` and are flavor-neutral; only their permission-asking consumer is isolated under `src/launcherEnabled`. `SUPPORT_LAUNCHER` and `SUPPORT_NETWORK_MONITOR` cover the identical flavor pair, `standard` and `noLegal`. Every file in the phase-05 and phase-06 tables also targets `src/main`, so reuse raises no Rule 14 conflict; only S1433's own permission ask will need the flavor split that `LauncherSensorPermissionManager` demonstrates.
