# Phase 01 — Util and Provisioning

**Strategic spec:** [`../S0130_virtual-resource-file-operations.md`](../S0130_virtual-resource-file-operations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Extend `isAggregateVirtualPath()` to cover `camera_photos`, then set `isWritable = true` for all aggregate virtual paths in both provisioning code paths.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/VirtualPathUtils.kt` | Modified | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt` | Modified | ≤ 175 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt` | Modified | ≤ 310 |

---

## Steps

### Step 01.1 — Add `camera_photos` to `isAggregateVirtualPath()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/VirtualPathUtils.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `VirtualPathUtils.isAggregateVirtualPath()`, add `|| path == VIRTUAL_PATH_CAMERA_PHOTOS` as a fifth branch. Camera photos is semantically an aggregate virtual resource (aggregates files from the Camera folder) and must receive the same write permission as all_audio/video/images/docs.

**Verification:**

- `Grep` — `path == VIRTUAL_PATH_CAMERA_PHOTOS` present in `VirtualPathUtils.kt`.
- `Grep` — `isAggregateVirtualPath` still contains `VIRTUAL_PATH_ALL_AUDIO`, `VIRTUAL_PATH_ALL_VIDEO`, `VIRTUAL_PATH_ALL_IMAGES`, `VIRTUAL_PATH_ALL_DOCS` in the same function body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 2/2 PASS. Files: VirtualPathUtils.kt (added VIRTUAL_PATH_CAMERA_PHOTOS branch). Dev log recorded.

---

### Step 01.2 — Fix provisioning in `ProvisionDefaultResourcesUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `ProvisionDefaultResourcesUseCase.createVirtualResource()`, change the `isWritable` field from the hard-coded `false` to `VirtualPathUtils.isAggregateVirtualPath(path)`. Add the required import for `VirtualPathUtils`. This ensures new installs provision aggregate virtual resources as writable while `virtual://recent` remains non-writable (it is not an aggregate path).

**Verification:**

- `Grep` — `isWritable = VirtualPathUtils.isAggregateVirtualPath(path)` present in `ProvisionDefaultResourcesUseCase.kt`.
- `Grep` — `import com.sza.fastmediasorter.util.VirtualPathUtils` present in `ProvisionDefaultResourcesUseCase.kt`.
- `Grep` — no remaining `isWritable = false` literal inside `createVirtualResource` function body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: ProvisionDefaultResourcesUseCase.kt (import + isWritable changed). Dev log recorded.

---

### Step 01.3 — Fix provisioning in `AddResourceVirtualCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `AddResourceVirtualCoordinator.buildVirtualResource()`, change `isWritable = false` to `isWritable = VirtualPathUtils.isAggregateVirtualPath(virtualPath)`. Add the required import for `VirtualPathUtils`. This covers the user-manually-added virtual resource path (Add Resource flow) so it is consistent with provisioning.

**Verification:**

- `Grep` — `isWritable = VirtualPathUtils.isAggregateVirtualPath(virtualPath)` present in `AddResourceVirtualCoordinator.kt`.
- `Grep` — `import com.sza.fastmediasorter.util.VirtualPathUtils` present in `AddResourceVirtualCoordinator.kt`.
- `Grep` — no remaining `isWritable = false` literal inside `buildVirtualResource` function body.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 3/3 PASS. Files: AddResourceVirtualCoordinator.kt (import already present; isWritable changed). Dev log recorded.

---

### Step 01.4 — Add S0130 debug Timber tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDefaultResourcesUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt`
**Depends on:** Step 01.2, Step 01.3

**Prompt for developer:**

> Insert one `Timber.d("S0130: provisioning virtual resource path=$path isWritable=${VirtualPathUtils.isAggregateVirtualPath(path)}")` call at the top of `createVirtualResource()` body.
> Insert one `Timber.d("S0130: building user-added virtual resource path=$virtualPath isWritable=${VirtualPathUtils.isAggregateVirtualPath(virtualPath)}")` call at the top of `buildVirtualResource()` body.

**Verification:**

- `Grep` — `Timber.d("S0130:` appears at least twice across all `.kt` files (one per flow entry; Phase 02 adds a third tag in `AppStartupInitializer`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. 2 Timber.d("S0130:") tags found (ProvisionDefaultResourcesUseCase.kt + AddResourceVirtualCoordinator.kt). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (VirtualPathUtils public API changed).

---

## Handoff Notes to Next Phase

- `isAggregateVirtualPath()` now returns `true` for all five non-recent virtual paths.
- New installs will provision aggregate virtual resources with `isWritable = true`.
- Phase 02 must add the startup fixer for existing users where the DB already has `isWritable = false`.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed in this phase. Startup fixer in Phase 02 handles existing data.
