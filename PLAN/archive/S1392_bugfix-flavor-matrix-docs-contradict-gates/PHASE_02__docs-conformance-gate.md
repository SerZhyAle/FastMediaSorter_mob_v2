# Phase 02 - Docs conformance gate

**Strategic spec:** [`../S1392_bugfix-flavor-matrix-docs-contradict-gates.md`](../S1392_bugfix-flavor-matrix-docs-contradict-gates.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Add a static gate that compares every registered structured flavor table against the Phase 01 snapshot cell by cell, reports mismatches addressably, and runs inside the existing fast-gate batch and closure facade.

---

## Prerequisites

- [x] Phase 01 is ✅ Done and `docs/flavors/flavor-matrix.json` exists.
- [x] Wiring points in `research/01__flavor-matrix-surface-inventory.md` §6 read.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/flavor-matrix-docs.psd1` | New | ≤ 160 |
| `scripts/quality/assert-flavor-matrix-docs.ps1` | New | ≤ 400 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 10 delta |
| `scripts/post-change.ps1` | Modified | ≤ 30 delta |
| `docs/DEV_OPS.md` | Modified | ≤ 20 delta |

---

## Steps

### Step 02.1 - Declare the checked tables as data

**Files:** `scripts/quality/flavor-matrix-docs.psd1`

**Depends on:** - start of phase

**Prompt for developer:**

> Create a PowerShell data manifest listing every structured table the gate checks. Per entry: document path (with its locale siblings), a stable anchor for locating the table (heading text or the header row), which axis carries flavors (columns or rows), the mapping from each column/row label to a flavor key in the snapshot, the mapping from each row/column label to a snapshot flag name, and the glyph vocabulary that document uses for true and false. Include: the two matrices in `docs/DEV_OPS.md`, the availability table in `docs/HOW_TO.md` and its RU/UK siblings, the flavor table in `docs/QUICK_START.md` and its siblings, the minimum-requirements table in `dev/TECH_REQUIREMENTS.md`, and `docs/FLAVOR_MATRIX.md` itself. Where a documented row is a user-facing feature rather than a flag, record which flag or flags it maps to, and mark rows that legitimately combine two flags so the gate can require a footnote instead of one cell.

**Why:**

Strategic §5.3 requires the list of checked documents to be data rather than code so adding a table needs no change to the comparison logic; strategic §7 records that a gate too rigid about wording gets disabled, which is why the glyph vocabulary and the combined-flag rows are declared per document.

**Verification:**

- `Glob` - `scripts/quality/flavor-matrix-docs.psd1` exists.
- Run `pwsh -NoProfile -Command "Import-PowerShellDataFile scripts/quality/flavor-matrix-docs.psd1 | Out-Null"` - exit 0.
- `Grep` - all of `docs/DEV_OPS.md`, `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`, `docs/QUICK_START.md`, `dev/TECH_REQUIREMENTS.md`, `docs/FLAVOR_MATRIX.md` appear as entries.

**Status:** `[x]` done

---

### Step 02.2 - Write the comparison gate

**Files:** `scripts/quality/assert-flavor-matrix-docs.ps1`

**Depends on:** Step 02.1

**Prompt for developer:**

> Create `scripts/quality/assert-flavor-matrix-docs.ps1`. It loads `docs/flavors/flavor-matrix.json`, walks the manifest, locates each table by its anchor, and compares each mapped cell's value - not its literal text - against the snapshot. Report one line per mismatch: document path, line number, the flavor and flag, expected state, found state. Treat a table whose anchor is missing, whose flavor axis lost a column, or whose mapped row disappeared as a failure, not a skip. Support `-Gate` (non-zero exit on any mismatch), `-Quiet`, and a `-ScopeToFile`-style filter consistent with the other gates in `scripts/quality/`. Document the exit codes in the header per Rule 7, distinguishing "found a mismatch" from "could not verify".

**Why:**

Strategic §2 goal 2 makes silent drift the defect being fixed, so a missing anchor has to fail rather than pass quietly; strategic §3.1 asks for addressed diagnostics because a bare FAIL on a matrix of six flavors and eighteen flags is not actionable.

**Verification:**

- `Glob` - `scripts/quality/assert-flavor-matrix-docs.ps1` exists.
- `Grep` - `-Gate` and `-Quiet` declared in the `param(` block.
- `Grep` - header comment contains `Exit codes` with 0, 1 and 2 distinguished.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.
- Run the gate without `-Gate` on the current tree - it prints the known mismatches from `research/01__flavor-matrix-surface-inventory.md` §3 items 1, 2, 8, 9, 10 with file and line.

**Status:** `[x]` done

---

### Step 02.3 - Prove the gate fails on a corrupted cell

**Files:** none - verification only

**Depends on:** Step 02.2

**Prompt for developer:**

> With `docs/FLAVOR_MATRIX.md` freshly generated, flip exactly one cell in it by hand, run the gate with `-Gate`, and confirm exit 1 with a message naming that document, that line, that flavor and that flag. Restore the file by re-running the Phase 01 generator. Repeat once on a table the gate reads but does not generate - remove one flavor column header from the `docs/HOW_TO.md` table copy in a scratch copy under `temp/S1392/` and confirm the missing-column path also fails rather than skipping.

**Why:**

Strategic §11 criterion 2 states the gate must return non-zero with an addressed message when a single cell is corrupted; a gate never observed failing is not evidence of anything, per CLAUDE.md §12 no-completion-claim-without-evidence.

**Verification:**

- Corrupted-cell run: exit code 1 recorded, output cites the document, line, flavor, flag.
- Restored run: exit code 0 recorded.
- Missing-column run on the scratch copy: exit non-zero recorded, message distinguishes structural failure from a value mismatch.
- `Glob` - no leftover scratch file outside `temp/S1392/`.

**Status:** `[x]` done

---

### Step 02.4 - Wire into the fast-gate batch and the closure facade

**Files:** `scripts/quality/assert-fast-gates.ps1`, `scripts/post-change.ps1`

**Depends on:** Step 02.3

**Prompt for developer:**

> Register the gate in `scripts/quality/assert-fast-gates.ps1` - add it to the `$gateArgs` table with `-Quiet` and to the ordered run list, and name it in the header comment's gate list. Add a block in `scripts/post-change.ps1` calling it with `-Gate`, placed beside the existing documentation-conformance gates (`assert-howto-settings-paths`, `assert-settings-doc-sync`, `assert-script-cheatsheet-sync`) and following their pattern for advisory-versus-fatal handling. Confirm the added runtime keeps the batch inside its fast-check budget.

**Why:**

Strategic §3.1 wish 2 requires the gate to run in the shared fast batch rather than as a separate ritual that has to be remembered, and strategic §11 criterion 3 makes that wiring a completion criterion.

**Verification:**

- `Grep` - `assert-flavor-matrix-docs.ps1` appears in `scripts/quality/assert-fast-gates.ps1` in both the args table and the run list.
- `Grep` - `assert-flavor-matrix-docs.ps1` appears in `scripts/post-change.ps1`.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - the new gate is listed in the summary; overall verdict reflects the still-uncorrected docs (expected FAIL at this point, corrected in Phase 03).
- Recorded elapsed time for the batch is within a few seconds of its pre-change value.

**Status:** `[x]` done

---

### Step 02.5 - Document the gate where the matrix is documented

**Files:** `docs/DEV_OPS.md`

**Depends on:** Step 02.4

**Prompt for developer:**

> In the `FEATURE FLAGS (BuildConfig)` section of `docs/DEV_OPS.md`, add a short lead stating that `docs/FLAVOR_MATRIX.md` is generated from the build file and is the canonical answer, that the tables in this section are checked against it by `scripts/quality/assert-flavor-matrix-docs.ps1`, and how to regenerate. Do not correct the table contents yet - that is Phase 03.

**Why:**

Strategic §5.1 requires other surfaces to defer to the snapshot instead of restating it; a developer editing this section needs to learn about the gate at the point of editing, not after the gate fails.

**Verification:**

- `Grep` - `docs/FLAVOR_MATRIX.md` referenced in `docs/DEV_OPS.md`.
- `Grep` - `assert-flavor-matrix-docs.ps1` referenced in `docs/DEV_OPS.md`.
- `Grep` - `generate-flavor-matrix.ps1` referenced in `docs/DEV_OPS.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No `.kt` touched - validation ladder rung is Script (run, exit 0) plus Doc (grep).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the phase.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gate is expected to be RED entering Phase 03 - it now reports the real drift. Phase 03 is done when the same command is green, so do not touch the gate's logic to make it pass; change the documents.

---

## Rollback Plan

Revert the two wiring edits, delete the gate script and its manifest, revert the `docs/DEV_OPS.md` lead paragraph. No generated artifact depends on this phase.


