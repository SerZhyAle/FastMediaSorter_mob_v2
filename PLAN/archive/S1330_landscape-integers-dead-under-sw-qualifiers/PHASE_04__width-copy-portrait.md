# Phase 04 - Width copy in portrait

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

Settle what `values-w600dp/integers.xml` still hands to a tablet in **portrait** once phases 01 and
02 have pruned it: either remove the copy so the base values apply, or keep it and say so on purpose.
This is the integers counterpart of the question S1282 §6.1 left open for dimensions.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the file holds exactly three keys by then.
- [x] **Unblocked 2026-08-02:** the owner decision listed in [`INDEX.md`](INDEX.md)
      Pre-Implementation Blockers - **keep and document**.

---

## The decision is one key wide

After phases 01-02 the file holds `grid_column_count_landscape` 6, `destinations_column_count` 2 and
`statistics_card_span` 4. Removing all three changes exactly one cell in the whole device matrix:

| Key | Where the `w600dp` line wins today | What wins after removal | Moves? |
|-----|-----------------------------------|-------------------------|:------:|
| `grid_column_count_landscape` | `Ph-Lw` | `values-land`, same 6 | no |
| `destinations_column_count` | `Ph-Lw`, `Md-L` | `values-land`, same 2 | no |
| `statistics_card_span` | `Ph-Lw`, `Md-L`, `Tb-L` | `values-land`, same 4 | no |
| `statistics_card_span` | **`Tb-P`** | `values`, **2 instead of 4** | **yes** |

`Tb-P` is the only configuration where a width-qualified copy is the sole declaration in play:
`grid_column_count_landscape` and `destinations_column_count` are both declared in `values-sw600dp`,
which outranks `w600dp` on every tablet, and `statistics_card_span` is declared in no sw bucket at
all. So the question reduces to: should a tablet in portrait show four statistics summary cards per
row, or two?

**This is where the integers case parts company with S1282.** There the width copy was handing
*vertical compactness* to a device with plenty of height, and a width qualifier cannot express
height at all - the copy was wrong by construction. Here it hands a *column count* to a device with
plenty of width, which is exactly what `wNNNdp` is for. The mechanism is identical; the verdict need
not be. Answer this key on its own evidence rather than by inheriting S1282's answer.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-w600dp/integers.xml` | Modified | ≤ 20 |

> The file stays. Its `bools.xml` and `dimens.xml` neighbours in the same bucket are owned by other
> tickets and are out of scope here either way.

---

## Steps

### Step 04.1 - Apply the owner decision to the width bucket

**Files:** `app_v2/src/main/res/values-w600dp/integers.xml`

**Depends on:** - start of phase

**Why:**

The owner retained the width-qualified values so portrait tablet cards preserve their intended width.

**Prompt for developer:**

> Keep and document, decided by the owner 2026-08-02. The "remove" outcome is rejected: a tablet in
> portrait is ~800dp wide, so four summary cards land at ~200dp each - the same physical card width a
> phone in portrait already gets from two columns. Dropping to 2 would make the tablet's cards, and
> the metric rows that share the grid, twice as wide as anywhere else in the app.
>
> Leave the three values and replace the opening comment, which currently reads "Grid column counts
> for landscape orientation" and is wrong on both counts - the qualifier is available width, not
> orientation, and it matches a tablet in portrait. State what it targets (available width >= 600dp,
> portrait included), and that the column counts are deliberate there because they hold the card width
> constant across device classes, so the next reader does not delete it as a landscape leftover. While
> editing, note that two of the three keys are outranked by `values-sw600dp` on every tablet and only
> carry `Ph-Lw` / `Md-L`, where `values-land` declares the same values.
>
> Do not split the difference by pruning some keys and keeping others - the three move together
> because only one of them decides anything, and pruning the other two silently would leave the file
> saying less than it does now for no gain.

**Verification:**

- `Grep` - the file's leading comment contains `w600dp` and the word `portrait`.
- `Grep` - the file contains exactly 3 `<integer name=` lines.
- `Grep` - `name="statistics_card_span">4<` still present in the file and in `values-land/integers.xml`.
- `Grep` - `name="statistics_card_span">2<` still present in `values/integers.xml`.
- `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0 and reports `0 baselined`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 5/5 PASS. Documented the deliberate w600dp portrait-tablet value.

---

## Phase Done Criteria

- [x] Step 04.1 is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.
- [x] Dev log entry added for the touched file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Audit focus: re-derive from the tree
      that `Tb-P` still resolves `statistics_card_span` = 4, and that no phase-01/02 prune has left
      `Ph-Lw`, `Md-L` or `Tb-L` resolving anything but 4. PASS: the surviving width value and
      landscape value agree, while the base retains the two-column narrow-screen fallback.

---

## Handoff Notes to Next Phase

Final resource state reached across all three qualifier axes - smallestWidth, available width and
orientation. Phase 05 records it.

---

## Rollback Plan

Restore the file's previous comment. A single resource file with no consumer changes and no value moved.
