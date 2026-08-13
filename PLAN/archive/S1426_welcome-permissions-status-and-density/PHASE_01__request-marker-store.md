# Phase 01 - Request-marker store

**Strategic spec:** [`../S1426_welcome-permissions-status-and-density.md`](../S1426_welcome-permissions-status-and-density.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Turn the unused per-permission "shown once" store into a persisted "the system request for this permission has already been fired" marker, with its Hilt binding and unit tests. No status, screen or layout change yet.

---

## Prerequisites

- [ ] Strategic §6 item 1 is Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRequestMarkerRepository.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRequestMarkerRepositoryImpl.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ContextualRationaleRepository.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImpl.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/PermissionModule.kt` | Modified | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImplTest.kt` | Deleted | - |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRequestMarkerRepositoryImplTest.kt` | New | ≤ 90 |

---

## Steps

### Step 01.1 - Add the marker contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRequestMarkerRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PermissionRequestMarkerRepository` in the domain repository package with two synchronous members keyed by a permission entry id: `wasRequested(permissionId: String): Boolean` and `markRequested(permissionId: String)`. Keep both non-suspend. Add a KDoc sentence stating that the marker records that the system permission dialog was fired, which is the only way to tell a never-requested permission from a permanently denied one.

**Why:**

The strategic spec's ADR-1 decides that the app remembers the fact of the request itself, because the system answers identically before the first request and after a permanent denial.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRequestMarkerRepository.kt` exists.
- `Grep` - `fun wasRequested(permissionId: String): Boolean` present.
- `Grep` - `fun markRequested(permissionId: String)` present.
- `Grep` - `suspend` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.2 - Implement it over SharedPreferences

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRequestMarkerRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement the contract with a `@Singleton` class taking `@ApplicationContext Context`, backed by its own `SharedPreferences` file named `perm_request_marker_prefs`, storing one boolean per permission id. Wrap the read in `StrictModeHelper.allowDiskReads` and the write in `allowDiskWrites`, matching the deleted `ContextualRationaleRepositoryImpl` it replaces. Missing key reads as `false`.

**Why:**

Strategic §3.2 requires status computation to stay synchronous and cheap, and the status use case is called on the main thread while building rows, so the marker must be readable without suspending.

**Verification:**

- `Glob` - the impl file exists.
- `Grep` - `class PermissionRequestMarkerRepositoryImpl` matches exactly once.
- `Grep` - `perm_request_marker_prefs` present.
- `Grep` - `allowDiskReads` and `allowDiskWrites` both present.
- `Grep` - `DataStore` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.3 - Rebind in Hilt and delete the superseded store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/PermissionModule.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ContextualRationaleRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImpl.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImplTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `PermissionModule`, replace the `provideContextualRationaleRepository` binding with one providing `PermissionRequestMarkerRepository` from the new impl. Delete `ContextualRationaleRepository`, `ContextualRationaleRepositoryImpl` and `ContextualRationaleRepositoryImplTest`: the store has no production caller and the new marker supersedes it. Confirm by grep that no reference to the deleted names survives anywhere under `app_v2/src`.

**Why:**

CLAUDE.md Rule 20 requires orphaned classes to be deleted in the same change rather than left beside their replacement, and the superseded store carried a different meaning - "the rationale UI was shown" - that would mislead a later reader.

**Verification:**

- `Grep` - `ContextualRationale` returns zero hits across `app_v2/src`.
- `Grep` - `PermissionRequestMarkerRepository` present in `di/PermissionModule.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 01.4 - Cover the marker with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRequestMarkerRepositoryImplTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a Robolectric test class covering three cases: an unknown id reads `false`; after `markRequested` the same id reads `true`; marking one id leaves a second id `false`. Mirror the structure of the deleted `ContextualRationaleRepositoryImplTest` so the coverage that existed is not lost.

**Why:**

The research artifact records that nothing in this area except the registry has test coverage, and this marker becomes the single source of truth for whether a permission was ever requested.

**Verification:**

- `Glob` - the test file exists.
- `Grep` - `wasRequested` appears at least three times in that file.
- `.\a.ps1 fu` reports this class passing.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

A synchronous, per-permission-id marker exists and is injectable. Nothing reads it yet.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed. A stale preferences file left on a device reads as "not requested", which is the pre-phase behaviour.
