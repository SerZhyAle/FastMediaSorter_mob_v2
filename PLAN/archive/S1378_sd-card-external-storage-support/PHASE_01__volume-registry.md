# Phase 01 - Volume registry

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 05, Phase 06
**Steps done:** 6 / 6
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Introduce a single source of truth for the device's storage volumes - name, removable flag, mounted state, mount path, total and free bytes - reachable from the domain layer; no UI, no file operations, no persistence changes yet.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired before the first source edit (`scripts/utils/enter-code-lock.ps1 -Reason "S1378 phase 01"`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StorageVolumeInfo.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StorageVolumeRepository.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StorageVolumeSource.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StorageVolumeRepositoryImpl.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/StorageVolumeEntryPoint.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStorageVolumesUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 40 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt` | Modified | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StorageVolumeRepositoryImplTest.kt` | New | ≤ 200 |

> Backup / split thresholds: no file in this phase crosses 500 LOC, so no backup sub-step is required.

**Plan correction, 2026-08-05 (implementation).** Two files were added beyond the original list, both forced by the same constraint: `UriPathResolver.getPath` is called synchronously from picker result callbacks (`BrowseFolderPickerHandler`, `BrowseFileOperationsManager`, `FileOperationsHandler`, `PlayerFolderPickerHandler`) and Step 01.6 requires its signature to stay unchanged, so it cannot await a suspend repository.

- `StorageVolumeSource.kt` holds the blocking platform access - the `StorageManager` enumeration, the `getDirectory()`/`getPath()` reflection fallback and `StatFs`. The repository wraps it in `Dispatchers.IO` for domain consumers, and the resolver calls its non-usage-reading `mountPathFor` directly, which is exactly what the resolver did inline before. This also gives the phase its test seam: with the platform behind an interface the repository is testable without Robolectric or hidden-API mocking.
- `StorageVolumeEntryPoint.kt` is how the resolver, being an `object`, reaches that source - the pattern already used by `MediaCapabilitiesEntryPoint` and `StreamTrackPreferenceEntryPoint`.

---

## Steps

### Step 01.1 - Add the `StorageVolumeInfo` domain model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StorageVolumeInfo.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the domain model describing one storage volume: a stable `id` (the platform volume uuid, or the literal `primary` for built-in storage), `displayName`, `isRemovable`, `isMounted`, nullable `mountPath`, `totalBytes` and `availableBytes`. Keep it a plain data class in the domain layer with no Android imports.

**Why:**

Strategic §5.1 pillar 1 requires one source of truth about device volumes covering name, kind, state and free space; without a domain-level model each consumer would re-derive those fields from the platform and drift apart.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `data class StorageVolumeInfo` matches exactly once.
- `Grep` - `import android.` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.2 - Add the `StorageVolumeRepository` contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StorageVolumeRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare the domain interface with three suspend members: list all volumes, find one volume by its id, and resolve the mount path of a volume id. Return `StorageVolumeInfo` types only - no `StorageVolume`, no `Uri`, no `File`.

**Why:**

Strategic §5.3 requires the registry to accept a new kind of medium later without touching the layers above it, which is only possible if consumers depend on a domain contract instead of the platform storage service.

**Verification:**

- `Grep` - `interface StorageVolumeRepository` matches exactly once.
- `Grep` - `android.os.storage` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.3 - Implement the repository over `StorageManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StorageVolumeRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement the contract with an injected application `Context`. Enumerate `StorageManager.storageVolumes`, map each entry to `StorageVolumeInfo`, and fill free space with `StatFs` over the resolved mount path. Resolve the mount path with `StorageVolume.getDirectory()` on API 30 and above, falling back to the hidden `getPath()` reflection below it - move that fallback here so it lives in exactly one place. A volume whose state is not `MEDIA_MOUNTED` reports `isMounted = false` and zero byte counts instead of being dropped from the list. Run every platform call on `Dispatchers.IO`. Catch only the specific failure of a single volume and log it with `Timber.w`, keeping the remaining volumes in the result - never swallow the whole enumeration.

**Why:**

Strategic §3.2 requires volume enumeration off the main thread, and the research artifact records that the reflection fallback is today the only removable-aware code path and is duplicated nowhere else - concentrating it here is what makes it testable and keeps the OEM-variance risk in §7 to a single call site.

**Verification:**

- `Grep` - `class StorageVolumeRepositoryImpl` matches exactly once.
- `Grep` - `Dispatchers.IO` present.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 01.4 - Add `GetStorageVolumesUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetStorageVolumesUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add the use case exposing two operations: all volumes, and removable volumes only. Sort the result with the primary volume first, then removable volumes by display name, so every caller renders the same order.

**Why:**

Strategic §5.2 routes the picker through a "list volumes" scenario rather than letting UI reach the repository directly, and a fixed order is what keeps the picker section stable between openings.

**Verification:**

- `Grep` - `class GetStorageVolumesUseCase` matches exactly once.
- `Grep` - `StorageVolumeRepository` appears in the constructor parameter list.

**Status:** `[x]` done

---

### Step 01.5 - Bind the repository in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Bind `StorageVolumeRepositoryImpl` to `StorageVolumeRepository` in the existing `RepositoryModule`, matching the binding style already used there. Do not create a new module.

**Why:**

not stated in strategic spec - the binding is the mechanical consequence of introducing a repository in a Hilt project.

**Verification:**

- `Grep` - `StorageVolumeRepository` matches in `RepositoryModule.kt`.
- `.\a.ps1 fk` exits 0, and a full `.\a.ps1 d` is run at phase end because a compile check does not validate the Hilt graph.

**Status:** `[x]` done

---

### Step 01.6 - Route `UriPathResolver` through the repository and cover it with tests

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StorageVolumeRepositoryImplTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Replace the inline `StorageManager` lookup and the private `volumePath` reflection in `UriPathResolver` with a call to the new repository, keeping the object's public `getPath` signature unchanged for existing callers. Then add unit tests covering: a document id with the `primary` volume prefix resolves to primary storage, a document id carrying a volume uuid resolves to that volume's mount path, an unknown uuid resolves to null, and an unmounted volume is reported with `isMounted = false` rather than omitted.

**Why:**

The research artifact records that this resolver is the single existing removable-aware code path and has zero test coverage while relying on hidden-API reflection, which strategic §7 lists as the device-variance risk to mitigate by covering the resolution with tests.

**Verification:**

- `Grep` - `getSystemService(Context.STORAGE_SERVICE)` returns zero hits in `UriPathResolver.kt`.
- `Grep` - `fun getPath(` still present in `UriPathResolver.kt`.
- `.\a.ps1 fu` - the new test class passes; record `expected: PASS | actual: <result>`.

**Result, 2026-08-05:** `expected: PASS | actual: PASS` - `check-standard-fast.ps1 -Mode Unit -Tests "*StorageVolumeRepositoryImplTest*"` exit 0, results XML reports `tests="6" failures="0" errors="0"`. The targeted filter was used instead of the whole suite because the full `fu` run truncates on memory pressure (S1244) and would not have proved this class either way.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this phase adds public classes.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`StorageVolumeInfo` and `GetStorageVolumesUseCase` are the only supported way to learn about volumes from here on. Phase 02 binds resources to `StorageVolumeInfo.id`; Phase 05 reads `availableBytes` from the same model; Phase 06 renders the list this phase produces.

Two properties of the registry that the next phases should not re-discover the hard way:

- The registry holds no cache: every `getVolumes()` re-enumerates and re-runs `StatFs` per mounted volume. That is what makes free space truthful for Phase 05's preflight, and what makes it wrong to call it per list row in Phase 06 - read once per picker opening.
- `UriPathResolver` deliberately goes through `StorageVolumeSource.mountPathFor`, not through the repository: it is called synchronously on Main and `mountPathFor` is the one path that never touches `StatFs`. Do not "simplify" it onto the suspend repository.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed.
