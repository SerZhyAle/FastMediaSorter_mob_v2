# Phase 01 - The dimension-literal ratchet rule

**Strategic spec:** [`../S1922_layout-hardcoded-dimension-literals.md`](../S1922_layout-hardcoded-dimension-literals.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`layout-hardcoded-dimens` exists in the shared lexical rule registry with a mechanically-taken baseline, counts every layout root, and does not count `0dp`.

---

## Prerequisites

- [ ] Strategic §6 items 1 and 2 are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/source-matchers.ps1` | Modified | ≤ 20 added |
| `scripts/quality/layout-hardcoded-dimens-baseline.txt` | New | 1 |

---

## Steps

### Step 01.1 - Register the rule

**Files:** `scripts/quality/lib/source-matchers.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `New-RegexRule` entry named `layout-hardcoded-dimens` immediately after `layout-hardcoded-colors`. Match a dimension literal in an attribute value while excluding `="0dp"`, restrict `-Extensions` to `.xml`, and set `-Roots` and `-PathFilter` to cover all five layout directories: `layout`, `layout-land`, `layout-sw480dp`, `layout-sw720dp`, `layout-w600dp`. Write a comment above it recording that `0dp` is excluded because it is ConstraintLayout's "match constraints" keyword rather than a size, with the 1561-of-3454 measurement that justifies it.

**Why:**

Strategic ADR-2 rests on that measurement - 45% of the captured number is `0dp` - so the exclusion is the difference between a counter that describes real debt and one that demands work nobody should do, and a reader who meets the rule later needs the number to re-judge it.

**Verification:**

- `Grep` - `layout-hardcoded-dimens` appears exactly once as a rule name in `source-matchers.ps1`.
- `Grep` - all five layout directory names appear in the rule's `-Roots`.
- Run: `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only layout-hardcoded-dimens -List` - expected: exit 0, and the rule reports a non-zero count.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 01.1

---

### Step 01.2 - Take the baseline mechanically

**Files:** `scripts/quality/layout-hardcoded-dimens-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Produce the baseline with `assert-source-gates.ps1 -Only layout-hardcoded-dimens -UpdateBaseline`. Do not type the number by hand. Confirm the written value equals an independent `grep` count of the same pattern over the same five directories.

**Why:**

Strategic §7 names a hand-written baseline as the risk that makes the rule silently stop catching growth, and strategic §11.2 requires the number to be taken by the scanner - a baseline and a rule that disagree about what they count is a gate that passes on everything.

**Verification:**

- `Glob` - `scripts/quality/layout-hardcoded-dimens-baseline.txt` exists and holds a single integer.
- Run: the independent grep count matches the baseline exactly. Record both numbers in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 01.2

---

### Step 01.3 - Prove the rule fails on growth and ignores `0dp`

**Files:** a scratch layout under `app_v2/src/main/res/layout/`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a layout carrying one hardcoded `16dp` and confirm the rule counts it as growth and fails. Replace that literal with `0dp` alone and confirm the rule passes. Remove the scratch layout and confirm the count returns to the baseline. Record all three outcomes here.

**Why:**

Strategic §11.3 and §11.4 are two separate claims - growth fails, and `0dp` does not count - and only the second one can silently regress into a rule that nags about ConstraintLayout, which is the failure ADR-2 exists to prevent.

**Verification:**

- Recorded in this file: growth run fails and names the file; `0dp` run passes; post-removal count equals the baseline.
- `Glob` - no scratch layout remains.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 01.3

---

### Step 01.4 - Confirm the message tells the reader what to do

**Files:** `scripts/quality/lib/source-matchers.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Check the failure message names both halves: move the value into `@dimen/`, and `0dp` is not counted. Adjust the wording if either half is missing.

**Why:**

Strategic §11.6 requires it, and §7 names "the rule pressures someone into converting a literal that should stay a literal" as a live risk - the message is the only place that risk gets addressed at the moment somebody hits the gate.

**Verification:**

- `Grep` - the rule's `-FailMessage` contains `@dimen/` and mentions `0dp`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1922 step 01.4

---

## Evidence (2026-08-21)

**Step 01.2 - the baseline agrees with an independent count.** The scanner reported `layout-hardcoded-dimens: NO BASELINE yet | actual 1893` and `-UpdateBaseline` wrote `1893`. The independent shell count over the same five directories - `grep -rhoE '="[0-9]+(\.[0-9]+)?(dp|sp)"' app_v2/src/main/res/layout*/ | grep -v '="0dp"' | wc -l` - also returned **1893**. The unfiltered count is 3454, of which 1561 are `"0dp"`, which is the 45% ADR-2 rests on. The scan reads 331 files in ~0.3 s, so the rule adds nothing measurable to the shared walk.

**Step 01.3 - both directions, and the exclusion holds.**

| Probe | actual vs baseline | Verdict |
| --- | --- | --- |
| layout adding one `16dp` | 1894 vs 1893, delta 1 | `-Gate` exit **1**, FAIL |
| same layout carrying only `0dp` | 1893 vs 1893, delta 0 | PASS |
| probe removed | 1893 vs 1893, delta 0 | PASS |
| clean tree, `-Gate` | 1893 vs 1893 | exit **0** |

The middle row is the one that matters: a rule counting `0dp` would have failed there and started demanding ~1561 conversions that must not happen.

**Step 01.4 - the message says what to do.** Printed verbatim on the failing run:

```
FAIL: new hardcoded dimension literal in a layout (S1922). Move the value into @dimen/ and
reference it, so the size can be changed in one place. Structural "0dp" (ConstraintLayout
match-constraints) is NOT counted by this rule - if that is what you added, this is not the finding.
```

Both halves present: the action, and the `0dp` carve-out that stops someone converting a layout keyword.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable: no Kotlin, no build file. The rule is lexical and runs no gradle.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The rule and its baseline are proven in both directions. Phase 02 carries documentation and closure only.

---

## Rollback Plan

Delete the rule entry and its baseline file - no other rule reads either, and no source file was migrated in this phase.
