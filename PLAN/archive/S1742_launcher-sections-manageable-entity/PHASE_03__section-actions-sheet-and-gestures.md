# Phase 03 - Section Actions Sheet and Gestures

**Strategic spec:** [`../S1742_launcher-sections-manageable-entity.md`](../S1742_launcher-sections-manageable-entity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Give a section header a list of actions - rename first - reachable both by long press in normal unlocked mode and by a visible button in edit mode.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/section/LauncherSectionActionsSheet.kt` | New | ≤ 150 |
| `app_v2/src/launcherEnabled/res/layout/sheet_launcher_section_actions.xml` | New | ≤ 80 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 03.1 - Build the Actions Sheet

**Files:** `LauncherSectionActionsSheet.kt`, `sheet_launcher_section_actions.xml`, `strings.xml` (en/ru/uk)
**Depends on:** - start of phase

**Prompt for developer:**

> Add a bottom sheet listing the section's actions, following the shape of `LauncherSignalListBottomSheet` (caller supplies the items and a tap callback before `show()`, first row takes focus for D-pad, tap dismisses). Iteration one carries rename only; move and delete join it in Phase 04. Strings in EN/RU/UK via `set-android-string.ps1 -Action add`.

**Why:** Owner decision 6 (resolved) - section editing is presented as a bottom sheet with a list of actions. Following the existing sheet keeps the D-pad focus behaviour the launcher already relies on.

**Verification:**

- `Glob` - both new files exist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<new key prefix>"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - LauncherSectionActionsSheet and layout created; strings localized

---

### Step 03.2 - Open the Sheet by Long Press in Normal Unlocked Mode

**Files:** `LauncherCellViewBinder.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Attach a long-press handler to the section header that opens the actions sheet, and only when the desktop is unlocked and not in edit mode. Leave `decorateForEdit`'s scrim untouched, and extend the header's accessibility announcement so the new action is named rather than silently present.

**Why:** Owner decision 8 (resolved) plus research 01 item 4 - in edit mode the long press already starts a drag, and in normal mode no handler exists at all, so the gesture is free there and only there. Strategic risk 4 requires the announcement to be revised together with the gesture, not after it.

**Verification:**

- `Grep` - a long-click listener exists on the section path and is guarded by the unlocked, non-edit condition.
- `Grep` - the header's accessibility announcement names the new action.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Section header long press and edit-mode sectionActionsButton added and verified

---

### Step 03.3 - Add the Gesture-Free Button in Edit Mode

**Files:** `LauncherCellViewBinder.kt`, `sheet_launcher_section_actions.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> In edit mode, give the section header a visible actions button that opens the same sheet, focusable and activatable by touch, mouse, D-pad and keyboard.

**Why:** Owner decision 9 (resolved) and strategic §3.3 - a capability reachable only by a gesture is unreachable for a screen reader, which is what risk 3 records.

**Verification:**

- `Grep` - the edit-mode header path adds a focusable actions button wired to the same sheet.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Section header long press and edit-mode sectionActionsButton added and verified

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles cleanly via `.\a.ps1 dq`.
- [x] Layout evidence for the header and the sheet captured this phase and its path written into the Step Log (UI phase gate).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The sheet exists and is reachable two ways; Phase 04 adds move and delete to it.

---

## Rollback Plan

Revert the phase commit(s) - no database migration changed.
