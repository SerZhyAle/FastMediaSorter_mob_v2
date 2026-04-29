# Phase 04 — Ray HUD Hit-Test

**Strategic spec:** [`../spec_vr-immersive-controls-panel.md`](../spec_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Create `VrRayPanelHitTester` that computes ray-plane intersection between the controller aim ray (NDC from Phase 02) and the interactive panel quad plane, returning UV coordinates. Create `VrPanelHitZoneResolver` that maps UV to a `PanelZone` using `VrInteractivePanelComposer.zoneAt()`. Wire both into `OpenXrSessionManager` / `VrPlayerActivity` so hover, click, and seek-drag events reach `VrInteractivePanelDriver`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`onControllerPointerMove` emits NDC every frame).
- [ ] Phase 03 is ✅ Done (`VrInteractivePanelComposer.zoneAt()` exists; `VrInteractivePanelDriver.updateHoverZone()` wired).
- [ ] Research Q2 resolved: aim space confirmed available (or action binding added in Phase 02 Step 2.3).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrRayPanelHitTester.kt` | **New** | ≤ 150 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrPanelHitZoneResolver.kt` | **New** | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt` | Modified | ≤ 70 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 560 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt` | Modified | ≤ 260 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ current + 50 |

---

## Steps

### Step 4.1 — Create VrRayPanelHitTester

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrRayPanelHitTester.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `VrRayPanelHitTester`. It receives NDC coordinates from `onControllerPointerMove` and converts them to UV coordinates in the panel texture space.
>
> The panel NDC space (from Phase 02 native) and the panel UV space are related by a simple linear mapping:
> - NDC ∈ [-1, 1] → U = (ndcX + 1f) / 2f, V = (1f - ndcY) / 2f.
>
> When `ndcX` or `ndcY` are outside [-1.5, 1.5] (ray misses the panel plane), emit `UV_MISS` sentinel (-1f, -1f).
>
> Public API:
>
> ```kotlin
> data class HitResult(val u: Float, val v: Float) {
>     val isMiss get() = u < 0f
> }
> fun computeHit(ndcX: Float, ndcY: Float): HitResult
> ```
>
> No state. No Android dependencies. Pure math class — testable in isolation.
> Use Timber only for `LOG_D`-level debug in unusual miss cases. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrRayPanelHitTester.kt` exists.
- `Grep` — `class VrRayPanelHitTester` in that file.
- `Grep` — `fun computeHit` in that file.
- `Grep` — `data class HitResult` in that file.
- `Grep` — `val isMiss` in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrRayPanelHitTester.kt` ≤ 150 lines.

**Status:** `[ ]` not done

---

### Step 4.2 — Create VrPanelHitZoneResolver

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrPanelHitZoneResolver.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Create `VrPanelHitZoneResolver(private val composer: VrInteractivePanelComposer)`.
>
> Public API:
>
> ```kotlin
> fun resolve(hit: VrRayPanelHitTester.HitResult): Int  // returns zone ID or -1 on miss
> fun resolveSeekFraction(hit: VrRayPanelHitTester.HitResult): Float  // returns 0f–1f fraction if hit is in ZONE_SEEK_SLIDER, else -1f
> ```
>
> Implementation:
> - `resolve`: if `hit.isMiss` → return -1. Otherwise call `composer.zoneAt(hit.u, hit.v)?.id ?: -1`.
> - `resolveSeekFraction`: call `resolve(hit)` → if result != `ZONE_SEEK_SLIDER` return -1f. Otherwise use the zone's `bounds.left` / `bounds.right` to compute the fractional X position within the slider zone from `hit.u`.
>
> Use `VrInteractivePanelComposer.ZONE_SEEK_SLIDER` constant. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrPanelHitZoneResolver.kt` exists.
- `Grep` — `class VrPanelHitZoneResolver` in that file.
- `Grep` — `fun resolve(` in that file.
- `Grep` — `fun resolveSeekFraction` in that file.
- `Grep` — `ZONE_SEEK_SLIDER` referenced in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrPanelHitZoneResolver.kt` ≤ 120 lines.

**Status:** `[ ]` not done

---

### Step 4.3 — Add onControllerPanelHover to XrInputCallback

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt`
**Depends on:** — start of phase (parallel with Step 4.1)

**Prompt for developer:**

> Add a second new default method to `XrInputCallback`:
>
> ```kotlin
> /**
>  * Resolved panel hit zone under the controller ray cursor.
>  * Emitted every frame while the interactive panel is visible.
>  *
>  * @param hand one of [XrHand] constants.
>  * @param zoneId hit zone ID from [VrInteractivePanelComposer] constants, or -1 on miss.
>  * @param seekFraction fractional seek position if hovering [VrInteractivePanelComposer.ZONE_SEEK_SLIDER], else -1f.
>  */
> fun onControllerPanelHover(hand: Int, zoneId: Int, seekFraction: Float) {}
> ```
>
> Do not modify `onControllerPointerMove` or `onPointerMove`.

**Verification:**

- `Grep` — `fun onControllerPanelHover` in `XrInputCallback.kt`.
- `Grep` — `fun onControllerPointerMove` still unchanged in `XrInputCallback.kt`.
- File size — `XrInputCallback.kt` ≤ 70 lines.

**Status:** `[ ]` not done

---

### Step 4.4 — Wire hit-test into OpenXrSessionManager callback

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`
**Depends on:** Step 4.1, Step 4.2, Step 4.3

**Prompt for developer:**

> In `OpenXrSessionManager`, locate the `XrInputCallback` implementation (the anonymous class or concrete class) that handles `onControllerPointerMove`.
>
> Add a `VrRayPanelHitTester` instance and a `VrPanelHitZoneResolver` instance as fields (constructor-injected or set via a setter `fun attachHitTester(hitTester: VrRayPanelHitTester, resolver: VrPanelHitZoneResolver)`).
>
> In the `onControllerPointerMove(hand, ndcX, ndcY)` override:
> 1. Call the existing forwarding logic (pass to `controllerRayManager`).
> 2. If `hitTester != null` and panel is visible:
>    - `val hit = hitTester.computeHit(ndcX, ndcY)`
>    - `val zoneId = resolver.resolve(hit)`
>    - `val seekFrac = resolver.resolveSeekFraction(hit)`
>    - Call `inputCallback.onControllerPanelHover(hand, zoneId, seekFrac)`.
>
> The panel visibility check uses `panelDriver.isPanelVisible()` — add `fun isPanelVisible(): Boolean = state.panelVisible` to `VrInteractivePanelDriver` if not already present.

**Verification:**

- `Grep` — `VrRayPanelHitTester` referenced in `OpenXrSessionManager.kt`.
- `Grep` — `VrPanelHitZoneResolver` referenced in `OpenXrSessionManager.kt`.
- `Grep` — `onControllerPanelHover` called in `OpenXrSessionManager.kt`.
- `Grep` — `Log\.d(` returns zero hits in `OpenXrSessionManager.kt`.

**Status:** `[ ]` not done

---

### Step 4.5 — Handle hover and click in VrPlayerActivity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 4.4

**Prompt for developer:**

> In `VrPlayerActivity` (or the `XrInputCallback` impl wired in Step 2.6), override `onControllerPanelHover(hand, zoneId, seekFraction)`:
>
> - `panelDriver.updateHoverZone(zoneId)`.
> - If `seekFraction >= 0f`: `panelDriver.updateSeekDrag(seekFraction)`.
>
> Also wire the trigger-button click event (from the existing `onInputEvent` / `XrInputEventType.TRIGGER_DOWN`) to generate a click on the currently hovered zone:
> - If `panelDriver.isPanelVisible() && lastHoveredZoneId >= 0`:
>   dispatch `PlaybackCommand` corresponding to `lastHoveredZoneId` (see Phase 05 for full command table). For now, implement only `ZONE_EXIT` → `onCommand(PlaybackCommand.ExitImmersive)` as a placeholder; other zones emit a Timber warning "zone N click not yet wired — Phase 05".
>
> Store `lastHoveredZoneId` as a field in the activity or its helper.

**Verification:**

- `Grep` — `onControllerPanelHover` overridden in `VrPlayerActivity.kt` or the wiring file.
- `Grep` — `panelDriver.updateHoverZone` called.
- `Grep` — `panelDriver.updateSeekDrag` called.
- `Grep` — `ZONE_EXIT` handled in the click dispatch.
- `Grep` — `Log\.d(` returns zero hits in touched files.

**Status:** `[ ]` not done

---

### Step 4.6 — Wire hit-tester to VrPlayerActivity (construction)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 4.4, Step 4.5

**Prompt for developer:**

> In `initializeVrRenderPipeline()` (or wherever `panelDriver` is constructed in Step 3.8):
>
> - Construct `VrRayPanelHitTester()`.
> - Construct `VrPanelHitZoneResolver(panelComposer)`.
> - Call `sessionManager.attachHitTester(hitTester, resolver)` (added in Step 4.4).
>
> In teardown: no explicit release needed (both are stateless or stateless-ish).

**Verification:**

- `Grep` — `VrRayPanelHitTester()` constructor call in `VrPlayerActivity.kt` (or equivalent init file).
- `Grep` — `VrPanelHitZoneResolver(` constructor call.
- `Grep` — `attachHitTester(` call.
- `Grep` — `Log\.d(` returns zero hits in touched file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `VrRayPanelHitTester.kt` ≤ 150 lines; `VrPanelHitZoneResolver.kt` ≤ 120 lines.
- [ ] On device (Quest 3): pointing the controller at a button highlights it (different background colour). Clicking Exit works. (Manual test — document in Blockers Log if unavailable.)
- [ ] `Grep` for `Log\.d(` in every Kotlin file touched returns zero hits.
- [ ] Dev log entries:

  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrRayPanelHitTester.kt" "feature" "Phase 04: new VrRayPanelHitTester (NDC → UV ray-plane intersection)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrPanelHitZoneResolver.kt" "feature" "Phase 04: new VrPanelHitZoneResolver (UV → zone ID, seek fraction)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt" "feature" "Phase 04: add onControllerPanelHover default method"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" "feature" "Phase 04: wire hit-test into onControllerPointerMove; emit onControllerPanelHover"
  ```

---

## Handoff Notes to Next Phase

- `VrPanelHitZoneResolver` returns zone IDs from `VrInteractivePanelComposer` constants. Phase 05 uses those same constants to build the full `PlaybackCommand` dispatch table.
- `ZONE_EXIT` is the only zone with a real command wired in this phase; all others emit a Timber warning and are completed in Phase 05.
- Seek drag fraction is forwarded to `panelDriver.updateSeekDrag(fraction)` in this phase; Phase 05 translates fractions to `PlaybackCommand.SeekTo(positionMs)` with the debounce guard from `spec_vr-input-reliability`.

---

## Rollback Plan

Revert phase commits. `VrRayPanelHitTester` and `VrPanelHitZoneResolver` are new files — delete them. `XrInputCallback` new default method is backwards-compatible. `OpenXrSessionManager` reverts the hit-test wiring. No native changes in this phase.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 3 (MD031 blank lines before code fences in Step 4.1 and Step 4.2 prompts; MD031 blank line before dev-log powershell block)
  - REVIEW applied: 1 (R3: Files Touched — added `VrInteractivePanelDriver.kt | Modified | ≤ 260` and `VrPlayerActivity.kt | Modified | ≤ current + 50`; Steps 4.4–4.6 modify these files)
  - DISCUSS proposed: 0
