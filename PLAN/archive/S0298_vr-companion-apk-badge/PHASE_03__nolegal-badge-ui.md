# Phase 03 - noLegal badge UI

**Strategic spec:** [../S0298_vr-companion-apk-badge.md](../S0298_vr-companion-apk-badge.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-27
**Completed:** 2026-05-27

---

## Objective

Render the VR badge in noLegal list/grid tiles and connect the runtime binder to Browse.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/NoLegalBrowseApkTileBadgeBinder.kt` | New | ≤ 260 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkTileBadgeModule.kt` | New | ≤ 120 |
| `app_v2/src/noLegal/res/layout/item_media_file.xml` | New | ≤ 220 |
| `app_v2/src/noLegal/res/layout/item_media_file_grid.xml` | New | ≤ 220 |
| `app_v2/src/noLegal/res/values/strings_s0298.xml` | New | ≤ 80 |
| `app_v2/src/noLegal/res/values-ru/strings_s0298.xml` | New | ≤ 80 |
| `app_v2/src/noLegal/res/values-uk/strings_s0298.xml` | New | ≤ 80 |

---

## Steps

### Step 03.1 - Add noLegal badge layouts and strings

**Files:** `app_v2/src/noLegal/res/layout/item_media_file.xml`, `app_v2/src/noLegal/res/layout/item_media_file_grid.xml`, `app_v2/src/noLegal/res/values/strings_s0298.xml`, `app_v2/src/noLegal/res/values-ru/strings_s0298.xml`, `app_v2/src/noLegal/res/values-uk/strings_s0298.xml`
**Depends on:** Phase 02

**Prompt for developer:**

> Override the list and grid item layouts in noLegal so both expose the same decorative VR pill inside the thumbnail zone. Add EN/RU/UK strings for the badge accessibility description. Keep the badge non-clickable, D-pad-neutral, and visually aligned with the dark S0292 badge language.

**Verification:**

- `Glob` - `app_v2/src/noLegal/res/layout/item_media_file.xml` exists.
- `Glob` - `app_v2/src/noLegal/res/layout/item_media_file_grid.xml` exists.
- `Grep` - `browseApkVrBadgeContainer` present in both noLegal layout files.
- `Grep` - `apk_vr_badge_content_description` present in all three `strings_s0298.xml` locale files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 4/4 PASS. Landscape counterparts absent in both main and noLegal source sets (expected: absent | actual: absent). Files: noLegal list/grid layouts and EN/RU/UK strings. Existing dev log entries present from 2026-05-26; no new code edit needed in this pass.

---

### Step 03.2 - Bind noLegal classification to tile state

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/NoLegalBrowseApkTileBadgeBinder.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkTileBadgeModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement the noLegal binder that clears stale badge state, peeks cache on bind, requests async classification on cache miss, and updates only the currently bound tile when the result returns. Override the main Hilt module in the noLegal source set so Browse receives the real binder only for the noLegal flavor.

**Verification:**

- `Glob` - `NoLegalBrowseApkTileBadgeBinder.kt` exists.
- `Grep` - `class NoLegalBrowseApkTileBadgeBinder` present in the binder file.
- `Grep` - `requestClassification` or `request(` present in the binder file.
- `Grep` - `bindBrowseApkTileBadgeBinder` matches exactly once in the noLegal `BrowseApkTileBadgeModule.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-27 - Verification 4/4 PASS. Files: `NoLegalBrowseApkTileBadgeBinder.kt`, `BrowseApkTileBadgeModule.kt`. Dev log recorded for the noLegal Hilt module rename; binder dev log entry already present from 2026-05-26; catalog sync PASS.
- 2026-05-27 - Phase build repair: `MediaFileAdapter.kt` now has exactly one shared helper set for `bindFileClick`, `bindFileTypeClick`, and `bindRightClickContextMenu`. `build-nolegal-debug.ps1` PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

noLegal Browse tiles now expose the decorative badge surface and the runtime binder drives visibility.

---

## Rollback Plan

Revert phase commit(s) - UI changes are layout-only and flavor-scoped.
