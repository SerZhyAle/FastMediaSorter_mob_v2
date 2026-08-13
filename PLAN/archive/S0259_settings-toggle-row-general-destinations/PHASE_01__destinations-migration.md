# Phase 01 - Destinations Migration

**Strategic spec:** [`../S0259_settings-toggle-row-general-destinations.md`](../S0259_settings-toggle-row-general-destinations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Migrate the destinations settings screen and its fragment wiring to `SettingsToggleRow` without changing behavior.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Backups created for any touched file projected above 500 lines.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 750 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Backup the large fragment before edits

**Files:** `OperationsSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a timestamped backup of `OperationsSettingsFragment.kt` under `temp/` before modifying the 653-line file.

**Verification:**

- `Test-Path` - matching `temp/*OperationsSettingsFragment*.backup*` file exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Backups created in `temp/`: `OperationsSettingsFragment_.backup.kt`, `fragment_settings_destinations_land_.backup.xml`, `SettingsSearchIndex_.backup.kt`.

---

### Step 01.2 - Convert destinations portrait and landscape rows

**Files:** `fragment_settings_destinations.xml`, `fragment_settings_destinations.xml` (layout-land)
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the destinations screen switch rows with `SettingsToggleRow`, preserving paired-row grouping, nested visibility wrappers, helper text, and the existing trailing trash-clear action on the trash row. Keep portrait and landscape IDs aligned as `row*`.

**Verification:**

- `Grep` - `SettingsToggleRow` appears in both destinations layout files.
- `Grep` - migrated row IDs use `@+id/row` prefixes in both files.
- `Grep` - raw `SwitchMaterial` count decreases in both destinations layout files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Portrait and landscape destinations layouts migrated to `SettingsToggleRow` with `row*` ids and scheduled/safety/copy-move parity preserved.

---

### Step 01.3 - Rewire OperationsSettingsFragment to row API

**Files:** `OperationsSettingsFragment.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Switch the destinations-related bindings in `OperationsSettingsFragment` from `binding.switch*` to `binding.row*`, using the same listener/update pattern already used by other migrated settings fragments. Preserve dependent visibility logic and the extra trash action wiring.

**Verification:**

- `Grep` - destinations-related `binding.switch*` references are gone from `OperationsSettingsFragment.kt`.
- `Grep` - corresponding `binding.row*` references exist for the migrated destinations rows.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `OperationsSettingsFragment.kt` rewired from `switch*` to `row*`, including scheduled toggle and trash trailing action hookup.

---

### Step 01.4 - Prove destinations behavior still compiles structurally

**Files:** destinations layouts + `OperationsSettingsFragment.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Re-read the touched fragment and verify no stale IDs remain between XML and view binding names before moving on to general settings.

**Verification:**

- `Grep` - no `switchEnableSafeMode|switchConfirmDelete|switchConfirmMove|switchUseTrash|switchEnableCopying|switchOverwriteOnCopy|switchGoToNextAfterCopy|switchEnableMoving|switchOverwriteOnMove` hits remain in `OperationsSettingsFragment.kt`.
- `Grep` - no removed `@id/switch*` references remain in the touched destinations XML files for migrated rows.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. No stale migrated `switch*` ids remain in destinations XML or fragment wiring.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Portrait and landscape destinations layouts both use `SettingsToggleRow` for the migrated rows.
- [x] `OperationsSettingsFragment.kt` builds against the new binding names.
- [x] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

General settings should mirror the same row API conventions so `SettingsSearchIndex` can be updated once.
