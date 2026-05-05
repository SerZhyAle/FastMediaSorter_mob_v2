# Phase 01 - App Data Contract

**Strategic spec:** [`../S0090_bugfix-settings-default-credentials-input.md`](../S0090_bugfix-settings-default-credentials-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Make the `App Data` section discoverable on first entry and normalize the Default User / Default Password row contract across portrait and landscape before input-routing changes.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | <= 120 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | <= 340 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | <= 320 |

> No file in this phase is projected above 500 lines after change.

---

## Steps

### Step 01.1 - Default App Data to expanded only on first entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `GeneralSettingsSectionsHelper.kt`, keep stored user choices authoritative, but change the fallback for `KEY_APP_DATA_EXPANDED` to `true` only when the preference key has never been written yet. Do not change the defaults for `Interface`, `Permissions`, `System`, or `Debug` sections. The resulting behavior must be: first visit opens `App Data`; any explicit later collapse/expand choice is preserved.

**Verification:**

- `Grep` - `KEY_APP_DATA_EXPANDED to prefs.getBoolean\(KEY_APP_DATA_EXPANDED, true\)` returns exactly **one** hit in `GeneralSettingsSectionsHelper.kt`.
- `Grep` - `KEY_INTERFACE_EXPANDED to prefs.getBoolean\(KEY_INTERFACE_EXPANDED, false\)` returns exactly **one** hit in `GeneralSettingsSectionsHelper.kt`.
- `Grep` - `KEY_SYSTEM_EXPANDED to prefs.getBoolean\(KEY_SYSTEM_EXPANDED, false\)` returns exactly **one** hit in `GeneralSettingsSectionsHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` (+1 LOC). Dev log recorded.

---

### Step 01.2 - Normalize credentials field XML contract in portrait and landscape

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In both `fragment_settings_general.xml` files, keep the visible hint on `tilDefaultUser` / `tilDefaultPassword` only and remove duplicate `android:hint` from the nested `TextInputEditText` controls. Preserve all existing ids, keep `iconHelpDefaultCredentials` independent, and keep the same semantic row structure in both orientations so the credentials row exposes the same editable affordance in portrait and landscape. Do not move the row or modify unrelated settings sections.

**Verification:**

- `Grep` - `android:id="@\+id/etDefaultUser"` returns exactly **one** hit in each layout file.
- `Grep` - `android:id="@\+id/etDefaultPassword"` returns exactly **one** hit in each layout file.
- `Grep` - `android:hint="@string/default_user"` returns exactly **two** hits across the two layout files.
- `Grep` - `android:hint="@string/default_password"` returns exactly **two** hits across the two layout files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 4/4 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_general.xml` (-2 LOC), `app_v2/src/main/res/layout-land/fragment_settings_general.xml` (+0 LOC semantic change). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build standard-debug`.
- [x] `Grep` for `TODO\(phase-01\)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `App Data` is visible by default for first-time entry, while stored user preference still wins after the first explicit toggle.
- Portrait and landscape now expose the credentials row with the same hint and editable contract.
- Phase 02 may assume the row is visible and orientation-consistent.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent schema change involved.
