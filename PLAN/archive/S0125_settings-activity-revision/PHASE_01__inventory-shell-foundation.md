# Phase 01 - Inventory And Shell Foundation

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Lock the baseline inventory, migration map, and revised host contracts so native page rewrites can land without losing current search, focus, fallback, or orientation behavior.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] The 2026-05-19 blueprint remains the active ordering source for tabs and sections.
- [ ] Revised settings is still an incubation surface, not the primary public settings route.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0125_inventory_portrait.md` | New | ≤ 250 |
| `temp/S0125_inventory_landscape.md` | New | ≤ 250 |
| `temp/S0125_migration_map.md` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsPageContract.kt` | New | ≤ 180 |
| `app_v2/src/main/res/layout/activity_settings_revised.xml` | Modified | ≤ 220 |
| `app_v2/src/main/res/layout-land/activity_settings_revised.xml` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Capture the baseline inventory and migration map

**Files:** `temp/S0125_inventory_portrait.md`, `temp/S0125_inventory_landscape.md`, `temp/S0125_migration_map.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Inventory the currently visible settings controls in portrait and landscape, including titles, summaries, helper affordances, management entries, destructive actions, and search targets. Write a migration map that records `current placement -> target surface -> preserved behavior -> target phase` for every row or management surface touched by S0125.

**Verification:**

- `Glob` - `temp/S0125_inventory_portrait.md` exists.
- `Glob` - `temp/S0125_inventory_landscape.md` exists.
- `Grep` - `Preserved behavior` present in `temp/S0125_migration_map.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `temp/S0125_inventory_portrait.md`, `temp/S0125_inventory_landscape.md`, `temp/S0125_migration_map.md`. Evidence: inline `grep_search` + `list_dir` PASS.

---

### Step 01.2 - Introduce revised page-state contracts at the host level

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsPageContract.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a small page contract for revised tabs that can later supply expanded-section and scroll state. Save and restore the active tab and search-overlay shell state in `RevisedSettingsActivity`, but leave page-specific section restoration to the page phases that create the final native layouts.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsPageContract.kt` exists.
- `Grep` - `interface RevisedSettingsPageContract` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsPageContract.kt`.
- `Grep` - `override fun onSaveInstanceState` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsPageContract.kt`. Evidence: `get_errors` clean, interface grep PASS, `onSaveInstanceState` grep PASS.

---

### Step 01.3 - Normalize the revised search registry to blueprint section ids

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Re-key the revised search registry to the blueprint section map, keep multilingual alias coverage, and point management-surface results at entry controls instead of legacy child rows. Do not expose the revised host publicly in this step.

**Verification:**

- `Grep` - `sectionId = "network_cache"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `sectionId = "quick_sort_list"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `fun search(query: String)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`. Evidence: `get_errors` clean, `network_cache` grep PASS, `quick_sort_list` grep PASS, `fun search(query: String)` grep PASS. Added canonical blueprint-section entries on visible headers/entry controls so the hidden revised host keeps usable navigation while later phases finish full section remapping.

---

### Step 01.4 - Align portrait and landscape host shells

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt`, `app_v2/src/main/res/layout/activity_settings_revised.xml`, `app_v2/src/main/res/layout-land/activity_settings_revised.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Keep the revised host toolbar, tab strip, and search overlay structurally identical across portrait and landscape. Preserve the incubation title, but make sure both layout variants expose the same focusable toolbar controls and search anchors so later phases do not need another host rewrite.

**Verification:**

- `Grep` - `@+id/searchOverlay` present in `app_v2/src/main/res/layout/activity_settings_revised.xml`.
- `Grep` - `@+id/searchOverlay` present in `app_v2/src/main/res/layout-land/activity_settings_revised.xml`.
- `Grep` - `InputSurface.SETTINGS` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsKeyboardNavigationManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/layout/activity_settings_revised.xml`, `app_v2/src/main/res/layout-land/activity_settings_revised.xml`. Evidence: `rg` PASS for both `@+id/searchOverlay` anchors and `InputSurface.SETTINGS`; `get_errors` clean. Landscape host shell now mirrors the portrait toolbar/tab structure so later phases can reuse one focus/search contract.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits in implementation and artifact files.
- [x] Dev log entry added for every modified file in this phase via `./scripts/add_to_dev_log.ps1`.
- [x] No public API changed in Phase 01, so `dev/CATALOG/<module>.jsonl` regeneration was not required.

---

## Handoff Notes to Next Phase

Baseline inventories and migration map are frozen. The revised host now has stable shell-level state and canonical section ids for the native page rewrites.

---

## Rollback Plan

Revert phase commit(s) and delete the temporary inventory artifacts. No persisted settings data or public entry point changed in this phase.