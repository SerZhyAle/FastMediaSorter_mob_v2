---
name: feedback_no_scaffolding_as_done
description: Never mark a feature ticket "done" or invite device testing when the core user-visible behavior is scaffolded only - if the button doesn't do the thing it advertises, the work isn't done
metadata:
  type: feedback
---

A feature ticket is "done" only when the headline user-visible behavior actually works end-to-end. Scaffolding (instance acquired, contract wired, classes created, builds pass) is a milestone, NOT a deliverable. If the button advertises "open immersive image" but in fact only shows a "cannot start" toast, do not:
- mark phases "Done" with green checkmarks
- move spec status to BlockNeedUserTest
- propose the user run device verification
- summarize as if the feature exists

Why: 2026-05-19 S0249 Phase 06. I closed all 6 phases and proposed Quest 3 device-test for a Test Immersive button that, when pressed, only produced a "cannot start VR" toast because xrCreateSession + swapchain + composition layer + frame loop were not implemented. User reaction: "Yeah, this implementation is just nothing. Until the button is ready that actually launches the test, don't even suggest I run this on hardware. What, I should test a button? Another button, that it gets pressed?" Justified frustration - testing scaffolding wastes their time and confuses the spec lifecycle.

How to apply:
- Before flipping any phase to `[x]` in a tactical spec, ask: does pressing the button I just wired do what the button's label promises? If the answer is "it shows a placeholder toast" or "it logs but does nothing visible", the work continues.
- "It compiles" is not done. "All unit tests pass" is not done if the user-facing flow never executes the new code path. The headline behaviour must actually run.
- Do not flip the spec to `BlockNeedUserTest` until the device-test would meaningfully exercise the feature - testing scaffolding (a button that does nothing) wastes the user's hardware time.
- "Deferred to follow-up ticket" is acceptable for genuinely separate concerns (logging polish, error message wording), but never for the headline behaviour advertised by the ticket name.
- If a phase plan splits the actual feature across "scaffold + implement" boundaries, recognize that completing the scaffolding alone does NOT complete the phase deliverable - finish the implementation in the same phase before marking it done.
