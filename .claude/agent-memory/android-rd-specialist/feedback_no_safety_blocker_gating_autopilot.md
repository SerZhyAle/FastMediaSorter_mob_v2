---
name: feedback-no-safety-blocker-gating-autopilot
description: Don't gate /spec-dev auto-chain on a manufactured "interactive-only" safety blocker for destructive cleanup sweeps — owner accepts autopilot
metadata:
  type: feedback
---

When a tactical plan is already structured safely (detector-first foundation phase, ratchet-down baselines, per-site developer prompts), do NOT add a Pre-Implementation Blocker that forces "interactive per-site review" just because the cleanup is destructive (mass comment removal, catch-block rewrites across ~1k+ files). That blocker blocks the `/spec-tech` → `/spec-dev` auto-chain unnecessarily.

**Why:** On S0383 (neuroslop hygiene, 7 phases) I designed exactly such a plan, then added a "confirm execution mode" blocker and asked via AskUserQuestion. Owner chose "full `/spec-dev` autopilot" — overriding my caution. The per-site prompts in the phase files already encode the judgment guidance; the executor follows them unattended.

**How to apply:** For hygiene/cleanup/refactor sweeps, prefer auto-chaining to `/spec-dev` when there are no *genuine* open research/scope blockers. Reserve real blockers for unresolved §6 research items or undecided scope. If unsure whether destructive autopilot is acceptable, mention it in one line and proceed to auto-chain — don't stop the pipeline on caution alone. Extends [[feedback_no_owner_questions_when_architecture_already_answers]] and [[feedback_research_over_owner_question]]. Note the existing safety rule still holds: scaffolding is not "done" ([[feedback_no_scaffolding_as_done]]).
