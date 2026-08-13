# Phase 01 - Host: drop offscreen preload + defer/warm search index

**Strategic spec:** [`../S0474_settings-activity-perf-research.md`](../S0474_settings-activity-perf-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Stop `SettingsActivity` from creating a second tab fragment at open (A1) and from building the settings-search index synchronously on every `setupViews()` (A5); warm the index off the main thread instead.

---

## Prerequisites

- [ ] Strategic §6 items Resolved (all are - see INDEX Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 600 |

> `SettingsActivity.kt` is 595 LOC (>500) - backup step required (Step 01.1).

---

## Steps

### Step 01.1 - Back up SettingsActivity before edit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `SettingsActivity.kt` exceeds 500 LOC. Copy it to `temp/SettingsActivity_<yyyyMMdd-HHmmss>.kt.bak` before editing (CLAUDE.md §10.5).

**Verification:**

- `Glob` - `temp/SettingsActivity_*.kt.bak` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS. Backup `temp/SettingsActivity_20260617-122342.kt.bak` created.

---

### Step 01.2 - Remove offscreen preload of the adjacent tab

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `setupViews()` the pager is configured with `binding.viewPager.offscreenPageLimit = 1` (currently line ~195), which eagerly creates the tab adjacent to the active one at open. Remove that assignment so ViewPager2 uses its default (`OFFSCREEN_PAGE_LIMIT_DEFAULT`) and builds only the visible tab. Keep the `setPageTransformer` block intact. Do not touch the DEBUG timing `Timber.d` lines except to drop the now-stale "offscreenLimit" mention in the adjacent log message if present.

**Verification:**

- `Grep` - `offscreenPageLimit` returns zero hits in `SettingsActivity.kt`.
- `Grep` - `setPageTransformer` still present exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. `offscreenPageLimit` removed (0 hits); `setPageTransformer` retained (1 hit). `SettingsActivity.kt`.

---

### Step 01.3 - Defer search-index build off the open path and warm it in background

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `setupGlobalSearch()` the line `updateSearchResults(settingsSearchRegistry.entries)` (currently line ~479) forces the lazy index (`SettingsSearchRegistry.entries` → XML scan of 9 layouts via `LayoutSettingsSearchSource.collect()`) to build synchronously on the main thread at every Settings open, even though the search overlay starts hidden. Remove that eager call - `openSearchOverlay()` already calls `updateSearchResults(settingsSearchRegistry.entries)` when the user actually opens search. Then warm the index off the main thread: in `setupViews()` add `lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { settingsSearchRegistry.entries } }` so the lazy build runs on IO and the first overlay open is instant. Add the `Dispatchers` import. The registry's `allEntries` lazy is `SYNCHRONIZED`, so the background warm and a later main-thread access cannot race into a double build.

**Verification:**

- `Grep` - `updateSearchResults(settingsSearchRegistry.entries)` matches exactly once in `SettingsActivity.kt` (the `openSearchOverlay()` call only).
- `Grep` - `Dispatchers.IO` present in `SettingsActivity.kt`.
- `Grep` - `settingsSearchRegistry.entries` inside a `lifecycleScope.launch` block.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. Eager `updateSearchResults(entries)` removed from `setupGlobalSearch` (now 1 hit, overlay-only at L509); IO warm added at L234 with `Dispatchers.IO` import. `SettingsActivity.kt`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (25s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `SettingsActivity.kt` via post-change.

---

## Handoff Notes to Next Phase

Open path no longer eagerly builds the adjacent tab or the search index. Search overlay behavior is unchanged for the user (index warmed in background, built on demand if the warm has not finished). Phases 02-04 are independent and may run in any order.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. Restore from `temp/SettingsActivity_*.kt.bak` if needed.
