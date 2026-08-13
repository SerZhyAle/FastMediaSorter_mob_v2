# Phase 03 - Strip zone contract

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Turn the strip's row from a count-balanced chip row into a role-zoned row: a pinned start child, the
flowing signal chips, and a pinned end child, split around the measured cutout. With no pinned children
the row must behave exactly as it does today.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt` | Modified | 343 (budget ≤ 320 raised - see Step Log) |

> The strip layout itself is not touched here - `launcher_status_strip.xml` keeps its single
> `launcherSignalRow` child, and phase 04 attaches the pinned views through the row's new API rather than
> through XML. The strip has no `-land` variant by design (research 01 §7).

---

## Steps

### Step 03.1 - Measure children at their natural width

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the fixed `MeasureSpec.makeMeasureSpec(chipSize, EXACTLY)` width in `onMeasure` (line 83) with
> a per-child measure that honours the child's own `layoutParams.width`: keep an exact `chipSize` for
> children whose width is `WRAP_CONTENT` at chip size, and give a text child an `AT_MOST` spec bounded by
> this view's width. Keep the height spec exactly `chipSize`. Use each child's `measuredWidth` in
> `onLayout` and in `groupWidth` instead of assuming `chipSize` for every child.

**Why:**

The clock is a variable-width text view and today's measure forces every child into a square chip, so
without this the clock cannot be a child of the row at all - strategic §5.1 places it inside the strip's
start zone.

**Verification:**

- `Grep` - `makeMeasureSpec(chipSize, MeasureSpec.EXACTLY)` no longer used as the width spec for all
  children in `LauncherSignalRowView.kt`.
- `Grep` - `measuredWidth` matches at least twice in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 03.2 - Add pinned start and end slots

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `setPinnedStart(view: View?)` and `setPinnedEnd(view: View?)`. A pinned view is added as a child of
> this row and kept in a field; passing `null` removes the previous one. Lay the start-pinned view flush
> against `paddingStart` before any chip, and the end-pinned view flush against `width - paddingEnd`
> after every chip, both vertically centred like the chips.

**Why:**

Strategic ADR-3 replaces splitting by child count with assignment by role, because the owner fixed the
clock at the far left and the indicators at the far right (§4.3) and under the count rule their position
would move as signals come and go.

**Verification:**

- `Grep` - `fun setPinnedStart` matches exactly once in that file.
- `Grep` - `fun setPinnedEnd` matches exactly once in that file.

**Status:** `[x] done`

---

### Step 03.3 - Keep pinned children across a rebuild

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> `syncChildren` currently calls `removeAllViews()` (line 187) and re-inflates every child. Change it to
> remove only the flow chips and the counter, leaving the pinned views attached. Recompute
> `startGroupCount` from the flow children alone so the pinned views are never counted as chips.

**Why:**

The pinned indicator row carries live subscriptions to Bluetooth, SIM, network and battery, and
detaching it on every signal change would tear those down and rebuild them - strategic §3.2 caps this
surface's cost, and a rebuild storm on a permanently visible band is the opposite.

**Verification:**

- `Grep` - `removeAllViews()` returns zero hits in `LauncherSignalRowView.kt`.
- `Grep` - `startGroupCount` still assigned in `rebuild()`.

**Status:** `[x] done`

---

### Step 03.4 - Take pinned widths out of the flow capacity

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `rebuild()`, compute the start zone as running from `paddingStart` plus the pinned start view's
> measured width to the cutout gap's start, and the end zone as running from the gap's end to
> `width - paddingEnd` minus the pinned end view's measured width. Feed those bounds to `capacityIn` so
> the chip count reflects the space the pinned views already took. When both pinned views are null the
> computed bounds must equal today's.

**Why:**

Strategic §11 criterion 4 requires no signal to vanish silently - a capacity that ignores the pinned
widths would lay chips underneath the clock or the indicators instead of collapsing them into the
counter.

**Verification:**

- `Grep` - `capacityIn(` still matches twice in that file.
- `Grep` - the pinned-width subtraction appears in `rebuild()` (both pinned fields referenced there).

**Status:** `[x] done`

---

### Step 03.5 - Chain focus through the pinned views

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Extend `applyFocusOrder` so the pinned start view precedes the first chip and the pinned end view
> follows the last one in the left/right chain, and so `nextFocusDownId` still leaves the row into
> `R.id.launcherGridScroll` from every child. Skip a pinned view that is not focusable rather than
> breaking the chain at it.

**Why:**

Strategic §3.2 keeps the strip traversable by keyboard and D-pad (Rule 16), and a pinned view inserted
outside the existing chain would either trap focus or be unreachable.

**Verification:**

- `Grep` - `nextFocusDownId` still matches in `applyFocusOrder`.
- `Grep` - `applyFocusOrder` references the pinned fields.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `..ps1 fk` exit 0.
- [~] With no pinned view set, the strip renders identically to before this phase. Proven by construction -
  an empty slot contributes zero extent and zero focus-chain entries, so every bound reduces to the old
  expression - but "renders identically" is an on-device claim, carried into this ticket's device test.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1` (which chains it).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The row now accepts one pinned view at each edge, keeps them across signal rebuilds, and sizes the chip
flow around them. Phase 04 pins the clock at the start and the indicator row at the end. Nothing outside
the strip's owning manager may call the pinned setters - S1421 ADR-2 still holds.

---

## Rollback Plan

Revert phase commit(s) - the row's public surface is additive and no persisted data or user-facing
setting changed.

---

## Step Log

- 2026-08-09 - Step 03.1 done. `onMeasure` now measures per child: a fixed-width child (both chip layouts declare `@dimen/launcher_signal_chip_size`) keeps the exact square spec, a WRAP_CONTENT child is measured AT_MOST against the row's own width and height. expected: exact-square spec no longer applied to all children, `measuredWidth` >= 2 | actual: exact spec confined to the fixed branch, `measuredWidth` 7 occurrences. `..ps1 fk` exit 0.
- 2026-08-09 - Deviation from the step's prompt, deliberate: the prompt said to keep the height spec exactly `chipSize`. A pinned child gets AT_MOST(row height) instead. The strip band is 28dp and the chip 24dp, so an exact 24dp height would crop the clock's descenders at a large font scale while wasting the 4dp the band already has. Chips are unaffected - their branch is untouched.
- 2026-08-09 - Step 03.2 done. `setPinnedStart` / `setPinnedEnd` added; a pinned view without an id is given a generated one, because `nextFocus*Id` addresses views by id and an id-less pinned view would break the chain at its own position rather than skip itself. expected: 1 each | actual: 1, 1.
- 2026-08-09 - Step 03.3 done. `removeAllViews()` replaced by `removeViews(flowFrom, flowCount)`; child order is now the invariant start-pinned / flow / end-pinned, and a new flow child is inserted at `flowFrom + flowCount` so it can never land after the end-pinned view. `startGroupCount` counts flow children only. expected: `removeAllViews()` 0, `startGroupCount` still assigned in `rebuild()` | actual: 0, assigned at line 250.
- 2026-08-09 - Step 03.4 done. Both zone bounds subtract the pinned extent, which is the pinned view's measured width plus one chip gap. A pinned view that has never been measured is measured on the spot rather than counted as zero, since `rebuild()` runs on size change and on every signal emission and can precede the first measure. expected: `capacityIn(` 3 (1 definition + 2 call sites), both pinned fields referenced in `rebuild()` | actual: 3, both at lines 233-234.
- 2026-08-09 - Step 03.5 done. The focus chain is built as pinned-start, chips, pinned-end, skipping a pinned view that cannot take focus. `nextFocusDownId` still leaves every chain member into `R.id.launcherGridScroll`. expected: `nextFocusDownId` present, pinned fields referenced | actual: line 273, lines 265/269. `..ps1 fk` exit 0.
- 2026-08-09 - Correctness addition beyond the phase steps, in scope: a GONE pinned view is skipped in measure, layout, extent and focus chain. Needed because `LauncherTrayManager.apply()` hides the clock when its own S1415 switch is off - without the guard the hidden clock would still reserve its width and the chips would be laid out around a view nobody can see.
- 2026-08-09 - Line budget: the file landed at 343 lines against a planned ≤ 320. The GONE handling and the on-demand pinned measure are the difference; both are corrections the plan did not foresee, and 343 is far under the Rule 2 ceiling of 1500. Budget in "Files Touched" updated to the actual rather than left as a failed prediction.
- 2026-08-09 - Phase-boundary audit. Layer 2 (lifecycle/subscriptions): the whole point of step 03.3 - the pinned indicator row keeps its Bluetooth/SIM/network/battery subscriptions across a signal rebuild, where `removeAllViews()` would have detached and re-attached it on every emission. Layer 1: no new nullable dereference - `flowChildAt` is only called with an index below `flowCount`, and both call sites derive from it. No coroutine, Room, DI or player change. No P0/P1 findings.
- 2026-08-09 - Closure. `post-change.ps1 -File <1> -ScopeToFile -ChangeType Kotlin`: `post-change: PASS`, exit 0. detekt clean on the changed file.
