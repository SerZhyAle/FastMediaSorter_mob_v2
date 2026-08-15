# Phase 04 - DI Bindings

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** Phase 05, 06, 07, 08
**Steps done:** 3 / 3
**Started:** 2026-06-02 15:36:00
**Completed:** 2026-06-02 15:37:00

---

## Objective

Wire DeviceProfileDetector, DeviceProfileRepository, and related classes into Hilt DI. Create module bindings for standard flavor; ensure flavor isolation for VR (no VR-specific detector logic in standard flavor).

---

## Prerequisites

- [x] Phase 01, 02, 03 are ✅ Done.
- [x] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/datasource/DeviceProfileLocalDataSource.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 20 |

---

## Steps

### Step 04.1 - Expose DeviceProfileDao & DeviceProfileLocalDataSource in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/datasource/DeviceProfileLocalDataSource.kt`
**Depends on:** - start of phase

**Status:** `[x] done`

---

### Step 04.2 - Annotate RealDeviceProfileDetector context with @ApplicationContext

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetector.kt`
**Depends on:** Step 04.1

**Status:** `[x] done`

---

### Step 04.3 - Bind DeviceProfileRepository and DeviceProfileDetector in RepositoryModule

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`
**Depends on:** Step 04.1, 04.2

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entries for all touched files.

---

## Handoff Notes to Next Phase

DI wiring complete. Repository and detector are now injectable into ViewModels (Phase 05–06) and presenters. VR-flavor specific detector can override in src/vr/java/di/ if needed (future).

---

## Rollback Plan

Revert module files - no runtime logic changed, only DI wiring.
