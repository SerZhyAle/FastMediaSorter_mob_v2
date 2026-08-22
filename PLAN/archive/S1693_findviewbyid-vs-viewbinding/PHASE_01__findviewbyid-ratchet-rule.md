# Phase 01 - findViewById Ratchet Rule

**Strategic spec:** [`../S1693_findviewbyid-vs-viewbinding.md`](../S1693_findviewbyid-vs-viewbinding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`assert-source-gates.ps1` gains a `findviewbyid` count-ratchet dimension over `app_v2/src/main`
with its baseline captured from the current tree.

---

## Prerequisites

- [ ] Working tree readable; no baseline file exists yet.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/source-matchers.ps1` | Modified | +25 |
| `scripts/quality/findviewbyid-baseline.txt` | New (generated) | 1 |

---

## Steps

### Step 01.1 - Add the `findviewbyid` rule to `Get-SourceRules`

**Files:** `scripts/quality/lib/source-matchers.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `New-RegexRule` entry named `findviewbyid` with pattern `[regex]'\bfindViewById\s*[<(]'`
> and defaults (Roots/PathFilter `app_v2/src/main`, `.kt`). FailMessage: growth message naming
> S1693 - a new `findViewById` in a file where the count grew must either use the layout's
> generated binding field, or the file must genuinely be one of the legitimate shapes (custom
> View, adapter/ViewHolder, runtime-resolved layout, documented host-neutral helper) - in which
> case the ratchet is lowered elsewhere first or the growth is justified in review. Precede the
> entry with a short comment recording the S1693 model: legality of one call is not lexically
> decidable, so the rule counts growth, not placement; category-C files convert opportunistically
> when another ticket touches them (Rule 32 model), each conversion lowers the baseline, and the
> baseline never rises without a boundary decision.

**Why:**

Strategic goal 2 and ADR-1: growth is stopped by a ratchet, not an allowlist, because the research
proved legitimate and legacy calls are lexically indistinguishable per call site.

**Verification:**

- `Grep` - `findviewbyid` present in `source-matchers.ps1` as a rule name.
- `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only findviewbyid` runs and
  reports a count (record command + exit code).

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - 01.1 rule added, -Only findviewbyid runs (exit 0); 01.2 baseline seeded 362 (src/main), -Gate -Only exit 0. Full -Gate: findviewbyid at baseline; unrelated class-architecture-naming +6 = sibling S1861 in-flight WIP (WearFileReceiverManager in wear/data/repository), not this ticket's files

---

### Step 01.2 - Capture the baseline

**Files:** `scripts/quality/findviewbyid-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -UpdateBaseline -Only findviewbyid`
> to write the baseline from the current tree, then re-run with `-Gate -Only findviewbyid` and
> confirm exit 0.

**Why:**

Strategic goal 3: the baseline is the ratchet's anchor; capturing it green on the current tree is
what makes growth - and only growth - a failure.

**Verification:**

- `Glob` - `scripts/quality/findviewbyid-baseline.txt` exists and holds one integer.
- `-Gate -Only findviewbyid` exit 0 (record command + exit code).

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - 01.1 rule added, -Only findviewbyid runs (exit 0); 01.2 baseline seeded 362 (src/main), -Gate -Only exit 0. Full -Gate: findviewbyid at baseline; unrelated class-architecture-naming +6 = sibling S1861 in-flight WIP (WearFileReceiverManager in wear/data/repository), not this ticket's files

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `scripts/quality/assert-source-gates.ps1 -Gate` (full) reports the new rule at or below
      baseline alongside the existing rules.
- [x] Dev log entry added for the file set.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The ratchet is live: every class-B conversion in Phase 02 lowers the baseline automatically on the
next green full run.

---

## Rollback Plan

Remove the rule entry and the baseline file - no product code touched.
