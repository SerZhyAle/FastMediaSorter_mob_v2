---
name: owner-inputs-gate-needs-section-33
description: check-owner-inputs.ps1 fails a spec that carries only the newer "§0 Approval Gate (owner input)" block - it looks solely for "### 3.3 Owner inputs (Approval gate)", so a §0-only draft cannot reach Approved
metadata:
  type: feedback
---

`scripts/spec_catalog/check-owner-inputs.ps1` keys **only** on the literal heading `### 3.3 Owner inputs (Approval gate)`. A spec whose owner inputs live in the newer `## 0. Approval Gate (owner input)` block and nowhere else fails with `Missing section` and exit 1, no matter how completely §0 is filled.

**Why:** the two forms coexist and neither replaced the other. `/spec-draft` emits §0; the gate predates it and was never taught the second shape. Measured 2026-08-15: of six specs carrying `## 0. Approval Gate`, four (S1642-S1645) had no §3.3 and all four failed the gate; S1565 carried both and passed. So the fix is additive - §3.3 is expected *alongside* §0, not instead of it.

**How to apply:**

- Filling §0 during `/spec-quiz` or `/spec` is not enough to unblock Draft -> Approved. Add §3.3 too, in the same pass, before running the gate.
- Minimum the gate accepts is a `- **Related tickets:** ..` bullet; every bullet it finds must be filled, so keep §3.3 short and point at §0/§6 for the long-form decisions instead of duplicating them.
- Do not delete or "migrate" §0 to satisfy the gate - the `Approved is blocked while any mandatory line contains MISSING` line in §0 is a second, independent check that the draft pipeline reads.
- Related: [[spec-tech-ui-placement-refusal]] - passing this gate still does not satisfy `/spec-tech` step 5.5, which asks whose judgement filled the fields.
