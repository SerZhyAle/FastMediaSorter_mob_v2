---
name: subagent-impl-skips-final-phase
description: implementation subagents for multi-phase tooling plans tend to finish core phases but run out of budget before the final docs-catalog-cleanup phase; plan a central pickup
metadata:
  type: feedback
---

When delegating a multi-phase `/spec-dev` implementation to a subagent, the subagent reliably lands the core/logic phases but often exhausts its budget before completing the FINAL `docs-catalog-cleanup` phase (README, header docs, final dev-log placeholder).

**Why:** the core phases consume most of the token/tool budget on real code + self-validation; the final doc phase is low-priority to the subagent and gets truncated mid-run (observed twice in one session: S0313 stopped at phase 04, S0315 stopped mid-phase 02 before writing the output module + entrypoint).

**How to apply:** when orchestrating wave-style `/spec-dev` across parallel subagents, expect to finish the last phase yourself centrally - verify the subagent's claimed phases against the actual files on disk (run the self-tests, list the directory), then write the missing README/entrypoint/output module and tick the final phase. Do NOT trust an INDEX that says "phase N done" without confirming the files exist and the script actually runs. Pairs with [[verify-subagent-build-failures]]. Also: SendMessage to continue a spent subagent is not available as a tool in this harness - either spawn a fresh agent with full state context, or finish it inline (inline is faster when the remainder is well-specified by the phase file).
