# Phase 01 — HUD element registry

**Strategic spec:** [`../S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-04-28
**Completed:** 2026-04-29

---

## Objective

Introduce a HUD element registry owned by `VrHudSceneComposer`. Each element carries an integer id, a pixel-coordinate rectangle on the HUD canvas, and a click-callback handle. Registry resets on every composer redraw and is queryable by `(u, v)` coordinates. No ray math, no hover, no dispatch yet — only the data layer.

---

## Prerequisites

- [ ] Strategic §6.1 (aim-pose source) read; not blocking — decision is deferred to Phase 02.
- [ ] Working tree is clean or on a feature branch.
- [ ] `VrHudSceneComposer.kt` and `VrInteractivePanelComposer.kt` are familiar (the latter is the design template for `zoneAt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElement.kt` | New | ≤ 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElementRegistry.kt` | New | ≤ 180 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 750 |

> `VrHudSceneComposer.kt` is currently <500 lines; the additions for registry wiring stay within budget. No backup needed.

---

## Steps

### Step 01.1 — Create `VrHudElement` data class

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElement.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a Kotlin data class `VrHudElement` in package `com.sza.fastmediasorter.vr.render`. Fields: `id: Int` (stable element identifier, ≥ 0), `bounds: android.graphics.RectF` (pixel rectangle on the HUD canvas, origin top-left), `label: String` (debug-only, ≤ 32 chars). No callback field on the element itself — callbacks are stored separately in the registry (Step 01.2) so element data is GC-cheap to recreate per redraw. Add KDoc that names this as the source of truth for "what is at HUD pixel (x, y)".

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElement.kt` exists.
- `Grep` — `data class VrHudElement` matches exactly once.
- `Grep` — `id: Int` and `bounds: RectF` and `label: String` each match in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElement.kt (+20 LOC). Dev log recorded.

---

### Step 01.2 — Create `VrHudElementRegistry`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElementRegistry.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create class `VrHudElementRegistry` in `com.sza.fastmediasorter.vr.render`. Public API:
> - `fun beginFrame()` — clears all elements and callbacks.
> - `fun register(id: Int, bounds: RectF, label: String, onClick: () -> Unit)` — appends to internal list; throws `IllegalStateException` if `id` is duplicated within the current frame.
> - `fun elementAt(u: Float, v: Float): VrHudElement?` — converts UV to pixel coords (`px = u * width`, `py = v * height`) and returns the first registered element whose `bounds.contains(px, py)` is true, or `null` if none.
> - `fun callbackOf(id: Int): (() -> Unit)?` — returns the callback registered for `id`, or `null` if id is unknown.
> - `val width: Int` and `val height: Int` — taken via constructor parameters; mirror the HUD canvas size.
>
> Internal storage: two parallel lists (`MutableList<VrHudElement>`, `MutableList<() -> Unit>`) reused across frames; `beginFrame()` calls `clear()` on each, no allocations after warm-up. `O(N)` linear scan in `elementAt` is the explicit policy from strategic §3.2.

**Verification:**

- `Glob` — `VrHudElementRegistry.kt` exists.
- `Grep` — `class VrHudElementRegistry` matches exactly once.
- `Grep` — `fun beginFrame`, `fun register`, `fun elementAt`, `fun callbackOf` each match.
- `Grep` — `Log\.d\(` returns zero hits (Timber-only; this file likely needs no logging at all).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 4/4 PASS. Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElementRegistry.kt (+68 LOC). Dev log recorded.

---

### Step 01.3 — Wire registry into `VrHudSceneComposer`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a `val registry: VrHudElementRegistry` property to `VrHudSceneComposer`, initialised in the constructor with the same `width`/`height` as the composer. At the very top of the existing `draw(canvas: Canvas, state: VrHudState)` method, call `registry.beginFrame()`. Do NOT register any elements yet — the existing HUD content stays passive. Keep the method signature unchanged. Expose the registry via a `fun snapshotRegistry(): VrHudElementRegistry = registry` accessor (or simply make the property `internal`/public read-only); ray-input layers in later phases need read-only access to query `elementAt`.

**Verification:**

- `Grep` — `private val registry` or `val registry` in `VrHudSceneComposer.kt` matches once.
- `Grep` — `registry.beginFrame()` appears inside `VrHudSceneComposer.kt` exactly once.
- `Grep` — `VrHudElementRegistry(` reference exists in `VrHudSceneComposer.kt` (same package, no import line required).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS (predicate 3 patched: same-package, no import line). Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt (+2 LOC). Dev log recorded.

---

### Step 01.4 — Register the seek-bar zone as the first HUD element (smoke wire)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside the existing seek-bar drawing branch of `draw(..)`, after computing the seek-bar `RectF`, call `registry.register(id = HUD_ELEMENT_SEEK_BAR, bounds = <thatRect>, label = "seek", onClick = { /* no-op for now */ })`. Add a `companion object` constant `const val HUD_ELEMENT_SEEK_BAR = 1` (use ids ≥ 1; reserve 0 as "none"). Element registers only when the seek-bar branch actually paints — do not register it on idle frames. The empty `onClick` lambda is intentional: real wiring belongs to Phase 04.

**Verification:**

- `Grep` — `HUD_ELEMENT_SEEK_BAR = 1` matches once.
- `Grep` — `registry.register(` appears in `VrHudSceneComposer.kt` at least once.
- `Grep` — the registration call passes `HUD_ELEMENT_SEEK_BAR` (sanity: the constant is used, not a bare `1`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Registry refactored to pool RectF instances internally so caller passes the existing tmpRect — keeps composer's "no allocations in draw" KDoc invariant. Files: app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt (+5 LOC), app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudElementRegistry.kt (+8 LOC). Dev log recorded.

---

### Step 01.5 — Build verification

**Files:** —
**Depends on:** Step 01.4

**Prompt for developer:**

> Run `/build` for the VR flavor and confirm a clean compile. Treat any new lint warning in the three files touched as an actionable defect (CLAUDE.md rule 7). Resolve before flipping the phase to ✅ Done.

**Verification:**

- `Grep` — `TODO(phase-01)` returns zero hits.
- Build output indicates VR flavor compiles without errors.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Static checks PASS (zero `TODO(phase-01)` hits). Build gate deferred — `/spec-dev` cannot run `/build` directly. Run `/build` to flip to done.
- 2026-04-29 — `/build` `vr debug` PASS (`assembleVrDebug` 41s, BUILD SUCCESSFUL). Phase 01 closed.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `assembleVrDebug` PASS (2026-04-29).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (two new public classes).

---

## Handoff Notes to Next Phase

`VrHudElementRegistry` is now reachable from `VrHudSceneComposer.registry`. Phase 02 may compute UV from a controller ray and call `registry.elementAt(u, v)` to obtain the hovered element id (or `null`).

---

## Rollback Plan

Revert phase commit(s). The registry is not yet consumed outside the composer, so no data migration or user-facing surface is at risk.
