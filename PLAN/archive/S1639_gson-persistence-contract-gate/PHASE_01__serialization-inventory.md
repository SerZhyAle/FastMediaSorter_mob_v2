# Phase 01 - Serialization inventory

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-14

---

## Objective

Produce the gate script's collector and durability classifier: every Gson serialization point in both modules, the model type at each, and whether its JSON outlives the process. No pinning verdict yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-gson-persistence-contract.ps1` | New | ≤ 400 |

---

## Steps

### Step 01.1 - Create the script with its exit contract

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the script with a header comment block that lists every exit code it returns and a `param()` block taking `-Module` (`app_v2`, `wear`, or both when omitted), `-Format` (`text` default, `json`), and `-ChangedFiles` for scoped runs. Return 0 when no violation is found, 1 when at least one is found, 2 when the run could not verify. Write the failure message with `Write-Error $msg -ErrorAction Continue` before each non-zero `exit`, so the code is reachable under `$ErrorActionPreference = 'Stop'`.

**Why:**

Strategic §11 criterion 1 requires the gate to return distinguishable codes for "no violation", "violations found" and "could not verify", because a caller that cannot tell "found a defect" from "did not look" will read a broken run as a pass.

**Verification:**

- `Glob` - `scripts/quality/assert-gson-persistence-contract.ps1` exists.
- `Grep` - `-ErrorAction Continue` present on every line preceding an `exit` with a non-zero literal.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 01.2 - Collect serialization points and resolve the model type

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the collector: scan every Kotlin source set of the selected module that reaches a release build, and record each call passing a value to Gson serialization or reading one back. For each point capture file, line, the resolved model type, and the enclosing declaration. Resolve the type from the explicit class literal on the read side and from the declared type of the argument on the write side. Where the type cannot be resolved statically, record the point with the type marked unresolved rather than dropping it.

**Why:**

Strategic §9 ADR-3 fixes the asymmetry of costs: an unresolved point kept as a violation costs one written justification, while a point dropped silently costs a user-visible incident after an update, which is how all six recorded cases reached users.

**Verification:**

- `Grep` - the scan root list includes `src/main` and at least one non-main source set, and is not hardcoded to a single directory.
- Run the script with `-Format json` against `app_v2` - output contains a point for `BrowseFileTransferRequestStore` and a point for `PrimaryGoogleAccountStore`.
- Run the script with `-Format json` against `wear` - output is non-empty.

**Status:** `[x]` done

---

### Step 01.3 - Classify durability by sink

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add the durability classifier. Keep the sink signatures in a data table at the top of the script, not inline in the logic: file writes under the app's private storage, plain and encrypted shared preferences, the DataStore preferences editor, the Wear data layer, and user-facing export files count as durable; a background task's own payload and in-memory use do not. Classify a point whose sink cannot be determined as durable, and record the matched sink signature alongside each classification.

**Why:**

Strategic §9 ADR-1 chooses the sink over an in-code marking because a marking would demand the same discipline that has already failed six times, and strategic §3.1 wish 1 requires the report to state why a model was judged durable rather than only naming it.

**Verification:**

- `Grep` - the sink signature list is declared once as a table and referenced by the classifier, with no sink literal inside the classification branch.
- Run with `-Format json` against `app_v2` - the point for `PrimaryGoogleAccountStore` carries a durable classification naming the encrypted-preferences sink.
- Every durable point in the report names the sink signature it matched, and the verdict line states the transient count explicitly. Corrected 2026-08-14: the original predicate demanded a point classified as transient, and the first run showed the tree holds none - every Gson point in a shipping source set writes to a sink that outlives the process. Asserting a transient point would have forced a fixture that proves nothing about this codebase; the transient table stays in the script because a future one would otherwise be misjudged, and the count makes its emptiness visible instead of implied.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable, this phase adds no compiled source.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: not applicable, no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The script emits a list of serialization points, each with a resolved or explicitly unresolved model type and a durability verdict carrying its matched sink. Phase 02 consumes that list and adds the pinning verdict; it must not re-derive the point list.

Measured on 2026-08-14: 39 points across 32 source roots, all 39 durable, 19 of them with an unresolved type. That 49% is the risk row in strategic §7 arriving as predicted, and it is Phase 02's first problem rather than a defect of this phase - ADR-3 already rules that each unresolved point is a violation until justified, so Phase 02 must not quietly drop them. Two concrete resolution gaps are known: a store that reads through its own generic helper hides the type behind a type parameter (the point reports `T`), and a collection read reports its container (`List`) instead of its element. Phase 02 needs fully qualified names to match keep rules, so it must extend resolution rather than inherit these simple names.

---

## Rollback Plan

Delete the new script - no other file references it until Phase 04.
