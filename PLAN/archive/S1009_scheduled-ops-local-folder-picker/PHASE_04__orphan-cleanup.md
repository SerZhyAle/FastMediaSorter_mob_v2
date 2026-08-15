# Phase 04 - Orphan cleanup of hidden resources

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Steps 04.1-04.4 verification PASS. `ResourceRepository.deleteResourceIfHidden` (guarded on `is_hidden`, FTS-safe `deleteByIdWithFts`); `CleanupHiddenResourceUseCase` (null/0 no-op). VM `delete` and `saveOperation` snapshot the old FK ids from `operations.value` before mutating, then clean up an owned hidden resource on delete / clear-all / re-point (same-id guard). `FakeResourceRepository` extended; `CleanupHiddenResourceUseCaseTest` (hidden-deleted / visible-preserved / null-no-op). Build: BUILD SUCCESSFUL (1m17s). Audit (Layer 2/4): FTS-safe transactional delete, no TOCTOU (is_hidden never flips), strict 1:1 - no P0/P1.

---

## Objective

Auto-delete a hidden local resource when the operation that owns it is deleted, when its source/target is re-pointed to a different resource, or when all operations are cleared. Deletion is strict 1:1 and applies ONLY to `isHidden` rows - a reused visible resource is never deleted (strategic §9 cross-interaction). No reference counting (owner decision).

---

## Prerequisites

- [ ] Phase 03 ✅ - hidden resources are created on Save with a 1:1 FK to the operation.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResourceRepository.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupHiddenResourceUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/CleanupHiddenResourceUseCaseTest.kt` | New | ≤ 150 |

> The old source/target ids are read from the ViewModel's own `operations.value` StateFlow BEFORE the mutating call - no extra DAO round-trip. `ScheduledOperationsViewModel` currently has no resource dependency; add exactly one constructor-injected use-case, not a repository.

---

## Steps

### Step 04.1 - FTS-safe, hidden-guarded delete on the repository

**Files:** `domain/repository/ResourceRepository.kt`, `data/repository/ResourceRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun deleteResourceIfHidden(resourceId: Long)` to `ResourceRepository`. Implement it in `ResourceRepositoryImpl`: read `resourceDao.getResourceByIdSync(resourceId)`; if it is non-null and `isHidden`, delete it via the transactional `resourceDao.deleteByIdWithFts(resourceId)` (which also clears the `resources_fts` row) - otherwise no-op. The `isHidden` guard is belt-and-suspenders: even a mistaken call can never remove a visible, user-owned resource. Use `deleteByIdWithFts`, NOT the FTS-skipping `deleteById` used by `deleteResource` (that path leaks FTS rows - tracked as [[S1159]]).

**Verification:**

- `Grep` - `fun deleteResourceIfHidden(` present in both `ResourceRepository.kt` and `ResourceRepositoryImpl.kt`.
- `Grep` - the impl calls `deleteByIdWithFts` and guards on `isHidden`.
- `Grep` - the impl does NOT call the plain `deleteById` for this path.

**Status:** `[x]` done

---

### Step 04.2 - Cleanup use-case

**Files:** `domain/usecase/CleanupHiddenResourceUseCase.kt` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `CleanupHiddenResourceUseCase` (`@Inject constructor(resourceRepository)`). `suspend operator fun invoke(resourceId: Long?)`: return early if `resourceId == null`; otherwise call `resourceRepository.deleteResourceIfHidden(resourceId)`. Keep it a thin orchestration seam so the ViewModel stays layer-clean (UI -> ViewModel -> UseCase -> Repository) and the behaviour is unit-testable.

**Verification:**

- `Glob` - `CleanupHiddenResourceUseCase.kt` exists.
- `Grep` - `deleteResourceIfHidden` referenced in the use-case.

**Status:** `[x]` done

---

### Step 04.3 - Wire cleanup into delete, clear-all, and re-point paths

**Files:** `ui/settings/ScheduledOperationsViewModel.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Inject `CleanupHiddenResourceUseCase`. In `delete(operationId)`: before calling `deleteScheduledOperationUseCase`, read the operation from `operations.value` (`firstOrNull { it.id == operationId }`) and capture its `sourceResourceId` and `targetResourceId`; after the delete succeeds, call the cleanup use-case for each captured id (guard against calling it twice for the same id if source == target). This one method already backs single-delete, per-item confirm-delete, and clear-all (clear-all is N calls to `delete`), so all three are covered here. In `upsert(operation)` (edit): read the pre-existing operation from `operations.value` by `operation.id`; for source and target, if the OLD id differs from the NEW id, call cleanup on the OLD id after the upsert succeeds (re-pointing to a different folder orphans the previous hidden resource). Do the reads BEFORE the mutating call - afterward the row/ids are gone.

**Verification:**

- `Grep` - `CleanupHiddenResourceUseCase` injected in the ctor and invoked in both `delete` and `upsert`.
- `Grep` - the old source/target ids are read from `operations.value` before the mutating call.
- `Grep` - a same-id guard (source == target) exists in `delete`.

**Status:** `[x]` done

---

### Step 04.4 - Unit test the cleanup contract

**Files:** `src/test/.../domain/usecase/CleanupHiddenResourceUseCaseTest.kt` (New)
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `CleanupHiddenResourceUseCaseTest` over `FakeResourceRepository`. Assert: (a) a hidden resource id is deleted; (b) a VISIBLE resource id is NOT deleted (the `isHidden` guard holds); (c) a `null` id is a no-op. Extend `FakeResourceRepository` with a minimal `deleteResourceIfHidden` honoring the `isHidden` guard if it is not already present.

**Verification:**

- `Glob` - `CleanupHiddenResourceUseCaseTest.kt` exists.
- `Grep` - asserts both the hidden-deleted and visible-preserved cases.
- `/build` unit-test run green for this class.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `CleanupHiddenResourceUseCaseTest` passes (JVM unit).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every modified file; `dev/CATALOG/app_v2.jsonl` regenerated (new `CleanupHiddenResourceUseCase`).
- [ ] Phase-boundary audit - verify: deleting an op removes its hidden resource; clearing all ops leaves zero orphaned hidden rows; re-pointing a source/target deletes the previous hidden row; a reused VISIBLE resource is never deleted; FTS rows are cleared alongside.

---

## Handoff Notes to Next Phase

The hidden-resource lifecycle is now closed: created on Save (Phase 03), invisible everywhere (Phase 02), auto-removed on unlink/re-point/clear (this phase), always FTS-safe. Phase 05 records the capability and regenerates indexes.

---

## Rollback Plan

Revert the phase commit(s). Without cleanup, hidden resources accumulate as invisible orphan rows but cause no crash or visible leak (they stay filtered); a follow-up cleanup pass can prune them. No schema change in this phase.
