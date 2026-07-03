---
name: draft-style-gate
description: Spec style sanitation (.. /ё + lists-over-tables etc.) is a gate only at Draft->Approved, not during Draft drafting
metadata:
  type: feedback
---

Spec writing style hygiene - `..` not `...`, `ё`/`Ё`, lists-over-tables, no pseudographics, one-idea-per-bullet, no section summaries - is an **enforced gate only at the `Draft` -> `Approved` transition**. While a `PLAN/Sxxxx_*.md` spec is in `Draft`, content comes first: rough phrasing, `...`, missing `ё`, and tables are all acceptable and must NOT block draft creation or trigger a standalone hygiene sweep.

**Why:** the user found my pedantry about ellipsis and "sanitation" while composing Draft tickets praiseworthy but misplaced - a draft is a draft, style polish is approval-time work, not drafting-time friction.

**How to apply:**
- Drafting / iterating a Draft spec → don't sweat style; don't raise ellipsis/`ё`/table/summary findings; don't run hygiene-only passes.
- The skill that promotes Draft -> Approved owns cleaning the draft to full style before flipping status. (`/spec` auto-approves in step 6, so it still cleans then.)
- Global Author Style for chat / UI strings / production docs / `Approved`+ specs is unchanged - still non-negotiable there.
- Encoded in CLAUDE.md "Author Style" + "Spec Writing Style" sections and in `.claude/commands/spec.md` (Constraints) + `spec-update.md` (language focus area). See [[feedback_strategic_spec_owner_gate]] for the related Approval-gate owner-inputs mechanics.
