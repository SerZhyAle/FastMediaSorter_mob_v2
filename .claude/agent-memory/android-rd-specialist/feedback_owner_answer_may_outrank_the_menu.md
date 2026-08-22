---
name: owner-answer-may-outrank-the-menu
description: When offered a multiple-choice placement question, the owner often answers a level above the menu or states a rule that contradicts the option he picked; surface the contradiction back instead of resolving it yourself
metadata:
  type: feedback
---

When you put a multiple-choice UI/placement question to the owner, treat his answer as possibly **larger than the question**. Two distinct shapes, both seen on 2026-08-16 within one session:

- **He answers a level above the menu.** Asked which of three flat placements a new watch settings row should take, he answered "нужна группировка настроек на субэкраны. настроек будет много" - rejecting all three and restructuring the screen. The right move was to park the restructure as its own ticket (S1724) and block the original (S1718) on it, not to pick the closest of the three.
- **He picks an option AND states a rule that contradicts it.** Asked to choose a group cut, he picked "mirror the current sections - 5 groups" while, in the same response, giving the rule "две настройки про одно место - новый раздел; одна - уходит в «другие»". Applying his own rule to the current content yields 4 groups, not 5.

**Why:** in the second case the contradiction was mine to own - my option list never contained the design his rule implied, so he could not have picked it. Guessing placement is the largest correction class he reports (33%), so silently reconciling would have been guessing dressed as obedience. Showing the collision back as one cheap question got a one-word decisive answer ("правило действует сразу - 4 группы"). Both asks were answered immediately and in full, which is the confirmation that asking here was right, not over-asking.

**How to apply:**

- After any owner answer, re-apply his own stated rules to the current content before writing the plan. If rule and chosen option disagree, ask once, with both resulting screens side by side in `preview`, and say plainly that your option list was the thing at fault.
- When his answer outranks the question, park the larger thing via `/spec-draft` and block the original with `Blocker: Sxxxx` - never expand the original ticket to swallow it, and never ship half of the original as consolation.
- Record his words verbatim in §3.3 and quote them; a rule he stated in passing is often the most reusable thing in the whole exchange - the "две настройки / одна в другие" rule answered a §5.3 requirement that no amount of research could have.
- This does not license repeated rounds. Two asks closed this ticket end to end; batch every open decision into one round with concrete previews, then go all the way to the tactical plan without stopping again.

Related: [[spec-tech-ui-placement-refusal]], [[argue-then-obey]], [[never-attribute-agent-inference-to-owner]].
