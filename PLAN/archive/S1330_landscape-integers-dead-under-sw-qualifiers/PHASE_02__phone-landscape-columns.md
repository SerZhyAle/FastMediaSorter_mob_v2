# Phase 02 - Phone landscape columns

**Strategic spec:** [`../S1330_landscape-integers-dead-under-sw-qualifiers.md`](../S1330_landscape-integers-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Settle `welcome_feature_grid_columns`, the one baselined key whose landscape value differs from what
phones actually resolve, and empty the shadowing baseline. Satisfies the ticket's mechanical
completion signal: zero entries left.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the baseline holds exactly the two entries this phase clears.
- [x] **Unblocked 2026-08-02:** strategic §6 item 1 answered - **outcome D, 3 columns**, recorded in
      [`INDEX.md`](INDEX.md) under the before/after table. Outcomes A, B and C are rejected.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-sw320dp-land/integers.xml` | Modified | ≤ 15 |
| `app_v2/src/main/res/values-land/integers.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-w600dp/integers.xml` | Modified | ≤ 15 |
| `scripts/quality/qualifier-shadowing-baseline.txt` | Modified | ≤ 15 |

> `values-sw480dp-land/integers.xml` is NOT touched here - that was outcome B only, and it lost.
> The `sw480-599dp` band already resolves 3 for this key through `values-sw480dp`.

> Both combined buckets already exist and already hold an `integers.xml` - unlike S1282, which had to
> create its `dimens.xml` files there. No new file in this phase.
>
> `values-*` buckets, not `layout*` - CLAUDE.md Rule 11 does not apply.

---

## Steps

### Step 02.1 - Restore the landscape column count per the owner answer

**Files:** `app_v2/src/main/res/values-sw320dp-land/integers.xml`

**Depends on:** - start of phase

**Why:**

The owner selected three columns to form two even rows without narrowing the capped welcome grid.

**Prompt for developer:**

> Outcome D, decided by the owner 2026-08-02. Nothing is pruned in this step - the tree stays correct
> at every point.
>
> Add `welcome_feature_grid_columns` **3** to `values-sw320dp-land/integers.xml`, and only there. On a
> `sw320-479dp` phone the wizard resolves 2 columns today, so six feature tiles take three rows in the
> orientation with the least height; 3 columns makes that two even rows. 4 was rejected: the grid does
> not get the screen width in landscape, it gets ~325dp after the 400dp content cap and the hero icon
> column, so a fourth column shrinks the tile to ~75dp and still leaves a ragged 4 + 2 layout. The
> measurement and its sources are in [`INDEX.md`](INDEX.md) under "Outcome D".
>
> Extend the existing file's comment rather than replacing it - it already explains the
> smallestWidth-outranks-orientation rule for its neighbour key.
>
> Do not touch `values-sw480dp-land/integers.xml`: the `sw480-599dp` band already resolves 3 for this
> key through `values-sw480dp`, so a combined bucket there would restate a value nobody disputes.
>
> The tablets are safe and must not be given a combined bucket: `values-sw600dp` declares this key (4)
> and `values-sw720dp` declares it (4), so a `sw600dp+` device eliminates both `sw320dp-land` and
> `sw480dp-land` in the smallestWidth round before orientation is considered. Do NOT create
> `values-sw600dp-land` or `values-sw720dp-land`.

**Verification:**

- `Grep` - `name="welcome_feature_grid_columns">3<` present in `values-sw320dp-land/integers.xml`.
- `Grep` - `welcome_feature_grid_columns` returns zero hits in `values-sw480dp-land/integers.xml`.
- `Glob` - `app_v2/src/main/res/values-sw600dp-land/` and `values-sw720dp-land/` return no match.
- `Grep` - `name="welcome_feature_grid_columns">4<` still present in `values-sw600dp/integers.xml` and in `values-sw720dp/integers.xml`.
- `Grep` - `name="welcome_feature_grid_columns">3<` still present in `values-sw480dp/integers.xml`, so the mid band is unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 5/5 PASS. Added the owner-approved phone-landscape three-column value.

---

### Step 02.2 - Prune the shadowed declaration and empty the baseline

**Files:** `app_v2/src/main/res/values-land/integers.xml`, `app_v2/src/main/res/values-w600dp/integers.xml`, `scripts/quality/qualifier-shadowing-baseline.txt`

**Depends on:** Step 02.1

**Why:**

Once the combined phone-landscape bucket owns the value, both legacy copies are unreachable.

**Prompt for developer:**

> Delete `welcome_feature_grid_columns` from `values-land/integers.xml` and from
> `values-w600dp/integers.xml`. Under every outcome of step 02.1 both lines are unreachable:
> `values-sw320dp` declares the key and matches every device, so the landscape copy has never won and
> cannot start winning. Then delete the last two entries from
> `scripts/quality/qualifier-shadowing-baseline.txt` and rewrite its header comment: it currently
> says the entries are integer keys that S1330 owns and will clear, which stops being true here.
> Replace it with the rule the empty file now carries - a new entry means a `values-land` or
> `values-w600dp` declaration that no device can resolve, and it is a regression to fix rather than
> to accept. Keep the file; do not delete it. `assert-string-format-baseline.txt` is the precedent
> for an entry-free baseline that still exists.

**Verification:**

- `Grep` - `welcome_feature_grid_columns` returns zero hits in `values-land/integers.xml`.
- `Grep` - `welcome_feature_grid_columns` returns zero hits in `values-w600dp/integers.xml`.
- `Grep` - `values-land/integers.xml` contains exactly 5 `<integer name=` lines.
- `Grep` - `values-w600dp/integers.xml` contains exactly 3 `<integer name=` lines.
- `Glob` - `scripts/quality/qualifier-shadowing-baseline.txt` still exists.
- `Grep` - the baseline file contains exactly 0 non-comment, non-blank lines.
- `Grep` - `S1330` returns zero hits in the baseline file.
- `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0 and reports `0 baselined`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 8/8 PASS. Removed unreachable copies and emptied the shadowing baseline.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.
- [x] Negative check recorded: re-add `welcome_feature_grid_columns` to `values-land/integers.xml`,
      confirm the gate exits 1 naming `integers.xml|values-land|welcome_feature_grid_columns`, then
      revert and confirm exit 0. Proves the empty baseline still detects, rather than having gone blind.
- [x] Dev log entry added for the phase via `post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Audit focus: re-derive what
      `Tb-P` and `Tb-L` resolve for this key from the tree, not from this plan, and confirm both are
      still 4. That specific check is what S1282's phase 01 audit caught late. PASS: sw600dp
      declarations eliminate the combined phone bucket before orientation selection.

---

## Handoff Notes to Next Phase

The baseline is empty, so the ticket's mechanical signal is met and the gate now guards a clean tree.
Everything left in `values-land/integers.xml` wins on at least one device class. Phases 03 and 04
handle the two defects the gate cannot see by construction: a shadow that starts only at 480dp, and
a width-qualified copy that reaches portrait.

---

## Rollback Plan

Remove the combined-bucket line added in step 02.1, restore the two pruned declarations and the two
baseline entries. Single-file resource edits with no consumer changes.
