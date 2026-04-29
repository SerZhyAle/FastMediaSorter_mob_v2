# Phase 03 — Hover state and redraw-on-change

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Track the current hover element id, redraw the HUD only when the id changes, and paint a subtle hover highlight near the hovered element. The hover indicator must be visible but unobtrusive (strategic §3.1 #2). No clicks yet.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `VrHudSceneDriver.kt` known — it owns the redraw cadence for `VrHudSceneComposer` (S0009 §5.1.2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudHoverState.kt` | New | ≤ 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 800 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | Modified | ≤ 400 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1000 |

> If `VrPlayerActivity.kt` is already >500 lines pre-edit, write a timestamped backup into `temp/` first (CLAUDE.md rule 5).

---

## Steps

### Step 03.1 — Create `VrHudHoverState`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudHoverState.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `VrHudHoverState` in `com.sza.fastmediasorter.vr.render`. Tiny holder for the currently-hovered element id and an "id-changed since last consumed" flag.
> - `@Volatile private var current: Int = 0`
> - `@Volatile private var consumed: Int = 0`
> - `fun setCurrent(id: Int)` — assigns and returns `true` if changed.
> - `fun current(): Int` — reads `current`.
> - `fun markConsumed()` — sets `consumed = current`.
> - `fun hasPendingChange(): Boolean` — `current != consumed`.
> No allocation in any method; safe for read on render thread, write on render thread (single-writer).

**Verification:**

- `Glob` — `VrHudHoverState.kt` exists.
- `Grep` — `class VrHudHoverState` matches once.
- `Grep` — `fun setCurrent`, `fun current`, `fun markConsumed`, `fun hasPendingChange` each match.

**Status:** `[ ]` not done

---

### Step 03.2 — Render hover highlight in `VrHudSceneComposer`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend `VrHudSceneComposer.draw(..)` signature to accept `hoverId: Int` (default `0`). After the existing element registration is complete in the current frame, look up `registry.callbackOf(..)`-keyed bounds via the registry list and paint a subtle highlight: a 2-px stroked rounded-rect (corner radius 6 px) drawn around `bounds`, colour `Color.argb(120, 120, 200, 255)`. No fill; no full-area wash. If `hoverId == 0` or no element with that id is registered this frame, skip the highlight. Add a private `hoverPaint: Paint` field initialised once (no allocations in `draw`).

**Verification:**

- `Grep` — `fun draw(canvas: Canvas, state: VrHudState, hoverId: Int` matches once.
- `Grep` — `private val hoverPaint` matches once.
- `Grep` — `Paint(Paint.ANTI_ALIAS_FLAG)` count in the file does not double after this step (sanity: hoverPaint is the only new Paint).

**Status:** `[ ]` not done

---

### Step 03.3 — Trigger redraw only on hover-id change

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `VrPlayerActivity`, instantiate `VrHudHoverState` once. Replace the bare `currentHudHoverId` field from Phase 02 with calls to `hudHoverState.setCurrent(id)`. In `VrHudSceneDriver`, accept a `VrHudHoverState` constructor parameter (or settable field) and consult `hoverState.hasPendingChange()` each tick: when true, request a HUD repaint and call `hoverState.markConsumed()`. The existing low-frequency 2 Hz tick continues to run for live indicators (seek, buffer); hover changes piggy-back on this tick or on the existing event-driven repaint path — pick one consistent route and document the choice with a one-line WHY-comment. Pass `hoverState.current()` into `composer.draw(canvas, state, hoverId = ..)` from the driver.

**Verification:**

- `Grep` — `hudHoverState` matches in both `VrPlayerActivity.kt` and `VrHudSceneDriver.kt`.
- `Grep` — `hasPendingChange()` matches in `VrHudSceneDriver.kt` at least once.
- `Grep` — `markConsumed()` matches in `VrHudSceneDriver.kt` at least once.
- `Grep` — `composer.draw(.+ hoverId` (or equivalent named-arg call) matches at least once.

**Status:** `[ ]` not done

---

### Step 03.4 — Build verification

**Files:** —
**Depends on:** Step 03.3

**Prompt for developer:**

> Run `/build` for the VR flavor. Visual smoke-test on Meta Quest 3 (memory: user owns the device — VR testing is not a blocker): aim ray at the HUD seek-bar — a thin highlight ring appears; aim away — ring disappears within one tick. No flicker on idle frames.

**Verification:**

- `Grep` — `TODO(phase-03)` returns zero hits.
- Build output indicates VR flavor compiles without errors.
- On-device smoke-test logged in journal `updated` line if any anomaly observed.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`VrHudHoverState` is new).

---

## Handoff Notes to Next Phase

The activity now exposes a `hudHoverState` whose `current()` reflects the hover element id. Phase 04 reads `current()` on a trigger event and dispatches the registered callback.

---

## Rollback Plan

Revert phase commit(s). The new `hoverId` parameter on `composer.draw` defaults to `0`; reverting only the activity wiring leaves the composer signature in place but harmless.
