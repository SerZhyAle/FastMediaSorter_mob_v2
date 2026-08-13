# 02 - How established VR video players map the Touch controllers

Survey run 2026-07-28. Apps covered: Meta Horizon TV / Quest system media player, Bigscreen, Skybox VR, DeoVR, Pigasus, Virtual Desktop.

## The honest headline: there is far less convention here than the ticket assumed

S1240 section 3 assumes a survey will reveal shared conventions to converge on. It mostly does not. Only three things are near-universal:

- **Trigger = point-and-click / select.** Meta's own platform default, and consistent across Skybox and DeoVR.
- **Grip = grab.** Every surveyed app with a spatial screen uses it to grab, move or resize that screen: Bigscreen, Skybox, Virtual Desktop. Meta's guidance says the same, and VRC.Quest.Input.2 tells developers to prefer grip over trigger for pickup.
- **The right controller's Meta button is reserved** and always does system things regardless of the app.

Everything else - what summons the UI, what seeks, what changes track - is **app-invented and mutually inconsistent**. Skybox seeks with thumbstick L/R and changes track with grip+thumbstick; Bigscreen summons with a thumbstick click; Pigasus seeks by swipe and reorients on a 2-second trigger hold; DeoVR (hand tracking) summons with a clap. There is no shared muscle memory to inherit for these.

So "copy what the others do" is only actionable at the level of *roles*, not gestures. Below that level this ticket is choosing, not converging, and the mapping table should say so rather than dress a choice up as a convention.

Users of these apps say the same thing our owner says. Skybox's own forum carries a thread titled "Controls are Skybox's biggest problem"; Pigasus's help text and its observed behaviour disagree about what a double-click does; Meta's own forum has "Quest TV 360 video playback controls confusing". Well-funded apps have not solved this - which argues for a small, explicable mapping plus the S1223 legend, not a rich one.

## Documented mappings worth borrowing

**Skybox VR** - the only app in the set with an official controller reference (skybox.xyz/support/Oculus-Touch-Controller-buttons), and therefore the most useful model:

- Single click of **A/X or Trigger** on empty area - summon the control panel; same click dismisses it.
- **Double-click A/X or Trigger** - play/pause.
- **Thumbstick left/right** - rewind / fast-forward.
- **Hold Grip + thumbstick left/right** - previous / next video.
- **Thumbstick up/down** - zoom by default, switchable to volume in settings.
- Long-press **B/Y** - reset screen position.

Note that Skybox uses A/X for *summon*, not for exit. That matters for us (see the current-bindings correction in artifact 01).

**Bigscreen** - summon the main menu with a **thumbstick click**; grip grabs and repositions the screen, grip+scroll changes its distance, grip+sideways changes its curve. It deliberately avoids overloading the trigger at all.

**Virtual Desktop** - the control bar appears when the controller ray *intersects the window* and hides otherwise. A pointing-based summon, no button at all.

**DeoVR** - fully remappable by the user, which is itself a statement about how contested these bindings are.

## Platform rules - these are not opinions

From Meta's own documentation (developers.meta.com), these bind our hands:

- **Right controller Meta/Oculus button: reserved.** `/input/system/click` is unavailable to apps. Not bindable, not negotiable.
- **Left controller Menu button: explicitly NOT reserved, and explicitly recommended.** VRC.Quest.Input.1: "In-app menus should be activated with the menu button on a gamepad controller or the menu button on the left Touch controller." The 2017 Touch Button Mapping tech note repeats it: "Using the Menu button for menus is strongly recommended." This is the strongest platform steer we have, and none of our current bindings use it.
- **A/X are accept/select; B/Y are back/cancel** - Meta's stated default button semantics.
- **Volume buttons: reserved**, system-handled.
- **Palm-up + pinch (hand tracking): reserved.** VRC.Quest.Input.8 - the app must not act on it and must ignore gesture events while it fires.
- **Recenter**: apps must react to `XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING`, not attempt to bind the system button (VRC.Quest.Functional.9).
- **While the system Universal Menu is open, the app must ignore all controller and hand input** (VRC.Quest.Input.4).

## The screenshot-collision claim needs re-testing

`xr_session.cpp:304-309` records that S0290 Phase 10 moved navigation off trigger/pinch because it "collide[s] with Quest 3 system gestures like screenshot".

Meta documents the screenshot gesture as **hold the Meta button, then pull the trigger**. It rides on top of the already-reserved system button, so a plain trigger pull with no Meta button held should not be able to fire it. On that documentation, a controller trigger binding is not in conflict.

Two readings survive, and this ticket must not pick one from the armchair:

1. The original collision was real but specific to **hand-tracking pinch**, not the controller trigger - plausible, since the code moved "trigger/pinch" as one unit and pinch is the gesture nearer to system gestures.
2. The collision was misdiagnosed at the time, and the real cause of whatever the owner saw was something else.

Either way the owner's new instruction (trigger summons the HUD) is not obviously blocked by it. **Confirm on device before writing this into the mapping as settled** - one Quest session pulling the trigger repeatedly while watching for a screenshot is enough to close it.

## Trigger dual-use: how the field actually solves it

S1240 section 2 flags the real conflict - if the trigger both summons and clicks, the summoning pull must not activate whatever the ray was pointing at. Four mechanisms are observable in the wild:

1. **Nothing to hit while hidden (mode gating).** Skybox: with the panel hidden there are no interactive targets, so a trigger click can only mean "summon". Simple, and it is essentially free for us because our HUD is the only ray target.
2. **Explicit input swallowing.** DeoVR states it outright: "When the player interface is hidden no actions will work apart from the clap. This is to avoid unwanted actions when watching the video." Meta mandates the same shape at OS level in VRC.Quest.Input.4.
3. **Use a different input entirely.** Bigscreen: thumbstick click summons, trigger only ever selects.
4. **Disambiguate by duration or count.** Skybox single vs double click; Pigasus 2-second hold.

**No surveyed app uses dwell** (hover-and-wait) for this. Worth knowing before anyone proposes it.

Mechanisms 1 and 2 are the same idea from two directions and are what the owner's instruction implies: while hidden, the trigger means summon and *only* summon; the press that summons is consumed and never hit-tested.

## Sources

- Skybox official controller reference: https://skybox.xyz/support/Oculus-Touch-Controller-buttons
- Skybox user complaint thread: https://forum.skybox.xyz/d/952-controls-are-skybox-s-biggest-problem-here-s-why
- DeoVR hand-tracking guide (input swallowing quote): https://deovr.com/blog/36-the-complete-guide-to-hand-tracking-in-deovr
- Bigscreen guides: https://www.bigscreen.info/
- Virtual Desktop control-bar behaviour: https://steamcommunity.com/app/382110/discussions/0/1699416432426102145/
- Pigasus FAQ (controls are image diagrams, not text): https://hanginghatstudios.com/pigasus-faq/
- Meta controller design guidance: https://developers.meta.com/horizon/design/controllers/
- VRC.Quest.Input.1 (menu button): https://developers.meta.com/horizon/resources/vrc-quest-input-1/
- Touch button mapping best practices: https://developers.meta.com/horizon/blog/tech-note-touch-button-mapping-best-practices/
- OpenXR actions/bindings on Meta runtime: https://developers.meta.com/horizon/documentation/native/android/mobile-openxr-actions-actionsets-bindings/

Where a binding could not be established from a credible source it is recorded above as not established rather than guessed. Horizon TV and Pigasus are largely undocumented in text - Pigasus publishes its controls only as images.
