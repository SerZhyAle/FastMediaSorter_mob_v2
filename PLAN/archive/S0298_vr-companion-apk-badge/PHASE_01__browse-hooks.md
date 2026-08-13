# Phase 01 - Browse hooks

**Strategic spec:** [../S0298_vr-companion-apk-badge.md](../S0298_vr-companion-apk-badge.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-27
**Completed:** 2026-05-27

---

## Objective

Introduce the main-source Browse badge extension hook so noLegal can render APK VR state without leaking flavor logic into `src/main`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic UI clarification is READY.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkTileBadgeBinder.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/NoOpBrowseApkTileBadgeBinder.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkTileBadgeModule.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 520 |
| `app_v2/src/main/res/values/ids.xml` | New | ≤ 80 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Add the main Browse badge contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkTileBadgeBinder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/NoOpBrowseApkTileBadgeBinder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkTileBadgeModule.kt`, `app_v2/src/main/res/values/ids.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a main-source interface for decorative APK tile badge binding and a default no-op implementation. Bind the default implementation from `src/main` via Hilt. Add the shared `id` resources needed for optional `findViewById` lookups so noLegal layout overrides can expose badge views without changing market layouts.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkTileBadgeBinder.kt` exists.
- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/NoOpBrowseApkTileBadgeBinder.kt` exists.
- `Grep` - `interface BrowseApkTileBadgeBinder` matches exactly once in `BrowseApkTileBadgeBinder.kt`.
- `Grep` - `class NoOpBrowseApkTileBadgeBinder` matches exactly once in `NoOpBrowseApkTileBadgeBinder.kt`.
- `Grep` - `bindBrowseApkTileBadgeBinder` matches exactly once in `BrowseApkTileBadgeModule.kt`.
- `Grep` - `browseApkVrBadgeContainer` present in `app_v2/src/main/res/values/ids.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 6/6 PASS. Files: `BrowseApkTileBadgeBinder.kt`, `NoOpBrowseApkTileBadgeBinder.kt`, `BrowseApkTileBadgeModule.kt`, `ids.xml`. Dev log recorded; catalog sync invoked by Kotlin post-change closure.

---

### Step 01.2 - Thread the badge hook through Browse tile binding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject the badge binder into `BrowseActivity`, pass it into `BrowseManagerInitializer`, and wire `MediaFileAdapter` so both list and grid holders call the binder on full bind and clear it on recycle. Keep all behavior flavor-agnostic from the perspective of `src/main`.

**Verification:**

- `Grep` - `BrowseApkTileBadgeBinder` appears in `BrowseActivity.kt`.
- `Grep` - `browseApkTileBadgeBinder` appears in `BrowseManagerInitializer.kt`.
- `Grep` - `apkTileBadgeBinder.bind` appears in `MediaFileAdapter.kt`.
- `Grep` - `apkTileBadgeBinder.onViewRecycled` appears in `MediaFileAdapter.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 4/4 PASS. Files: `BrowseActivity.kt`, `BrowseManagerInitializer.kt`, `MediaFileAdapter.kt`. Existing dev log entries present from 2026-05-26; no new code edit needed in this pass.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Main Browse code now exposes a single decorative badge hook and remains flavor-neutral.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent user data changes.
