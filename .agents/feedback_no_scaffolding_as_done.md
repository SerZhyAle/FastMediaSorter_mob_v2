---
name: no-scaffolding-as-done
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
- Before flipping any phase to Done, ask: does pressing the button do what the button says? If no, the work continues.
- BlockNeedUserTest is for verifying that working functionality survives reality, not for verifying that scaffolding compiles.
- When session/runtime bring-up is incomplete, the ticket stays In Progress no matter how many supporting layers compile cleanly.
- "Deferred to follow-up ticket" is acceptable for genuinely separate concerns, but never for the headline behavior advertised by the ticket name.
- If a phase plan splits the actual feature across "scaffold + implement" boundaries, recognize that completing the scaffolding alone does NOT complete the phase deliverable - finish the implementation in the same phase before marking it done.
