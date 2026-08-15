# Phase 03 - Operations Native Page

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Deliver a native revised Operations page that surfaces Safety, Delete & Trash, Scheduled, Copy & Move, and Quick Sort List as their own card sections without flattening management-heavy flows.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `temp/S0125_migration_map.md` covers Browse automation preselection and Operations management surfaces.
- [ ] Legacy `SettingsActivity` remains available as the public fallback route.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt` | Modified | ≤ 550 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedOperationsSectionBinder.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1350 |
| `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-land/fragment_settings_revised_operations.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3650 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3250 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3250 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Replace the Operations host layout with native revised sections

**Files:** `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_operations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the include-based Operations shell with revised layouts ordered `Safety & Confirmation -> Delete & Trash -> Scheduled -> Copy & Move -> Quick Sort List`. Keep `rvScheduledOps` and `rvDestinations` inside dedicated card bodies so the management surfaces stay visually distinct from plain toggle rows.

**Verification:**

- `Grep` - `revisedOperationsLegacyContent` returns zero hits in `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml`.
- `Grep` - `rvScheduledOps` present in `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml`.
- `Grep` - `rvDestinations` present in `app_v2/src/main/res/layout-land/fragment_settings_revised_operations.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_operations.xml`. Evidence: `get_errors` clean, `revisedOperationsLegacyContent` zero hits in portrait, `rvScheduledOps` present in portrait, `rvDestinations` present in landscape, dev log recorded.

---

### Step 03.2 - Rebind Operations logic without the legacy binding shell

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedOperationsSectionBinder.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Bind the revised Operations hierarchy directly from the revised fragment, preserve scheduled add/edit/log/bulk-clear behavior, and keep the Browse `EXTRA_SOURCE_RESOURCE_ID` preselection flow intact. Do not collapse Scheduled or Quick Sort List into a flat toggle list just to reuse legacy layout ids.

**Verification:**

- `Grep` - `FragmentSettingsDestinationsBinding` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt`.
- `Grep` - `SettingsActivity.EXTRA_SOURCE_RESOURCE_ID` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt`.
- `Grep` - `checkAndOpenAutomateDialog` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedOperationsSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedOperationsSectionBinder.kt`. Evidence: `get_errors` clean, `FragmentSettingsDestinationsBinding` zero hits in revised fragment, `SettingsActivity.EXTRA_SOURCE_RESOURCE_ID` present, `checkAndOpenAutomateDialog` present, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

### Step 03.3 - Localize Operations section labels and search anchors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add or rename the Operations section labels in EN, RU, and UK and move revised search targets onto the final Safety, Delete & Trash, Scheduled, Copy & Move, and Quick Sort List anchors. Apply `docs/COMMUNICATION_POLICY.md` §2 and §6 to new user-visible strings.

**Verification:**

- `Grep` - `settings_section_quick_sort_list` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `settings_section_quick_sort_list` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `settings_section_quick_sort_list` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/layout/fragment_settings_revised_operations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_operations.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`. Evidence: `get_errors` clean, `settings_section_quick_sort_list` present in EN/RU/UK, `scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"` OK, search registry points to `headerDeleteTrash`, `headerScheduled`, `headerCopyMove`, and `headerDestinations`, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Operations is now a native revised page with preserved management surfaces and stable automation-entry anchors. Media can now adopt the same card grammar without reusing the old Operations shell.

---

## Rollback Plan

Revert phase commit(s) and restore the include-based revised Operations layout if needed. `EXTRA_SOURCE_RESOURCE_ID` semantics must remain unchanged during rollback.