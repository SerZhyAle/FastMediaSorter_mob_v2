# Phase 01 - MainActivity programs-menu extraction

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-06-28
**Completed:** 2026-06-28

---

## Objective

Extract the programs-menu orchestration out of `MainActivity` into a new `MainProgramsMenuCoordinator` helper, behaviour-preserving, so `MainActivity` drops below the 1500-LOC limit and the new scenario has a clean insertion point. No new feature behaviour.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `MainActivity.kt` confirmed at ~1540 LOC (> 1500 limit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainProgramsMenuCoordinator.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | < 1500 after edit |

> `MainActivity.kt` is > 500 LOC: take a timestamped backup into `temp/` before editing.

---

## Steps

### Step 01.1 - Create MainProgramsMenuCoordinator and move menu orchestration into it

**Files:** `MainProgramsMenuCoordinator.kt` (New), `MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `MainActivity.kt` to `temp/` first (> 500 LOC). Create `MainProgramsMenuCoordinator` in `ui/main/helpers/`. Move the cohesive programs-menu block out of `MainActivity` into it: the `MENU_ITEM_*` / `MENU_ORDER_*` companion constants currently in `MainActivity` (CALCULATOR, CAMERA_OCR, APP_LAUNCH_PANEL and the order constants), `getMainWindowDropdownMenuItemCount()`, `populateMainWindowDropdownMenu()`, `programNewWindowActionFor()`, and `programRemoveActionFor()`. Inject/forward the existing per-scenario menu managers and the `isXxxEnabled` gate flags the moved functions read - pass them in as constructor params or method arguments rather than reaching back into `MainActivity` state. The coordinator must not own Android lifecycle; `MainActivity` keeps owning the managers and simply delegates these calls to the coordinator. This is a pure move - identical behaviour, no new menu items, no id/order changes.

**Verification:**

- `Glob` - `MainProgramsMenuCoordinator.kt` exists.
- `Grep` - `class MainProgramsMenuCoordinator` matches exactly once.
- `Grep` - `fun populateMainWindowDropdownMenu` / `fun programRemoveActionFor` / `fun programNewWindowActionFor` now resolve inside `MainProgramsMenuCoordinator.kt`.
- `Grep` - those four function bodies no longer duplicated in `MainActivity.kt` (delegating call-sites only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-28 - Created `MainProgramsMenuCoordinator` (5 public methods: populate/handleMenuItem/itemCount/newWindowActionFor/removeActionFor + `ProgramsMenuGate`). Moved item ids/orders + the 5 menu functions out of MainActivity; MainActivity keeps thin delegating wrappers + `currentProgramsMenuGate()`. Removed 2 dead imports. Coordinator method names cleaned (populate vs populateMainWindowDropdownMenu) - bodies verified resident in coordinator.

---

### Step 01.2 - Verify MainActivity is under the size limit and behaviour is preserved

**Files:** `MainActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Confirm `MainActivity.kt` is now under 1500 LOC. Build the standard debug variant and confirm the programs menu / panel still populate, dispatch, "Open in new window", and "Remove" exactly as before (no behaviour change).

**Verification:**

- `wc -l app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` → result < 1500.
- `/build` (standard debug) compiles green.
- `Grep` - no `BuildConfig.IS_` / `SUPPORT_` flavor guard introduced in `MainActivity.kt` or the new coordinator.

**Status:** `[x] done`

**Step Log:**

- 2026-06-28 - `wc -l MainActivity.kt` = 1441 (< 1500) PASS. `.\a.ps1 fk` (compileStandardDebugKotlin) BUILD SUCCESSFUL after adding the coordinator import. neuroslop gate: no regression (empty-catch -1, em-dash -1). No flavor guard added.

---

## Phase Done Criteria

- [ ] Both steps `[x]`.
- [ ] `MainActivity.kt` < 1500 LOC.
- [ ] Project compiles - `/build`.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class) - deferred allowed to Phase 08, but the class must be scannable.

---

## Handoff Notes to Next Phase

`MainProgramsMenuCoordinator` is the single home for menu-item registration, count, dispatch, and the per-item new-window / remove resolvers. Phase 07 adds the screen-recording scenario by editing the coordinator, not `MainActivity`.

---

## Rollback Plan

Revert the phase commit - pure refactor, no data migration or user-facing surface changed.
