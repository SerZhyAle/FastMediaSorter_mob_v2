# Phase 03 - sw480 landscape band

**Strategic spec:** [`../S1330_landscape-integers-dead-under-sw-qualifiers.md`](../S1330_landscape-integers-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Settle `grid_column_count_landscape` in the `sw480-599dp` band, the one key strategic §0 names that
the mechanical gate cannot see. Either the intended 6 columns are restored there or the 2 is accepted
on purpose.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the baseline is empty, so any gate output in this phase is about this key.
- [x] **Unblocked 2026-08-02:** strategic §6 items 1 and 2 answered for this key - **restore 6**.

---

## Why the gate is silent here

`assert-qualifier-shadowing.ps1` fails only when `values-sw320dp` (or its combined sibling) declares
the key, because that threshold matches every device and makes the landscape copy dead everywhere.
`values-sw320dp` does not declare `grid_column_count_landscape`, so the landscape 6 still wins below
480dp and the sw value wins above it. That split is indistinguishable from a deliberate
phone-versus-tablet design, which is why the script's `.DESCRIPTION` records it as a known
limitation needing a human. This phase is that human.

---

## What actually reads the key

`MainLayoutChromeManager.updateLayoutManagerForScreenSize()` reads
`grid_column_count_landscape` only when `Configuration.isWideLayout()` is true - landscape OR
available width >= 600dp (`core/orientation/WideLayout.kt`). Otherwise it reads `grid_column_count`.
So the resolved value matters on `Ph-Ln`, `Ph-Lw`, `Md-L`, `Tb-P` and `Tb-L`, and is never read on
`Ph-P` or `Md-P`. The column counts a user can actually meet today, in ascending device size:

| Config | `Ph-Ln` | `Ph-Lw` | `Md-L` | `Tb-P` | `Tb-L` |
|---|---:|---:|---:|---:|---:|
| resolved today | 6 | 6 | **2** | 5 | 5 |
| after restore | 6 | 6 | **6** | 5 | 5 |

The 2 is not a smaller-screen value sitting between its neighbours - it is below every one of them,
including the narrowest phone. `values-sw480dp/integers.xml` opens with "480x480 SCREEN
CONFIGURATION - Integer values optimized for 480x480 compact screens", so the 2 was authored for a
square compact display and reaches ordinary `sw480-599dp` devices only as collateral.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-sw480dp-land/integers.xml` | Modified | ≤ 15 |

> One file. The restore outcome adds a line to the combined bucket and leaves `values-land` alone;
> `values-land/integers.xml` was on this list only for the rejected accept outcome.
>
> `values-*` buckets, not `layout*` - CLAUDE.md Rule 11 does not apply.

---

## Steps

### Step 03.1 - Apply the owner decision to the 480dp band

**Files:** `app_v2/src/main/res/values-sw480dp-land/integers.xml`

**Depends on:** - start of phase

**Why:**

The owner selected six columns for ordinary medium-size landscape devices while retaining two for square displays.

**Prompt for developer:**

> Restore, decided by the owner 2026-08-02 against strategic §6 items 1 and 2. The "accept" outcome
> is rejected and its instruction is dropped - do not delete `grid_column_count_landscape` from
> `values-land/integers.xml`; below 480dp that is the line supplying the 6.
>
> Add `grid_column_count_landscape` **6** to `values-sw480dp-land/integers.xml`. A `sw480-599dp`
> device in landscape then resolves the same 6 a smaller phone already gets, instead of the 2 authored
> for a square 480x480 screen. Extend the file's existing comment to name this key too.
>
> Do not touch `values-sw480dp/integers.xml`. Its 2 stays correct for the square compact screen the
> file was written for - a combined `-land` bucket does not apply there, because a square screen
> reports portrait.

**Verification:**

- `Grep` - `name="grid_column_count_landscape">6<` present in `values-sw480dp-land/integers.xml`.
- `Grep` - `name="grid_column_count_landscape">6<` still present in `values-land/integers.xml`.
- `Grep` - `name="grid_column_count_landscape">5<` still present in `values-sw600dp/integers.xml`, so no combined bucket can reach a tablet.
- `Grep` - `name="grid_column_count_landscape">2<` still present in `values-sw480dp/integers.xml`.
- `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0 and reports `0 baselined`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 5/5 PASS. Added the owner-approved six-column medium landscape value.

---

## Phase Done Criteria

- [x] Step 03.1 is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.
- [x] Dev log entry added for the touched file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Audit focus: confirm from the tree
      that `Tb-L` still resolves 5, and that the restore did not reach `Md-P`. PASS: sw600dp
      outranks sw480dp-land on tablets; the combined bucket does not match portrait.

---

## Handoff Notes to Next Phase

Both landscape-orientation defects are settled. What is left is the third axis - the width-qualified
copy that reaches a tablet in portrait, which phase 04 decides.

---

## Rollback Plan

Remove the added line, or restore the deleted one. Single-file resource edit, no consumer changes.
