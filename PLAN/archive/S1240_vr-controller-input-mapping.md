# S1240 - Define the full VR controller mapping from how established VR video players do it

**Status:** Archived
**Priority:** 80

## 0. Raw capture

Owner, 2026-07-28:

> - HUD показывать когда я стреляю тригером джойстика (как бы вызываю)
> - на джойстиках помимо двух "ручек" есть ещё 4 кнопки , кнопка меню, 4 курка. Изучить как устроено управление в других видеопроигрывателях и повторить его у нас

## 1. Why this exists

Four separate complaints in one evening - "управление неочевидное", "как вызвать HUD", "как вызвать HUD" again, and the rejection of the collapse pill - are all the same problem seen from different angles: the immersive surface uses a fraction of the controller and has no model behind the choices it did make.

What is bound today:

- **Trigger** - ray click on the HUD quad.
- **Thumbstick X**, either hand - previous / next media.
- **Thumbstick Y**, either hand - image zoom.
- **Grip** - drag / recenter handling in `xr_hud_world.cpp`.
- **X (left) and A (right)** - instant, unconfirmed exit from the immersive session.

**Correction, 2026-07-28.** The last row was missing here and is also denied outright by S1232 section 3. Both were written from memory; `xr_input.cpp` binds `exit_click` to `/user/hand/left/input/x/click` and `/user/hand/right/input/a/click` and calls `xr_session_request_exit()` on press. Read the evidence in `research/01__current-bindings-ground-truth.md`.

Meta's own semantics make **A/X the accept buttons and B/Y back/cancel**, so the app ends the session on the two buttons a user presses when they mean "yes".

**Retracted 2026-07-28 (owner statement).** The sentence that stood here - that exploring the controller punishes you and that this is a plausible direct cause of the "управление неочевидное" complaints - was an agent inference, never an owner report. Asked directly, the owner answered: *"я не жаловался на случайные выходы, меня устраивает текущая кнопка"*. No accidental-exit problem exists. Do not re-derive one from the A/X divergence, and do not cite this ticket as evidence for one.

Genuinely unbound: **B**, **Y**, the left **menu button**, and thumbstick click.

## 2. Owner's immediate instruction

**Pulling the trigger summons the HUD.** This supersedes the "B button" proposal recorded in **S1232** section 2 - amend that ticket rather than implementing both.

Note the conflict to resolve: the trigger is currently the ray *click*. If a trigger pull also summons, then the first pull after the strip is hidden must summon **without** also activating whatever the ray happens to be pointing at, or the user will hit a control by accident every time they call up the HUD.

## 3. The research half - do this first

Study how established VR video players map their controllers, then converge on their conventions instead of inventing:

- Meta's own Quest media player / Horizon TV, Bigscreen, Skybox VR, DeoVR, Pigasus, Virtual Desktop's media mode.
- For each: what summons the UI, what dismisses it, what exits, what seeks, what changes tracks, what the menu button does, whether the two hands differ.
- Record which conventions are near-universal (those are the ones users arrive with) versus app-specific.

Deliverable: a table of "input -> action" for our player, with each row justified by either a convention found in the survey or an explicit owner decision. Write it to `PLAN/S1240_vr-controller-input-mapping/research/` before any code.

**Done, 2026-07-28.** Three artifacts:

- `research/01__current-bindings-ground-truth.md` - the full action set as it exists, read from source.
- `research/02__how-established-vr-players-map-controllers.md` - the survey.
- `research/03__proposed-mapping-table.md` - the deliverable table.

The survey's finding is not the one this section expected, and it is worth stating plainly: **there is very little convention to converge on.** Only three things are near-universal - trigger = select, grip = grab, and the right controller's Meta button is reserved. What summons the UI, what seeks, what changes track is app-invented and mutually inconsistent across all six players surveyed. Below the level of roles, this ticket is *choosing*, not converging, and the table says so per row rather than dressing choices up as conventions.

Users of those apps report the same confusion the owner does - Skybox's forum has "Controls are Skybox's biggest problem"; Pigasus's own help text contradicts its observed behaviour. Well-resourced apps have not solved this. That argues for a small, explicable mapping plus the S1223 legend, not a rich one.

Two platform facts that constrain everything, both from Meta's own documentation:

- The **left menu button is explicitly recommended** for in-app menus (VRC.Quest.Input.1) and is currently unbound here - the single clearest miss.
- The right controller's Meta button is reserved and not bindable; so are the volume buttons and the palm-up hand gesture.

**The screenshot-collision rationale needs re-testing.** `xr_session.cpp:304-309` justifies moving navigation off the trigger by a collision with the Quest 3 screenshot gesture. Meta documents that gesture as *Meta button held, then trigger* - it rides on the reserved system button, so a plain trigger pull should not fire it. Either the original collision was specific to hand-tracking pinch (the code moved "trigger/pinch" as one unit), or it was misdiagnosed. Not resolvable from the armchair: one Quest session pulling the trigger while watching for a screenshot settles it, and it must be settled before "trigger summons the HUD" is promised to the user.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1232 (must land first - supplies the hidden HUD state and the hide button), S1239 (shares the seek path with thumbstick X), S1223 (legend authored from this mapping), S1238 (contests the same strip row), S1133 (its contextual-action decision belongs in this mapping)
- **UI placement:** no flat-app UI. Two in-headset surfaces: the existing media strip (S1232 owns its buttons) and a new settings HUD panel opened by the left menu button (decision 4), sharing the media strip's quad and render path rather than trampolining out to the flat settings Activity.
- **Flavor scope:** `vr` only. Every binding lives in `src/vr/` native code (`xr_input.cpp`, `xr_session.cpp`) plus its Kotlin XR helpers; no `src/main/` behaviour changes.
- **Input model:** controllers first. Hand tracking loses A/X and the menu button entirely, so exit and settings must also be reachable from the strip - see `research/03` "Hand tracking degrades".
- **Verification:** Quest 3 only. The screenshot-gesture collision that gates "trigger summons the HUD" cannot be settled on an emulator (`research/03` "Still unproven").

## 4. The implementation half

Once the mapping is agreed:

- Add the missing OpenXR actions in `xr_input.cpp` with suggested bindings per interaction profile. Do not hard-code Oculus Touch paths only - the `vr` flavor targets Store distribution beyond Quest, so the profile list matters.
- Edge detection per input, following the existing `g_prevStickState` / `g_prevTriggerClicked` pattern - presses, not held states.
- One JNI callback surface for button events rather than one callback per button.

## 5. Constraint that shapes everything

Quest 3 system gestures already claim some inputs - S0290 Phase 10 moved navigation off the trigger/pinch specifically because it collided with the system screenshot gesture (`xr_session.cpp:305`). Any proposed mapping must be checked against what the runtime intercepts before it is promised to the user.

## 6. Owner decisions (2026-07-28) - these were the blocking questions

Product choices, not derivable from the codebase or the survey. `research/03` recorded a low-risk default for each; where the owner overrode the default that is called out below, because the default was chosen for a reason that no longer applies.

1. **Thumbstick left/right seeks.** Owner overrode the low-risk default: the axis matches Skybox and scrubs the timeline; prev/next media moves onto grip+thumbstick. Consequences to plan for:
   - Seeking now has two entry points - this axis and the **S1239** HUD bar. They must share one seek path, not two, so a scrub started on the stick and finished on the bar does not fight itself.
   - The S0290 Phase 10 rationale for media-on-X (file-to-file is the higher-frequency action in a *sorter*) is knowingly traded away. Grip+thumbstick is a two-hand gesture, so the file step gets measurably more expensive - watch for a complaint about it and do not treat one as a new bug.
   - Streams and any source without a known duration must degrade rather than crash on this axis (same constraint as S1239 goal 3).
2. **Exit does not confirm.** Current behaviour stands: a single press leaves immersive mode immediately. There is no accidental-exit problem to mitigate - see the retraction in section 1. A confirm dialog would tax every deliberate exit to solve a complaint nobody made.
3. **The trigger summons only; it never dismisses.** Owner picked summon-only over the pure toggle, and gave the reason: *"я могу курком вызвать панель, а потом курок стреляет на элементы"*. The trigger has two jobs that are separated by HUD state, not by press count - a toggle would have collided the two, because every click aimed at a HUD row would also have been a dismiss.
   - **HUD hidden:** a trigger pull summons the strip and nothing else. It must not activate whatever the ray happened to be pointing at.
   - **HUD visible:** the trigger is the ray click, exactly as today. It has no effect on visibility.
   - **S1232's hide button is therefore mandatory**, not optional - it is the only way to put the strip away. Ship the two together; a summon-only trigger without a hide button is a one-way door.
   - No debounce or double-press scheme is wanted. The state machine above is the whole rule; do not add a "long pull dismisses" shortcut on top of it.
4. **The menu button opens the settings surface, rendered as a HUD panel.** Owner refinement beyond the two offered options: not the existing 2D settings Activity and not the media strip, but a HUD-native copy of settings shown in the headset. Plan for a second HUD panel kind alongside the media strip, sharing the same quad/render path, rather than a trampoline out of immersive mode into the phone UI. Scope: which settings appear there is a separate decision, not settled here.
5. **Exit stays on A/X.** Owner declined the proposal to move it to B/Y: *"меня устраивает текущая кнопка, которая будет продублирована кнопкой на HUD"*. Meta's accept/cancel semantics stay inverted in this app; the divergence is deliberate and must not be re-raised as a finding.
   - The second exit input S1232 goal 2 asks for is **the on-strip exit button**, duplicating the A/X binding rather than replacing or relocating it. Physical button and HUD button call the same exit path.
   - B, Y, the left menu button and thumbstick click stay free for other uses; the menu button is claimed by decision 4.

## 7. How the work splits, and what is blocked

Not all of this ticket is blocked. The split below is deliberate, so the next session does not either stall the whole ticket or start the one part that cannot be started.

**Delivered by S1232, not here.** The trigger-summons binding. It has nothing to summon *from* until S1232 delivers a genuinely hidden HUD state - today the strip is always present or collapsed to the pill S1232 removes. Decision 3 additionally requires the summon binding and S1232's hide button to ship in the same change, because a summon-only trigger with no way to dismiss is a one-way door. Implement the binding inside S1232's phases and reference decision 3 there; do not carry a duplicate of it in this ticket's plan.

**Split out to S1271 (2026-07-29).** Decision 4's settings HUD panel is now its own ticket. Its own text says which settings appear there is unsettled, so it is owner-gated where the rest of this ticket is not; keeping the two together would have parked a fully specified change behind an unanswered question. **S1271** carries the menu-button binding with it.

**This ticket's own scope, none of it blocked:**

- Thumbstick X becomes a seek axis; prev/next media moves to grip + thumbstick X (decision 1).
- The seek path itself, which does not exist yet - see 7.1.
- The guard for sources with no duration on the seek axis.
- Nothing for decisions 2 and 5: they preserve current behaviour. Nothing for decision 3 - S1232 shipped it.

### 7.1 The seek path has to be built here

Measured, not assumed: `HudPlaybackController` exposes `setVolume`, `play`, `pause`, `next`, `prev` and **nothing else**. The only `seekTo` anywhere in the immersive path is `VrDiagnosticPlaybackController:143`, restoring a snapshot position on entry. There is no seek capability in the headset at all.

So this ticket does not "move seeking onto the stick" - it *introduces seeking*, and the stick is the first entry point. **S1239**'s HUD bar becomes the second consumer of the same path, which is exactly the single-seek-path requirement recorded above, now with an owner rather than a hope.

Step size is 10 s per deflection, matching what `docs/VR_CONTROLS.md` has documented as the target design since before this ticket existed. Not an invented number.

### 7.2 Grip is already taken, and the collision is real

Decision 1 puts prev/next on grip + thumbstick X. But grip is not free: `xr_hud_process_rays` repositions the HUD quad on **any** grip-down, unconditionally, and a grip double-click recenters it. So the naive reading of decision 1 would drag the panel and change the file at the same time.

Resolved from the architecture, not from the owner: **a grip that is used as a modifier is not a drag.** When a thumbstick deflection fires while grip is held, that hand's grip is latched as a modifier for the remainder of the hold, and its drag is suppressed. Rationale - the two gestures are distinguishable in intent and only one can be meant; Skybox, whose convention decision 1 adopts, has no draggable panel on grip, so the convention arrives without an answer for this and one has to be chosen.

Note that S1232 already removes half the problem: while the HUD is hidden, `xr_hud_process_rays` returns early, so there is no drag to collide with.

### 7.3 Implementation state (2026-07-29)

Native (`app_v2/src/vr/cpp/`):

- `xr_input.h` - `HandInputState.gripIsModifier`, the per-hand latch from 7.2.
- `xr_input.cpp` - the latch clears when the grip is released, so it lives exactly as long as the hold that set it.
- `xr_session.cpp` - the thumbstick-X deflection block now branches on grip: bare axis emits seek forward/back (events 4/5), grip-held emits next/prev (1/2) and latches the modifier. Event codes 1-5 are named constants instead of bare literals.
- `xr_hud_world.cpp` - the drag branch in `xr_hud_process_rays` skips a hand whose grip is latched as a modifier, so `dragging` also stays false for it.

Kotlin (`app_v2/src/vr/java/`):

- `HudPlaybackController.seekBy(deltaMs)` - new, main-thread marshalled like every other method here, clamped to `[0, duration]`, and a no-op with a probe line when `duration` is `C.TIME_UNSET` or non-positive. This is the single seek path S1239 must consume rather than duplicate.
- `DiagnosticXrActivity` - handles events 4/5 **before** the `mediaPlaylist.isEmpty()` guard, because seeking concerns the item already playing rather than the playlist; `SEEK_STEP_MS` is 10 s, the granularity `docs/VR_CONTROLS.md` already documented.

Docs: `VR_CONTROLS.md` + RU/UK now describe the seek axis, the grip modifier and the no-duration behaviour.

Split out: the settings HUD panel and its menu-button binding -> **S1271**.

**Cross-ticket constraints that outlive this split:**

- Seek has two entry points (this axis and the S1239 bar) and must have one seek path.
- The mapping is only discoverable once the **S1223** legend exists. A richer mapping without it moves the problem rather than solving it: the complaint was never that there were too few bindings, it was that the bindings could not be found. S1223's legend text must be authored from `research/03`, not from the pre-decision bindings.
- The screenshot-gesture collision that gates trigger-summons is still unproven and needs one Quest session (`research/03` "Still unproven"). That is S1232's gate, since S1232 now owns the binding.

## 8. Related

- **S1232** - summon/exit actions; its B-button proposal is superseded by section 2 here. Its two-exit-buttons requirement stands.
- **S1223** - discoverability. A richer mapping raises the value of the one-time legend proposed there from useful to necessary.
- **S1133** - controller grid navigation in the immersive browser, blocked on **S1132** and on an owner decision about contextual action sets. That decision belongs in this ticket's mapping table.
