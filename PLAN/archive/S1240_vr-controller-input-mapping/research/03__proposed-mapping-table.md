# 03 - The decided input -> action mapping

**Revised 2026-07-28 after the owner answered every open question.** The first version of this file
proposed a mapping; this version records the one that was chosen. Rows that were a *choice* say so,
and where the owner overrode the low-risk default that is named, because the default had a reason
and the reason is now traded away rather than disproved.

Justification per row is one of: a convention found in artifact 02, a platform rule from Meta's
documentation, or an explicit owner decision (spec section 6).

## The table

| Input | Action | Justification |
| --- | --- | --- |
| **Trigger** (either hand), HUD hidden | Summon the HUD. The press is consumed - no hit-test, no ray activation | Owner decision 3. Consumption follows DeoVR's stated rule and Meta VRC.Quest.Input.4 |
| **Trigger** (either hand), HUD visible | Ray click on the HUD widget under the ray. No effect on visibility | Owner decision 3, and near-universal: trigger = select. Already the current behaviour |
| **Grip** (either hand), alone | Grab and reposition the HUD; recenter | Near-universal: grip = grab (Bigscreen, Skybox, Virtual Desktop, Meta guidance). Already the current behaviour - keep |
| **Grip + Thumbstick X** (either hand) | Previous / next media | Owner decision 1. Displaced here from bare Thumbstick X; matches how Skybox frees that axis |
| **Thumbstick X** (either hand) | Seek within the current item | Owner decision 1, overriding the low-risk default. Matches Skybox |
| **Thumbstick Y** (either hand) | Image zoom | Existing behaviour, unchanged. Skybox agrees (zoom on this axis) |
| **Menu button** (left only) | Open settings, rendered as a HUD panel - not the flat settings Activity | Owner decision 4, on top of the platform rule: VRC.Quest.Input.1 and Meta's tech note both name this button for in-app menus. Currently unbound |
| **A / X** | Exit immersive immediately, no confirmation | Owner decision 5 - existing behaviour explicitly kept. **Diverges** from Meta's accept semantics, deliberately |
| **B / Y** | Unassigned - stays free | Owner decision 5 declined the exit move. Do not bind speculatively |
| **Thumbstick click** | Unassigned - stays free | Bigscreen summons its menu here, but the owner chose the trigger. Keep free rather than duplicate |
| Right controller **Meta button** | Nothing - reserved | Platform rule, not bindable |

## Retracted: "the correction that matters most"

The first version of this file argued at length that binding exit to A/X is *inverted* against Meta's
accept/cancel semantics, that "exploring the controller punishes you", and that this was a plausible
contributor to the "управление неочевидное" reports. It proposed moving exit to B/Y.

**All of that is withdrawn.** Asked directly on 2026-07-28 the owner answered: *"я не жаловался на
случайные выходы, меня устраивает текущая кнопка, которая будет продублирована кнопкой на HUD"*.
There is no accidental-exit problem; the argument was an agent inference presented as a finding.

What survives from it: the divergence from Meta's semantics is real and is now a **deliberate,
recorded choice**. Do not re-raise it as a defect in a later audit, and do not cite this artifact as
evidence for an accidental-exit problem.

What replaces the B/Y proposal: S1232's "two exit inputs" requirement is satisfied by the on-strip
exit button **duplicating** the A/X binding. Physical button and HUD button call one exit path.

## Consequences the table does not show

- **Two seek entry points.** Thumbstick X and the S1239 HUD seek bar both scrub. They must drive a
  single seek path so a gesture begun on one and finished on the other cannot fight itself, and the
  stick must honour S1239's seek-on-release contract rather than issuing a seek per axis sample.
- **The file step got more expensive.** Grip+thumbstick is a two-hand gesture; stepping between
  files is the core loop of a media *sorter*, which is exactly why S0290 Phase 10 put it on the bare
  axis. That trade is knowingly made. A later complaint about it is a re-decision, not a regression.
- **No duration, no seek.** Streams and any source without a known duration must degrade on the
  thumbstick X axis rather than divide by zero - the same requirement S1239 carries for its bar.
  Streams cannot enter immersive today (S1218), so this is a guard, not a feature.
- **Summon needs something to summon from.** The HUD is a one-way latch today
  (`g.hudContentUploaded`, `xr_session.cpp:251`): once content arrives the quad is always drawn.
  A summon binding is meaningless until S1232 delivers a genuinely hidden state, and a summon-only
  trigger without S1232's hide button is a one-way door. The two ship together.
- **The menu button opens a second panel kind.** Decision 4 asks for settings *in the headset*, not
  a trampoline back to the flat Activity. That means a second HUD panel sharing the media strip's
  quad/render path - materially more work than binding a button. Which settings appear there is not
  settled and is not this ticket's to settle.

## Hand tracking degrades, and that needs an answer

The second suggested profile is `/interaction_profiles/ext/hand_interaction_ext`, which offers
pinch, grasp and aim only. **No face buttons and no menu button exist in hand-tracking mode**, so
every row above resting on A/X or Menu vanishes when the controllers are put down.

Minimum viable answer: pinch = trigger (summon / select), grasp = grip (grab). Exit and settings must
then be reachable from the HUD strip itself. S1232 goal 2 already requires the exit button; decision
4's settings panel needs an on-strip entry point for the same reason. This is the second independent
argument that the strip's buttons are not optional decoration.

DeoVR solves the same problem with a clap gesture and finger-role assignment - an invention, not a
convention, and out of scope here.

## Still unproven, and it gates the headline binding

`xr_session.cpp:304-309` justifies keeping navigation off the trigger by a collision with the Quest 3
screenshot gesture. Meta documents that gesture as *Meta button held, then trigger* - it rides on the
reserved system button, so a plain trigger pull should not fire it. Either the original collision was
specific to the hand-tracking pinch (the code moved "trigger/pinch" as one unit) or it was
misdiagnosed.

Not resolvable from the armchair. One Quest session pulling the trigger while watching for a
screenshot settles it, and it must be settled before "trigger summons the HUD" is promised to the
user. This is an owner device-test, not an emulator check.

## Sequencing

1. **S1232** - hidden HUD state plus the hide and exit buttons. Nothing else can land first.
2. **S1240** implementation - the bindings in the table above.
3. **S1223** - the legend. The mapping only becomes discoverable when it exists; a richer mapping
   without it moves the problem rather than solving it. Its text must be authored from the table
   above, not from the pre-decision bindings.
