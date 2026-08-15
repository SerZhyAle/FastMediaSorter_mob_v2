# Phase 02 - Visibility filter

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Steps 02.1-02.5 verification PASS. Filter at use-case boundary: `GetResourcesUseCase.invoke` filterNot isHidden; `GetDestinationsUseCase.invoke` + `getDestinationsExcluding` gain `!it.isHidden` (counts untouched); `getFilteredResources` standard-SQL branch filterNot isHidden (FTS/name-search branch left unfiltered per owner); `SendResourcesToWatchUseCase` defensive `!it.isHidden`. New `GetResourcesUseCaseTest` (JVM). Audit (Layer 1): centralized filter, FK/search/counts intact - no P0/P1.

---

## Objective

Exclude `isHidden` resources from every VISIBLE surface (main list, scheduled-op sender dropdown, receiver/destination pickers, Home browse list, resource-picker dialog) while keeping FK-by-id resolution, name-search (FTS), and count/limit checks unfiltered. The filter lives in the use-case / repository layer, never in the UI (strategic ADR §9).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `isHidden` exists on `MediaResource` and round-trips through the mappers.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResourcesUseCase.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetDestinationsUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt` | Modified | ≤ 100 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GetResourcesUseCaseTest.kt` | New | ≤ 150 |

> `getAllResources()` / `getAllResourcesSync()` at the repository/DAO root have ~50 non-UI callers (backup, widget-config activities reading the DAO directly, migrations, cloud dedup) that MUST keep seeing hidden rows. Do NOT add the filter at the DAO or `ResourceRepositoryImpl.getAllResources()` level - only at the UI-facing use-case boundary below.

---

## Steps

### Step 02.1 - Filter hidden from the main resource use-case

**Files:** `domain/usecase/GetResourcesUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `GetResourcesUseCase.invoke()`, wrap the returned Flow so hidden resources are dropped: `repository.getAllResources().map { list -> list.filterNot { it.isHidden } }` (import `kotlinx.coroutines.flow.map`). This is the single UI-facing boundary - its 8 call sites (main list, scheduled-op sender via `SettingsViewModel.resources`, `ResourcePickerDialog`, settings lists) all want hidden excluded. Leave `getById()` (FK resolution) and `getFiltered()` untouched here - `getById` MUST still resolve hidden rows.

**Verification:**

- `Grep` - `filterNot { it.isHidden }` (or `filter { !it.isHidden }`) present in `GetResourcesUseCase.invoke()`.
- `Grep` - `getById` body still calls `repository.getResourceById` with no `isHidden` guard.

**Status:** `[x]` done

---

### Step 02.2 - Filter hidden from destination pickers only (not counts)

**Files:** `domain/usecase/GetDestinationsUseCase.kt`
**Depends on:** - independent of 02.1

**Prompt for developer:**

> Add `&& !it.isHidden` to the existing `.filter { ... }` predicate in `invoke()` and in `getDestinationsExcluding()`. Do NOT touch `getDestinationCount()`, `isDestinationsFull()`, or `getNextAvailableOrder()` - the owner deliberately leaves resource counts/limits unfiltered (a hidden row may still count toward the recipient limit).

**Verification:**

- `Grep -n` - `!it.isHidden` appears exactly twice in `GetDestinationsUseCase.kt` (in `invoke` and `getDestinationsExcluding`).
- `Grep` - `getDestinationCount` and `getNextAvailableOrder` bodies contain no `isHidden`.

**Status:** `[x]` done

---

### Step 02.3 - Filter hidden from the Home filtered (browse) path, not the search path

**Files:** `data/repository/ResourceRepositoryImpl.kt`
**Depends on:** - independent of 02.1/02.2

**Prompt for developer:**

> `getFilteredResources()` has two branches: a name-filter branch that runs the FTS query (`searchResourcesFts`) and a standard-SQL branch (`getResourcesRaw`) for type/media/sort filtering. Apply `.filterNot { it.isHidden }` to the mapped result of the **standard-SQL (non-name) branch only**. Leave the FTS/name-filter branch UNFILTERED - the owner consciously allows hidden rows to surface in resource search, and Home's name filter is that same search (strategic §6 item 8). Add a one-line WHY comment on the standard-SQL filter: S1009 - hide ad-hoc local folders from browse; name-search stays unfiltered per owner.

**Verification:**

- `Grep` - `filterNot { it.isHidden }` (or equivalent) present in the standard-SQL branch of `getFilteredResources`.
- `Grep` - the block that calls `searchResourcesFts` has NO adjacent `isHidden` filter.

**Status:** `[x]` done

---

### Step 02.4 - Defensive hidden exclusion on the watch-sync push

**Files:** `domain/usecase/SendResourcesToWatchUseCase.kt`
**Depends on:** - independent

**Prompt for developer:**

> `invoke()` already narrows to `{SMB,FTP,SFTP}` (line ~33), so S1009's LOCAL hidden resources never reach the watch today. Add `&& !it.isHidden` to that `.filter { ... }` as defense-in-depth so a future non-LOCAL hidden type ([[S1010]]) cannot leak to the watch. One-line WHY comment: S1009 - never push hidden resources to the watch.

**Verification:**

- `Grep` - `!it.isHidden` present in the resource filter of `SendResourcesToWatchUseCase.invoke()`.

**Status:** `[x]` done

---

### Step 02.5 - Unit test the visibility filter

**Files:** `data/../../test/.../domain/usecase/GetResourcesUseCaseTest.kt` (New)
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Add `GetResourcesUseCaseTest` (currently absent) using the existing `testing/fakes/FakeResourceRepository`. Assert: (a) `invoke()` omits a resource whose `isHidden = true` but includes visible ones; (b) `getById()` on a hidden resource still returns it (FK resolution unfiltered). If `FakeResourceRepository` lacks an `isHidden` seam, extend the fake minimally. Optionally extend `GetDestinationsUseCaseTest` to assert `invoke()` drops a hidden destination while `getDestinationCount()` still counts it.

**Verification:**

- `Glob` - `GetResourcesUseCaseTest.kt` exists under `src/test/`.
- `Grep` - test references `isHidden` and asserts both inclusion (getById) and exclusion (invoke).
- `/build` unit-test run is green for this class (JVM).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `GetResourcesUseCaseTest` passes (JVM unit).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every modified file.
- [ ] Phase-boundary audit run - confirm no visible surface reads hidden (main list, both dropdowns, Home browse) and no unfiltered surface was accidentally filtered (search, counts, `getById`, backup, widgets).

---

## Handoff Notes to Next Phase

Hidden resources are now invisible on every list surface but still resolve by id and still appear in name-search and counts. A resource created with `isHidden = true` in Phase 03 will therefore be FK-usable yet absent from the sender/receiver dropdowns and Home - exactly what the picker needs. Phase 03 must still resolve an EXISTING hidden source/target by id when EDITING an operation (it is not in the filtered dropdown list).

---

## Rollback Plan

Revert the phase commit(s). Each change is an additive predicate; reverting restores the prior (unfiltered) behaviour with no data impact.
