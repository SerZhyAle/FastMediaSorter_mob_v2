# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1607_stranded-owner-ruling-in-closed-spec.md`](../S1607_stranded-owner-ruling-in-closed-spec.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Write the carrier-token contract where spec authors read it - the repository rules, the strategic template and the audit command - so the requirement is met before the gate reports it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 12 added |
| `AGENTS.md` | Modified | ≤ 12 added |
| `.claude/templates/strategic-spec.md` | Modified | ≤ 6 added |
| `.claude/commands/spec-check.md` | Modified | ≤ 4 added |
| `scripts/spec_catalog/SCHEMA.md` | Modified | ≤ 8 added |

---

## Steps

### Step 03.1 - Record the carrier-token contract in the repository rules

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the Spec Catalog section of `CLAUDE.md` with the closing contract for section 6: a transition into `Implemented` or `Verified` requires every section 6 item to be `Resolved`, or to carry a literal `Carrier: Sxxxx` token naming the ticket that now owns the question. State that the token is the only thing the tooling reads, for the same reason the neighbouring `Blocker: Sxxxx` token is literal. Name the gate `scripts/spec_catalog/check-open-items-carried.ps1` and state that `Archived` is deliberately not gated. Mirror the same paragraph into `AGENTS.md`, which the rules require to be synced whenever a shared rule changes.

**Why:**

The strategic spec's ADR-1 makes the carrier token the mechanism that gives a stranded question an owner, and the repository's own measurement is that an ungated rule performs at 1 to 8 per cent, so the rule text and the gate have to ship together rather than the text arriving later.

**Verification:**

- `Grep` - `Carrier: Sxxxx` matches in `CLAUDE.md`.
- `Grep` - `check-open-items-carried.ps1` matches in `CLAUDE.md`.
- `Grep` - `Carrier: Sxxxx` matches in `AGENTS.md`.
- `Grep` - `Archived` appears within the added paragraph in both files.

**Status:** `[x]` done

---

### Step 03.2 - Teach the template and the audit command about the token

**Files:** `.claude/templates/strategic-spec.md`, `.claude/commands/spec-check.md`, `scripts/spec_catalog/SCHEMA.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `.claude/templates/strategic-spec.md`, add a `**Носитель:**` line to the section 6 item skeleton, marked as required when the item stays `Open` at closing time and omitted otherwise, quoting the literal `Carrier: Sxxxx` form. In `.claude/commands/spec-check.md`, add a row to the verification-mechanics table for the open-item contract, naming the checker script as the how. In `scripts/spec_catalog/SCHEMA.md`, note under the status list that transitions into `Implemented` and `Verified` run the closing gates and name both checkers.

**Why:**

The strategic spec's section 3.1 asks that passing the gate cost nothing for a clean spec, which only holds if the author sees the required field while writing section 6 rather than discovering it at the closing transition.

**Verification:**

- `Grep` - `Носитель` matches in `.claude/templates/strategic-spec.md` section 6 skeleton.
- `Grep` - `check-open-items-carried` matches in `.claude/commands/spec-check.md`.
- `Grep` - `check-open-items-carried` matches in `scripts/spec_catalog/SCHEMA.md`.

**Status:** `[x]` done

---

### Step 03.3 - Close the change through the facade

**Files:** all files touched by phases 01 to 03
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` once for the whole changed set, passing every file from phases 01 to 03 via `-Files` with `-ScopeToFile`, `-ChangeType Tooling` and a description naming S1607. Then run the document-registry closing trio, because the changed set includes documents registered under the `repository-rules` and `spec-process` records: `validate.ps1`, `generate.ps1`, and `generate.ps1 -Check`. Read the closure verdict and treat `PASS WITH ADVISORIES` as a result to report, not to ignore.

**Why:**

The repository requires mechanical closure to run through the `post-change.ps1` facade rather than hand-rolled steps, and the document-registry loop must close whenever a registered document changes, which this phase's edits to `CLAUDE.md`, `AGENTS.md` and the command and template files trigger.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<set>" -ScopeToFile -ChangeType Tooling -Target "spec-lifecycle" -Description "S1607: closing contract for section 6 open items"` - exit 0, final line `post-change: PASS` or `PASS WITH ADVISORIES`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.
- `Grep` - `S1607` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `post-change.ps1` returned a clean verdict for the whole changed set.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" - covered by the facade in Step 03.3.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration not applicable - no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and template text only. Reverting this phase alone leaves the gate of phases 01 and 02 armed but undocumented, so revert phases 02 and 03 together if the contract is being withdrawn.
