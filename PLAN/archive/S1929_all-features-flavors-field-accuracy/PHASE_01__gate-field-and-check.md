# Phase 01 - The `gate` field and the check behind it

**Strategic spec:** [`../S1929_all-features-flavors-field-accuracy.md`](../S1929_all-features-flavors-field-accuracy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

An inventory record may name the BuildConfig flag it lives behind, and the validator refuses any record whose `flavors` disagrees with that flag's row in the generated flavor matrix.

---

## Prerequisites

- [ ] Strategic §6 items 1-3 are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/all_features/validate.ps1` | Modified | ≤ 60 added |
| `scripts/all_features/add.ps1` | Modified | ≤ 15 added |

---

## Steps

### Step 01.1 - Read the matrix row for a flag

**Files:** `scripts/all_features/validate.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a function that parses `docs/FLAVOR_MATRIX.md` once and returns, for a flag name, the set of flavors whose column carries the "on" marker. Read the column order from the table's own header row rather than assuming it. Return nothing distinguishable for a flag the matrix does not list, so the caller can tell "off everywhere" from "not a flag at all".

**Why:**

Strategic §3.2 makes the generated matrix the only permitted source for the flavor grid - CLAUDE.md forbids restating it from memory after S1392 - and §5.1 requires an unknown flag to be an error rather than a silent skip, which is only possible if absence and emptiness are different answers.

**Verification:**

- `Grep` - the new function appears once and reads `docs/FLAVOR_MATRIX.md`.
- `Grep` - the column order is taken from the header row, not hard-coded.
- Run: a one-off call for `SUPPORT_WEAR_COMPANION` - expected: exactly `standard, noLegal, legacy`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 01.1

---

### Step 01.2 - Enforce the rule

**Files:** `scripts/all_features/validate.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Where the validator already checks `flavors` membership, add: when a record carries `gate`, its `flavors` must equal that flag's matrix set exactly, order-insensitively. On a mismatch, report the record's line, the flag, the expected set and the recorded set. On a flag the matrix does not list, report that instead. A record without `gate` is untouched.

**Why:**

Strategic §11.2 requires the message to name both sets, because a validator that only says "mismatch" leaves the reader to re-derive the matrix by hand - which is the manual step whose unreliability created the defect in the first place.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` on the unmodified inventory - expected: exit 0, all 783 records still pass.
- `Grep` - the new branch is entered only when the record has a `gate` property.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 01.2

---

### Step 01.3 - Prove all four outcomes

**Files:** a scratch copy of the inventory
**Depends on:** Step 01.2

**Prompt for developer:**

> Against a scratch copy, not the real inventory, run the validator on four records: `gate` with a matching set (passes); `gate` with a wrong set (fails, and the message names both sets); `gate` naming a flag the matrix does not list (fails); no `gate` with any set at all (passes). Record every exit code and the mismatch message verbatim here.

**Why:**

Strategic §11.1-§11.5 are five separate claims and four of them are these branches; the fourth in particular - a record without `gate` passing whatever its flavors say - is what protects `documentation_site_pages`, which the capture explicitly warns must not be swept up.

**Verification:**

- Recorded in this file: four exit codes and the verbatim mismatch message.
- `Glob` - no scratch inventory file remains.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 01.3

---

### Step 01.4 - Let the writer set the field

**Files:** `scripts/all_features/add.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add an optional `-Gate` parameter that writes the field when supplied and omits it entirely when not. Do not default it to anything.

**Why:**

Strategic §5.1 wants the field set when the record is created rather than hand-appended afterwards, and ADR-2 forbids a default because an absent `gate` is an assertion that the capability is behind no flag - a default would turn that assertion into a guess.

**Verification:**

- `Grep` - `-Gate` appears in the `param()` block and is not mandatory.
- Run: `add.ps1` without `-Gate` against a scratch file - expected: the written record has no `gate` key at all.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1929 step 01.4

---

## Evidence (2026-08-21)

**Step 01.1 - the matrix parser reads the real rows.** 23 flags parsed from `docs/FLAVOR_MATRIX.md`, with the column order taken from the table's own header:

| flag | parsed set |
| --- | --- |
| `SUPPORT_WEAR_COMPANION` | standard, noLegal, legacy |
| `SUPPORT_IMAGES` | standard, noLegal, lite, photos, legacy, vr |
| `SUPPORT_STREAMS` | standard, noLegal, legacy, vr |

The first row is the one §0 needed and matches the matrix exactly. The parser treats `[+]` and `[+]*` alike, per the file's own legend - the asterisk records that the value was inherited from `defaultConfig`, which is a fact about where the value came from, not about what it is.

**Step 01.2 - nothing regressed.** `ALL_FEATURES validation PASS: 783 record(s)`, exit 0, on the unmodified inventory. No existing record carries `gate`, so none is judged by the new rule.

**Step 01.3 - all four outcomes, against the gitignored noLegal inventory used as scratch and restored byte-identically afterwards:**

| Case | Exit | Message |
| --- | --: | --- |
| `gate` matches the matrix | 0 | - |
| `gate` disagrees | 1 | `flavors disagree with gate 'SUPPORT_WEAR_COMPANION' - expected [legacy, noLegal, standard], recorded [standard]` |
| `gate` names an unknown flag | 1 | `gate 'SUPPORT_NOT_A_FLAG' is not a flag in docs/FLAVOR_MATRIX.md` |
| no `gate`, all six flavors | 0 | - |

The last row is the one the capture asked for by name: it is what lets `documentation_site_pages` keep its six flavors instead of being swept to a runtime flag's set.

**Step 01.4 - the writer omits the key rather than emptying it.** Without `-Gate` the written record contains no `gate` key at all; with it, the key carries the flag. Both records validate.

`docs/ALL_FEATURES.jsonl` is untouched by this phase, and the scratch file was restored with a matching hash.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable: no Kotlin, no build file. The validator is lexical and runs no gradle.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `docs/ALL_FEATURES.jsonl` is unchanged by this phase - the mechanism lands before any data does.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The check exists and refuses correctly, but no record carries a `gate` yet, so it currently guards nothing. Phase 02 supplies the data - and is the phase that must not judge a record by its id prefix.

---

## Rollback Plan

Revert both scripts - no record carries the new field until Phase 02, so nothing else depends on it.
