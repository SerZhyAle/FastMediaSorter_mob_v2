# Phase 05 - Destinations fragment landscape completion

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (shared column convention)
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Pair remaining solo COMPACT toggles in the destinations landscape layout, while keeping the file under the 1500-LOC hard limit. Landscape-only.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Read `research/01__settings-fragment-element-inventory.md` (destinations LOC risk).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 1500 (HARD) |

> File is ~1207 LOC - Step 05.0 backs it up; Step 05.1 must monitor the line count and stop short of 1500. Portrait `layout/fragment_settings_destinations.xml` is NOT edited. Card margin-token inconsistency noted in research is out of scope (cosmetic).

---

## Steps

### Step 05.0 - Backup landscape destinations layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy the file to `temp/fragment_settings_destinations_land_<timestamp>.xml` before any edit (file exceeds 500 LOC and is near the 1500 limit).

**Verification:**

- `Glob` - a `temp/fragment_settings_destinations_land_*.xml` file exists.

**Status:** `[ ]` not done

---

### Step 05.1 - Pair remaining solo toggles within LOC budget

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 05.0

**Prompt for developer:**

> Identify COMPACT `SettingsToggleRow` items still rendered full-width solo in landscape (many copy/move/safety toggles are already paired - target only the unpaired ones). Pair logically related ones using the Phase 01 weighted-column shape, with `nextFocusLeft/Right`. Prioritise the tallest cards. STOP adding pairings before the file reaches 1500 LOC - each wrapper adds ~6 lines. If the highest-value pairings cannot fit under 1500, extract one self-contained card body into a `<include>` partial (`layout/_settings_destinations_<card>.xml` reused by both orientations) FIRST, then add pairings; record which card was extracted in the commit. Keep flavor-conditional cards (`groupSystemApps`, `groupMenuScreenshot`, `groupScreenGestures`) full-width - do not 2-up them (visibility holes).

**Verification:**

- `Bash` - file line count < 1500 (HARD gate).
- `Grep` - new `layout_weight="1"` pairings added vs backup.
- `Grep` - `nextFocusRight` count increased vs backup.
- `Grep -n "=\"#"` returns zero hardcoded hex colors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `.\a.ps1 fr` passes.
- [ ] File line count < 1500.
- [ ] Portrait id set ⊆ landscape id set (no id lost); no portrait `layout/fragment_settings_destinations.xml` change in diff.
- [ ] Flavor-conditional cards (`groupSystemApps`, `groupMenuScreenshot`, `groupScreenGestures`) left full-width, untouched in structure.
- [ ] If a `<include>` partial was created: it exists and is referenced by both orientations.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the modified file (and any new partial).

---

## Handoff Notes to Next Phase

All in-scope landscape fragments densified. If the LOC limit forced deferring some destinations pairings, note them in the commit for a possible follow-up.

---

## Rollback Plan

Restore from `temp/fragment_settings_destinations_land_<timestamp>.xml` or revert the phase commit.
