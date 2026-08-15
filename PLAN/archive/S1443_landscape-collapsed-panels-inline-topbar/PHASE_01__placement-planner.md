# Phase 01 - Placement planner

**Strategic spec:** [`../S1443_landscape-collapsed-panels-inline-topbar.md`](../S1443_landscape-collapsed-panels-inline-topbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Introduce the pure placement planner and the reserve dimen it consumes; no view, no reparenting, no wiring yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlanner.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlannerTest.kt` | New | ≤ 160 |
| `app_v2/src/main/res/values/dimens_main_panels.xml` | Modified | ≤ 40 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 01.1 - Add the inline reserve dimen

**Files:** `app_v2/src/main/res/values/dimens_main_panels.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Add `<dimen name="main_collapsed_chip_inline_reserve">16dp</dimen>` to `dimens_main_panels.xml`, next to the existing shared grid dimens, with a one-line comment naming it as the hysteresis gap a chip must clear on top of its own measured width before it is allowed into the command bar. Do not add orientation-qualified copies - the value is orientation independent.

**Why:**

Strategic §7 lists "остаток переезжает и тут же уезжает обратно при пограничной ширине" as a risk and names the mitigation as a margin with a reserve, so the planner needs a single shared number rather than a literal buried in code.

**Verification:**

- `Grep` - `main_collapsed_chip_inline_reserve` matches exactly once in `app_v2/src/main/res/values/dimens_main_panels.xml`.
- `Grep` - `main_collapsed_chip_inline_reserve` returns zero hits under `app_v2/src/main/res/values-land/` and `app_v2/src/main/res/values-w600dp/`.

**Status:** `[x]` done

---

### Step 01.2 - Create the planner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlanner.kt`

**Depends on:** Step 01.1

**Prompt for developer:**

> Create `MainCollapsedChipPlacementPlanner` as an `object` with no Android view dependency - it may reference no type outside `kotlin.*`. Declare `data class ChipCandidate(val viewId: Int, val widthPx: Int)` and `data class ChipPlacement(val inlineIds: List<Int>, val rowIds: List<Int>)` in the same file. Expose `fun plan(freeWidthPx: Int, gapPx: Int, reservePx: Int, candidates: List<ChipCandidate>): ChipPlacement`. Walk `candidates` in the given order, keeping a running total of consumed width where each accepted candidate costs `widthPx + gapPx`; accept a candidate only while `consumed + widthPx + gapPx + reservePx <= freeWidthPx`. On the first candidate that does not fit, stop: that candidate and every candidate after it go to `rowIds` even when a later one would have fit alone. Return every candidate in `rowIds` when `freeWidthPx <= 0` or `candidates` is empty. Keep the relative order of the input list inside both output lists.
>
> Give the file a KDoc header that states the take-while-fits rule and that stopping at the first miss is what keeps the chips in a stable order. Keep the function total: no exceptions, no nullable return.

**Why:**

Strategic §5.1 requires the placement decision to be a pure, unit-testable function separated from view mutation, and ADR-3 fixes the rule as "take in the existing visual order, stop at the first candidate that does not fit" so that no chip ever changes position relative to its neighbours.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlanner.kt` exists.
- `Grep` - `object MainCollapsedChipPlacementPlanner` matches exactly once in that file.
- `Grep` - `fun plan(` matches exactly once in that file.
- `Grep` - `data class ChipCandidate` and `data class ChipPlacement` each match exactly once in that file.
- `Grep` - `android\.` returns zero hits in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 01.3 - Unit-test the planner

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlannerTest.kt`

**Depends on:** Step 01.2

**Prompt for developer:**

> Create `MainCollapsedChipPlacementPlannerTest` covering: zero and negative `freeWidthPx` put every candidate in `rowIds`; an empty candidate list returns two empty lists; a budget that fits all three candidates returns all three in `inlineIds` in input order; a budget that fits only the first returns the first inline and the remaining two in `rowIds` in input order; a budget where the second candidate does not fit but the third would fit alone still returns only the first inline, proving the stop-at-first-miss rule; a two-candidate list (the `lite` / `photos` shape, no streams chip) behaves identically to the three-candidate list; a budget exactly equal to `width + gap + reserve` accepts the candidate and a budget one pixel below it rejects the candidate. Use plain JUnit assertions, no Robolectric and no mocking framework.

**Why:**

Strategic §11 criteria 2, 3 and 8 state observable outcomes for the all-fit, partial-fit and two-candidate cases, and §3.2 requires the solution to work for any candidate count from zero to three, which is exactly the surface a pure function can prove without a device.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipPlacementPlannerTest.kt` exists.
- `Grep` - `@Test` matches at least 7 times in that file.
- `Grep` - `mockk|Robolectric` returns zero hits in that file.
- `.\a.ps1 fu` - `MainCollapsedChipPlacementPlannerTest` passes; read the class result in the JUnit XML rather than trusting the suite summary.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 01.1 done. `main_collapsed_chip_inline_reserve` = 16dp added to `values/dimens_main_panels.xml`; Grep confirms one hit there and zero under `values-land/` and `values-w600dp/`.
- 2026-08-08 - Step 01.2 done. `MainCollapsedChipPlacementPlanner` created with top-level `ChipCandidate` / `ChipPlacement`; Grep confirms one declaration each and zero `android.` / `Log.d(` hits.
- 2026-08-08 - Step 01.3 done. `MainCollapsedChipPlacementPlannerTest` created with 8 tests. Targeted run `scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*MainCollapsedChipPlacementPlannerTest"` exit 0; JUnit XML reports tests=8 failures=0 errors=0 skipped=0.
- 2026-08-08 - Phase 01 done. The same targeted run executed `:app_v2:compileStandardDebugKotlin` and `:app_v2:compileStandardDebugUnitTestKotlin` successfully, which is the compile proof for this phase.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:compileStandardDebugKotlin` succeeded inside the step 01.3 targeted unit run (exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1 -Files`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated - chained by `post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`MainCollapsedChipPlacementPlanner.plan` is the single decision point: Phase 02 supplies measured widths and a free-width budget and applies the returned split, and adds no fitting logic of its own.

---

## Rollback Plan

Revert phase commit(s) - two new files and one dimen, no data migration and no user-facing surface changed.
