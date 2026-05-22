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

**How to apply:** A draft paragraph is not "done" until it has been mirrored to all three locales (EN/RU/UK) and matches the policy tone. A polished EN copy without RU/UK mirrors is scaffolding, not delivery - same category as a button that compiles but does nothing. Before reporting a docs task as complete, confirm: (a) all three locale files were touched, (b) the tone checklist in `docs/COMMUNICATION_POLICY.md` §6 passes, (c) the post-change ritual ran. "I drafted the EN version, RU/UK to follow" is `In Progress`, not `Done`. Genuinely separate deliverables (e.g. release-notes RU translation tracked as its own ticket) are fine - but never split the headline locale set of a single doc-update task.
