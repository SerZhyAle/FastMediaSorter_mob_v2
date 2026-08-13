# Phase 06 - Settings UI Profile Change

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 04
**Blocks:** Phase 07, 08
**Steps done:** 4 / 4
**Started:** 2026-06-02 15:46:00
**Completed:** 2026-06-02 15:55:00

---

## Objective

Add device profile display and change UI to Settings → Interface section. Show current profile and source. Allow user to change profile; warn that preset may overwrite settings; require explicit confirmation before apply.

---

## Prerequisites

- [x] Phase 01, 04 are ✅ Done.
- [x] Settings Interface section already exists.
- [x] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileViewModel.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileDialogFragment.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsProfileHelper.kt` | New | ≤ 100 |
| `app_v2/src/main/res/layout/dialog_settings_profile_selector.xml` | New | N/A |
| `app_v2/src/main/res/layout-land/dialog_settings_profile_selector.xml` | New | N/A |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | N/A |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | N/A |
| `app_v2/src/main/res/values/strings.xml` | Modified | +10 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +10 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +10 keys |

---

## Steps

### Step 06.1 - Create SettingsProfileViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileViewModel.kt`
**Depends on:** - start of phase

**Status:** `[x] done`

---

### Step 06.2 - Create SettingsProfileDialogFragment and layouts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileDialogFragment.kt`, layouts (2 dialog layouts)
**Depends on:** Step 06.1

**Status:** `[x] done`

---

### Step 06.3 - Add localized settings profile strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 06.2

**Status:** `[x] done`

---

### Step 06.4 - Integrate into Settings Interface section

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 06.2

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles.
- [x] Settings shows current profile; changing profile shows warning; apply saves to repository.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entries.

---

## Handoff Notes to Next Phase

Settings profile selection UI is complete and fully integrated. Users can view their current device profile and selection source, click to change, review a warning message about resetting settings, and confirm to apply a new profile.
Phase 07 implements the actual preset matrix application logic.

---

## Rollback Plan

Revert settings general fragment, layouts, and strings.
