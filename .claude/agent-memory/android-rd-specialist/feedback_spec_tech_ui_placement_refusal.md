---
name: spec-tech-ui-placement-refusal
description: /spec-tech refuses to write any phase touching ui/** or res/layout* unless the strategic spec carries an owner-recorded placement decision; passing the §3.3 owner-inputs gate does NOT satisfy it, and in loop mode the only sanctioned escape is BlockQuestions.
metadata:
  type: feedback
---

**Rule.** `/spec-tech` step 5.5 fails the plan self-check for any phase whose `Files Touched` names `res/layout*`, an `Activity`/`Fragment`/`*View`/`ui/**` class, or a settings surface, unless the strategic spec carries a placement decision that is either a `/ui-clarify` record or **the owner's own ruling quoted verbatim**. There is no "decide during implementation" path. In a `/spec-next` or `/spec-do` loop, asking is forbidden, so the escape is `update.ps1 -Status BlockQuestions -StatusNote '<the exact decisions needed>'`.

**Why.** Two distinct gates guard placement and they are easy to confuse:

- `check-owner-inputs.ps1` runs at Draft -> Approved and only checks that the §3.3 bullets `/spec` emitted are *filled*. It cannot tell whose judgement filled them.
- `/spec-tech` step 5.5 asks the harder question - is the placement decision *the owner's*.

S1436 (2026-08-06) passed the first with 10/10 fields and was still correctly refused by the second, because every §3.3 value was my own inference written during auto-approval. Guessed placement is the largest correction class the owner reports (33% of all corrections) against one `/ui-clarify` invocation in a month, which is exactly why the refusal is mechanical rather than advisory.

**How to apply.**

- Before planning any UI-touching ticket, look for the owner's actual words - the §0 captured text, a quoted ruling in §3.1/§3.3, or a `/ui-clarify` record. A filled §3.3 you wrote yourself is not evidence.
- A contrast worth keeping: S1426's §3.3 carries a concrete row contract plus `Owner sign-off: 2026-08-05`, so its UI phases plan freely. S1436's §3.3 carries the same field names filled by inference, so it does not.
- When blocking, make the questions answerable in a minute - name the concrete options and say which is safest and why. A vague block wastes the owner's turn as surely as a wrong guess.
- Never write `Owner sign-off: <today>` on a spec the owner never reviewed. State plainly that the scope was auto-approved by the pipeline - see [[never-attribute-agent-inference-to-owner]].
- Behaviour that merely *changes* inside an existing surface (swapping a hardcoded string for a shared one) is not a placement decision. The refusal is about where something goes and whether an existing control survives, not about every edit under `ui/**`.
