# Phase 02 - General Migration

**Strategic spec:** [`../S0259_settings-toggle-row-general-destinations.md`](../S0259_settings-toggle-row-general-destinations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Migrate the general settings screen and its helper stack to `SettingsToggleRow` while preserving current sections and observer behavior.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] No unresolved view-binding errors remain from destinations migration.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 200 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Convert general portrait and landscape rows

**Files:** `fragment_settings_general.xml`, `fragment_settings_general.xml` (layout-land)
**Depends on:** Phase 01

**Prompt for developer:**

> Replace the general settings switch rows with `SettingsToggleRow`, preserving the two-column landscape grouping, help payloads, and the existing section/container structure. Keep IDs aligned as `row*` in both orientations.

**Verification:**

- `Grep` - `SettingsToggleRow` appears in both general layout files.
- `Grep` - migrated row IDs use `@+id/row` prefixes in both files.
- `Grep` - raw `SwitchMaterial` count decreases in both general layout files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Portrait and landscape general layouts migrated to `SettingsToggleRow`; portrait-only compact/thumbnail rows preserved with nullable binding parity.

---

### Step 02.2 - Rewire general setup and observer helpers

**Files:** `GeneralSettingsObserversHelper.kt`, `GeneralSettingsViewSetupHelper.kt`, `GeneralSettingsSectionsHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Migrate helper code from `binding.switch*` accessors to `binding.row*` for the converted general rows. Use the row component silent-update API where needed, keep existing logging, and do not change section collapse or dependent visibility behavior.

**Verification:**

- `Grep` - migrated general-row `binding.switch*` references are gone from the three helper files.
- `Grep` - corresponding `binding.row*` references exist for the migrated rows.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `GeneralSettingsViewSetupHelper.kt` and `GeneralSettingsObserversHelper.kt` now use `row*` bindings and built-in row help payloads.

---

### Step 02.3 - Rewire GeneralSettingsFragment layout orchestration

**Files:** `GeneralSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Update `GeneralSettingsFragment` to use the new row/container IDs in any orientation-specific layout code and keep section orchestration unchanged.

**Verification:**

- `Grep` - no stale migrated `binding.switch*` references remain in `GeneralSettingsFragment.kt`.
- `Grep` - migrated `binding.row*` references exist where fragment-level access is still required.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. `GeneralSettingsFragment.kt` required no new direct row wiring beyond existing container/orientation orchestration.

---

### Step 02.4 - Prove helper stack consistency

**Files:** general layouts + fragment + helpers
**Depends on:** Step 02.3

**Prompt for developer:**

> Re-read the general settings fragment/helper stack and resolve any binding drift so the generated binding API is internally consistent before touching `SettingsSearchIndex`.

**Verification:**

- `Grep` - no removed migrated `@id/switch*` references remain across the touched general settings Kotlin files.
- `Grep` - no removed migrated `@id/switch*` references remain in the touched general XML files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. No stale migrated `switch*` ids remain across touched general XML or helper files.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Portrait and landscape general layouts both use `SettingsToggleRow` for the migrated rows.
- [x] General fragment/helper stack compiles against the new binding names.
- [x] Dev log entry added for every modified file.

---

## Handoff Notes to Next Phase

Once general and destinations IDs are final, `SettingsSearchIndex` can replace all `viewId = 0` placeholders with concrete row IDs.
