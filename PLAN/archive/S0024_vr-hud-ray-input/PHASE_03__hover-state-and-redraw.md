# Phase 03 — Hover state and redraw-on-change

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Track the current hover element id, redraw the HUD only when the id changes, and paint a subtle hover highlight near the hovered element. The hover indicator must be visible but unobtrusive (strategic §3.1 #2). No clicks yet.

---

## Pre-Implementation Note (2026-05-03)

Phase 02 moved `currentHudHoverId` from `VrPlayerActivity` to `VrRenderPipelineManager`
(CLAUDE.md rule 3 — Activity logic prohibited). Phase 03 follows: `VrHudHoverState`
lives on `VrHudSceneDriver` (driver owns the redraw cadence), and the pipeline-level
sink writes `hoverState.setCurrent(..)` then calls `driver.onHoverIdChanged()` to
schedule a repaint. No Activity-level state added.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudHoverState.kt` | New | ≤ 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElementRegistry.kt` | Modified | ≤ 100 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 800 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | Modified | ≤ 400 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 800 |

---

## Steps

### Step 03.1 — Create `VrHudHoverState` ✅

`current/consumed` pair with `setCurrent`, `current`, `markConsumed`, `hasPendingChange`.
Single-writer (xr-render-thread input manager); `@Volatile` reads from the GL/main
threads. No allocations.

**Verification:** Glob + Grep predicates PASS (4/4).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. File: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudHoverState.kt (+34 LOC). Dev log recorded.

---

### Step 03.2 — Render hover highlight in `VrHudSceneComposer` ✅

Added `hoverPaint: Paint` (single instance, no per-frame alloc) and an extra
`hoverId: Int = 0` parameter to `draw(state, canvas, hoverId)`. Paint is a 2-px
stroked rounded-rect (corner radius 6 px, ARGB(120, 120, 200, 255)) drawn last so
it overlays the element. Bound lookup uses the new `VrHudElementRegistry.boundsOf(id)`
accessor (registry change — see Files Touched). Skips when `hoverId == 0` or
unregistered this frame.

**Verification:**

- `Grep` — `fun draw(state: VrHudState, canvas: Canvas, hoverId: Int` matches once. (Argument order patched: existing signature is `(state, canvas)`, not `(canvas, state)` — Phase 03 spec verification predicate updated to match the actual order.)
- `Grep` — `private val hoverPaint` matches once.
- `Grep` — composer Paint count: existing 14 + hoverPaint = 15 (one new instance).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS (signature predicate adjusted for actual `(state, canvas, hoverId)` order). Files: VrHudElementRegistry.kt (+14 LOC), VrHudSceneComposer.kt (+15 LOC). Dev log recorded.

---

### Step 03.3 — Trigger redraw only on hover-id change ✅

`VrHudSceneDriver` accepts a `VrHudHoverState` (default-constructed). Added a public
`onHoverIdChanged()` method that posts a `requestRedraw()` only when (a) `hoverState.hasPendingChange()`
is true and (b) the HUD layer is currently submitted. The `redrawRunnable` reads the
latest hover id via `hoverState.current()`, passes it into `composer.draw(state, canvas, hoverId)`,
then calls `hoverState.markConsumed()` — id changes are reflected exactly once per redraw.

`VrRenderPipelineManager.hudHoverSink` updates `currentHudHoverId`, calls
`driverRef.hoverState.setCurrent(hudElementId)` and, on a real change, invokes
`driverRef.onHoverIdChanged()`.

**Verification:**

- `Grep` — `hoverState` matches in `VrHudSceneDriver.kt` (5+) and `VrRenderPipelineManager.kt` (2+). Activity-level `hudHoverState` field intentionally not added (note above).
- `Grep` — `hasPendingChange()` matches in `VrHudSceneDriver.kt` (1).
- `Grep` — `markConsumed()` matches in `VrHudSceneDriver.kt` (1).
- `Grep` — `composer.draw(state, canvas, hoverId)` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files: VrHudSceneDriver.kt (+25 LOC), VrRenderPipelineManager.kt (+8 LOC). Dev log recorded.

---

### Step 03.4 — Build verification ✅

`./gradlew :app_v2:assembleVrDebug` PASS in 15s. BUILD SUCCESSFUL. No new lint warnings
in the touched files.

On-device smoke-test deferred to manual verification (user owns Quest 3 — memory:
`user_hardware.md`). No anomaly to log against the journal at this time.

**Verification:**

- `Grep` — `TODO(phase-03)` returns zero hits.
- Build PASS.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `assembleVrDebug` PASS.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`VrHudHoverState` is new) — Phase 06.

---

## Handoff Notes to Next Phase

`VrHudSceneDriver.hoverState` exposes `current()` reflecting the hover element id
(updated on the xr-render-thread by the input manager). Phase 04 reads `current()`
on a trigger event and dispatches the registered callback via
`VrHudSceneDriver.registry.callbackOf(id)`.

---

## Rollback Plan

Revert phase commit(s). The new `hoverId` parameter on `composer.draw` defaults to `0`;
reverting only the driver wiring leaves the composer signature in place but harmless.
