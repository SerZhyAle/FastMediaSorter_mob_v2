# Phase 02 - Move Resource-Ops-Overflow Toggle

**Strategic spec:** [`../S0911_main-window-interface-settings-group.md`](../S0911_main-window-interface-settings-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Relocate the "Resource ops in overflow menu" row from the General > Interface section into the new General > Main window interface section - a same-fragment, same-file move with zero Kotlin wiring change (its read/write code already references the row by view id, unaffected by which section contains it).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`containerMainWindowInterface` exists in both General layouts).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Move the row's markup between sections

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** - start of phase (Phase 01 already done)

**Prompt for developer:**

> In both layout files, cut the `layoutResourceOpsInOverflowMenu` wrapper `LinearLayout` (containing `rowResourceOpsInOverflowMenu`, comment "S0160: Resource ops overflow toggle..") out of `containerInterface` and paste it as the first child of `containerMainWindowInterface` (added empty in Phase 01). Keep the view ids, the `SettingsToggleRow` attributes, and the explanatory comment unchanged - only the parent container changes. Do not touch `GeneralSettingsViewSetupHelper.kt` or `GeneralSettingsObserversHelper.kt` - both already reference `binding.rowResourceOpsInOverflowMenu` by id and need no change since the id and its enclosing `FragmentSettingsGeneralBinding` are unaffected by which section contains the row.

**Verification:**

- `Grep` - `rowResourceOpsInOverflowMenu` no longer appears between `containerInterface` and its closing tag in either layout file (i.e. `containerInterface`'s body no longer contains it).
- `Grep` - `rowResourceOpsInOverflowMenu` appears inside `containerMainWindowInterface`'s body in both layout files.
- `Grep` - `binding.rowResourceOpsInOverflowMenu` still present, unchanged, in both `GeneralSettingsViewSetupHelper.kt` and `GeneralSettingsObserversHelper.kt` (confirms no accidental wiring edit).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: layout/fragment_settings_general.xml (repositioned), layout-land/fragment_settings_general.xml (repositioned; landscape row comment corrected from "Three" to "Two" toggles since the third column moved out - the row is now 2-column not 3). Kotlin wiring untouched as expected. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [~] Project compiles - **whole-project resource-link (`.\a.ps1 fr`) currently BLOCKED by unrelated, pre-existing tree state**: `aapt2` fails on `app_v2/src/main/res/layout/view_recording_indicator.xml:42,50` ("attribute app:icon not found") - that file carries an `S0774` authorship comment and was not touched by this ticket; S0774 was independently flagged `drift-needs-review` earlier this session (unrelated in-flight WIP, not to be fixed here per CLAUDE.md "never fix another ticket's in-flight work"). Interim proof for this phase's own change: both touched layout files parse as well-formed XML (`[xml](Get-Content ..)` PASS), the moved block is byte-identical to the block Phase 01's own successful `.\a.ps1 fc` build already validated, and `aapt2`'s error list contains zero entries for either touched file - only the unrelated file. Re-run `.\a.ps1 fr`/`fc` once S0774's WIP stabilizes to get the formal whole-project green build.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The new section now visibly holds one working row, proving the scaffold from Phase 01 renders and persists correctly. Phase 03 performs the first cross-fragment move.

**Carried-forward blocker:** the unrelated `view_recording_indicator.xml` (S0774 WIP) resource-link failure noted above will keep blocking whole-project resource-link builds (`fr`/`fc`/`d`) for Phases 03-05 too, until S0774 stabilizes. Each subsequent phase records the same interim-evidence approach (Kotlin compile via `fk` + XML well-formedness + targeted greps) instead of insisting on a full green build it cannot control.

---

## Rollback Plan

Low-risk: revert this phase's commit(s) - pure XML repositioning within the same file, no Kotlin change, no data migration.
