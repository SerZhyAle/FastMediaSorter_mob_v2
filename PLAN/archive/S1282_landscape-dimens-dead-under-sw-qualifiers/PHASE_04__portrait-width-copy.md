# Phase 04 - Portrait width copy

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 1
**Started:** -
**Completed:** -

---

## Objective

Settle the thirteen keys that `values-w600dp/dimens.xml` still hands to a tablet in **portrait**:
either remove them so the base values apply, or keep them and say so on purpose. Satisfies strategic
§11 criterion 4.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the file holds exactly the thirteen keys by then.
- [ ] **Blocked:** strategic §6.1 item 4 answered. This phase cannot start before it, and no other phase depends on it.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-w600dp/dimens.xml` | Modified or deleted | ≤ 30 |

---

## Steps

### Step 04.1 - Apply the owner decision to the width bucket

**Files:** `app_v2/src/main/res/values-w600dp/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Two mutually exclusive outcomes, decided by strategic §6.1 item 4.
>
> **If the answer is "remove":** delete the file. The thirteen keys fall back to `values/dimens.xml`,
> which is what a tablet in portrait had before the copy existed - `settings_item_min_height` returns
> to 36dp, `dialog_file_info_min_width` to 150dp, and the whole `settings_*` block to its roomier
> base spacing. A phone in landscape is unaffected: `values-land` declares the same thirteen keys
> with the same values and wins there once the width bucket is gone.
>
> **If the answer is "keep":** leave the values and replace the opening comment with the reason,
> naming the configuration it targets - available width at or above 600dp including portrait - so the
> next reader does not mistake it for a landscape file and delete it as dead weight.
>
> Do not split the difference by pruning some keys and keeping others; the thirteen move together
> because they share one question.

**Verification:**

- If removed: `Glob` - `app_v2/src/main/res/values-w600dp/dimens.xml` returns no match.
- If removed: `Grep` - `settings_item_min_height` still declared in `values/dimens.xml` and in `values-land/dimens.xml`.
- If kept: `Grep` - the file's leading comment contains `w600dp` and the word `portrait`.
- Either way: `/build` -> `standard debug` exits 0.
- Either way: `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 04.1 is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the touched file.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final resource state reached. Phase 05 records it and regenerates the tooling docs.

---

## Rollback Plan

Restore the file from the previous revision. It is a single resource file with no consumer changes.
