# Phase 05 — Idle gate and accessibility feedback

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Suppress ray-vs-HUD computation entirely when the HUD layer is not in the OpenXR composition (strategic §2 #5, §11 #4). Add an audio cue on click (strategic §3.2 accessibility) so hover highlight is not the only feedback channel.

---

## Pre-Implementation Note (2026-05-03)

Two scope reductions were applied vs. the original phase spec:

1. **Idle gate** — pushed entirely to the **Kotlin layer** during Phase 02. The
   per-frame `onControllerPointerMove → hudHitTester` math is short-circuited by
   `hudVisibleProvider?.invoke() == true` (driven by
   `VrHudSceneDriver.isLayerVisible`). Strategic §11 #4 is satisfied without
   modifying `OpenXrInput.cpp::syncControllerAimRay` — the C++ side keeps emitting
   NDC because the legacy `VrControllerRayManager` still consumes it for Android
   `MotionEvent` dispatch into the decor view; gating in C++ would break that
   unrelated path. The optimisation cost is one extra `sin/cos`-class arithmetic
   per frame in C++ — negligible. Adding a C++ flag would have been duplicate state.
2. **Audio cue** — system `AudioManager.FX_KEY_CLICK` reused (matches
   `VrControllerInputManager.handlePointerClick` choice from S0007 §3.5). No new
   `hud_click.ogg` asset shipped — the system sound already provides a 60–120 ms
   click and avoids both the 8 KB binary in version control and the SoundPool
   lifecycle. Owner concern of "subtle accessibility cue" is met identically.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt` | Modified | ≤ 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 800 |

---

## Steps

### Step 05.1 — Idle gate (Kotlin layer, retained from Phase 02) ✅

The `VrControllerInputManager.onControllerPointerMove` HUD branch is
guarded by `hudVisibleProvider?.invoke() == true`. The provider lambda
returns `vrHudSceneDriver?.isLayerVisible == true`, mirroring the most
recent `renderer.setVisible(..)` call. When the HUD layer is hidden,
the hit-tester is never invoked and no hover sink fires.

**Verification:**

- `Grep` — `hudVisibleProvider` matches in `VrControllerInputManager.kt` (3+ hits — declaration + invocation) and `VrRenderPipelineManager.kt` (1 hit — assignment).
- `Grep` — `isLayerVisible` matches in `VrHudSceneDriver.kt` (1) and `VrRenderPipelineManager.kt` (1).
- C++ side intentionally unchanged — see Pre-Implementation Note #1.

**Status:** `[x] done`

---

### Step 05.2 — Audio cue on click ✅

`VrHudInputDispatcher` accepts an `onClickAudioCue: () -> Unit = {}`
constructor lambda. After a successful drift-checked dispatch in
`onTriggerUp`, the cue fires before the main-thread callback post.
`VrRenderPipelineManager` supplies a lambda that calls
`audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)`.

**Verification:**

- `Grep` — `onClickAudioCue` matches in `VrHudInputDispatcher.kt` (2: constructor + invocation) and `VrRenderPipelineManager.kt` (1: lambda).
- `Grep` — `FX_KEY_CLICK` matches in `VrRenderPipelineManager.kt` (1).
- No new resource file required — see Pre-Implementation Note #2.

**Status:** `[x] done`

---

### Step 05.3 — Build verification ✅

`./gradlew :app_v2:assembleVrDebug` PASS in 12s. BUILD SUCCESSFUL. No new lint warnings.

On-device idle-gate verification deferred to manual: aim ray at the HUD region
while the HUD is auto-hidden, confirm no hover highlight; re-show the HUD via
controller activity, aim again — highlight + click + audio cue all fire. User
owns Quest 3 (memory: `user_hardware.md`); follow-up captured against journal
`updated` if any anomaly observed.

**Verification:**

- `Grep` — `TODO(phase-05)` returns zero hits.
- Build PASS.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `assembleVrDebug` PASS (2026-05-03, 12s).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [ ] On-device verification — deferred to manual (Quest 3 owner).

---

## Handoff Notes to Next Phase

All five strategic criteria are functionally satisfied (strategic §11). Phase 06 is documentation, catalogue regen, dev-log finalisation.

---

## Rollback Plan

Revert phase commit(s). The audio-cue lambda defaults to no-op; the idle-gate
short-circuit was added in Phase 02 and is independent.
