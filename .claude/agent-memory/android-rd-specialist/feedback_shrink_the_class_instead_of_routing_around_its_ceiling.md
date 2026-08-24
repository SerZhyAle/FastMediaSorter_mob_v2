---
name: shrink-the-class-instead-of-routing-around-its-ceiling
description: When a detekt size ceiling blocks one more method, the owner's answer is to extract duplicated boilerplate so the class shrinks - not to persist state elsewhere or drop the feature
metadata:
  type: feedback
---

When a class sits on a detekt size ceiling (`LargeClass` / `TooManyFunctions`) and the work genuinely
needs one more method there, extract the boilerplate its existing methods repeat so the class ends up
**smaller** than it started. Do not route around the ceiling by moving the state somewhere else, and do
not drop the feature.

**Why:** ruled by the owner on 2026-08-24 (S1988). A debug-only lens-pinning switch needed one rebind
entry point in `CameraCaptureSessionManager`. The class was detekt-clean at 1102 lines with zero baseline
entries, and one added method produced a new `LargeClass` finding, so the first attempt was built end to
end and reverted. Three options were put to the owner: (1) extract the rebind boilerplate that seven call
sites repeated verbatim, (2) persist the flag in prefs and seed it at receiver registration, (3) drop the
switch. The owner picked (1) without discussion. It was the only option that made the class smaller, so it
*satisfied* the size rule rather than arguing with it - and the two rejected options each bought a real
defect: (2) races the first bind and produces a confident wrong measurement, (3) leaves the ticket stuck
at diagnosis. The extraction took the class to 1089 lines before the new code went in.

**How to apply:** when a gate refuses a new method on size, first look for a block repeated at three or
more call sites in that same class - the provider/preview pair, the null check, the `runCatching`, the
same log line. Collapsing it usually frees more than the new method costs. Report the before/after line
and function counts as evidence. Present the alternatives with their costs and let the owner choose, but
lead with this one. Note that after such a rescue the class is typically one function from the ceiling
again, so say so explicitly - the *next* primitive belongs in a helper.

Related: [[feedback_moving_code_resurfaces_ratchet_findings]],
[[feedback_write_detekt_clean_first_time]], [[feedback_argue_then_obey]].
