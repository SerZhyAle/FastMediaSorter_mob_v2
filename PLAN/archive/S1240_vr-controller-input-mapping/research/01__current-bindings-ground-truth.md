# 01 - What is actually bound today (read from source, 2026-07-28)

Read directly from `app_v2/src/vr/cpp/xr_input.cpp`, `xr_session.cpp`, `xr_hud_world.cpp`. This corrects two inventories that were written from memory and are wrong in the same way.

## The correction

Both **S1240 section 1** ("Unbound: all four face buttons (A/B/X/Y)") and **S1232 section 3** ("there is no action for the A/B/X/Y face buttons") are inaccurate. Two face buttons are bound, and they are bound to the most destructive action in the app.

`xr_input.cpp` creates `exit_click` and suggests it on **both**:

```
{g_exitAction, "/user/hand/left/input/x/click"},
{g_exitAction, "/user/hand/right/input/a/click"},
```

and polls it as an unconditional, unconfirmed teardown:

```
if (exitState.isActive && exitState.changedSinceLastSync && exitState.currentState) {
    xr_session_request_exit();
}
```

So **X or A ends the immersive session instantly, on press, with no confirmation and no on-screen hint that it will.** Any mapping table has to start from that fact rather than from "the face buttons are free".

This also partly satisfies S1232 goal 2 ("кнопок выхода нужно две"): there are already two exit inputs. What is missing is not the count - it is that they are invisible, unlabelled, and adjacent to the buttons a user presses while exploring.

## Full inventory of the current action set

Eight actions in one set (`diagnostic_input`), Oculus Touch profile:

| Action | Type | Bound to | Consumed as |
| --- | --- | --- | --- |
| `exit_click` | boolean | left `x/click`, right `a/click` | immediate `xr_session_request_exit()` |
| `prev_trigger` | float | left `trigger/value` | select / ray click (see note) |
| `next_trigger` | float | right `trigger/value` | select / ray click (see note) |
| `grip_squeeze` | float | both `squeeze/value` | HUD drag + recenter (`xr_hud_world.cpp:225-233`) |
| `thumbstick_input` | vector2f | both `thumbstick` | X = prev/next media, Y = image zoom |
| `aim_pose` | pose | both `aim/pose` | ray origin |
| `grip_pose` | pose | both `grip/pose` | HUD placement |
| `haptic_feedback` | vibration out | both `output/haptic` | feedback |

Note on the trigger actions: the names `prev_trigger` / `next_trigger` are legacy. `xr_session.cpp:304-309` records that S0290 Phase 10 **moved** prev/next off the trigger onto thumbstick X, explicitly because trigger/pinch collided with the Quest 3 system screenshot gesture. The trigger now only produces `triggerClicked`, used as the ray click. The action names were never renamed, so the source reads as if the trigger still navigates.

Second profile suggested: `/interaction_profiles/ext/hand_interaction_ext` (pinch/grasp/aim only) - hand tracking, no face buttons, so any face-button mapping degrades to nothing in hand-tracking mode and needs its own answer.

## What is genuinely unbound

- `b/click` (right), `y/click` (left) - the two remaining face buttons.
- `menu/click` (left controller only; the right-hand system button is reserved by the runtime).
- `thumbstick/click` (stick press), both hands.
- `trigger/touch`, `thumbrest/touch`, and the proximity/touch inputs.
- Thumbstick X/Y on the **right** hand is bound to the same action as the left, i.e. the two hands are not distinguishable in behaviour today - both do prev/next and zoom.

## Constraints this puts on the mapping

1. Whatever the table proposes for X and A must be a deliberate decision, not an accident - either keep them as exit and label them, or move exit and rebind them.
2. The trigger is already load-bearing as the ray click, and the reason it is *not* the navigation input is a documented Quest system-gesture collision. Any "trigger summons the HUD" proposal inherits that history - re-check the collision before promising it.
3. A face-button mapping is controller-only. Hand tracking gets pinch/grasp/aim and nothing else, so every essential action needs a non-face-button route or the hand-tracking path loses it.
