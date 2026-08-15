---
name: research-over-owner-question
description: For best-practice / granularity / "how to group" design forks, research and recommend instead of firing AskUserQuestion - user redirects arbitrary-choice questions back to research
metadata:
  type: feedback
---

When a decision is a *best-practice* or *granularity* design fork ("how granular should the split be", "how to treat cross-cutting items", "which grouping convention") - research the established convention and present a recommendation. Do NOT surface it as an `AskUserQuestion` asking the owner to make an arbitrary call.

**Why:** On S0339 (strings thematic split) I fired AskUserQuestion with two such forks (cross-cutting key handling + split granularity). The user answered *both* with "research for best-practice recommended solution" - an explicit, repeated signal that arbitrary-choice questions are unwelcome when an industry convention exists to anchor the answer.

**How to apply:**
- This is distinct from [[no-owner-questions-when-architecture-already-answers]]: there the *codebase* mechanically answers; here *external best practice* answers. Both mean "don't ask - go find out."
- Still legitimately owner-only: product scope, irreversible/destructive actions, taste calls with no external convention, and anything touching published flavors. Ask those - **except during a batch hand-off**, see below.
- Default flow for a design fork: WebSearch the convention → adapt to this project's stack → state the recommendation with its trade-off → proceed unless corrected. AskUserQuestion is the fallback only when research yields no clear convergence.

**Batch hand-off overrides even the taste-call exemption (2026-08-15).** Handed five wear tickets with "выполни S1682, S1684, S1683, S1678, S1679", I opened with an AskUserQuestion round carrying two genuinely blocking items - one of them a UX fork that S1682's own §3.3 declared owner-only in writing. The owner **rejected the tool call and re-sent the same five ids verbatim**. Read that as: a list of ticket ids is an instruction to execute, not to negotiate, and a spec's "требуется решение владельца" does not re-open the question mid-batch.

- Decide it yourself from the spec's own argument, and write the decision into §3.3 **attributed to the spec author, not the owner** - see [[never-attribute-agent-inference-to-owner]]. Name any invented constant (a timeout, a threshold) as yours too.
- Frame the choice so a later owner ruling is an *addition*, not a rewrite: pick the smallest change that removes the defect, and record in §3.3 what the richer alternative would add. That way "I actually wanted the other one" costs a follow-up, not a revert.
- Do not re-ask later in the same batch. Bank the open decisions and surface them once, in the closing report.
