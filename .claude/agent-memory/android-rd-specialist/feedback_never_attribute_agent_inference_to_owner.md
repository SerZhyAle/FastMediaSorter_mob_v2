---
name: never-attribute-agent-inference-to-owner
description: Specs must not state an agent's guess about the owner's motivation as if the owner reported it - the owner disowned an invented "accidental VR exits" complaint on 2026-07-28
metadata:
  type: feedback
---

Never write an inferred user motivation into a spec as established fact. Only text the owner
actually said belongs in the owner's voice; everything else is labelled as a hypothesis, or left out.

**Why:** S1240 §1 claimed the A/X exit binding meant "exploring the controller punishes you -
a plausible direct cause of the 'управление неочевидное' complaints". Nobody said that. §6 then
built a decision ("does exit confirm?") on top of it, S1232 inherited it, and I repeated it back
to the owner as a caveat. The answer was flat: *"я не жаловался на случайные выходы, меня
устраивает текущая кнопка"*. One invented complaint had propagated into three specs and shaped
a design question that should never have existed. The tell was the word "plausible" - a hedge
in the drafting sentence that hardened into a premise two sections later.

**How to apply:**
- In `## 0. Raw capture`, quote verbatim. Below it, any motivation not in that quote is written
  as "hypothesis" / "not owner-reported", or is a research task, never as a finding.
- Before offering the owner a decision, check whether the problem the decision solves was ever
  reported. If it traces back only to my own reasoning, ask whether the problem exists first -
  or drop the decision.
- When the owner denies an inference, retract it **in the spec** with their words, not just in
  chat - the spec is what the next session reads. Add "do not re-derive this" so the same
  inference is not rediscovered from the same code.
- The same care applies to `**Why:**` lines in memory and to `## Last Audit` verdicts.

Related: [[verify-full-evidence]], [[no-scaffolding-as-done]].
