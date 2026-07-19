---
name: clarify-task-when-framing-unclear
description: Unsure what a task is asking (framing/goal/scope) -> clarify with the owner before implementing; do not guess the interpretation
type: feedback
---

When genuinely unsure about **what a task is asking** - its framing, goal, scope boundary, or which of several valid interpretations is intended - clarify with the owner before implementing, instead of guessing.

**Why:** Owner instruction (2026-07-19, "запиши себе уточнять задачу, если не уверен в её постановке"). Guessing the wrong reading of an ambiguous task statement wastes a full research/impl cycle and ships the wrong deliverable. Cheap to ask up front, expensive to redo.

**How to apply:**
- Separate two kinds of uncertainty:
  - **Implementation** uncertainty (which class, pattern, path, flavor) that the codebase / docs / architecture / an existing spec already answers -> resolve it yourself, do NOT ask. See [[no-owner-questions-when-architecture-already-answers]] and [[research-over-owner-question]].
  - **Framing** uncertainty (what outcome is wanted, what the task even means, scope in/out, two conflicting valid readings) that no artifact resolves -> clarify with the owner.
- A spec that explicitly delegates the decision ("decide which and apply", "owner leaves it to the implementer") is NOT framing uncertainty - it is an instruction to choose; proceed and record the rationale (e.g. S1110 enumerated three size-format options and said "decide and apply").
- In `/spec-next` loop mode the standing contract is "never ask mid-loop; defer human-gated items to the final report". Honour it: surface a genuine framing-uncertainty in the round verdict / final report, or park it via `/spec-draft` with the open question - do not interrupt the loop, and do not silently guess.
- Interactive turns: use `AskUserQuestion` (multiple-choice, recommended default first). Autonomous loop turns: defer / park.
