# Phase 01 — Player Cold-Start Sort

**Strategic spec:** [`../S0045_per-resource-sort-type-memory.md`](../S0045_per-resource-sort-type-memory.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Pass `resource.sortMode` to `GetMediaFilesUseCase` in the `PlayerMediaFilesLoader` slow path so that Slideshow and Player cold starts (no `MediaFilesCacheManager` hit) load files sorted according to the resource's persisted sort mode, consistent with Browse.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none — this is the foundation phase).
- [ ] Strategic §6 research items blocking this phase are Resolved (all resolved; see INDEX.md).
- [ ] Working tree is clean or on a feature branch.
- [ ] `ResourceEntity.sortMode` field exists and is populated via `BrowseSortFilterManager.setSortMode` → `UpdateResourceUseCase` (confirmed by code audit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 402 |

> Current LOC: 402. No backup required (≤500 threshold not exceeded).

---

## Steps

### Step 01.1 — Pass `resource.sortMode` to `GetMediaFilesUseCase` in slow path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** none — start of phase

**Prompt for developer:**

> In `PlayerMediaFilesLoader.loadMediaFiles()`, locate the slow-path call to `getMediaFilesUseCase(...)` (the branch executed when `MediaFilesCacheManager.getCachedList(resource.id)` returns null or empty — currently around line 234). Add the named argument `sortMode = resource.sortMode` to this call. `GetMediaFilesUseCase.invoke()` already accepts a `sortMode: SortMode = SortMode.NAME_ASC` parameter; only the call site needs updating. No other changes. Do not alter the cache-hit fast path (lines that return `cachedFiles` directly) — sorting in that path is already handled by the Browse layer that populated the cache.

**Verification:**

- `Grep` pattern `sortMode = resource\.sortMode` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` — must match at least once.
- `Grep` pattern `getMediaFilesUseCase\(` in the same file — confirm each occurrence that is NOT the cache-hit branch now includes `sortMode =`.
- `Grep` pattern `Log\.d\(` in `PlayerMediaFilesLoader.kt` — must return zero hits (Timber-only rule).

**Status:** `[x]` done — `PlayerMediaFilesLoader.kt:236` now passes `sortMode = resource.sortMode` to `getMediaFilesUseCase` in slow path.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added:
  `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt" "S0045" "Phase 01: pass resource.sortMode to GetMediaFilesUseCase slow path"`

---

## Handoff Notes to Next Phase

Phase 01 establishes that `PlayerMediaFilesLoader` cold-start loads files in the same sort order as Browse persisted to `ResourceEntity.sortMode`. Phase 02 (docs-catalog-cleanup) may proceed independently of any further runtime validation.

---

## Rollback Plan

Revert the single named argument addition — no schema migration, no data change, no user-visible surface changed. Rollback: revert the commit for this phase. Cache-hit path is unaffected.
