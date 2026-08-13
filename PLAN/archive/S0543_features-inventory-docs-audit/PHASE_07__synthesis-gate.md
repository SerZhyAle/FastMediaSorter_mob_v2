# Phase 07 - Synthesis & Consistency Gate

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all phases
**Steps done:** 3 / 3

## Results (2026-06-19)

- §6 Q1 resolved: no separate heavy drift gate; instead `validate.ps1` strengthened to forbid spec-id-as-area-prefix on active records (removed tombstones exempt). Skill duties (P04/P05) cover the rest.
- §6 Q2 resolved: showcase selection stays manual via `/skill-release` (CLAUDE.md §11); audit only recommends candidates.
- `## Last Audit` written into the strategic spec with §11 outcomes + residuals.
- validate.ps1 PASS (359) with the new gate.

---

## Objective

Resolve the strategic §6 open items, decide and (if chosen) build the inventory-consistency gate, and finalize the audit record.

---

## Steps

### Step 07.1 - Resolve open research items

**Prompt:**

> Resolve strategic §6: (Q1) separate consistency gate vs strengthened `validate.ps1` + skill duties - decide based on Phase 02/03 noise levels; (Q2) showcase-selection depth - fully manual vs heuristic candidate flagging. Write the decisions into strategic §6 (mark resolved).

**Verification:**

- Strategic §6 shows both items resolved with a one-line rationale each.

**Status:** `[ ]`

---

### Step 07.2 - Build the chosen consistency mechanism

**Prompt:**

> If Q1 chose a gate: add `scripts/quality/assert-all-features-sync.ps1` (re-scan surface, compare to inventory by area, fail on a configurable drift threshold) and wire it into `scripts/post-change.ps1` behind a narrow trigger (inventory/showcase/strings touched). If Q1 chose validate-strengthening: extend `validate.ps1` (e.g. forbid spec-as-area ids, require area↔id-prefix match). Keep warn-not-block if noisy.

**Verification:**

- Chosen mechanism present and runs `exit 0` on the current tree.
- If wired into `post-change.ps1`, the trigger is narrow (does not fire on unrelated edits).

**Status:** `[ ]`

---

### Step 07.3 - Finalize audit record

**Prompt:**

> Summarize outcomes (records added/changed/removed, id fixes, showcase changes, docs/site fixes, settings items handed to S0440, residual gaps) into the strategic spec `## Last Audit` block. Confirm all strategic §11 criteria.

**Verification:**

- `## Last Audit` block present with counts and residuals.
- Strategic §11 criteria each marked met or explicitly deferred.

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Steps 07.1-07.3 are `[x]`.
- [ ] §6 resolved; consistency mechanism in place; `## Last Audit` written.
- [ ] `/spec-check S0543` returns `Verified`.
