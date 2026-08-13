# Phase 02 - General Native Page

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the include-based revised General shell with a native card-section page that matches the blueprint order and keeps Permissions as a dedicated management entry.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S0125_migration_map.md` covers every General control touched by this phase.
- [ ] Revised settings is still not the primary public settings route.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedGeneralSectionBinder.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1300 |
| `app_v2/src/main/res/layout/fragment_settings_revised_general.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_revised_general.xml` | Modified | ≤ 450 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3600 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3200 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3200 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Replace the General host layout with native revised cards

**Files:** `app_v2/src/main/res/layout/fragment_settings_revised_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the include-based General shell with dedicated revised layouts ordered `Interface -> Grid & Browse -> Network & Cache -> App Data & Backups -> Permissions & Access -> About`. Keep headers focusable, keep portrait and landscape section order identical, and treat Permissions as an entry row into a dedicated screen instead of a normal collapsible body.

**Verification:**

- `Grep` - `revisedGeneralLegacyContent` returns zero hits in `app_v2/src/main/res/layout/fragment_settings_revised_general.xml`.
- `Grep` - `headerInterface` present in `app_v2/src/main/res/layout/fragment_settings_revised_general.xml`.
- `Grep` - `btnPermissionsManagement` present in `app_v2/src/main/res/layout-land/fragment_settings_revised_general.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_revised_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_general.xml`. Evidence: `rg` PASS (`revisedGeneralLegacyContent` zero hits, `headerInterface` present, `btnPermissionsManagement` present in landscape), `get_errors` clean. Revised General now owns native section cards directly instead of wrapping the legacy include shell.

---

### Step 02.2 - Bind the new General hierarchy directly in the revised fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedGeneralSectionBinder.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Move `RevisedGeneralSettingsFragment` off `FragmentSettingsGeneralBinding` and bind the new revised view hierarchy directly. Reuse existing view models and helpers only where they do not pin the fragment to legacy layout ids, and keep Google account, debug, and reset-all slices out of the public revised General flow.

**Verification:**

- `Grep` - `FragmentSettingsGeneralBinding` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt`.
- `Grep` - `PermissionsManagementFragment()` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt`.
- `Grep` - `fun ensureSectionExpanded(sectionId: String)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedGeneralSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedGeneralSectionBinder.kt`. Evidence: `get_errors` clean, `FragmentSettingsGeneralBinding` zero hits in revised fragment, `PermissionsManagementFragment()` present, `fun ensureSectionExpanded(sectionId: String)` present, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.
- 2026-05-19 - Runtime regression hotfix. The grep predicate "no `FragmentSettingsGeneralBinding` in the fragment" passed, but `RevisedGeneralSectionBinder.legacyBinding()` still calls `FragmentSettingsGeneralBinding.bind(root)` as a helper-compatibility shim. `ensureCompatibilityViews()` only covered 5 legacy-only ids; opening Revised General crashed with `NullPointerException: Missing required view with ID: btnImportTestCredentials` (logs/current.log:659). Extended `ensureCompatibilityViews()` to inject hidden views for `containerGeneralActions`, `btnShowLog`, `btnShowSessionLog`, `containerIntegrationTests`, `btnIntegrationTests`, `btnImportTestCredentials` with correct ConstraintLayout/Button types; `pwsh -NoProfile -File ./build-debug.PS1` finished with `BUILD SUCCESSFUL`. Architectural debt remains: helpers are still pinned to `FragmentSettingsGeneralBinding`, which contradicts the spec language "do not pin the fragment to legacy layout ids". Recommend a corrective tactical phase to decouple `GeneralSettings*Helper` from the legacy binding (e.g. accept narrow per-helper interfaces) before the next public re-exposure.

---

### Step 02.3 - Localize General section labels and search anchors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add or rename the revised General section strings in EN, RU, and UK, then update the search registry to point at the final General section headers and management entry rows. Apply `docs/COMMUNICATION_POLICY.md` §2 and §6 to every new or changed user-visible string.

**Verification:**

- `Grep` - `settings_section_network_cache` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `settings_section_network_cache` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `settings_section_network_cache` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`. Evidence: `get_errors` clean, `settings_section_network_cache` present in EN/RU/UK, `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"` OK, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

The revised General page no longer depends on the legacy included layout. General section ids and search anchors are stable for the remaining page rewrites.

---

## Rollback Plan

Revert phase commit(s) and restore the include-based revised General layout if needed. No persisted settings keys or defaults change in this phase.