---
name: feedback-phase-boundary-audit
description: Audit the phase just finished before starting the next phase in any multi-phase task - fixing there is far cheaper than after later phases build on top or at end-of-pipeline audit.
metadata:
  type: feedback
---

Rule: in any large task split into sequential phases (tactical spec phases via `/spec-dev`, or ad-hoc multi-step work under `dev/AGENT_WORKFLOW.md`), audit the phase just completed against `docs/CODE_AUDIT_PROTOCOL.md` before starting the next phase's first step - not only at the very end of the pipeline. Fix P0/P1 findings inline immediately; P2 fix if trivial else log as an `AUDIT-P2` candidate for a mechanical gate; P3 fix inline or skip.

**Why:** Owner's explicit instruction (2026-07-17) - during big tasks it is much cheaper to catch and fix defects at the start of the next phase than to let them accumulate under N later phases of code built on top, or wait for a single end-of-pipeline audit (`/spec-check` at F5) to surface them cold. Fix cost scales with how much later work already depends on the defective code.

**How to apply:** Now codified as a mandatory process step ("Phase-boundary audit") in [[spec-catalog-exit-code-contract]]-adjacent tooling - concretely `.claude/commands/spec-dev.md` runs it automatically after every phase flips `✅ Done`, `.claude/commands/spec-tech.md`'s phase template carries a matching Phase Done Criteria checkbox, `docs/CODE_AUDIT_PROTOCOL.md` has a dedicated "Phase-boundary audits" section and trigger, and CLAUDE.md/AGENTS.md §13 list it as an explicit audit trigger. `/spec-dev`/`/spec-all` need no separate reminder - they already inherit it. Still apply the same discipline manually whenever driving a multi-phase task outside `/spec-dev` (raw `dev/AGENT_WORKFLOW.md` 5-step execution, ad-hoc multi-step implementation without a formal spec): after finishing a phase or milestone, pause and self-review that phase's diff against the protocol before moving to the next one.
