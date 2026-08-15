# Phase 01 - Foundations

**Strategic spec:** [`../S0258_settings-toggle-row-template.md`](../S0258_settings-toggle-row-template.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Introduce the reusable toggle-row component and align repo rules with the new title-adjacent helper pattern.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt` | New | ≤ 260 |
| `app_v2/src/main/res/layout/view_settings_toggle_row.xml` | New | ≤ 220 |
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt` | Modified | ≤ 220 |
| `docs/ARCHITECTURE.md` | Modified | ≤ 220 |
| `CLAUDE.md` | Modified | ≤ 900 |
| `.github/copilot-instructions.md` | Modified | ≤ 900 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Update canonical trigger-row rules

**Files:** `docs/ARCHITECTURE.md`, `CLAUDE.md`, `.github/copilot-instructions.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the repository's canonical trigger-row documentation so switch rows now place the help icon inline next to the title, with subtitle below, while still allowing an optional trailing action slot for exceptional rows. Remove wording that requires the help icon to be the rightmost child.

**Verification:**

- `Grep` - `title + helper` present in `docs/ARCHITECTURE.md`.
- `Grep` - `rightmost child` absent from `docs/ARCHITECTURE.md`.
- `Grep` - `help-icon inline next to the title` present in `.github/copilot-instructions.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: docs/ARCHITECTURE.md (Pattern A restructured: title+helper inline, subtitle below, optional trailing action; added SettingsToggleRow reusable component reference), .github/copilot-instructions.md (UI_TRIGGER_ROW constraint updated to canonical helper-inline pattern). CLAUDE.md skipped - no trigger-row content existed to migrate. Dev log recorded.

---

### Step 01.2 - Add reusable settings toggle row view

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt`, `app_v2/src/main/res/layout/view_settings_toggle_row.xml`, `app_v2/src/main/res/values/attrs.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Introduce a compound view for canonical switch rows. The component must expose title, subtitle, help payload, checked state, enabled state, change listener, and an optional trailing action slot. Help opens the existing tooltip dialog.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt` exists.
- `Glob` - `app_v2/src/main/res/layout/view_settings_toggle_row.xml` exists.
- `Grep` - `declare-styleable name="SettingsToggleRow"` present in `app_v2/src/main/res/values/attrs.xml`.
- `Grep` - `class SettingsToggleRow` present in `SettingsToggleRow.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: SettingsToggleRow.kt (new compound view, 213 LOC, modelled on CollapsibleSectionHeader pattern), view_settings_toggle_row.xml (new, merge-style layout: switch + title-row(title+helper inline) + subtitle + trailing slot), attrs.xml (added declare-styleable SettingsToggleRow with str_ prefix). Dev log recorded.

---

### Step 01.3 - Add row-level helpers for settings fragments

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Extend the base settings fragment helpers so fragments can bind and update the new row component without duplicating checked-state glue code.

**Verification:**

- `Grep` - `bindSwitch(row: SettingsToggleRow` present in `BaseSettingsFragment.kt`.
- `Grep` - `setSwitchChecked(row: SettingsToggleRow` present in `BaseSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: BaseSettingsFragment.kt (added row-level overloads bindSwitch/setSwitchChecked for SettingsToggleRow; row.setOnCheckedChangeListener + setCheckedSilently preserve isUpdatingFromSettings semantics). Dev log recorded.

---

### Step 01.4 - Refresh strategic/tactical artifacts for implementation start

**Files:** `PLAN/S0258_settings-toggle-row-template.md`, `PLAN/S0258_settings-toggle-row-template/INDEX.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Keep the spec and tactical index in sync with the start of implementation: strategic status remains tactical-ready, tactical index shows Phase 01 in progress.

**Verification:**

- `Grep` - `**Status:** Approved` present in strategic spec.
- `Grep` - `| 01 | foundations | - | 🚧 In Progress |` present in tactical index.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: PLAN/S0258_settings-toggle-row-template.md (Status: Approved retained per spec contract), PLAN/S0258_settings-toggle-row-template/INDEX.md (Phase 01 counter bumped 0/5 -> 3/5). Dev log recorded.

---

### Step 01.5 - Validate foundation compiles before pilot migration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Run a target build after the foundation changes so the pilot phase starts from a compiling baseline.

**Verification:**

- `/build` - `standard debug` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. Build: BUILD SUCCESSFUL in 40s (assembleStandardDebug). Version: 2.60.5191.845. Pre-existing `open` warnings in PlayerActivity.kt unrelated to S0258.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the reusable row API and the new canonical repo rule; Phase 02 may migrate a real screen without re-litigating layout structure.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing persistence format changed.
