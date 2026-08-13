# Phase 01 - Dead column declarations

**Strategic spec:** [`../S1330_landscape-integers-dead-under-sw-qualifiers.md`](../S1330_landscape-integers-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Remove every landscape-flavoured column declaration whose removal cannot move a value on any device
class, and retire the six baseline entries they own. No owner decision is involved and no device
resolves a different number after this phase.

---

## Prerequisites

- [ ] The per-key decision table and the before/after tables in [`INDEX.md`](INDEX.md) are the single
      source for which keys go; do not extend the set by eye.
- [ ] No owner decision is required for this phase - do not wait on the Pre-Implementation Blockers.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/integers.xml` | Modified | ≤ 50 |
| `app_v2/src/main/res/values-sw320dp/integers.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-sw600dp/integers.xml` | Modified | ≤ 15 |
| `app_v2/src/main/res/values-land/integers.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values-w600dp/integers.xml` | Modified | ≤ 15 |
| `scripts/quality/qualifier-shadowing-baseline.txt` | Modified | ≤ 15 |

> All five resource files are `values-*` buckets, not `layout*` - CLAUDE.md Rule 11 (landscape
> parity) does not apply. No layout file changes in this phase.

---

## Steps

### Step 01.1 - Delete the unreferenced `grid_column_count_list` key

**Files:** `app_v2/src/main/res/values/integers.xml`, `app_v2/src/main/res/values-sw320dp/integers.xml`, `app_v2/src/main/res/values-sw600dp/integers.xml`, `app_v2/src/main/res/values-land/integers.xml`, `app_v2/src/main/res/values-w600dp/integers.xml`

**Depends on:** - start of phase

**Why:**

The unused key has no consumer, so removing it eliminates dead resources without changing any screen.

**Prompt for developer:**

> Delete every declaration of `grid_column_count_list` - all five of them, including the one in the
> base `values/integers.xml`. Nothing reads this key: it appears in no Kotlin file, no layout, and
> no `getIdentifier` call (the only `getIdentifier` uses in the project resolve `string` and an
> Android-framework `dimen`). The base file groups it under "Default values for missing resources
> (lint fix)", so it was added to quiet lint rather than to drive a grid. Restoring a landscape
> value for a key nobody reads would invent behaviour; deleting it is CLAUDE.md Rule 20 dead-weight
> hygiene. Delete the whole `<integer .. />` line in each file, leaving surrounding keys and
> comments alone.
>
> One stale comment in `values/integers.xml` is corrected in the same edit, because the file is open
> anyway and the statement is wrong today: the `statistics_card_span` comment says landscape "widens
> to three so all summary cards sit on one row", while `values-land` declares 4. Say four. The claim
> stays true under every phase 04 outcome, since phase 04 only decides what a tablet resolves in
> portrait.

**Verification:**

- `Grep` - `name="grid_column_count_list"` returns zero hits across `app_v2/src` and `wear/src`.
- `Grep` - `values/integers.xml` no longer contains the word `three` in the `statistics_card_span` comment.
- `Grep` - `grid_column_count_list` returns zero hits in `app_v2/src/main/res/values/integers.xml`.
- `Grep` - `app_v2/src/main/res/values/integers.xml` contains exactly 28 `<integer name=` lines.
- `Grep` - `app_v2/src/main/res/values-sw320dp/integers.xml` contains exactly 2 `<integer name=` lines.
- `Grep` - `app_v2/src/main/res/values-sw600dp/integers.xml` contains exactly 5 `<integer name=` lines.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Tactical self-correction: the source files held 3 and 6 integer entries before this deletion, so the specified post-delete counts were stale by one. Updated predicates to sw320=2 and sw600=5; scoped `post-change.ps1` already passed.
- 2026-08-03 - Verification 5/5 PASS. Expected: base=28, sw320=2, sw600=5 | actual: base=28, sw320=2, sw600=5. Files: five `integers.xml` resource buckets. Dev log recorded by scoped closure.

---

### Step 01.2 - Prune the value-identical `grid_column_count`

**Files:** `app_v2/src/main/res/values-land/integers.xml`, `app_v2/src/main/res/values-w600dp/integers.xml`

**Depends on:** Step 01.1

**Why:**

The same lower-priority value already wins wherever this declaration could otherwise apply.

**Prompt for developer:**

> Delete `grid_column_count` from `values-land/integers.xml` and from `values-w600dp/integers.xml`.
> `values-sw320dp` declares the same key with the same value 3 and matches every device, so neither
> line has ever won anywhere. The shipped resource table already proves it: aapt2 deduplicated the
> key down to `() 3` and `(sw600dp) 4`, collapsing four declarations that carried no information.
> Do not add a combined-bucket replacement - the landscape value is identical to the phone value, so
> there is nothing to restore. Keep `values-sw320dp`, `values-sw480dp` and `values-sw600dp` as they
> are; the tablet 4 in `values-sw600dp` is the only meaningful override this key has.

**Verification:**

- `Grep` - `name="grid_column_count"` (exact attribute, not the `_landscape` / `_list` prefixes) returns zero hits in `values-land/integers.xml`.
- `Grep` - `name="grid_column_count"` (exact attribute) returns zero hits in `values-w600dp/integers.xml`.
- `Grep` - `name="grid_column_count">4<` still present in `values-sw600dp/integers.xml`.
- `Grep` - `name="grid_column_count">3<` still present in `values-sw320dp/integers.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 4/4 PASS. Removed identical unreachable declarations from `values-land` and `values-w600dp`; scoped `post-change.ps1` passed.

---

### Step 01.3 - Prune the unreachable `resource_grid_column_count`

**Files:** `app_v2/src/main/res/values-land/integers.xml`, `app_v2/src/main/res/values-w600dp/integers.xml`

**Depends on:** Step 01.2

**Why:**

Existing combined buckets already define the intended phone-landscape value for this key.

**Prompt for developer:**

> Delete `resource_grid_column_count` from `values-land/integers.xml` and from
> `values-w600dp/integers.xml`. Both declare 4 and neither can win: phone landscape is already owned
> by `values-sw320dp-land` and `values-sw480dp-land` (2 each, added 2026-07-11), phone portrait by
> `values-sw320dp` / `values-sw480dp` (1), and every tablet by `values-sw600dp` (2). The 4 is a
> leftover from before the combined buckets existed and contradicts the value that ticket
> deliberately chose - do not resurrect it into a combined bucket. Leave both combined buckets and
> their explanatory comments untouched; they are the working precedent this ticket copies.

**Verification:**

- `Grep` - `name="resource_grid_column_count"` returns zero hits in `values-land/integers.xml`.
- `Grep` - `name="resource_grid_column_count"` returns zero hits in `values-w600dp/integers.xml`.
- `Grep` - `name="resource_grid_column_count">2<` still present in `values-sw320dp-land/integers.xml`.
- `Grep` - `name="resource_grid_column_count">2<` still present in `values-sw480dp-land/integers.xml`.
- `Grep` - `values-land/integers.xml` contains exactly 5 `<integer name=` lines.
- `Grep` - `values-w600dp/integers.xml` contains exactly 4 `<integer name=` lines.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Tactical self-correction: Step 01.2 already reduced the `values-land` count, so the correct post-step count is five. Verification 6/6 PASS; qualifier-shadowing gate reported 8 baselined before baseline cleanup. Scoped `post-change.ps1` passed.

---

### Step 01.4 - Retire the six cleared baseline entries

**Files:** `scripts/quality/qualifier-shadowing-baseline.txt`

**Depends on:** Step 01.3

**Why:**

Keeping a baseline entry after its declaration is gone would falsely record resolved debt.

**Prompt for developer:**

> Delete the six entries steps 01.1-01.3 just cleared: `grid_column_count`, `grid_column_count_list`
> and `resource_grid_column_count`, each in both `values-land` and `values-w600dp`. Leave the two
> `welcome_feature_grid_columns` entries - phase 02 owns those. The gate tolerates a stale entry
> silently (it subtracts baselined lines from what it finds and never complains about a line that
> matches nothing), so an untrimmed baseline would keep claiming debt that no longer exists. Leave
> the header comment as it is for now; phase 02 rewrites it when the last entry goes.

**Verification:**

- `Grep` - `qualifier-shadowing-baseline.txt` contains exactly 2 non-comment, non-blank lines.
- `Grep` - both remaining lines match `welcome_feature_grid_columns`.
- `Grep` - `grid_column_count_list` returns zero hits in the baseline file.
- `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0 and reports `2 baselined`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 4/4 PASS. Baseline now has exactly two `welcome_feature_grid_columns` entries; qualifier-shadowing gate reported 2 baselined. Scoped `post-change.ps1` passed.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). A malformed or duplicated
      resource fails the merge, which is the only static proof available for resource buckets.
- [x] `Grep` - no `duplicate value for resource` or `resource merge` error in the build output.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.
- [x] Dev log entry added for the phase via `post-change.ps1` - one logical change, not one per file.
- [x] Public API unchanged - catalog regeneration not required.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Audit focus for this phase: confirm
      against the before/after tables in `INDEX.md` that no device class resolves a different number,
      and re-check that no consumer of a deleted key was missed. PASS: resource matrix and zero
      references confirm no runtime/lifecycle/data-risk finding applies.

---

## Handoff Notes to Next Phase

Every remaining declaration in `values-land/integers.xml` and `values-w600dp/integers.xml` now wins
somewhere, with one exception: `welcome_feature_grid_columns`, which phase 02 owns and which is the
last pair of baseline entries. Six of the eight are gone and nothing moved on screen.

---

## Rollback Plan

Restore the deleted lines in the five resource files and the six baseline lines. No consumer changed,
so resolution returns to its previous state exactly.
