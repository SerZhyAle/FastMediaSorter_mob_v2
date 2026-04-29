# Phase 02 — Ray-vs-HUD-plane intersection

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Compute the UV intersection of the controller aim-ray with the HUD plane (head-locked, ~1.0 m × 0.3 m at 1.5 m, ~20° below eye-line; geometry inherited from S0009 §6 #4). Reuse the existing `VrRayPanelHitTester` math layer where possible; introduce a HUD-specific sampler (`VrHudHitTester`) that knows the HUD plane geometry. Off-plane rays return a sentinel "miss" result. No hover state, no dispatch.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6.1 decision recorded: **reuse** the same aim-pose used by `VrControllerRayManager`. Mirror this in a one-line KDoc comment at the top of `VrHudHitTester.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudHitTester.kt` | New | ≤ 120 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt` | Modified | ≤ 500 |

> If `OpenXrNative.cpp` exceeds 1000 lines pre-edit, refuse and split via Manager pattern first (CLAUDE.md rule 2). Confirm size before editing.

---

## Steps

### Step 02.1 — Create `VrHudHitTester` (pure UV math)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudHitTester.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create class `VrHudHitTester` in `com.sza.fastmediasorter.vr.ui`. Pure math, no Android dependencies. Accept the same NDC convention as `VrRayPanelHitTester`: origin = HUD-plane centre, +X right, +Y up, range [-1, 1] when on-plane. Expose `data class HitResult(val u: Float, val v: Float) { val isMiss: Boolean get() = u < 0f }` and `fun computeHit(ndcX: Float, ndcY: Float): HitResult` returning UV in `[0, 1]` with `(u = (ndcX + 1) / 2, v = (1 - ndcY) / 2)`. Outside ±1.5 NDC guard band → return `UV_MISS = HitResult(-1f, -1f)`. Mirror the threshold constant from `VrRayPanelHitTester` rather than re-deriving. KDoc must state the HUD plane geometry (head-locked, 1.0 × 0.3 m at 1.5 m, ~20° below eye-line) and link to S0009 §6 #4.

**Verification:**

- `Glob` — `VrHudHitTester.kt` exists.
- `Grep` — `class VrHudHitTester` matches once.
- `Grep` — `fun computeHit(ndcX: Float, ndcY: Float): HitResult` matches once.
- `Grep` — `private const val MISS_THRESHOLD` matches once.

**Status:** `[ ]` not done

---

### Step 02.2 — Add HUD aim-pose hit-test in `OpenXrNative.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the per-frame render-thread block that already computes the controller aim-pose (the one feeding `VrControllerRayManager`), add a parallel ray-vs-plane intersection against the HUD quad geometry. Emit the resulting NDC `(ndcX, ndcY)` to a new JNI callback `nativeOnHudPointerMove(hand: Int, ndcX: Float, ndcY: Float)`. Use the same aim-pose as the interactive-panel ray (research item §6.1 default) — do not poll a second pose. Skip the call entirely when the HUD layer is not currently submitted to the compositor (S0009 idle policy; verified in Phase 05). On the Kotlin side, declare the matching `external` (or `@JvmStatic` callback) signature in `OpenXrNative.kt`, mirroring `nativeOnControllerPointerMove`.

**Verification:**

- `Grep` — `nativeOnHudPointerMove` matches in both `OpenXrNative.cpp` and `OpenXrNative.kt`.
- `Grep` — at least one call site in `OpenXrNative.cpp` invokes the new JNI callback.
- `Grep` — `Log\.d\(` returns zero hits in `OpenXrNative.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Plumb HUD pointer events into `VrPlayerActivity`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `VrPlayerActivity`, add a private field of type `VrHudHitTester` and a private callback method `onHudPointerMove(hand: Int, ndcX: Float, ndcY: Float)` that:
> 1. Calls `hudHitTester.computeHit(ndcX, ndcY)` to obtain the UV.
> 2. If `result.isMiss`, stores a sentinel "no hover" id (use `0`).
> 3. Else, queries `hudSceneComposer.registry.elementAt(result.u, result.v)?.id ?: 0` and stores it as the current candidate hover id.
> Store the candidate in a `@Volatile var currentHudHoverId: Int = 0` field. Phase 03 consumes this field — do not yet trigger any redraw. Wire the JNI callback declared in Step 02.2 to call `onHudPointerMove`. Use Timber `Timber.v` only (zero `Log.d`).

**Verification:**

- `Grep` — `private val hudHitTester = VrHudHitTester()` (or equivalent constructor call) matches once.
- `Grep` — `fun onHudPointerMove` matches once in `VrPlayerActivity.kt`.
- `Grep` — `currentHudHoverId` matches at least twice (declaration + assignment).
- `Grep` — `Log\.d\(` returns zero hits in the file.

**Status:** `[ ]` not done

---

### Step 02.4 — Build verification

**Files:** —
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` for the VR flavor. Confirm the JNI bridge resolves at link time (no `UnsatisfiedLinkError`). Resolve any lint warnings introduced.

**Verification:**

- `Grep` — `TODO(phase-02)` returns zero hits.
- Build output indicates VR flavor compiles without errors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`VrHudHitTester` is a new public role).
- [ ] Aim-pose source decision (research §6.1) recorded as a comment in `VrHudHitTester.kt`.

---

## Handoff Notes to Next Phase

`VrPlayerActivity.currentHudHoverId` now updates per-frame from controller aim. Phase 03 reads this field and translates id-changes into composer redraws + hover-highlight overlay.

---

## Rollback Plan

Revert phase commit(s); JNI callback is additive (no removal of existing aim-pose plumbing). No swapchain or session lifecycle changed.
