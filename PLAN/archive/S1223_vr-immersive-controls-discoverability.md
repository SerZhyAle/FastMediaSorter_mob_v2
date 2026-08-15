# S1223 - Immersive player controls are undiscoverable: no HUD summon, no in-headset legend

**Status:** Archived
**Priority:** 60

<!-- auto-approved by /spec-all - 2026-07-29 -->

## 0. Raw capture

Raised by the owner during the 2026-07-27 Quest 3 VR device-test session, verbatim:

"в иммерсив прогрывателе управление по прежэнему неочевидное - как вызвать диалог HUD - почему то левый джойстик вбок?"

"По-прежнему" - this is a repeat complaint, not a first observation.

## 1. Why this exists

The complaint is not "there are too few bindings". It is that the bindings that exist cannot be
found. Two separate gaps produce it.

- **The panel could not be summoned.** When the ticket was raised, native visibility was a one-way
  latch (`g.hudContentUploaded`): the quad appeared by itself once content arrived and then stayed
  for the session, so "how do I bring up the HUD" had no answer. S1232 has since replaced the latch
  with a real hidden state plus a trigger that summons.
- **There is no in-headset legend.** Every binding is learnable only by trial. The flat app has F1
  key-binding help and a touch-zones overlay; the immersive surface has neither.

The owner learned one binding by accident and assumed it was hand-specific ("левый джойстик"), while
both hands are bound identically (`xr_input.cpp:187-188`). That misreading is the discoverability gap
in one observation.

## 2. The mapping the legend must document

Authoritative source is `PLAN/S1240_vr-controller-input-mapping/research/03__proposed-mapping-table.md`,
narrowed to the rows actually wired in this working tree. The pre-S1240 list that the first draft of
this ticket carried is obsolete and must not be used.

- Trigger, panel visible - ray click on the control under the ray.
- Trigger, panel hidden - summon the panel; the press is consumed and activates nothing.
- Thumbstick left/right - seek within the current item, 10 s a step.
- Grip held plus thumbstick left/right - previous / next file.
- Thumbstick up/down - zoom the immersive image.
- Grip held alone - grab and reposition the panel.
- A (right) or X (left) - leave the immersive session immediately, no confirmation.
- HIDE and EXIT at the panel's two ends - the same two actions as buttons.

Deliberately absent, and the reason each is absent:

- The left menu button opening settings is **S1271**, not wired here. A legend row for it would
  document a binding the build does not have - the exact failure mode section 6 warns about.
- B, Y and thumbstick click are unassigned by owner decision 5 of S1240 and get no row.

## 3. Goal and scope

Ship a one-time in-headset legend that names the bindings above, plus a permanent way back to it, so
that the mapping is learnable without trial and without leaving the headset.

### 3.1 In scope

- A legend surface rendered in the headset, shown automatically on the first immersive entry after
  install.
- Persistence of "already shown" so it does not reappear on every entry.
- Dismissal by a controller input, consumed so the dismissing press does not also act.
- A help affordance on the media strip that shows the legend again on demand.
- Legend captions in EN, RU and UK, alongside the existing `vr_hud_*` keys.
- Sync of `docs/VR_CONTROLS.md` and its RU/UK mirrors with the shipped legend text.

### 3.2 Out of scope

- Designing or changing any binding. S1240 decided the mapping; this ticket only publishes it.
- The settings HUD panel and the menu-button binding - S1271.
- Controller navigation of the immersive browser grid - S1133.
- A keyboard or mouse path to the legend. `docs/VR_CONTROLS.md` lists F1 for a cheatsheet as target
  design for epic S0773; neither input reaches the immersive session today.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1240 (supplies the mapping the legend text is authored from, implementation landed), S1232 (supplies the hidden-panel state and the summon binding the legend describes, implementation landed), S0964 (owns the interactive strip the help affordance is added to), S1238 (contests the same strip row), S1239 (owns the seek band whose right-hand labels the help button shifts), S1271 (menu-button settings panel, deliberately absent from the legend)
- **UI placement:** no flat-app UI. Two in-headset changes - a legend page that takes over the HUD channel with its own quad geometry, and one localized HELP button in the strip's header band, immediately left of HIDE so the destructive EXIT stays isolated at the opposite end per S1232.
- **Flavor scope:** `src/vr/` only, which ships in the `vr` flavor and is borrowed by `noLegal`; both must compile. No `src/main/` behaviour change.
- **Input model:** controllers. Hand tracking keeps the legend reachable, because the summon/select role maps to pinch and the help button is on the strip rather than on a face button.
- **Verification:** Quest 3 only, and specifically the first-entry state - the legend cannot be observed on an emulator, same constraint family as S1238.

## 4. Design decisions resolved from the codebase

These were open in section 4 of the original draft. They resolve from the source rather than from the
owner, so they are recorded here instead of being asked.

- **The legend draws on the HUD channel, not on the main quad.** The HUD channel already owns text
  rendering and is the only writable text surface besides the subtitle quad. A main-quad takeover
  would blank the film to show help about the film.
- **The legend gets its own quad geometry.** The strip is a 2560x360 texture on a 1.40x0.197 m quad -
  eight rows at readable glyph height do not fit. `setHudQuadSize` is a runtime override, so the
  legend asserts its own size on show and the strip re-asserts the S1228 values on dismiss. The
  override persists in native state across sessions in-process, so failing to restore it is a real
  regression path, not a theoretical one.
- **Legend and strip cannot be on screen together.** They share one channel. That is acceptable and
  arguably correct: the legend is modal by nature, and the strip returns the moment it is dismissed.
- **Dismissal is consumed in Kotlin, not in native code.** The host already receives every navigation
  event and every ray click through its two JNI callbacks, so a guard at the top of each is enough to
  both dismiss and swallow the press. No new native surface is needed.
- **There is no auto-dismiss timer.** `docs/VR_CONTROLS.md` describes a 4-second cheatsheet as target
  design; four seconds is below reading speed for eight rows, and a legend that vanishes before it is
  read reproduces the complaint it exists to close.
- **Thumbstick up/down cannot be intercepted.** Zoom is applied natively and never reaches Kotlin, so
  a vertical deflection during the legend zooms without dismissing. Accepted rather than fixed - the
  alternative is a native change for one input, and the legend still dismisses on every other one.
- **A and X are not dismissal inputs.** They request session exit in native code before any callback
  runs. Pressing them during the legend leaves immersive mode, which is the documented behaviour of
  those buttons and is not worth diverging from for one screen.

## 5. Acceptance criteria

- On the first immersive entry after a clean install, the legend appears without any user action.
- The legend lists exactly the bindings in section 2, and none that are not wired.
- A trigger pull dismisses the legend, the media strip appears at its normal size and position, and
  the dismissing pull does not press any strip control.
- A thumbstick deflection also dismisses the legend, and does not additionally seek or change file.
- A second immersive entry, and every entry after it, goes straight to the strip with no legend.
- The strip's help button brings the legend back, and dismissing it returns the strip unchanged.
- Uninstall and reinstall makes the legend appear once again.
- The strip's file name, FPS reading and seek time label stay inside their bounds with the help
  button present, in the S1238 worst case where both track rows and both sliders are shown.

## 6. Owner decisions (2026-07-28)

- **Decision 1 - first step: the first-entry legend only.** Owner took the recommended default.
  Do not design a summon gesture inside this ticket - **S1240** already settled the input. The
  legend closes the actual complaint ("управление неочевидное") by naming bindings that exist.
  Shown once per app install, dismissed by any input, plus a "show again" row on the
  interactive HUD (S0964) so it stays reachable after the first dismiss.
- **Decision 2 - the HUD is summonable; hiding is a button, not a gesture.** This was asked as
  "always-on, or spend an input on a toggle?" and is answered by **S1240** decision 3: the
  trigger summons only, and the strip's own hide button (S1232) puts it away. The objection
  that no free input is left no longer holds - the trigger's ray-click role and its summon role
  were reconciled in S1240 rather than competing. The one-way visibility latch goes away with it.
  Note for the legend: "how do I get the panel back" now has a real answer, which is exactly
  the sentence a hideable HUD was said to need.
- **Q4 (where the legend draws) resolves technically:** the HUD quad is the interactive channel
  and already owns text rendering (`HudCanvasRenderer`), so a legend page on the HUD channel is
  the low-risk shape. Section 4 records how the size mismatch between the strip and a legend page
  is handled.

**The legend's content is no longer the four bindings the first draft listed.** S1240 rewrote the
mapping, so the legend must be authored against the decided map - section 2 above is that map,
narrowed to what is wired.

Verification after any implementation is Quest-only (immersive session, first-entry state),
i.e. an owner device-test - same constraint family as S1238.

## 7. Risks

- **Quad-size restore.** A dismissal path that skips the restore leaves the strip stretched onto the
  legend's quad for the rest of the process, and the next session inherits it. Every exit from the
  legend must go through one restore point.
- **Legend text drifting from the build.** The text is authored from S1240's table; a later binding
  change that does not touch the legend leaves the headset documenting a mapping that no longer
  exists. The same risk already materialised once - the first draft of this ticket listed the
  pre-S1240 map.
- **Strip width.** S1239 records that the control row's worst case already overruns its area. The
  help button is placed in the header band rather than the control row for that reason, and it
  shortens the file-name budget and moves the right-aligned labels.

## 8. Related

- **S1240** - the mapping. Its sequencing note puts this ticket third, after S1232 and S1240's
  implementation halves; both have landed and are awaiting Quest verification.
- **S1232** - the hidden panel state, the summon binding and the HIDE/EXIT buttons the legend names.
- **S0964** - the interactive strip that gains the help button.
- **S1271** - the settings HUD panel, deliberately not described by the legend.
- **S1133** - immersive browser grid navigation, a different surface.
