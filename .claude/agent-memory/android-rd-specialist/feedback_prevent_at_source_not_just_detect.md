---
name: prevent-at-source-not-just-detect
description: When a quality issue is found/gated, also update authoring rules (CLAUDE.md + code-gen skills/agents) so it stops being produced, not just caught
metadata:
  type: feedback
---

When you build a detector/ratchet gate for a code-quality problem, the owner also wants the *authoring* rules changed so the pattern is not produced in the first place - prevention at the source, not only detection after the fact.

**Why:** During S0383 (neuroslop hygiene) the owner explicitly asked to "change the rules and skills so we avoid producing the neuroslop you found." Catching it with `assert-neuroslop.ps1` was not enough; they wanted the four patterns (trivial comments, swallowing catches, hardcoded layout colors, lifecycle-unsafe Flow collects) blocked at generation time.

**How to apply:** After landing a quality gate, add the matching DON'T rule to `CLAUDE.md` (the always-loaded, load-bearing place - e.g. Rule 20) AND reinforce it in the code-generating skills/agents (`/spec-dev`, `/spec-tech`, `/quick`, `/skill-fix`, `android-kotlin-developer`, `android-rd-specialist`), cross-referencing the gate. Keep the skill/agent lines short pointers to the canonical CLAUDE.md rule; put the substance once in CLAUDE.md. Pairs with the ratchet-gate idiom in [[reference-ticket-log-gate]] and the build pattern in [[build-gotchas]].
