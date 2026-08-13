# Phase 01 — HUD Swapchain Scaling

**Strategic spec:** [`../S0080_enh-vr-hud-swapchain-resize.md`](../S0080_enh-vr-hud-swapchain-resize.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Replace the hardcoded 1024×256 HUD swapchain dimensions with values computed from the eye buffer size via `HUD_WIDTH_RATIO` / `HUD_HEIGHT_RATIO` constants, and propagate the computed size to `VrHudSceneComposer` so the Canvas layout matches the swapchain.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none — this is the foundation phase)*
- [ ] Strategic §6 research items: none open.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` exists (113 lines).
- [ ] `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` exists (634 lines).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/VrRenderPipelineManager_<timestamp>.kt` | New (backup) | — |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt` | Modified | ≤ 130 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 640 |

---

## Steps

### Step 1.1 — Backup VrRenderPipelineManager.kt to temp/

**Files:** `temp/VrRenderPipelineManager_<timestamp>.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` to `temp/VrRenderPipelineManager_<YYYYMMDD_HHmm>.kt` (replace `<timestamp>` with the current date-time). Do not modify either file.

**Verification:**

- `Glob` — at least one file matching `temp/VrRenderPipelineManager_*.kt` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Files: temp/VrRenderPipelineManager_20260504_1634.kt (backup). Dev log recorded.

---

### Step 1.2 — Add ratio constants and dynamic sizing to VrHudRenderer

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudRenderer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `VrHudRenderer.kt`:
>
> 1. Remove the `val width: Int = DEFAULT_WIDTH` and `val height: Int = DEFAULT_HEIGHT` constructor parameters.
> 2. Add `val width: Int` and `val height: Int` as class-level properties (not in the constructor).
> 3. Add an `init` block immediately after the property declarations that computes them:
>    ```kotlin
>    init {
>        val eyeW = sessionManager.eyeWidth(0)
>        val eyeH = sessionManager.eyeHeight(0)
>        width = if (eyeW > 0) (eyeW * HUD_WIDTH_RATIO).toInt() else DEFAULT_WIDTH
>        height = if (eyeH > 0) (eyeH * HUD_HEIGHT_RATIO).toInt() else DEFAULT_HEIGHT
>        Timber.i("VrHudRenderer: size %dx%d (eye %dx%d)", width, height, eyeW, eyeH)
>    }
>    ```
> 4. In the `companion object`, add before `DEFAULT_WIDTH`:
>    ```kotlin
>    const val HUD_WIDTH_RATIO = 0.80f
>    const val HUD_HEIGHT_RATIO = 0.22f
>    ```
>    Keep `DEFAULT_WIDTH = 1024` and `DEFAULT_HEIGHT = 256` as fallbacks.
>
> No other changes. All existing usages of `width` and `height` inside the class remain unchanged.

**Verification:**

- `Grep` — `const val HUD_WIDTH_RATIO = 0.80f` present in `VrHudRenderer.kt`.
- `Grep` — `const val HUD_HEIGHT_RATIO = 0.22f` present in `VrHudRenderer.kt`.
- `Grep` — `sessionManager.eyeWidth(0)` present in `VrHudRenderer.kt`.
- `Grep` — `sessionManager.eyeHeight(0)` present in `VrHudRenderer.kt`.
- `Grep` — `val width: Int = DEFAULT_WIDTH` does NOT appear in `VrHudRenderer.kt` (old constructor param removed).
- `Grep` — `Timber.i("VrHudRenderer: size` present in `VrHudRenderer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 6/6 PASS. Files: VrHudRenderer.kt (+10 LOC, ratio constants + init block). Dev log recorded.

---

### Step 1.3 — Pass renderer dimensions to VrHudSceneComposer in VrRenderPipelineManager

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt`
**Depends on:** Step 1.1, Step 1.2

**Prompt for developer:**

> In `VrRenderPipelineManager.kt`, find the `VrHudSceneComposer(...)` constructor call (currently passes only `context` and `onSeekBarClick`). Add `width = newRenderer.width` and `height = newRenderer.height` as named arguments so the composer's Canvas layout matches the computed swapchain size:
>
> ```kotlin
> val composer = VrHudSceneComposer(
>     context = activity,
>     width = newRenderer.width,
>     height = newRenderer.height,
>     onSeekBarClick = { Timber.d("HUD click: seek-bar") },
> )
> ```
>
> No other changes.

**Verification:**

- `Grep` — `width = newRenderer.width` present in `VrRenderPipelineManager.kt`.
- `Grep` — `height = newRenderer.height` present in `VrRenderPipelineManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: VrRenderPipelineManager.kt (+2 LOC, width/height args to VrHudSceneComposer). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run `.\build-debug.PS1` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API of `VrHudRenderer` changed (constructor signature) → `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `VrHudRenderer.width` and `VrHudRenderer.height` are now computed from the eye buffer at construction time; the native swapchain and the Canvas layer are in sync.
- `VrHudSceneComposer` receives the same dimensions → `VrHudElementRegistry` is also correctly sized.
- Phase 02 regenerates the catalog render to reflect the constructor change.

---

## Rollback Plan

Revert phase commit(s). No data migration or user-facing surface changed. Backup at `temp/VrRenderPipelineManager_<timestamp>.kt`.
