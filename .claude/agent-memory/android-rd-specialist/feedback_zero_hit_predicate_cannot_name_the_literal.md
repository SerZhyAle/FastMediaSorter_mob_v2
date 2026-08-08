---
name: zero-hit-predicate-cannot-name-the-literal
description: A "grep returns zero hits for X" predicate fails if the replacement text quotes X - describe the shape, never the banned literal
type: feedback
---

When a step's Verification says `Grep - <Literal> returns zero hits in <file>`, the rewritten content of that same file must not contain `<Literal>` anywhere - including inside the new verification predicate, a Step Log entry, or a rationale sentence explaining what was removed. Describe the forbidden shape instead: "no feature-local chart class is created anywhere in `app_v2/src`".

**Why:** hit twice in one session on S1446. Step 02.1 demanded `SignalChartView` and `attrs_signal_chart` reach zero hits in S1433's phase 05; the rewrite scored 1 hit each, purely because the replacement predicate I wrote quoted both names. The same trap sits in every phase file's `Grep for TODO(phase-NN) returns zero hits` criterion, which matches itself - so a naive repo grep reports "56 files with leftovers" when the real count is zero.

**How to apply:** before running a zero-hit predicate, check whether the grep is matching the criterion line or the Step Log rather than real content, and scope the grep to the region that matters. When authoring one, write the predicate so it cannot match itself. See [[spec-tech-plan-quality]] and [[documented-invariant-is-a-claim]].
