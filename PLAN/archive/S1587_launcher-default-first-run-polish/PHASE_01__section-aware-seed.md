# Phase 01 - Section-aware seeding of the starter desktop

**Strategic spec:** [`../S1587_launcher-default-first-run-polish.md`](../S1587_launcher-default-first-run-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Pack the starter desktop per section instead of across the whole grid, and open the phone desktop with the content section, so no cell lands above its own header and the media resources are on the first screen.

---

## Prerequisites

- [ ] Strategic §6.1 and §6.2 are Resolved (both are).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 470 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 - Give the packer a per-section floor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `place`, track a packing floor that starts at row 0 and is raised to the header's own row whenever an item of kind `SECTION` is placed. Pass that floor to `firstFreeAnchor` as its starting row instead of the constant 0, so an item placed after a header can never anchor above it. Keep dense first-free packing inside a section, and keep `place` pure - no new parameters, no state outside the call.

**Why:**

Section membership is positional by row (S1428 contract, strategic §7), so an item that backfills a hole above its own header joins the wrong section and disappears when that section is collapsed - strategic §1 defect 1 and research `01__group-packing-width.md`.

**Verification:**

- `Grep` - `firstFreeAnchor(` in `LauncherStarterSets.kt` is called with a floor argument, and `private fun firstFreeAnchor(` declares it.
- `Grep` - `var row = 0` no longer appears inside `firstFreeAnchor`.
- `Grep` - `fun place(items: List<StarterItem>, columns: Int)` signature unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - place() now raises a packing floor at every SECTION header and itemsFor opens with the content section; verified by grep on firstFreeAnchor signature, call site and item order
- 2026-08-12 - Phase-boundary audit (Layer 1): pure data + pure packer, no lifecycle, coroutine, listener or Room surface touched; the one new mutable is a loop-local packing floor covered by the three new tests. No P0/P1 findings.

---

### Step 01.2 - Open the set with the content section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Reorder `itemsFor` so the set opens with the `SECTION_EVERYTHING_ELSE` header followed by the clock, the search gadget, the weather gadget, the common resources, the profile items, the common features and the third-party apps, and closes with the `SECTION_APP_FUNCTIONS` header over the launcher actions and the common tail. Keep the clock seeded at `CLOCK_SEED_W` x `CLOCK_SEED_H` and keep the search gadget immediately after the clock - `LauncherStarterSetsParityTest` reads that pair.

**Why:**

The owner ruled on 2026-08-12 (strategic §3.3) that content opens the desktop and the launcher's own actions move to the end; without the move the first screen is spent on five service shortcuts and the media resources fall below the fold, which is strategic §1 defect 4.

**Verification:**

- `Grep` - in `itemsFor`, the line adding `SECTION_EVERYTHING_ELSE` precedes the line adding `SECTION_APP_FUNCTIONS`.
- `Grep` - `items += gadget(GADGET_SEARCH)` is the line immediately after `items += clock()`.
- `Grep` - `launcherActions(profile)` is added after `commonThirdPartyApps(`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - place() now raises a packing floor at every SECTION header and itemsFor opens with the content section; verified by grep on firstFreeAnchor signature, call site and item order

---

### Step 01.3 - Assert no cell rises above its header

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a test that packs the `PERSONAL_SMARTPHONE` set at 3, 4 and 8 columns and asserts, for every placed item, that its row is greater than or equal to the row of the last `SECTION` item placed before it. Add a second test asserting that no placed item other than a section header sits in a row above the first header of its own group.

**Why:**

The packing floor is a pure function and the strategic §11.1 criterion is "no cell above its section header", so the criterion is provable without a device, at every column count the grid can resolve to (`LauncherGridGeometry` clamps 3..12).

**Verification:**

- `Grep` - the test file declares a test naming `section` and `floor` (or `above`) in its function name.
- `Grep` - `intArrayOf(3, 4, 8)` or three separate `place(` calls with 3, 4 and 8 appear in the new test.
- `.\a.ps1 fu` - the two new tests pass.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - LauncherStarterSetsTest updated for the new order plus three new tests (section floor at 3/4/8 columns, positional membership, first_screen at 4 columns); a.ps1 fu BUILD SUCCESSFUL, TEST-...LauncherStarterSetsTest.xml tests=22 failures=0

---

### Step 01.4 - Assert the first screen carries the resources

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a test that packs the `PERSONAL_SMARTPHONE` set at 4 columns with all six virtual resources resolved and asserts that every `Resource` shortcut lands in a row below 7, and that the first `LauncherAction` shortcut lands in a row of 7 or greater.

**Why:**

Research `02__first-screen-order.md` measured ~7.5 cell rows above the fold on the reference phone, so row 7 is the boundary that makes strategic §11.3 - resources visible without scrolling - a static assertion rather than a screenshot opinion.

**Verification:**

- `Grep` - the new test names `firstScreen` (or `first_screen`) in its function name.
- `.\a.ps1 fu` - the new test passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - LauncherStarterSetsTest updated for the new order plus three new tests (section floor at 3/4/8 columns, positional membership, first_screen at 4 columns); a.ps1 fu BUILD SUCCESSFUL, TEST-...LauncherStarterSetsTest.xml tests=22 failures=0

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `LauncherStarterSetsTest` and `LauncherStarterSetsParityTest` both pass.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The seeded desktop's row assignment is now stable and asserted, so Phases 02 and 03 change only how a cell draws, never where it sits.

---

## Rollback Plan

Revert the phase commit - the change affects the seeding of an empty desktop only, so no stored user layout is rewritten and no migration is involved.
