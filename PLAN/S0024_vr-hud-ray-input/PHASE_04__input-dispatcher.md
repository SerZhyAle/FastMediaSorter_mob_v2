# Phase 04 — Input dispatcher (controller trigger + hand pinch)

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Introduce `VrHudInputDispatcher`: a single sink for "trigger" events from any source (controller trigger, hand-tracking pinch). On a trigger, the dispatcher reads the current hover id and invokes the registered callback. Resolve the trigger-button-mapping research item (§6.3) and confirm no collision with player commands. Producer-agnostic: callers do not know whether the source was a controller or a pinch.

---

## Pre-Implementation Note (2026-05-03)

The C++ side emits the controller-trigger as a **single** edge event (`XrInputEventType.PAUSE_TOGGLE`)
— there is no separate `TRIGGER_DOWN`/`TRIGGER_UP` pair like the hand-pinch
`POINTER_CLICK_DOWN/UP`. To preserve the dispatcher's down/up semantics without
extending native code (out of scope), the controller branch issues
`onTriggerDown` followed immediately by `onTriggerUp` on the same hover id —
behaviourally a single click, structurally compatible with the dispatcher API.

Activity-level integration deliberately avoided per CLAUDE.md rule 3 — wiring
lives in `VrRenderPipelineManager.initializeVrRenderPipeline`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInteractionCallback.kt` | New | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt` | New | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt` | Modified | ≤ 600 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 850 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 800 |

---

## Steps

### Step 04.1 — Define `VrHudInteractionCallback` interface ✅

`fun interface VrHudInteractionCallback { fun onClick(elementId: Int) }` in
`com.sza.fastmediasorter.vr.ui`. KDoc records the main-looper invariant.

**Verification:** Glob + Grep predicates PASS (3/3).

**Status:** `[x] done`

---

### Step 04.2 — Implement `VrHudInputDispatcher` ✅

`class VrHudInputDispatcher(hoverState, registryProvider, mainHandler)` with
`enum class Source { CONTROLLER_TRIGGER, HAND_PINCH }`, `onTriggerDown(source)`,
`onTriggerUp(source)`, `hasLatch()`. Latched-id pattern: capture hover at down,
dispatch on up only when the hover still matches (drift-drop). KDoc encodes the
A/X/Y/B/menu non-collision verification (walked
`VrControllerInputManager.dispatchVrCommand` 2026-05-03 — trigger-only routing).

**Verification:** all Glob + Grep predicates PASS (5/5). `Log.d` count: 0.

**Status:** `[x] done`

---

### Step 04.3 — Wire controller trigger into the dispatcher ✅

In `VrControllerInputManager.dispatchXrEvent`, ahead of the existing
`PAUSE_TOGGLE → TogglePausePlay` resolution, the manager now consults
`hudHoverIdProvider`. When the hover id is non-zero and `hudInputDispatcher`
is attached, the trigger is consumed as a HUD click (down + immediate up
on the same id). All other input paths untouched. New fields:
`hudInputDispatcher: VrHudInputDispatcher?` and
`hudHoverIdProvider: (() -> Int)?`.

The dispatcher itself is constructed by `VrRenderPipelineManager` after the
HUD scene driver comes up (Step 04.5 wiring).

**Verification:** Grep — `VrHudInputDispatcher` import PASS;
`dispatcher.onTriggerDown` / `onTriggerUp` and `Source.CONTROLLER_TRIGGER` all
present.

**Status:** `[x] done`

---

### Step 04.4 — Wire hand-tracking pinch into the same dispatcher ✅

In `VrControllerInputManager.handlePointerClick` (the existing pinch entry point),
extended after the existing audio + `onPointerEvent` routing: when the pinching
hand currently hovers a HUD element, `dispatcher.onTriggerDown(HAND_PINCH)` fires
on press; on release, `onTriggerUp(HAND_PINCH)` runs only if a latch is open
(`dispatcher.hasLatch()`) so non-HUD pinches do not flap the dispatcher state.

Single dispatcher per ADR-2: panel and HUD do not collide because each consults
its own registry under its own hover-id state.

**Verification:** Grep — `Source.HAND_PINCH` (3 hits — declaration + two call
sites) PASS; `dispatcher.onTriggerDown(VrHudInputDispatcher.Source.HAND_PINCH)`
and `onTriggerUp(VrHudInputDispatcher.Source.HAND_PINCH)` PASS.

**Status:** `[x] done`

---

### Step 04.5 — Replace seek-bar `onClick` no-op with a real callback (smoke wire) ✅

`VrHudSceneComposer` now accepts an `onSeekBarClick: () -> Unit = {}` constructor
parameter and forwards it to the seek-bar `registry.register(..)` call. The
pipeline-side composer is constructed with
`onSeekBarClick = { Timber.d("HUD click: seek-bar") }`. Real seek behaviour is
S0019.

**Verification:** Grep — `onSeekBarClick: () -> Unit` matches once in the
composer; `HUD click: seek-bar` matches once in the pipeline manager (spec
relocated from Activity to manager per CLAUDE.md rule 3). Build PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Build PASS (vr debug, 14s). End-to-end ray-input smoke wire alive.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `assembleVrDebug` PASS (2026-05-03, 14s).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Trigger-mapping decision (§6.3) recorded as a comment in `VrHudInputDispatcher.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — Phase 06.

---

## Handoff Notes to Next Phase

End-to-end click path is alive: aim → hover → trigger → registered callback runs on main thread. Phase 05 closes idle/active gating and adds accessibility audio cue.

---

## Rollback Plan

Revert phase commit(s). The dispatcher and callback interface are additive; existing player-command routing for trigger remains as the else-branch from Step 04.3, so reverting the new branch restores prior behaviour.
