# Phase 03 - Seed the search cell into every profile

**Strategic spec:** [`../S1566_launcher-google-search-widget.md`](../S1566_launcher-google-search-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Put the search cell into the first-run desktop of every device profile, through the existing starter-set
table, without changing the packer.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `LauncherGadgetRegistry.KEY_SEARCH` exists and the gadget seeds at 2x1.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 440 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 260 |
| `app_v2/src/testLauncherEnabled/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 100 |

> Backup / split thresholds: the file is 419 LOC, below the 500-LOC backup threshold and far below the
> 1500-LOC split threshold; this step adds a handful of lines.
>
> **Flavor placement.** The file sits in `src/main` by existing design - it is pure data plus a pure packer
> and holds no launcher types, which is why it compiles in every flavor while the gadget does not.

---

## Steps

### Step 03.1 - Add the search cell to the common starter items

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `private const val GADGET_SEARCH = "search"` beside the other gadget key literals, with the same
> comment contract they carry about duplicating the registry constant. Add `GADGET_SEARCH` to the public
> `gadgetKeys` set. In `itemsFor`, emit `gadget(GADGET_SEARCH)` on the line **immediately after
> `items += clock()`** and before `weatherOrNull(profile)`, so it lands in the profile-independent part and
> not in any per-profile branch or membership set. Leave the packer, the key stride and every existing row
> untouched.

**Placement constraint (verified 2026-08-11, do not reorder):** `LauncherStarterSetsParityTest` asserts that
the **first** item of kind `GADGET` in `itemsFor(OTHER, ..)` is `KEY_CLOCK`. Emitting the search cell before
`items += clock()` therefore turns a green suite red for a reason that reads as unrelated. After the clock is
also the right place on screen: the search bar sits at the top of the "everything else" section rather than
below the resource shortcuts.

**Why:**

Strategic §6.4 records the owner's decision that the search cell belongs to the starter set of all profiles,
and the same section notes seeding runs once on an empty desktop, so a device with an existing desktop keeps
its layout and only fresh installs receive the cell.

**Verification:**

- `Grep` - `GADGET_SEARCH = "search"` matches exactly once.
- `Grep` - `GADGET_SEARCH` appears inside the `gadgetKeys` set.
- `Grep` - `gadget(GADGET_SEARCH)` matches exactly once, on the line directly after `items += clock()`, and not inside a `when (profile)` branch or a membership-set `if`.
- `.\a.ps1 fu` - `LauncherStarterSetsParityTest` and `LauncherStarterSetsTest` pass, proving the literal matches `KEY_SEARCH` and the spans match the gadget.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 4\4 PASS on the second run. First run failed 7 of 22: `LauncherStarterSetsTest` asserts the exact target sequence of a seeded desktop, so every expected list had to gain `"search"` after `"clock"`. That file was missing from the plan's Files Touched and was added to it before the edit rather than touched silently. Files: LauncherStarterSets.kt (+8 LOC), LauncherStarterSetsTest.kt (6 expected lists updated).

---

### Step 03.2 - Update the seeded-desktop expectations

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `LauncherStarterSetsTest` asserts the full target sequence each profile seeds. Insert `"search"` directly
> after `"clock"` in every expected list, and nowhere else. Change no other expectation: the cell is added to
> the common part, so no per-profile row moves and no count beyond the one cell shifts.
>
> `LauncherStarterSetsParityTest` separately hand-lists the registry keys the starter set may emit and
> compares that set to `LauncherStarterSets.gadgetKeys`. Add `LauncherGadgetRegistry.KEY_SEARCH` to it.

**Why:**

Strategic §6.4 records the owner's decision that the search cell joins the starter set of every profile, so a
test still asserting the previous desktop is describing a desktop the product no longer ships.

**Verification:**

- `Grep` - `"search"` follows `"clock"` in each of the six expected lists.
- `Grep` - `LauncherGadgetRegistry.KEY_SEARCH` present in the parity test's `registryKeys` set.
- Targeted run of `*LauncherStarterSets*` reports 22 tests, 0 failures.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Run 3: PASS. 22 tests across the two classes, 0 failures, result XML stamped 12:43:50Z.
- 2026-08-11 - Run 1 of the seed: 7 of 22 failed, all in `LauncherStarterSetsTest`, each an exact expected target sequence. Six lists updated with `"search"` after `"clock"`.
- 2026-08-11 - Run 2: 1 of 22 failed - `LauncherStarterSetsParityTest` hand-lists the registry keys the seed may emit and compares them to `gadgetKeys`, so it refused the new key until it was listed. Added `KEY_SEARCH` there. Both test files were missing from the plan's Files Touched and were added to it before each edit.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - the targeted unit run compiles `src/main` before it can execute, BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: the change is three lines of pure
      data in a pure table plus its two guards; no control flow, no new branch, the packer untouched. The
      duplicated key literal is deliberate and is exactly what the parity test exists to hold. Layers 2, 3
      and 4 not applicable - no coroutine, listener or Room surface.

---

## Handoff Notes to Next Phase

Every profile's first-run desktop now carries one more 2x1 cell. If the parity test fails after this step, the
cause is the duplicated literal, not the packer.

---

## Rollback Plan

Revert phase commit(s). A desktop already seeded with the cell keeps it as an unknown `target`, which the
registry renders as a broken cell the user can delete - so prefer reverting Phase 02 and Phase 03 together
once either has reached a device.
