# Phase 05 — Idle gate and accessibility feedback

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Suppress ray-vs-HUD computation entirely when the HUD layer is not in the OpenXR composition (strategic §2 #5, §11 #4). Add an audio cue on click (strategic §3.2 accessibility) so hover highlight is not the only feedback channel.

---

## Prerequisites

- [ ] Phase 04 ✅ Done.
- [ ] Read `VrHudSceneDriver.kt` to confirm which flag / state indicates "HUD currently submitted to compositor".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt` | Modified | ≤ 220 |
| `app_v2/src/vr/res/raw/hud_click.ogg` | New | binary |

---

## Steps

### Step 05.1 — Gate the JNI HUD-pointer callback on HUD-active

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a runtime flag `gHudLayerActive` (atomic bool) on the C++ side, written by an existing setter that already toggles HUD layer submission (locate via grep — likely something like `setHudLayerSubmitted` or similar; if not present, add a setter `nativeSetHudLayerActive(active: Boolean)` and call it from `VrHudSceneDriver` whenever the layer goes in/out of composition). Wrap the per-frame ray-vs-HUD-plane computation and the `nativeOnHudPointerMove` JNI dispatch in `if (gHudLayerActive) { .. }`. When inactive, the math is skipped entirely — strategic §2 #5.

**Verification:**

- `Grep` — `gHudLayerActive` matches at least three times in `OpenXrNative.cpp` (declaration + read + write).
- `Grep` — `nativeOnHudPointerMove` is reached only inside an `if (gHudLayerActive)` guard (visual review of the C++ branch, plus `Grep -B 2` confirming the guard).
- `Grep` — `nativeSetHudLayerActive` (or chosen setter name) matches in `VrHudSceneDriver.kt` if a Kotlin-side setter was added.

**Status:** `[ ]` not done

---

### Step 05.2 — Add audio cue on click in `VrHudInputDispatcher`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudInputDispatcher.kt`, `app_v2/src/vr/res/raw/hud_click.ogg`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a 60–120 ms short click sound resource at `app_v2/src/vr/res/raw/hud_click.ogg` (small ogg/vorbis, mono, ≤ 8 KB). In `VrHudInputDispatcher`, accept an `android.media.SoundPool` (or similar lightweight player) via constructor. After a successful callback dispatch in `onTriggerUp`, call `soundPool.play(clickStreamId, ..)`. If the sound resource cannot be loaded, fail silently — Timber `Timber.w` once at init, never per-click. Owner (`VrPlayerActivity`) supplies the SoundPool, loads the resource at `onCreate`, releases at `onDestroy`.

**Verification:**

- `Glob` — `app_v2/src/vr/res/raw/hud_click.ogg` exists.
- `Grep` — `SoundPool` matches at least once in `VrHudInputDispatcher.kt` and once in `VrPlayerActivity.kt`.
- `Grep` — `R.raw.hud_click` matches once in `VrPlayerActivity.kt`.

**Status:** `[ ]` not done

---

### Step 05.3 — Build verification + on-device idle test

**Files:** —
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `/build`. On Meta Quest 3: dismiss the HUD (let it auto-hide); observe that pointing the ray at the same screen region produces no hover highlight (proves the JNI gate works). Re-trigger the HUD; aim and click — audio cue plays. If the audio cue is too loud / sharp, capture a follow-up note in the Blockers Log of `INDEX.md` for ergonomic tuning (does not block phase completion).

**Verification:**

- `Grep` — `TODO(phase-05)` returns zero hits.
- Build output indicates VR flavor compiles without errors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] On-device verification confirms idle-gate behaviour.

---

## Handoff Notes to Next Phase

All five strategic criteria are functionally satisfied (strategic §11). Phase 06 is documentation, catalogue regen, dev-log finalisation.

---

## Rollback Plan

Revert phase commit(s). The audio resource is additive; the gate flag defaults to active so reverting the C++ side leaves behaviour functional but slightly less efficient.
