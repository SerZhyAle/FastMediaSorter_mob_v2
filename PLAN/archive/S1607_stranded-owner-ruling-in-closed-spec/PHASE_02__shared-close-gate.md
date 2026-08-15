# Phase 02 - Shared close gate

**Strategic spec:** [`../S1607_stranded-owner-ruling-in-closed-spec.md`](../S1607_stranded-owner-ruling-in-closed-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Route both status-change paths through one closing-gate call, so the new checker and the existing durable-evidence checker fire on the path `/spec-check` actually uses.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/_lib.ps1` | Modified | ≤ 60 added |
| `scripts/spec_catalog/update.ps1` | Modified | ≤ 30 changed |
| `scripts/spec_catalog/close.ps1` | Modified | ≤ 20 added |
| `scripts/spec_catalog/bulk-update.ps1` | Modified | ≤ 15 added |

---

## Steps

### Step 02.1 - Add the shared closing-gate function

**Files:** `scripts/spec_catalog/_lib.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a function `Assert-ClosingGates` to `scripts/spec_catalog/_lib.ps1`. It takes the ticket id, the old status and the new status, and runs the closing checkers when the new status is in a single list defined at the top of the function and the old status differs from it. Seed that list with `Implemented` and `Verified`; `Archived` stays out of it deliberately. Run `check-evidence-durable.ps1` and `check-open-items-carried.ps1`, both resolved relative to `$PSScriptRoot`, and skip a checker whose file is absent, matching how the existing gate call in `update.ps1` tolerates a missing checker. On a checker exit code of 1, print the checker's own output under a header naming the ticket and the attempted transition, then throw so the caller aborts before writing the journal. Treat exit 2 the same as a failure, since a gate that could not look has not passed.

**Why:**

The strategic spec's ADR-2 requires the gate to live where both status-change paths reach it, and its section 5.3 requires the set of gated closed statuses to sit in one place next to the existing durable-evidence list so a later gate does not re-declare it; ADR-3 fixes `Archived` outside that list because archiving closes a ticket that already passed the gate.

**Verification:**

- `Grep` - `function Assert-ClosingGates` matches exactly once in `scripts/spec_catalog/_lib.ps1`.
- `Grep` - `check-open-items-carried.ps1` and `check-evidence-durable.ps1` both referenced inside the function.
- `Grep` - `Archived` does not appear in the gated-status list literal.
- Run `pwsh -NoProfile -Command ". scripts/spec_catalog/_lib.ps1; Assert-ClosingGates -Id S1607 -OldStatus Approved -NewStatus Tactical"` - exit 0, no output (transition not gated).

**Status:** `[x]` done

---

### Step 02.2 - Route update.ps1 through the shared function

**Files:** `scripts/spec_catalog/update.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the inline durable-evidence gate block in `scripts/spec_catalog/update.ps1` with a single call to `Assert-ClosingGates`, passing the id, the old status and the requested status. Keep the Owner-Inputs gate for the `Approved` transition exactly as it is - it guards a different transition and does not belong in the closing gate. Preserve the existing behaviour that the failure aborts before the journal write, and keep a comment naming S1606 and S1607 as the two contracts the call now enforces.

**Why:**

The strategic spec's section 4 records that `update.ps1` is currently the only holder of the durable-evidence gate, so leaving a second copy of the logic here would let the two paths drift apart, which is the defect ADR-2 exists to prevent.

**Verification:**

- `Grep` - `Assert-ClosingGates` appears exactly once in command position in `scripts/spec_catalog/update.ps1`; the comment above it names the function too, so the raw hit count is 2.
- `Grep` - `check-evidence-durable.ps1` returns zero hits in `scripts/spec_catalog/update.ps1`.
- `Grep` - `check-owner-inputs.ps1` still matches in `scripts/spec_catalog/update.ps1`.
- Run `pwsh -NoProfile -Command ". scripts/spec_catalog/_lib.ps1; Assert-ClosingGates -Id S1612 -OldStatus Implemented -NewStatus Verified"` - throws, output names the uncarried open item. Do not drive this check by running `update.ps1` against a live ticket: a gate that wrongly passes would silently close someone else's ticket, so the assertion is made against the shared function, which writes no journal.

**Status:** `[x]` done

---

### Step 02.3 - Add the shared gate to close.ps1

**Files:** `scripts/spec_catalog/close.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Call `Assert-ClosingGates` in `scripts/spec_catalog/close.ps1` after the record is resolved and its old status is known, and before `Assert-Record` and the journal write. Pass the id, the old status and the `-Status` argument. Place the call inside the existing catalog-lock critical section, matching where `update.ps1` runs its gates, and add a comment stating that `close.ps1` is the path `/spec-check` uses, which is why the gate has to be here and not only in `update.ps1`.

**Why:**

The strategic spec's section 4 establishes that the canonical closing path runs `/spec-check` into `close-and-log.ps1` into `close.ps1`, which invokes no gate at all, so a gate wired only into `update.ps1` would guard the path used less often and fail goal 3.

**Verification:**

- `Grep` - `Assert-ClosingGates` matches exactly once in `scripts/spec_catalog/close.ps1`.
- `Grep` - the call appears at a lower line number than `Write-Catalog` and than `Assert-Record` in that file.
- Hash `PLAN/spec-catalog.jsonl` with `Get-FileHash`, run `pwsh -NoProfile -File scripts/spec_catalog/close.ps1 -Id S1612 -Status Verified`, then hash again - exit 1, output names the uncarried open item, the two hashes are equal, and `select.ps1 -Id S1612` still reports `Implemented`. Hashing rather than keeping a copy: the proof has to survive in the spec, and a path under `temp/` is disposable evidence the durable-evidence gate rejects.
- Run `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` - exit 0, confirming no journal was half-written by the aborted close.

**Status:** `[x]` done

---

### Step 02.4 - Close the batch path

**Files:** `scripts/spec_catalog/bulk-update.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Call `Assert-ClosingGates` inside the per-ticket loop of `scripts/spec_catalog/bulk-update.ps1`, only when `-Status` was supplied, passing the ticket id, its old status and the new one. Catch the failure and add it to the script's existing `$errors` collection instead of letting it throw, matching the batch contract where every ticket is judged and the whole batch then aborts if any failed.

**Why:**

This script writes the journal itself rather than delegating to `update.ps1`, so without this call a batch is simply a way to close a ticket around the gate, which defeats goal 3 of the strategic spec exactly as the `close.ps1` bypass did.

**Verification:**

- `Grep` - `Assert-ClosingGates` matches exactly once in `scripts/spec_catalog/bulk-update.ps1`.
- Hash `PLAN/spec-catalog.jsonl`, run `bulk-update.ps1 -Id S1612 -Status Verified`, then hash again - exit non-zero and the two hashes are equal, proving the batch aborted before the write.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] All three scripts run and return their documented exit codes - no gradle build applies, this phase touches no Kotlin.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration not applicable - no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Both closing paths now refuse an uncarried open item, and the durable-evidence gate of S1606 fires on `close.ps1` for the first time. The carrier token is enforced by a script but documented nowhere - Phase 03 writes it into the rules and the template so an author meets the requirement before the gate does.

---

## Rollback Plan

Revert phase commit(s) - three script files. No catalog data is mutated by this phase; a failed gate aborts before the journal write, so no half-written journal can survive a rollback.
