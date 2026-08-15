---
name: resolved-research-item-may-be-inference
description: A strategic spec's §6 item marked Resolved may have been answered by inference, not measurement - re-measure the premise before planning phases on it, especially screen classifications and resource-variant deltas
metadata:
  type: feedback
---

A `**Статус:** Resolved` on a §6 research item records that someone stopped asking, not that anything
was measured. Before `/spec-tech` turns such an item into phases, re-run the measurement it claims -
it is usually one script and a few minutes, against a whole plan built on a wrong premise.

**Why:** S1549 (2026-08-14). Its §6.2 marked Resolved a split of 16 screens into 9 "recreation is
safe" and 7 "expensive", derived by asking one question - is the state in a ViewModel. Reading the
screens refuted it for 6 of the 16. The question could not see state held in plain fields of
Activity-scoped helper managers (`MainVoiceCaptureManager` deletes an in-progress recording in
`onPause`; `CalculatorEngine` holds the whole expression with no `onSaveInstanceState`), nor work
wired into `onStop`. Separately, comparing the actual XML showed 12 of 89 `layout-land` files were
comment-only copies of their portrait sibling - 5 of them on the ticket's own screen list - so those
screens needed a deletion, not either branch. The ticket's ADR-2 was refuted the same way: it
asserted that no existing handler re-inflates, but `WelcomeActivity` does, via a `ComponentCallbacks`
implementation that a search for Activity `onConfigurationChanged` overrides cannot see.

**How to apply:**

- Treat a spec's screen/file classification as a hypothesis with a cheap test. For layout variants the
  test is a comment-stripped line-set diff of each qualified file against its base; write it as a
  throwaway script under `temp/<Sxxxx>/` and keep it, because later phases re-run it.
- A difference COUNT is not evidence of what the difference IS. In the same ticket an 8-line delta was
  first read as "small, therefore already covered by existing code" and was actually four attributes
  no code touched, while a 4-line delta really was one padding pair. Open the file before choosing a
  remedy.
- When measurement refutes an approved spec, patch the strategic spec first and record WHAT was
  refuted and by what evidence, then plan. A tactical plan that silently contradicts its own strategic
  spec is unreviewable.
- An enumeration that searched for one syntactic form has missed every other form. Before trusting
  "no screen does X", ask which shapes the search could not match - an interface implementation, a
  base-class dispatch, a callback registered on the Application.
- Sibling rule for written invariants generally: [[documented-invariant-is-a-claim]].
