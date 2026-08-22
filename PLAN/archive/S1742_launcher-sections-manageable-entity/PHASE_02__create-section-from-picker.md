# Phase 02 - Create a Section From the Picker

**Strategic spec:** [`../S1742_launcher-sections-manageable-entity.md`](../S1742_launcher-sections-manageable-entity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Put "create a section" as the first row of the existing section picker, ask for a name, and place the new header through the ordinary cell-placement path.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - a minted key renders under its own name.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAddFlowManager.kt` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Modified | ≤ 40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherSectionNameDialogFragment.kt` | New | ≤ 100 |
| `app_v2/src/launcherEnabled/res/layout/dialog_launcher_section_name.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellDraft.kt` | Modified | ≤ 30 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 40 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 02.1 - Offer "Create a Section" as the First Row

**Files:** `LauncherAddFlowManager.kt`, `strings.xml` (en/ru/uk)
**Depends on:** - start of phase

**Prompt for developer:**

> Add a "create a section" entry as the first row of the section picker the add flow shows, with its label in EN/RU/UK added through `scripts/utils/set-android-string.ps1 -Action add`. Selecting it opens a single-field name prompt rather than placing anything immediately.

**Why:** Owner decision 5 (resolved) - the create entry is the first row of the existing section list, not a separate button on the picker screen.

> Added during execution (S1742): the name prompt needed its own dialog - the launcher's only other
> single-field prompt is the phone-number one, and `RenameDialog` is bound to files and a file-operation
> use case, so it could not serve. The caption also had to be threaded through `LauncherCellDraft` and
> both add entry points, because a user section carries its name from birth.


**Verification:**

- `Grep` - the create entry is prepended to the section list the picker receives.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<new key prefix>"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Create row is the first entry of the section picker; four strings added across EN/RU/UK, parity exit 0.

---

### Step 02.2 - Place the Named Section Through the Ordinary Path

**Files:** `LauncherAddFlowManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> On confirming a name, mint a user key (Phase 01), place a SECTION cell through the same placement call every other add uses, and write the entered name as the cell's label override. A blank name places nothing.

**Why:** Strategic ADR-1 - a user section is an ordinary section cell, so it must go through the same placement and overlap handling as any other cell rather than a private write.

**Verification:**

- `Grep` - the creation path calls the shared placement entry and passes a label override.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Name prompt mints a user key and places the header through the shared placement path with its caption; a.ps1 fk exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles cleanly via `.\a.ps1 fk`.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

A user can now create a named section; Phase 03 gives them a way to rename it.

---

## Rollback Plan

Revert the phase commit(s) - no database migration changed.
