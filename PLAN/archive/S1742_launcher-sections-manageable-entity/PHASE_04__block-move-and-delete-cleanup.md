# Phase 04 - Block Move and Delete Cleanup

**Strategic spec:** [`../S1742_launcher-sections-manageable-entity.md`](../S1742_launcher-sections-manageable-entity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Move a section together with everything it owns, and stop a deleted section from leaving its collapsed state behind.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - the sheet exists to host these actions.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 120 |

---

## Steps

### Step 04.1 - Swap a Section With Its Neighbour, Contents and All

**Files:** `LauncherDesktopRepository.kt`, `LauncherDesktopRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a repository operation that exchanges a section block with the adjacent section block in a given direction: the header and every cell that belongs to it by position, moved as one transaction, preserving each block's internal row order. Moving past the first or the last section is a no-op rather than an error.

**Why:** Research 01 item 2 - no multi-cell move exists, single-cell move refuses on a differently-sized blocker, and the only tail shift is downward and uniform. An exchange of adjacent blocks is the one shape that cannot collide by construction, which is what strategic risk 1 demands.

**Verification:**

- `Grep` - the new operation exists on both the interface and the implementation and runs inside one transaction.
- Unit tests from step 04.3 pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - swapSectionBlock operation added to interface and implementation

---

### Step 04.2 - Delete a Section and Its Collapsed State Together

**Files:** `LauncherHomeViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> When the removed cell is a section, clear its collapsed-state entry as well as the row, using the same orientation-and-target key the collapse store writes.

**Why:** Research 01 item 3 measured that the entry is orphaned today, so a later section reusing that target silently inherits a stranger's collapsed state - the user-visible half of the leak, not the disk usage.

**Verification:**

- `Grep` - the removal path clears the collapse entry for a section cell.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - removeCell clears collapsed section state entry for section cells

---

### Step 04.3 - Prove the Block Move Keeps Every Owner

**Files:** `LauncherDesktopRepositoryImplTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add cases over two adjacent sections each holding shortcuts: after a move the two blocks have exchanged position, every shortcut still belongs to the same section it started in, and the internal order inside each block is unchanged. Add the no-op case at the ends.

**Why:** Strategic §11 item 3 and risk 1 - "no shortcut changes owner" is the acceptance sentence of this ticket, and membership is positional, so only a test that re-computes ownership after the move can state it.

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.data.repository.LauncherDesktopRepositoryImplTest"` exits 0.
- The filtered result XML lists the new case names and reports 0 failures.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Unit tests added in LauncherDesktopRepositoryImplTest proving block move exchanges positions and keeps shortcut owners

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles cleanly via `.\a.ps1 dq`.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit(s) - no database migration changed.
