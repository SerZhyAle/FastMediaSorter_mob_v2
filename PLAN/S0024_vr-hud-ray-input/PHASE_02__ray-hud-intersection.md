# Phase 02 — Ray-vs-HUD-plane intersection

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Compute the UV intersection of the controller aim-ray with the HUD plane (head-locked,
1.0 m × 0.3 m at 1.5 m, ~20° below eye-line; geometry inherited from S0009 §6 #4 and
pinned in `OpenXrInput.cpp::syncControllerAimRay` as `kHudHalfW=0.5`, `kHudHalfH=0.15`,
`kHudCentreY=-0.35`). Reuse the existing controller-aim NDC stream
(`XrInputCallback.onControllerPointerMove`) — the C++ side already projects against the
HUD quad. Add a Kotlin-side hit-tester (`VrHudHitTester`) that converts NDC → UV and an
input-manager sink that maps UV → registered HUD element via `VrHudElementRegistry.elementAt`.
Off-plane rays return a sentinel "miss" result. No hover state, no dispatch.

---

## Pre-Implementation Note (2026-05-03)

Phase 02 was originally scoped to add a new JNI callback `nativeOnHudPointerMove`. Field-log
review (strategic §13) and code audit revealed that `OpenXrInput.cpp::syncControllerAimRay`
already projects the aim-pose against the HUD quad geometry and emits NDC via
`onControllerPointerMove` — a parallel callback would duplicate the same math. Phase rescoped:
JNI work dropped; new sink added on the Kotlin input manager. C++ + `OpenXrNative.kt` left
untouched. Spec patched in-place by `/spec-all S0024 force`.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Strategic §6.1 decision recorded: **reuse** the same aim-pose used by
      `VrControllerRayManager`. Mirrored in the KDoc of `VrHudHitTester.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudHitTester.kt` | New | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | Modified | ≤ 400 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt` | Modified | ≤ 600 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 800 |

> Activity-level wiring deliberately avoided per CLAUDE.md rule 3 (Activity logic
> prohibited). HUD ray-input is composed by `VrRenderPipelineManager` analogous to
> the panel hit-test wiring at `VrRenderPipelineManager.kt:182-202`.

---

## Steps

### Step 02.1 — Create `VrHudHitTester` (pure UV math)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudHitTester.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create class `VrHudHitTester` in `com.sza.fastmediasorter.vr.ui`. Pure math, no Android
> dependencies. Mirror the NDC convention and threshold constant from `VrRayPanelHitTester`.
> Expose `data class HitResult(val u: Float, val v: Float) { val isMiss: Boolean get() = u < 0f }`
> and `fun computeHit(ndcX: Float, ndcY: Float): HitResult` returning UV in `[0, 1]` with
> `(u = (ndcX + 1) / 2, v = (1 - ndcY) / 2)`. Outside ±1.5 NDC guard band → `UV_MISS = HitResult(-1f, -1f)`.
> KDoc must state the HUD plane geometry (head-locked, 1.0 × 0.3 m at 1.5 m, ~20° below eye-line)
> and link to S0009 §6 #4. Document that the consumed NDC stream is
> `XrInputCallback.onControllerPointerMove` (already a HUD-plane projection).

**Verification:**

- `Glob` — `VrHudHitTester.kt` exists.
- `Grep` — `class VrHudHitTester` matches once.
- `Grep` — `fun computeHit(ndcX: Float, ndcY: Float): HitResult` matches once.
- `Grep` — `private const val MISS_THRESHOLD` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. File: app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrHudHitTester.kt (+57 LOC). Dev log recorded.

---

### Step 02.2 — Expose HUD registry from `VrHudSceneDriver` ✅

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Phase 01 exposed the registry on `VrHudSceneComposer.registry`. The driver wraps the
> composer; pipeline-side code already holds a `VrHudSceneDriver` reference. Add a
> read-only accessor `val registry: VrHudElementRegistry get() = composer.registry` to
> `VrHudSceneDriver`. This avoids leaking the composer through additional public surfaces
> and keeps Phase 04 dispatch wiring simple.

**Verification:**

- `Grep` — `val registry: VrHudElementRegistry` in `VrHudSceneDriver.kt` matches once.
- `Grep` — `composer.registry` in `VrHudSceneDriver.kt` matches once.

**Status:** `[x] done`

---

### Step 02.3 — Wire HUD hit-test through `VrControllerInputManager` + `VrRenderPipelineManager`

**Files:**
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> In `VrControllerInputManager`, mirror the panel hit-test contract:
> - Add a `@Volatile private var hudHitTester: VrHudHitTester? = null` field.
> - Add a `@Volatile var hudRegistryProvider: (() -> VrHudElementRegistry?)? = null`.
> - Add a `@Volatile var hudVisibleProvider: (() -> Boolean)? = null`.
> - Add a `@Volatile var hudHoverSink: ((hand: Int, hudElementId: Int) -> Unit)? = null`.
> - Add `fun attachHudHitTester(tester: VrHudHitTester)`.
> - Inside `onControllerPointerMove`, after the existing panel hit-test branch, run a parallel
>   HUD hit-test: skip when the HUD layer is not visible (`hudVisibleProvider?.invoke() != true`)
>   or when the registry is unavailable; otherwise compute UV via the tester, query
>   `registry.elementAt(u, v)`, and dispatch the resolved element id (or `0` for miss) to
>   `hudHoverSink`. Strategic §11 criterion 4 forbids paying for the math when the layer
>   is not submitted to the compositor — gate accordingly.
>
> In `VrRenderPipelineManager.initializeVrRenderPipeline`, after the HUD scene driver is
> constructed (around line 156), wire the hit-tester analogously to the panel block:
> instantiate `VrHudHitTester`, call `localInputManager.attachHudHitTester(..)`, set
> `hudRegistryProvider = { vrHudSceneDriver?.registry }`, set
> `hudVisibleProvider = { vrHudRenderer?.isVisible() == true }` (or the equivalent driver
> predicate), and set `hudHoverSink = { _, hudElementId -> currentHudHoverId = hudElementId }`.
> Add a `@Volatile var currentHudHoverId: Int = 0` field on the pipeline manager — Phase 03
> reads it. No Activity-level state.

**Verification:**

- `Grep` — `attachHudHitTester` matches in both `VrControllerInputManager.kt` and `VrRenderPipelineManager.kt`.
- `Grep` — `hudHoverSink` matches at least twice in `VrControllerInputManager.kt` (declaration + invocation) and once in `VrRenderPipelineManager.kt`.
- `Grep` — `currentHudHoverId` matches at least twice in `VrRenderPipelineManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in both files.

**Status:** `[x] done`

---

### Step 02.4 — Build verification ✅

**Step Log:**

- 2026-05-03 — `/build` `vr debug` (`./gradlew :app_v2:assembleVrDebug`) PASS in 19s. BUILD SUCCESSFUL.

**Files:** —
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` for the VR flavor. Confirm clean compile. Resolve any lint warnings introduced.

**Verification:**

- `Grep` — `TODO(phase-02)` returns zero hits.
- Build output indicates VR flavor compiles without errors.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `assembleVrDebug` PASS (2026-05-03, 19s).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`VrHudHitTester` is a new public role) — Phase 06.
- [x] Aim-pose source decision (research §6.1) recorded as a comment in `VrHudHitTester.kt`.

---

## Handoff Notes to Next Phase

`VrRenderPipelineManager.currentHudHoverId` now updates per-frame from controller aim
(gated by HUD layer visibility). Phase 03 reads this field and translates id-changes into
composer redraws + hover-highlight overlay.

---

## Rollback Plan

Revert phase commit(s); the new sink is additive and isolated to the VR flavor. No JNI/C++
changes were made — the existing `onControllerPointerMove` continues to feed the legacy
`VrControllerRayManager` path.
