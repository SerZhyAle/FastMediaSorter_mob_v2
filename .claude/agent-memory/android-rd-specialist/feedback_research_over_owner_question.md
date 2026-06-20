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
- Still legitimately owner-only: product scope, irreversible/destructive actions, taste calls with no external convention, and anything touching published flavors. Ask those.
- Default flow for a design fork: WebSearch the convention → adapt to this project's stack → state the recommendation with its trade-off → proceed unless corrected. AskUserQuestion is the fallback only when research yields no clear convergence.
