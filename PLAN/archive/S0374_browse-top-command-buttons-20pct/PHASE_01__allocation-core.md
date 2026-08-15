# PHASE_01 - Pure allocation core + unit test

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending

## Goal

A pure, Android-free function that partitions command-bar buttons into visible vs overflow by priority and measured width. No `View`, no `Context` - unit-testable.

## Steps

### Step 1.1 - Create allocation model + function

Create `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCommandBarAllocation.kt`.

- `data class CommandSlot(val viewId: Int, val measuredWidthPx: Int, val priority: Int)` - `priority` ascending = higher priority (overflows last).
- `data class CommandBarAllocation(val visibleIds: List<Int>, val overflowIds: List<Int>)`.
- `fun allocateCommandBar(slots: List<CommandSlot>, availableWidthPx: Int, reservedWidthPx: Int): CommandBarAllocation`.

Algorithm:
- If `slots` empty → both lists empty.
- Sort a copy by `priority` ascending (stable).
- Walk the sorted copy accumulating `measuredWidthPx`; a slot is visible while `running + slot.width + reservedWidthPx <= availableWidthPx`.
- The first slot that does not fit and every lower-priority slot after it go to overflow (no "skip-ahead" packing - preserves a predictable priority cut).
- Guard: if `availableWidthPx <= reservedWidthPx`, all slots overflow.
- Return ids in the original `slots` order within each bucket (filter the input list by membership) so the caller keeps XML visual order.

WHY-comment: priority cut, not best-fit packing, keeps the visible set a stable prefix of the priority ranking - avoids buttons popping in/out unpredictably across recomputes.

**Verification:**
- `Grep` `fun allocateCommandBar` in `BrowseCommandBarAllocation.kt` → expected: 1 | actual: record.
- `Grep` `import android` in `BrowseCommandBarAllocation.kt` → expected: 0 matches (pure file) | actual: record.

### Step 1.2 - Unit test

Create `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCommandBarAllocationTest.kt`.

Cases:
- All fit → `overflowIds` empty, `visibleIds` = all in input order.
- None fit (`availableWidthPx <= reservedWidthPx`) → all overflow.
- Partial: 3 of 5 fit → highest-3 priority visible, lowest-2 overflow; visible list preserves input order.
- Reserved width consumes space: same slots, smaller `availableWidthPx` shifts the cut.
- Empty input → empty/empty.

**Verification:**
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.browse.managers.BrowseCommandBarAllocationTest"` → expected: BUILD SUCCESSFUL, all cases pass | actual: record per-class XML report.

## Phase Done Criteria

- [ ] `BrowseCommandBarAllocation.kt` exists, contains `allocateCommandBar`, zero `android` imports.
- [ ] `BrowseCommandBarAllocationTest.kt` exists with ≥5 cases.
- [ ] The new test class passes (per-class XML report green).
- [ ] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` run after the `.kt` adds.
