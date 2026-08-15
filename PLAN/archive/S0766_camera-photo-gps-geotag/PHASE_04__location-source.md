# Phase 04 - Location source helper

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 1

---

## Objective

Provide a lightweight, dependency-free location source that warms a fix while the camera is open and exposes the freshest cached `Location` at shutter time, without blocking the shutter. Platform `LocationManager` only (no GMS), minSdk 23.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraLocationProvider.kt` | New | ≤ 130 |

---

## Steps

### Step 04.1 - Create CameraLocationProvider

**Files:** `ui/cameracapture/helpers/CameraLocationProvider.kt`

**Prompt for developer:**

> Create a new helper class `CameraLocationProvider` (UI helper, no Hilt - instantiated by the host). API:
> - `@SuppressLint("MissingPermission") fun start(context: Context)` - idempotent; resolve `LocationManager`; seed `lastLocation` from `getLastKnownLocation(..)` over the enabled providers (prefer `GPS_PROVIDER`, fall back to `NETWORK_PROVIDER`, keep the freshest by `Location.time`); register a `LocationListener` via `requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, Looper.getMainLooper())` on each enabled provider (suggested `minTimeMs = 2000L`, `minDistanceM = 0f`); update `lastLocation` on each callback keeping the freshest fix. The caller guarantees the permission grant; wrap registration in `runCatching` and `Timber.w` on failure (capture must never crash on a location error).
> - `fun lastKnownLocation(): Location?` - returns the freshest cached fix (or null).
> - `fun stop()` - `removeUpdates(listener)` for every registered provider; clear state. Idempotent; safe to call from `onDestroy`.
>
> Keep all reads off the capture path - `lastKnownLocation()` is a field read, no I/O. No `Log.*`; Timber only. Confine state to the main thread (provider started/stopped from the host lifecycle).

**Verification:**

- `Grep` - `class CameraLocationProvider` present.
- `Grep` - `getLastKnownLocation` and `requestLocationUpdates` present.
- `Grep` - `removeUpdates` present in `stop()`.
- `Grep -n "Log\.d\(|Log\.e\("` - zero hits.
- `.\a.ps1 fk` - Kotlin compiles.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Step 04.1 is `[x] done`.
- [ ] `register`/`removeUpdates` symmetric (start/stop) - listener-symmetry rule.
- [ ] No GMS / `play-services-location` import.
- [ ] `.\a.ps1 fk` compiles.

---

## Rollback Plan

Delete the new file. No other code references it until Phase 05.
