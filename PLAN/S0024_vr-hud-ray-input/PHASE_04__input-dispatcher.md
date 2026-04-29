# Phase 04 — Input dispatcher (controller trigger + hand pinch)

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Introduce `VrHudInputDispatcher`: a single sink for "trigger" events from any source (controller trigger, hand-tracking pinch). On a trigger, the dispatcher reads the current hover id and invokes the registered callback. Resolve the trigger-button-mapping research item (§6.3) and confirm no collision with player commands. Producer-agnostic: callers do not know whether the source was a controller or a pinch.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Strategic §6.3 decision recorded: **trigger** = HUD click; A/X stay on player commands. Implementation must enumerate the bindings already wired in `VrControllerInputManager` and confirm trigger is free; record the verified binding map as a one-line KDoc comment at the top of `VrHudInputDispatcher.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInteractionCallback.kt` | New | ≤ 40 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt` | New | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt` | Modified | ≤ 600 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 850 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1000 |

> If any file exceeds 500 LOC pre-edit, write a timestamped backup into `temp/` first.

---

## Steps

### Step 04.1 — Define `VrHudInteractionCallback` interface

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInteractionCallback.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `fun interface VrHudInteractionCallback` with `fun onClick(elementId: Int)`. Single-method interface so callers can register lambdas. KDoc must state that the callback is invoked on the main looper (the dispatcher hops there).

**Verification:**

- `Glob` — `VrHudInteractionCallback.kt` exists.
- `Grep` — `fun interface VrHudInteractionCallback` matches once.
- `Grep` — `fun onClick(elementId: Int)` matches once.

**Status:** `[ ]` not done

---

### Step 04.2 — Implement `VrHudInputDispatcher`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `VrHudInputDispatcher` in `com.sza.fastmediasorter.vr.ui`. Constructor takes `hoverState: VrHudHoverState`, `composerProvider: () -> VrHudSceneComposer`, and `mainHandler: android.os.Handler` (defaulting to `Handler(Looper.getMainLooper())`). Public API:
> - `fun onTriggerDown(source: Source)` — captures the current hover id at the moment of press; stores it.
> - `fun onTriggerUp(source: Source)` — if the latched id matches the current hover id, dispatch via `mainHandler.post { composerProvider().registry.callbackOf(latchedId)?.invoke() }`. If no longer matches, drop the click (mirrors the "press → drift → release" rule from `VrControllerRayManager`).
> - `enum class Source { CONTROLLER_TRIGGER, HAND_PINCH }` — diagnostic only; behaviour is identical (strategic ADR-2).
> Latched id is stored as `@Volatile private var latchedId: Int = 0` and reset to `0` after each dispatch. Use Timber `Timber.d` for one-line click traces; zero `Log.d`.

**Verification:**

- `Glob` — `VrHudInputDispatcher.kt` exists.
- `Grep` — `class VrHudInputDispatcher` matches once.
- `Grep` — `enum class Source` matches once.
- `Grep` — `fun onTriggerDown`, `fun onTriggerUp` each match.
- `Grep` — `Log\.d\(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.3 — Wire controller trigger into the dispatcher

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `VrPlayerActivity`, instantiate `VrHudInputDispatcher` once with the activity's `hudHoverState` and a lambda returning the current `VrHudSceneComposer`. In `VrControllerInputManager`, when a `XrInputEventType.TRIGGER` event fires, branch on whether the HUD is currently active (consumed in Phase 05): if active and the event is a press, call `dispatcher.onTriggerDown(Source.CONTROLLER_TRIGGER)`; on release, `dispatcher.onTriggerUp(Source.CONTROLLER_TRIGGER)`. Existing player-command routing for non-trigger buttons (A/X/menu) stays untouched. Trigger events that previously routed to a player command must remain routed if the HUD is inactive — keep the prior behaviour as the else-branch.

**Verification:**

- `Grep` — `VrHudInputDispatcher` import in `VrPlayerActivity.kt` and `VrControllerInputManager.kt`.
- `Grep` — `dispatcher.onTriggerDown(` and `dispatcher.onTriggerUp(` each match at least once.
- `Grep` — `Source.CONTROLLER_TRIGGER` matches at least twice.

**Status:** `[ ]` not done

---

### Step 04.4 — Wire hand-tracking pinch into the same dispatcher

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHandRayManager.kt` (or whichever class currently surfaces pinch from hand-tracking — verify via grep first)
**Depends on:** Step 04.3

**Prompt for developer:**

> Locate the existing pinch-event entry point that today drives the interactive panel (S0007 / `VrHandRayManager`). When a pinch begin/end fires while the HUD is active, additionally call `dispatcher.onTriggerDown(Source.HAND_PINCH)` / `onTriggerUp(Source.HAND_PINCH)`. Do not remove the existing interactive-panel routing — both layers may coexist; the panel still handles its own clicks via `VrPanelHitZoneResolver`. The shared dispatcher is the HUD path only. Add a one-line WHY-comment: "Single dispatcher per ADR-2: panel and HUD do not collide because each consults its own registry under its own hover-id state."

**Verification:**

- `Grep` — `Source.HAND_PINCH` matches at least twice.
- `Grep` — `dispatcher.onTrigger(Down|Up)` matches in the hand-ray file.

**Status:** `[ ]` not done

---

### Step 04.5 — Replace seek-bar `onClick` no-op with a real callback (smoke wire)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> The composer's `register(..)` call for `HUD_ELEMENT_SEEK_BAR` from Phase 01 currently passes an empty `onClick` lambda. Add a constructor parameter `onSeekBarClick: () -> Unit = {}` to `VrHudSceneComposer` and forward it as the seek-bar callback. Owner (`VrPlayerActivity`) supplies a real lambda that emits one Timber line `Timber.d("HUD click: seek-bar")`. No actual seek action yet — this is a smoke-wire to verify the dispatcher path end-to-end. Real seek behaviour is owned by S0019.

**Verification:**

- `Grep` — `onSeekBarClick: () -> Unit` matches once in `VrHudSceneComposer.kt`.
- `Grep` — `HUD click: seek-bar` matches once in `VrPlayerActivity.kt`.
- Build — `/build` succeeds.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Trigger-mapping decision (§6.3) recorded as a comment in `VrHudInputDispatcher.kt`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

End-to-end click path is alive: aim → hover → trigger → registered callback runs on main thread. Phase 05 closes idle/active gating and adds accessibility audio cue.

---

## Rollback Plan

Revert phase commit(s). The dispatcher and callback interface are additive; existing player-command routing for trigger remains as the else-branch from Step 04.3, so reverting the new branch restores prior behaviour.
