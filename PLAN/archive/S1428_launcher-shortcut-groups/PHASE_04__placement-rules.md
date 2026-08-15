# Phase 04 - Placement rules

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 3 / 3

> **Unblocked 2026-08-08.** Strategic §6.12 was resolved as option (в): seeding places a second header,
> so the first section always has a next header to end at and §6.7 applies literally. Membership needs
> no trailing-section special case.
>
> **The gadget refusal changed shape with it.** The second header put the whole desktop inside one
> section or the other, so "no gadget inside a section" would have refused every gadget everywhere. The
> owner refined §6.11 the same day: the refusal is "a gadget may not cover a header row", which removes
> exactly the undefinable straddle case and leaves the seeded clock free to move and resize.
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Express positional membership once, and refuse a gadget that would cover a section header row using the layer that already refuses an overlapping placement.

---

## Anchors

- `LauncherDesktopRepositoryImpl` - `.../data/repository/LauncherDesktopRepositoryImpl.kt` - `addCell:33`, `addCellInFirstFreeSlot:59`, `moveCell:144`, `resizeCell:113`.
- `LauncherCellDao.findOverlapping` - `.../data/local/db/LauncherCellEntity.kt:67` - real rectangle intersection.

## When the straddle check actually fires (established against the code, 2026-08-08)

A header is drawn full width, so on the grid it was stored for, nothing can share its row at all - the
existing overlap invariant already refuses it, and the straddle rule would be dead code. The rule earns
its keep in exactly one situation, and that situation is already the first row of strategic §7's risk
table: `findOverlapping` is a SQL predicate over the **stored** `spanW`, while the renderer widens a
header to the **live** column count via `LauncherGridGeometry.renderSpanW`. Raise the density factor or
rotate onto a wider grid and the squares past the stored span are free in the database while covered on
screen. That is the gap this check closes, and it is why it cannot be replaced by "the overlap check
already handles it".

Phase 05 must close the same gap from the other side by storing a header at the maximum column count
rather than the current one - see its own note.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembership.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 400 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/launcher/LauncherSectionMembershipTest.kt` | New | ≤ 250 |

---

## Steps

### Step 04.1 - State positional membership in one pure function

**Files:** `.../domain/model/launcher/LauncherSectionMembership.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write one pure function answering which section, if any, a given row belongs to: a cell belongs to the nearest header at or above its row, up to the next header. Add a second pure predicate for the straddle case - whether a rectangle of a given top row and height covers any header row. Keep both free of Android types so they are unit-testable, and let the collapse geometry of Phase 03 and the placement checks below call this single definition.

**Why:**

Strategic §6.7 defines membership positionally as everything below the header up to the next header, and §5.1.3 requires the chosen branch to answer drag, neighbour deletion, rotation and column-count change with one rule rather than three; §6.11's 2026-08-08 refinement makes "covers a header row" the exact predicate the placement layer refuses on.

**Verification:**

- Unit test covers: a cell directly under a header, a cell under the second of two headers, a cell above every header, and an empty section.
- Unit test covers the straddle predicate: a 1-row cell never straddles, a 2-row cell starting directly above a header does, and the same cell one row lower does not.
- `.\a.ps1 fu --tests "*LauncherSectionMembership*"` passes.

**Status:** `[x]` done

---

### Step 04.2 - Refuse a gadget inside a section

**Files:** `.../data/repository/LauncherDesktopRepositoryImpl.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the same placement layer that already checks rectangle intersection, refuse to place, move or resize a `GADGET` cell whose rectangle would cover a header row. Apply it to `addCell`, `addCellInFirstFreeSlot`, `moveCell` and `resizeCell` - a rule enforced on insert but not on move is not enforced. `addCellInFirstFreeSlot` must skip such an anchor and keep scanning rather than fail outright, or a tall gadget would be unplaceable on a desktop whose first free anchor happens to sit above a header.

**Why:**

Strategic §6.11, as refined by the owner on 2026-08-08 once §6.12 introduced the second header, refuses exactly the undefinable case - a gadget lying neither inside a section nor outside it - and its consequence puts the check in the placement layer rather than the renderer. The literal earlier reading ("no gadget inside any section") became unimplementable: with two headers the whole desktop is inside one section or the other, so it would have refused every gadget everywhere, the seeded clock included.

**Verification:**

- `Grep` - the straddle check is reachable from all four placement functions.
- Unit test: moving a two-row gadget so it covers a header row fails; the same gadget one row lower succeeds; a one-row shortcut is never refused.

**Status:** `[x]` done

---

### Step 04.3 - Show the refusal the way an occupied slot is shown

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Surface the refusal through the same feedback the desktop already gives when a cell is dropped on an occupied slot. Do not invent a second refusal style. **Read the existing feedback before writing anything:** an occupied-slot refusal today is silent - `addCell` "silently does nothing when the square is taken", `moveCell` lets "the cell simply snap back where it was" and `resizeCell` "keeps the last valid size" (`LauncherHomeViewModel` lines 231-235, 317, 324). So identity of feedback means the straddle refusal is also silent: the repository returns false and the existing re-render puts the cell back. Adding a Toast here would be the second refusal style this step forbids.

**Why:**

Strategic §6.11 requires the refusal to be visible to the user by the same means an occupied-slot refusal already uses, and §11.13 makes that identity an acceptance criterion.

**Verification:**

- `Grep` - no new `LauncherHomeEvent.Message` is emitted on the straddle path.
- Manual: strategic §11.13 - dragging a two-row gadget onto a header snaps it back exactly as dropping onto an occupied cell does.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `.\a.ps1 fu --tests "*Launcher*"` passes.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - Room/DAO and transaction edges included, since placement checks run inside the existing transaction.

---

## Handoff Notes to Next Phase

Membership has exactly one definition, and Phase 03's collapse geometry and Phase 04's placement rules both consume it. Seeding in Phase 05 bypasses the interactive checks by design and stays the packer's responsibility.

---

## Rollback Plan

Revert the phase commit. A gadget previously refused entry is simply allowed again; no stored cell is rewritten.
