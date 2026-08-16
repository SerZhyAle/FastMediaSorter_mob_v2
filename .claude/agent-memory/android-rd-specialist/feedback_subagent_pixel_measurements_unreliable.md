---
name: subagent-pixel-measurements-unreliable
description: A device-operator subagent's reported pixel extents from a screenshot can be flatly wrong - read the image yourself when the measurement is the evidence
metadata:
  type: feedback
---

When a layout ticket's proof is "where does the content sit in the frame", read the screenshot yourself.
A subagent asked to report pixel extents will return confident numbers that can be wrong by the entire
size of the change.

**Why:** On S1678 (2026-08-16, Wear round-display insets) the device-operator agent reported the home
screen chips spanning `x=20..460` after the fix - identical to before, which would have meant the inset
never applied. Reading the same file showed `x≈48..432`, exactly the 22.6dp the probe logged. Acting on
the report would have meant debugging a working fix. The agent also read a real side effect - a filename
truncating one word earlier - as a curiosity, when it was the narrowing itself.

**How to apply:** Delegate the *driving* (taps, swipes, launching, moving files) - that part is reliable
and saves context. Do not delegate the *measurement* on a layout ticket. Ask the agent for the artifact
paths and a factual description of what is on screen, then `Read` the before/after pair yourself for
anything numeric. Cross-check against an independent signal where one exists: a `Timber.d` probe that
prints the computed value turns a pixel argument into arithmetic. See
[[verify-subagent-build-failures]].
