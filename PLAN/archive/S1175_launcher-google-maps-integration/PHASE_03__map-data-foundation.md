# Phase 03 - Map Data Foundation

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3

## Objective

Provide a lifecycle-safe current-location snapshot and a keyless, cached map-image source behind domain seams.

## Steps

### Step 03.1 - Add location and map domain contracts

**Files:** new `domain/location/*`, new `domain/map/*`, new `domain/usecase/map/GetLauncherMapUseCase.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Define typed location, map snapshot, provider, and repository contracts that express permission, unavailable, fresh, and stale states without exposing Android UI objects.

**Why:** Strategic §5.3 requires independently replaceable location, tile, and reverse-label seams with useful degradation states.

**Verification:**

- Contracts compile with no Android view dependency.
- Done: `MapPoint`/`MapSnapshot`, `DeviceLocationSource`, `MapTileProvider`, `MapPlaceLabelProvider`, `MapRepository` + `MapResult` (Fresh/Stale/PermissionMissing/Unavailable), `GetLauncherMapUseCase`. No Android import in any of them; the tile travels as a file path, not a bitmap.

**Status:** `[x]` done

### Step 03.2 - Implement platform location and OSM image data

**Files:** new `data/location/*`, new `data/map/*`, `core/di/LauncherDesktopModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Read a last-known platform location off the main thread; download only a single static OSM tile image through a dedicated client with a declared app User-Agent and bounded disk cache; return stale cached content for failure.

**Why:** Strategic §3.2 and §7 require keyless tiles, seven-day cache behaviour, no prefetching, an identifying User-Agent, and no main-thread network or disk work.

**Verification:**

- Repository/cache tests cover cache hit and stale fallback.
- `a.ps1 fk` passes.
- Done: `PlatformDeviceLocationSource` (last-known fix only, no update subscription), `OsmMapTileProvider` (single tile, own `mapTiles` client with `TileUserAgent`, seven-day disk retention, no prefetch), `PlatformMapPlaceLabelProvider`, `MapRepositoryImpl` + `MapCachePolicy`, `MapModule`. `MapCachePolicyTest` runs 6 tests, 0 failures, and the Hilt graph compiles (`check-standard-fast.ps1 -Mode Unit`, exit 0).

**Status:** `[x]` done

### Step 03.3 - Audit the lifecycle and cache ownership

**Files:** Phase 03 source files
**Depends on:** Step 03.2

**Prompt for developer:**

> Run the code-audit lifecycle, concurrency, memory, and network/cache checks on the new data path before exposing it to a gadget.

**Why:** The feature introduces a long-lived data path with platform location, I/O, cache retention, and Hilt ownership.

**Verification:**

- Audit records no P0/P1 finding.
- Done: the audit found four P1s and all four are fixed - a truncating tile write (now temp file plus rename), an unbounded tile directory (now age eviction plus a 128-file cap), an empty cell after process death (now a SharedPreferences snapshot mirror), and two providers rethrowing against their own "never throws" contract. Fixed with them: the mutex held across an unbounded geocoder call (now `withTimeout`), no backoff after a 403/429 (now a six-hour cooldown), and `PermissionMissing` discarding the last picture, which was a §11.4 violation rather than a judgement call. `MapRepositoryImplTest` (6 tests) covers the degradation chain the audit found untested; it caught a float coordinate round-trip that broke cached-tile reuse.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All steps are `[x] done`.
- [x] `a.ps1 fk` passes.
