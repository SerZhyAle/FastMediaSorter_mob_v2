# Phase 01 - Metrics foundation

**Strategic spec:** [`../S1178_launcher-system-status-widgets.md`](../S1178_launcher-system-status-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Introduce the typed device-metric layer in shared code - the unknown-capable value type, the polling-period contract, and the storage and memory/uptime metrics - with no UI and no gadget yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - the 2026-08-05 storage note is Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStorageVolumesUseCase.kt` exists - this phase consumes it and must not re-implement volume enumeration.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/MetricValue.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/DeviceStatusProvider.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/StorageStatus.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/MemoryStatus.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetStorageStatusUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DeviceMemoryRepository.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlatformDeviceMemorySource.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetMemoryStatusUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 200 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase reaches 500 LOC - `RepositoryModule.kt` is 164 lines.
>
> **Flavor placement.** Every file above is shared code under `src/main/java/`. No `BuildConfig.IS_*` guard appears in any of them - strategic §3.2.

---

## Steps

### Step 01.1 - Add the unknown-capable metric value type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/MetricValue.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MetricValue<out T>` as a sealed interface with two members: `data class Known<T>(val value: T) : MetricValue<T>` and `data object Unknown : MetricValue<Nothing>`. Add a KDoc stating that a metric the device refuses to report is `Unknown` and never a zero. Do not add formatting, string resources, or Android imports to this file - it stays a pure domain type.

**Why:**

Strategic §5.2 requires an unavailable metric to produce an explicit "unknown" state, because zero free memory and unknown free memory are different facts that must not be conflated; §11.6 makes that a completion criterion.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/MetricValue.kt` exists.
- `Grep` - `sealed interface MetricValue` matches exactly once in that file.
- `Grep` - `data object Unknown` present.
- `Grep` - `^import android\.` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.2 - Add the polling-period contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/DeviceStatusProvider.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface DeviceStatusProvider<out T>` with `val refreshIntervalMs: Long` and `suspend fun read(): T`. KDoc it as the single contract every device metric implements, and state that the interval belongs to the metric because the view must not choose one. Keep the interval in seconds-scale units; document the floor as one second so no implementation can pick a frame-rate period.

**Why:**

Strategic §5.1.5 puts the refresh period on the metric rather than the view, and §3.2 names periodic polling of four gadgets as the set's biggest performance risk with "seconds, not frames" as the stated bound.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/DeviceStatusProvider.kt` exists.
- `Grep` - `interface DeviceStatusProvider` matches exactly once.
- `Grep` - `val refreshIntervalMs: Long` present.
- `Grep` - `suspend fun read()` present.

**Status:** `[x]` done

---

### Step 01.3 - Add the storage metric on top of GetStorageVolumesUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/StorageStatus.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetStorageStatusUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `StorageStatus` holding `internalTotalBytes`, `internalAvailableBytes` and a nullable `card: StorageVolumeInfo`-derived pair of total/available, each free/total pair carried as `MetricValue<Long>`. Add `GetStorageStatusUseCase` implementing `DeviceStatusProvider<StorageStatus>`, injecting `GetStorageVolumesUseCase`, taking the primary volume as internal storage and the first mounted removable volume as the card. Return `null` for the card section when no removable volume is mounted - never synthesise a row. Call `GetStorageVolumesUseCase` once per `read()`, never once per rendered value. Do not touch `UriPathResolver` and do not call `StorageManager` or `StatFs` from this file.

**Why:**

Strategic §11.4 requires the card row to be absent rather than invented when no card is present, and the 2026-08-05 §6 note binds this gadget to `GetStorageVolumesUseCase` as the single storage-enumeration path with one read per gadget refresh.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class GetStorageStatusUseCase` matches exactly once and the declaration line contains `DeviceStatusProvider<StorageStatus>`.
- `Grep` - `GetStorageVolumesUseCase` present in `GetStorageStatusUseCase.kt`.
- `Grep` - `StorageManager`, `StatFs`, `UriPathResolver` each return zero hits in both files.
- `Grep` - `Log\.d\(` returns zero hits in both files.

**Status:** `[x]` done

---

### Step 01.4 - Add the memory and uptime metric

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/devicestatus/MemoryStatus.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DeviceMemoryRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/PlatformDeviceMemorySource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/devicestatus/GetMemoryStatusUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `MemoryStatus` with `availableRamBytes: MetricValue<Long>`, `totalRamBytes: MetricValue<Long>` and `uptimeMillis: MetricValue<Long>`. Add `DeviceMemoryRepository` with a single `suspend fun read(): MemoryStatus`, and `PlatformDeviceMemorySource` implementing it from `ActivityManager.MemoryInfo` and `SystemClock.elapsedRealtime()`, wrapping the platform read in `withContext(Dispatchers.IO)` and mapping a failed read to `MetricValue.Unknown`. Add `GetMemoryStatusUseCase` implementing `DeviceStatusProvider<MemoryStatus>` and delegating to the repository. Bind both the source and the repository implementation in the existing `core/di/RepositoryModule.kt` alongside the `StorageVolumeSource` bindings.

**Why:**

Strategic §2.4 requires a gadget showing free RAM and time since boot, and §3.2 requires filesystem and memory reads to happen off the main thread because the gadget set must not become the reason the device stutters.

**Verification:**

- `Glob` - all four new files exist.
- `Grep` - `interface DeviceMemoryRepository` matches exactly once.
- `Grep` - `withContext(Dispatchers.IO)` present in `PlatformDeviceMemorySource.kt`.
- `Grep` - `bindDeviceMemoryRepository` present in `core/di/RepositoryModule.kt`.
- `Grep` - `Log\.d\(` returns zero hits in every file this step modifies.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, and the Hilt graph validated by a full `.\a.ps1 d` build.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by `post-change.ps1` catalog-sync.
- [x] Phase-boundary audit run - see Step Log, no P0/P1 findings.

---

## Step Log

- 2026-08-05 - Steps 01.1-01.4 done in one pass; every Verification predicate re-run and green. `.\a.ps1 fk` exit 0. Note on the 01.3 predicate "the declaration line contains `DeviceStatusProvider<StorageStatus>`": the class declaration is multi-line, so the supertype sits on the closing line of the constructor list rather than on the `class` line - both tokens verified present in the file.
- 2026-08-05 - Plan corrected during implementation: the memory source file was named `DeviceMemorySource.kt` while its single class is `PlatformDeviceMemorySource`, which detekt refuses (`Filename`, `MatchingDeclarationName`). Renamed to `PlatformDeviceMemorySource.kt`; Files Touched and the 01.4 predicate updated to match. The storage precedent does not apply - `StorageVolumeSource.kt` holds two declarations, so the rule never fires there.
- 2026-08-05 - Phase-boundary audit (Layer 1 architecture, Layer 2 dispatcher/coroutine, DI-scope trigger). No P0/P1. Findings: (a) `PlatformDeviceMemorySource` reads `ActivityManager` inside `withContext(Dispatchers.IO)` and is bound `@Singleton` with only an application `Context` held - no leak surface; (b) the null-service branch returns `MetricValue.Unknown` rather than zeroes, which is the invariant this phase exists to establish, and it is reached without a broad catch; (c) uptime is read from `SystemClock.elapsedRealtime()` before the service call so it survives a device that refuses memory figures; (d) P3, not acted on: both use cases carry their period as a private companion constant, so Phase 04 cannot tune periods without touching them - acceptable while `DeviceStatusProvider.refreshIntervalMs` remains the only thing the view reads.

---

## Handoff Notes to Next Phase

- `MetricValue.Unknown` is the only permitted representation of an unreadable metric; later phases must not introduce a sentinel number.
- `DeviceStatusProvider.refreshIntervalMs` is the sole source of a gadget's polling period; Phase 04 reads it and never hardcodes one.
- Storage enumeration has exactly one entry point in this ticket: `GetStorageVolumesUseCase`.

---

## Rollback Plan

Revert phase commit(s) - all files are new shared code plus two Hilt bindings, no data migration and no user-facing surface changed.
