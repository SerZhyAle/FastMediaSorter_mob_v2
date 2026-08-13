# Phase 05 - Open-in-FMS Resource Resolver

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-09
**Completed:** 2026-06-09

**Step Log (phase):**

- 2026-06-09 - 05.1 ResourceDao.getLocalResourceByPathSync + repo method + 2 fakes updated (no schema bump). 05.2 ResolveOpenInFmsTargetUseCase: sealed OpenInFmsTarget{Resolved,NotResolvable}, chain local-gate → existing All Files → flavor-gated aggregate → reuse/create folder resource (allFiles setting drives types/profile). 05.3 test 6/6 PASS. invoke(uri, mediaType) - family needed for aggregate/type selection.

---

## Objective

Introduce a use case that resolves the target resource for "Open in FastMediaSorter" by the owner-defined chain: existing «All Files» resource → matching type-aggregate resource → create a new persistent folder resource. Returns a resource id plus the absolute file path to open, or a not-resolvable outcome for non-local files.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`ResolveLocalPathFromUriUseCase`).
- [ ] Strategic §6.3 (resolved: persistent, reuse by folder) and §6.4 (resource identification) reviewed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCase.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCaseTest.kt` | New | ≤ 240 |

---

## Steps

### Step 05.1 - Add by-path lookup to ResourceDao

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ResourceDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a query to fetch an existing local resource whose path equals a normalized folder path, so the resolver can reuse a previously auto-created folder resource instead of duplicating it. No schema/version change - this is a read query against the existing `resources` table. Return nullable.

**Verification:**

- `Grep` - a new `@Query` selecting by `path` is present in `ResourceDao.kt`.
- `Grep` - no `@Database` version bump in this phase (schema unchanged).
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 05.2 - Add ResolveOpenInFmsTargetUseCase with the resolution chain

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCase.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `ResolveOpenInFmsTargetUseCase`. Input: the external URI. Steps, in order: (1) resolve local path/folder via `ResolveLocalPathFromUriUseCase`; if not local, return not-resolvable. (2) If an «All Files» predefined resource exists, return its id with the file path. (3) Else, if the type-aggregate resource matching the file's media family exists (All Photos / All Video / All Music / All Documents, gated by the flavor `SUPPORT_*`), return its id. (4) Else, reuse an existing auto-created folder resource for that folder (Step 05.1 query) or create a new persistent one named by the folder; its supported types follow the file's family, or «all files» when the user setting for all-files is enabled; add it to the persistent list. Return the resolved resource id plus absolute file path. Reuse existing use cases for locate/create rather than duplicating creation logic. Off the main thread.

**Verification:**

- `Glob` - `ResolveOpenInFmsTargetUseCase.kt` exists.
- `Grep` - `class ResolveOpenInFmsTargetUseCase` matches once.
- `Grep` - it references the «All Files» ensure/locate use case and the add-resource use case (no inline duplicate of resource creation).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 05.3 - Unit-test the resolution chain

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ResolveOpenInFmsTargetUseCaseTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Test each branch: not-local URI → not-resolvable; «All Files» present → returns its id; aggregate present (no All-Files) → returns aggregate id; neither present → creates a folder resource with the folder name and expected supported types, and reuses it on a second call for the same folder (no duplicate). Assert all-files setting toggles the created resource's type filter.

**Verification:**

- `Glob` - `ResolveOpenInFmsTargetUseCaseTest.kt` exists.
- `Grep` - `class ResolveOpenInFmsTargetUseCaseTest` matches once.
- Run `./gradlew.bat testStandardDebugUnitTest --tests "*ResolveOpenInFmsTargetUseCaseTest*"` - per-class XML report all passing.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case).

---

## Handoff Notes to Next Phase

`ResolveOpenInFmsTargetUseCase` yields `(resourceId, absoluteFilePath)` or not-resolvable. Phase 06 calls it from the standalone Open-in-FMS handler and launches the in-app player with `initialFilePath`. Auto-created folder resources are persistent and reused.

---

## Rollback Plan

Revert phase commit(s). New use case + read-only DAO query + test; no caller yet, no schema/version change.

