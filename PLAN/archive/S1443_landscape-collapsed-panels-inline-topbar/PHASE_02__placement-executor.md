# Phase 02 - Placement executor

**Strategic spec:** [`../S1443_landscape-collapsed-panels-inline-topbar.md`](../S1443_landscape-collapsed-panels-inline-topbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Produce the free-width measurement and the view-level executor that moves chips between the command bar and the collapsed-panels row; nothing calls the executor yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1` before the first edit - S1444 is being worked in parallel on the same command bar (strategic §10).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt` | New | ≤ 210 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 02.1 - Report the command bar free width and exclude inline chips from the fit decision

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Add a constructor parameter `onControlBarFreeWidth: (Int) -> Unit = {}` to `MainLayoutChromeManager`, declared last and with that default so existing construction sites still compile. Inside `applyControlBarOverflow`, skip any child whose id is `R.id.chipProgramsCollapsed`, `R.id.chipStreamsCollapsed` or `R.id.chipFilterCollapsed` when summing `needed` - hoist the three ids into a private `val inlineChipIds = setOf(..)` on the class. After the existing `needed > available` branch, invoke `onControlBarFreeWidth` exactly once per layout pass with `0` when `needed > available`, and with `available - needed` otherwise. Leave the S1258 posted `forceLayout` heal block and the `restitchControlBarFocusChain` call exactly as they are, and invoke the callback before them.
>
> Extend the existing KDoc on `applyControlBarOverflow` with a sentence stating that chips relocated into the bar are excluded from `needed` and that a bar which overflows reports zero free width.

**Why:**

Strategic §5.1 states the free-width source is an extension of the existing measurement rather than a second one, and ADR-4 requires that chips claim only the width left after every command has fitted - counting a relocated chip in `needed` would let a chip push `btnStartPlayer` out, which the S0972 rule this ADR defers to must keep owning alone.

**Verification:**

- `Grep` - `onControlBarFreeWidth` matches at least twice in `MainLayoutChromeManager.kt` (parameter plus invocation).
- `Grep` - `inlineChipIds` matches at least twice in that file.
- `Grep` - `restitchControlBarFocusChain()` still matches exactly once inside `applyControlBarOverflow`.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 02.2 - Create the placement executor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Create `MainCollapsedChipsPlacementManager` taking the command bar `ViewGroup`, the collapsed-panels row `ViewGroup`, and the three chip `View`s in their row order - programs, streams, filter. Capture each chip's original index inside the row in an init block, while every chip still sits in its inflated parent.
>
> Expose `fun apply(freeWidthPx: Int, wideLayout: Boolean)`. Collect the chips that are currently `VISIBLE` in row order, measure each with an `UNSPECIFIED` width spec and an `AT_MOST` height spec against the bar height, and build the `ChipCandidate` list. Read `main_panel_item_spacing` as the gap and `main_collapsed_chip_inline_reserve` as the reserve from resources. Call `MainCollapsedChipPlacementPlanner.plan`. Move every chip named in `inlineIds` into the command bar, appended after the last existing child, with `LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)` carrying `gravity = Gravity.CENTER_VERTICAL` and `marginStart` set to the gap; move every chip named in `rowIds` back into the collapsed-panels row at its captured original index, clamped to the row's current child count, with `LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)` and `marginEnd` set to the gap. Remove a chip from its current parent before adding it to the target parent, and skip a chip that already sits in its target parent. Set the row's visibility to `GONE` when no chip remains in it and `VISIBLE` otherwise.
>
> Do not change any chip's own visibility, background, text, drawables or click listener - the executor only moves views and rewrites layout params.

**Why:**

ADR-1 requires the remnant to be one live view relocated between hosts rather than a second copy or a redrawn compact form, and strategic §2 goal 4 makes the empty collapsed-panels row disappear, which only a component that knows the whole set can decide.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt` exists.
- `Grep` - `class MainCollapsedChipsPlacementManager` matches exactly once in that file.
- `Grep` - `fun apply(` matches exactly once in that file.
- `Grep` - `MainCollapsedChipPlacementPlanner.plan` matches exactly once in that file.
- `Grep` - `isVisible\s*=|visibility\s*=` matches only on the collapsed-panels row inside that file, never on a chip.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 02.3 - Guard against re-entrant layout and add the narrow-layout reset

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> Store the last applied `inlineIds` list in a private field and return from `apply` without touching a single view when the freshly planned `inlineIds` equals it and the row visibility already matches. When `wideLayout` is false, skip the planner entirely and move every chip back to its captured row index, which is the same code path `rowIds` already uses. Keep the early-return guard in force for the narrow path too.
>
> Add a KDoc note stating that `apply` runs from a layout callback, so an unguarded reparent would request another layout pass and loop.

**Why:**

Strategic §3.2 forbids the solution from triggering a repeated layout cycle on every frame, and §2 non-goals keep the narrow portrait layout visually identical to today, which requires an explicit path that returns every chip to the row rather than leaving a stale wide-layout placement after rotation.

**Verification:**

- `Grep` - `wideLayout` matches at least twice in `MainCollapsedChipsPlacementManager.kt`.
- `Grep` - `lastInlineIds` matches at least three times in that file.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 02.4 - Restitch focus around a relocated chip

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLayoutChromeManager.kt`

**Depends on:** Step 02.3

**Prompt for developer:**

> Add a constructor parameter `onPlacementChanged: () -> Unit = {}` to `MainCollapsedChipsPlacementManager` and invoke it once at the end of `apply`, only on the path that actually moved a view. In `MainLayoutChromeManager.restitchControlBarFocusChain`, keep the existing hard-coded candidate list and the `VISIBLE` filter, and append to it any of the three chips that is currently a child of the bar, in bar child order. Do not rebuild the list from the bar's direct children: `btnMainDropdownMenu` sits inside the `layoutMainDropdownMenu` wrapper (S1263), so a direct-children walk would put the wrapper in the chain and drop that button out of it. Set `nextFocusDownId` on the last candidate to `R.id.tabResourceTypes` so the vertical exit from the bar survives a chip landing at the end.
>
> Do not touch the static `nextFocusDown` attributes in the layout files - the runtime chain overrides them.

**Why:**

Strategic §3.2 requires the relocated remnant to stay reachable with D-pad and keyboard, and §7 rates the focus-chain break as the highest-probability risk because the bar's chain is currently built from a fixed button list that a relocated chip cannot enter.

**Verification:**

- `Grep` - `onPlacementChanged` matches at least twice in `MainCollapsedChipsPlacementManager.kt`.
- `Grep` - `binding.btnExit,` still matches exactly once inside `restitchControlBarFocusChain` in `MainLayoutChromeManager.kt` (the hard-coded list survives).
- `Grep` - `chipProgramsCollapsed` matches at least once inside `restitchControlBarFocusChain`.
- `Grep` - `nextFocusDownId` matches at least once in `MainLayoutChromeManager.kt`.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 02.1 done. `MainLayoutChromeManager` gained the defaulted `onControlBarFreeWidth` parameter and the `inlineChipIds` set; `applyControlBarOverflow` now skips relocated chips in the fit sum and reports `0` on overflow, `available - needed` otherwise. `.\a.ps1 fk` exit 0.
- 2026-08-08 - Step 02.2 done. `MainCollapsedChipsPlacementManager` created: captures each chip's inflated row index, measures visible chips, delegates the split to the planner, reparents with per-host layout params and hides the row when it is empty. Chip visibility, styling and listeners are untouched.
- 2026-08-08 - Step 02.3 done. The guard and the narrow-layout reset landed in the same `apply` body as step 02.2 - the early return has to sit between the plan and the first `removeView`, so it could not be added as a later edit without rewriting the function. Predicates verified separately: `wideLayout` x2, `lastInlineIds` x3.
- 2026-08-08 - Step 02.4 done. `onPlacementChanged` invoked only on the path that moved a view; `restitchControlBarFocusChain` now appends bar-parented chips, sorted by bar child index, to the hard-coded button list and pins `nextFocusDownId` on the last candidate. The hard-coded list was deliberately kept - a direct-children walk would chain the `layoutMainDropdownMenu` wrapper and drop `btnMainDropdownMenu` (S1263).
- 2026-08-08 - Phase-boundary audit (Layers 1-3): no P0/P1. Layer 2 - the only async work is one `post` in `healProbeMeasure`, which touches views on the main thread and holds no reference beyond the frame. Layer 3 - no listener is registered or retained here; the manager holds views owned by the activity's binding and outlives nothing.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 (compile-only symbol change; CLAUDE.md §12 validation ladder).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1 -Files`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated - chained by `post-change.ps1`.
- [x] `temp/CODE.LOCK` held continuously into Phase 03, which edits the same command bar - released at the Phase 03 boundary instead, to keep a parallel S1444 session from landing between the two halves of one change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`MainCollapsedChipsPlacementManager.apply(freeWidthPx, wideLayout)` is idempotent and safe to call from a layout callback, and `MainLayoutChromeManager` now emits the free width. Phase 03 only connects the two and re-triggers them on the events that change the chip set.

---

## Rollback Plan

Revert phase commit(s) - one new file plus additive parameters with defaults on an existing manager; no data migration and no user-facing surface changed while nothing calls `apply`.
