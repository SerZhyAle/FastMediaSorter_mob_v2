# Phase 03 - Accept a pin request onto the desktop

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Add the single domain entry point that accepts a pin request and lands it in the first free desktop slot, answering whether it landed.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1205 phase 03"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/AcceptPinnedShortcutUseCase.kt` | New | ≤ 90 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** Sits beside `PlaceHomeWidgetOnLauncherDesktopUseCase` in `src/main/java`, where every launcher use case already lives; nothing flavor-specific.

---

## Steps

### Step 03.1 - Write AcceptPinnedShortcutUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/AcceptPinnedShortcutUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `AcceptPinnedShortcutUseCase @Inject constructor(dataSource: AppShortcutDataSource, desktopRepository: LauncherDesktopRepository)` with `suspend operator fun invoke(request: LauncherApps.PinItemRequest, orientation: LauncherOrientation, addedAt: Long): Boolean`. Wrap the whole body in `withContext(Dispatchers.IO)` exactly as `StartAppShortcutUseCase` does. Accept the request through `acceptPinRequest`; return false when it yields null. Read `desktopRepository.state()`, take the column count for `orientation`, return false when it is below 1, then build a `LauncherCell` with `kind = SHORTCUT`, `spanW = 1`, `spanH = 1`, `labelOverride = null`, `rowIndex = 0`, `colIndex = 0` and `target = LauncherCellCommand.PinnedShortcut(..).encode()`, and hand it to `addCellInFirstFreeSlot(cell, columns)`. Return whether that call produced an id. Model the whole shape on `PlaceHomeWidgetOnLauncherDesktopUseCase`, including its note that the anchor fields are ignored by the placement call.

**Why:**

Strategic §4 decision 2 fixes placement as the first free cell scanned row by row - the mechanics already settled in S1170 - and §4's closing note requires the accepting surface to report a refusal rather than a success when the cell did not land, which only a boolean answer from here makes possible.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/AcceptPinnedShortcutUseCase.kt` exists.
- `Grep` - `class AcceptPinnedShortcutUseCase` matches exactly once in that file.
- `Grep` - `addCellInFirstFreeSlot` present in that file.
- `Grep` - `PinnedShortcut(` present in that file.
- `Grep` - `withContext(Dispatchers.IO)` present in that file.
- `Grep -n "Log\.d\("` over that file returns zero hits.
- `.\a.ps1 fk` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 03.1 PASS. File exists, `class AcceptPinnedShortcutUseCase` = 1 hit, `addCellInFirstFreeSlot` = 2 hits, `PinnedShortcut(` = 1 hit, `withContext(Dispatchers.IO)` = 1 hit, `Log.d(` = 0 hits.
- 2026-08-06 - First compile attempt FAILED in `LauncherHomeActivity.kt` (`addCellButton`, `onAddCellClick`, `NO_SLOT` unresolved) - a concurrent sibling session holding `CODE.LOCK` for "S1209 phase 02 taskbar add-shortcut" was mid-edit in that file. Not a regression of this ticket and not this ticket's file to fix; waited for the sibling instead of touching it.
- 2026-08-06 - `.\a.ps1 fk` - expected: 0 | actual: 0 (BUILD SUCCESSFUL) once the sibling's edit settled. This build also carried Phase 04's code and the S1205 probe tags, per the single-build rule for the final phase.
- 2026-08-06 - Phase-boundary audit: Layers 1 and 2. The use case does binder IPC plus an icon decode, and now runs entirely inside `withContext(Dispatchers.IO)` - the P2 carried from Phase 01 is closed. No listener, no Room, no shared mutable state. No P0/P1 findings.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The boolean this use case returns is the whole verdict Phase 04 shows the user: true selects `launcher_widget_placed`, false selects `launcher_widget_no_room`. Phase 04 adds no decision of its own.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed; nothing invokes the use case until Phase 04.
