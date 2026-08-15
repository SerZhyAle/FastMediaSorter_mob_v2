# S1232 - Summon the immersive HUD with a controller button; give it hide and exit actions

**Status:** Archived
**Priority:** 75

## 0. Raw capture

Owner, 2026-07-27, in the headset, reviewing the S1228 strip:

> "HUD кнопкой сворачивается в кнопку по центру. Нафиг она пользователю? Пользователь хочет вызывать HUD кнопокй на джойстике (например "B") а кнопок вызода нужно ве - выключить HUD и выйти из иммерсив (стоп)"

This is the third time in one session the owner has hit the same wall (see also **S1223**). The S1228 close button plus centre pill was my answer; the owner rejects the pill outright.

## 1. What is wrong with the current design

S1228 made the HUD dismissable by collapsing it to a centred "HUD" pill, on the theory that a fully hidden panel would be unrecoverable because the ray is the only input. The owner's objection is that the pill is still an obstruction occupying the middle of the view, and that the natural way to summon a HUD in VR is a controller button, not a target you must find and point at.

The pill exists only because no controller button was bound. Bind one and the pill has no reason to exist.

## 2. Goals

> **Amended 2026-07-28:** the owner replaced the B-button proposal with "HUD показывать когда я стреляю тригером джойстика". The summon input is now settled in **S1240**, which also surveys how established VR players map their controllers. Goal 1 below is superseded - do not implement a B binding. Goals 2 and 3 stand unchanged.

1. ~~A controller button (owner proposes **B**) toggles the HUD strip.~~ Superseded by **S1240**: the trigger summons. Note the conflict recorded there - the trigger is also the ray click, so the summoning pull must not activate whatever the ray is pointing at.
2. The strip carries **two** distinct terminal actions, visually distinguishable from each other:
   - **hide the HUD** - playback continues, the strip goes fully away (no pill).
   - **exit immersive** - stop playback and leave the immersive session entirely.
3. Fully hidden means fully hidden: nothing painted on the HUD quad.

**Non-goals:**

- The auto-hide timer - see section 5, already written.
- Any change to the strip's layout or readability, which S1228 settled.

## 3. Native work required

**Corrected 2026-07-28 (S1240 research).** This paragraph was wrong. `xr_input.cpp` does create a face-button action: `exit_click`, bound to `/user/hand/left/input/x/click` and `/user/hand/right/input/a/click`, which calls `xr_session_request_exit()` on press - instantly, unconfirmed, unlabelled. Evidence in `PLAN/S1240_vr-controller-input-mapping/research/01__current-bindings-ground-truth.md`.

One consequence for this ticket: goal 2's "two exit inputs" already exist in count - what is missing is that they are invisible.

**Superseded 2026-07-28 (section 4).** The paragraph here proposed moving exit onto B/Y because Meta's semantics make A/X accept and B/Y back/cancel. The owner declined - exit stays on A/X and gains an on-strip button that calls the same path. No rebinding, no new native action for exit.

Genuinely unbound and still free: **B**, **Y**, thumbstick click. The left menu button is claimed by S1240 decision 4 (settings as a HUD panel).

Adding a new binding, should some later ticket need one, means:

- A new `XR_ACTION_TYPE_BOOLEAN_INPUT` action in `xr_input.cpp` with suggested bindings for `/user/hand/right/input/b/click` and `/user/hand/left/input/y/click` (the Touch profile's equivalents - binding both hands matches how the thumbstick is already bound to both).
- Edge detection like `g_prevStickState` / `g_prevTriggerClicked` - a press event, not a held state.
- A JNI callback up to Kotlin. Note that `NativeDiagnosticXrRuntime.onNativeRayInteraction` is **gone** as of this ticket - it was an empty override whose JNI export had a comment for a body and bound to nothing. Follow the live pattern instead: `xr_session.cpp` reflects `triggerJniInputCallback` / `triggerJniRayInteraction` onto the stored Activity object.

Check the interaction-profile bindings against other OpenXR runtimes before hard-coding Touch paths - the `vr` flavor is meant to be Store-publishable beyond Quest.

## 4. Owner decisions (2026-07-28)

- **The summon input only summons.** Answered in **S1240** decision 3 - the trigger brings the HUD up and never takes it away. Goal 2's **hide button is therefore mandatory and blocking**: it is the sole way to dismiss the strip, so this ticket cannot ship the summon half without it. Sequence it with S1240's implementation, not after.
- **Exit immersive restores position.** "Стоп" means: stop playback, leave the session, return to the flat player at the same position. Do not add a position-abandoning path.
  - **Correction 2026-07-28:** this bullet first named `returnDispatcher.deliverReturnAndFinish(CancelledByUser)` as the path. Implementing that literally is a **defect**: it finishes the Activity while the native session is still running, skipping the EGL/OpenXR/`hudTexture` teardown, and the next immersive entry hits the `AlreadyRunning` failure already documented at `DiagnosticXrActivity.kt:898-905`.
  - The on-strip exit button must call **`renderThread?.requestExit()`**, the same path the physical A/X button and the Android back-press already take. Teardown then runs, and `onRenderThreadExit()` delivers the return itself. The enum it delivers (`CompletedNormally`) is not a behaviour difference - `PlayerVrLaunchManager` treats both values identically; the danger was never the enum, it was the skipped teardown.
- **The two terminal buttons sit apart, at opposite ends of the strip.** Deliberately far enough that "hide" and "exit" cannot be confused or mis-hit. Implemented in the header band: **exit at the left end, hide at the right end** - hide inherits the position of the S1228 close button so the one piece of muscle memory that exists is preserved, and exit lands as far from it as the strip allows. See section 4.1 for why this costs the row band nothing.

Nothing here is left open. Exit stays on A/X (**S1240** decision 5 declined the move to B/Y), and the owner's own framing settles what goal 2's "two exit inputs" means: the on-strip exit button **duplicates** the physical binding rather than replacing it. The existing `exit_click` action keeps `/user/hand/left/input/x/click` and `/user/hand/right/input/a/click`; this ticket adds a HUD button that calls the same exit path, plus the separate hide button. No new native action is needed for exit - section 3's "adding one means.." applies only if some future binding is added.

### 4.1 Design calls resolved from the architecture (2026-07-28)

The implementation research raised four questions. None needed the owner - each follows from goal 3, from S1240, or from an existing pattern. Recorded so they are not re-opened.

- **`xr_hud_process_rays()` is gated by `visible` wholesale**, not just its dispatch to Kotlin. Goal 3 says fully hidden means fully hidden; a quad that can still be hit-tested and grip-dragged while invisible is not hidden, it is transparent. Gating only the Kotlin dispatch would also leave hover haptics firing at an invisible target.
- **The summon edge rides the existing `onNativeInputEvent(eventType)` channel**, as a third event type beside the two navigation ones - not a new callback. There are already two native->Kotlin surfaces in this file family, one of which is inert (`NativeDiagnosticXrRuntime.onNativeRayInteraction`, a validation-grep stub). Adding a third surface would compound that; reusing the live channel does not.
- **No reserved edge-slot concept is needed, and none is added.** The premise was wrong. Measured in `HudCanvasRenderer`: the terminal buttons live in the **header band** (`CLOSE_TOP` 24 to 132), which today carries only the filename text and the FPS readout. The controls S1238 and S1239 contest sit in the **row band** (`ROW_TOP` 176 to `ROW_BOTTOM` 312), packed by `relayout()` between `ROW_AREA_LEFT` and `ROW_AREA_RIGHT`. The two bands do not overlap, so putting hide and exit at the header's two ends costs the row packer nothing. What the buttons actually shrink is the filename's ellipsize budget, which `drawHeaderLine` already computes from the neighbouring rect.
- **`HudAutoHideController` stays unwired.** The auto-hide timer is an explicit non-goal in section 2, and wiring it would ship an automatic disappearance the owner never asked for on the same day the manual hide button arrives. Section 5's "wire it to whatever hidden means" is a follow-up, not part of this change.

### 4.2 What the native side already has

Two facts that shrink this ticket materially, from `PLAN/S1240_vr-controller-input-mapping/research/`-adjacent source reading:

- `HUDWorldState.visible` (`xr_hud_world.h`) exists, defaults true, is **never set false by anything**, and already gates both `xr_hud_update()` and `xr_hud_render()` - including the laser-line and cursor-dot draw. The hidden state is unwired plumbing, not a missing concept.
- `HandInputState.triggerClicked` (`xr_input.h`) is edge-detected correctly every frame and **read by nothing**; the ray-click path uses the level `triggerDown` instead. That is the summon signal, already computed.

So no new OpenXR action and no new edge detection are required. What is missing is a setter, a gate on `xr_hud_process_rays()`, and the Kotlin buttons.

### 4.3 Known debt this ticket touches but does not fix

`xr_session.cpp` is 2101 lines against the repo's 1500-line ceiling (Rule 2). This change adds a forwarder and a gate to it. The rule's remedy (`helpers/*Manager.kt` extraction) is Kotlin-specific and there is no established C++ extraction pattern here, so splitting the file is its own ticket, not a side effect of this one.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1240 (Approved - owns the mapping; its decision 3 hands the trigger-summon binding to this ticket), S1228 (BlockNeedUserTest - built the strip this edits, including the pill removed here), S1223 (the legend that makes any of this findable), S1238 and S1239 (both lay out against the strip edges reserved here)
- **UI placement:** in-headset only. Two buttons on the existing HUD strip, at opposite horizontal ends - hide at one edge, exit at the other, deliberately far apart so they cannot be mis-hit for each other (section 4). The S1228 collapse pill is deleted, not restyled.
- **Flavor scope:** `vr` only - `app_v2/src/vr/cpp/` plus `ui/xr/` Kotlin and `src/vr/res/values*/strings.xml`. No `src/main/` behaviour changes, no `BuildConfig` guards.
- **Localization:** two new button captions in EN/RU/UK under the existing `vr_hud_*` key convention.
- **Verification:** Quest 3 only. Nothing here is observable on an emulator - the immersive session, the quad, and the controller are all absent.

## 5. Already written, not wired

`ui/xr/helpers/HudAutoHideController.kt` exists in the tree: a 15 s idle timer that collapses the strip, restarted by any ray interaction. 15 s comes from `FilenameOverlayAutoHideManager.TIMEOUT_DEFAULT_MS`, the only established auto-hide cadence for video in the flat player - the player's own bottom control panel has **no** auto-hide at all, it toggles on tap only (`PlayerViewModel.toggleControls`).

It is not referenced by any call site yet, deliberately: its collapse target was the pill this ticket removes. Wire it to whatever "hidden" means after section 2 lands.

Its `Timber.d("S1232: HUD auto-hide fired..")` probe was removed on 2026-07-28, together with the then-unused `timber.log.Timber` import. A probe tag may exist only while its ticket is `BlockNeedUserTest` (CLAUDE.md "Debug Verification Tags"); this one sat in a `Draft` on a class no call site reaches, so it could never have fired. Re-add it when the ticket actually reaches device testing.

## 5.1 Implementation state (2026-07-28)

Built and compiling in `noLegal` debug. Not yet verified in a headset - that is the only remaining gate.

Native (`app_v2/src/vr/cpp/`):

- `xr_hud_world.h/.cpp` - new `xr_hud_set_visible(bool)`. Hiding also clears `hasIntersection[]` and `dragging`, so the strip does not return already hovering a widget nobody is pointing at.
- `xr_hud_world.cpp` - `xr_hud_process_rays()` now returns early while hidden (§4.1 decision). The existing `visible` gates on `xr_hud_update()` and `xr_hud_render()` were already there and were left alone.
- `xr_session.h/.cpp` - `xr_session_set_hud_visible()` forwarder; the frame loop splits on visibility: hidden fires `kInputEventHudSummon` on a trigger edge and dispatches no ray interaction at all, visible keeps the pre-existing hover/click streaming untouched.
- `diagnostic_xr_runtime.cpp` - `nativeSetHudVisible` export added; the inert `onNativeRayInteraction` export deleted.

Kotlin (`app_v2/src/vr/java/`):

- `DiagnosticXrRuntime` / `NativeDiagnosticXrRuntime` - `setHudVisible(Boolean)` added; the dead `onNativeRayInteraction` interface method and its empty override removed (see the comment left in its place).
- `HudCanvasRenderer` - `closeRect`/`expandRect`/`isCollapsed`/`renderCollapsed()` deleted; `exitRect` (header left) and `hideRect` (header right) added with localized labels; `drawHeaderLine` now measures the filename budget between the two buttons instead of from a fixed anchor.
- `HudInteractionDispatcher` - `dispatchCollapseToggle` replaced by `dispatchTerminalClick`, which consumes the press so a terminal button cannot also hit a control beneath it.
- `DiagnosticXrActivity` - hide calls `runtime.setHudVisible(false)`, exit calls `renderThread?.requestExit()` (§4 correction), and `onNativeInputEvent` handles the summon **before** its `mediaPlaylist.isEmpty()` guard so a hidden HUD is never unrecoverable in the one state where the user most needs to reach exit.
- `vr/res/values{,-ru,-uk}/strings.xml` - `vr_hud_hide`, `vr_hud_exit`. Parity checked, exit 0.

Not done, deliberately: `HudAutoHideController` stays unwired (§4.1).

Side finding parked: `xr_session.cpp` is 2101 lines against the 1500 ceiling -> **S1270**.

## 6. Related

- **S1228** - the strip this changes, currently `BlockNeedUserTest`.
- **S1223** - the discoverability ticket. A controller binding with no legend is still undiscoverable; that ticket's one-time hint becomes more necessary, not less.
- **S1233** - the transport buttons on the same strip do not navigate anything yet.
