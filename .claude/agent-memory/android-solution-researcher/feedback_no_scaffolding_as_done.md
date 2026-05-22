---
name: no-scaffolding-as-done
description: Never mark a feature ticket "done" or invite device testing when the core user-visible behavior is scaffolded only - if the button doesn't do the thing it advertises, the work isn't done
metadata:
  type: feedback
---

A feature ticket is "done" only when the headline user-visible behavior actually works end-to-end. Scaffolding (instance acquired, contract wired, classes created, builds pass) is a milestone, NOT a deliverable. If the button advertises "open immersive image" but in fact only shows a "cannot start" toast, do not in the research report:
- describe the feature as "implemented"
- list it as a working capability in the Current Architecture section
- omit the gap from the Risks Identified section

**Why:** 2026-05-19 S0249 Phase 06. The writer agent closed all 6 phases and proposed Quest 3 device-test for a Test Immersive button that, when pressed, only produced a "cannot start VR" toast because xrCreateSession + swapchain + composition layer + frame loop were not implemented. Owner reaction: "Yeah, this implementation is just nothing. Until the button is ready that actually launches the test, don't even suggest I run this on hardware." Justified frustration - presenting scaffolding as a finished capability wastes everyone's time and corrupts the spec lifecycle.

**How to apply:**
- When the research scope includes a previously-closed ticket, do NOT take "Implemented" / "Verified" status at face value. Read the headline behaviour in code; if it short-circuits to a toast or stub, report the gap explicitly under "Risks Identified" with evidence (file:line).
- Distinguish in the report between "contract exists" and "behavior works". The Current Architecture table is for what is wired; a Risk row is the right place for "wired but inert".
- Recognise the pattern "scaffold + implement split across phases" - if the second half is not on disk, the headline capability is not yet delivered, regardless of phase-Done checkmarks in the spec file.
- If a referenced ticket is in status `BlockNeedUserTest` but the headline behaviour is clearly scaffolded only, flag it in the report's Open Questions as "Status may be premature; verify with owner".
