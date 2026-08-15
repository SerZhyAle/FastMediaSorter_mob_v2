# Phase 04 — Per-eye draw

**Strategic spec:** [`../S0065_vr-controller-ray-visual.md`](../S0065_vr-controller-ray-visual.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-03
**Completed:** 2026-05-03 — vr-debug build SUCCESSFUL (v2.60.5031.648, arm64-v8a CMake openxr_native compiled)

---

## Objective

Implement `drawControllerRays` so it builds the line + cursor vertex buffer from `ctx.rayState` and renders it into the currently bound FBO using the prepared shader program. Hook the call into the per-eye render loop so the ray appears in both eyes of the projection layer.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (Phase 02 + Phase 03)
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `ctx.rayResources.ready` is set true at session creation (Phase 02 verified).
- [ ] `ctx.rayState` is populated each frame (Phase 03 verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrRayDraw.cpp` | Modified | ≤ 320 |
| `app_v2/src/vr/cpp/OpenXrFrame.cpp` | Modified | ≤ 510 |

> `OpenXrFrame.cpp` is currently 475 lines — projected ≤ 510 stays under 1000 but crosses 500. Create `temp/OpenXrFrame.cpp.<ts>.bak` before editing.

---

## Steps

### Step 04.1 — Implement `drawControllerRays` body

**Files:** `app_v2/src/vr/cpp/OpenXrRayDraw.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the stub body of `xrnative::drawControllerRays(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov)` so it:
>
> 1. Early-exit if `!ctx.rayResources.ready` or `!ctx.controllerRayEnabled.load()`.
> 2. Determine which hands are active: `const bool aL = ctx.rayState[0].active; const bool aR = ctx.rayState[1].active;`. If neither active → return.
> 3. Build a view-projection matrix for the eye:
>    - Projection from `eyeFov` (angles in radians) — standard `xrCreateProjectionFovMatrix`-equivalent: `m[0]=2/(tanRight-tanLeft); m[5]=2/(tanUp-tanDown); m[8]=(tanRight+tanLeft)/(tanRight-tanLeft); m[9]=(tanUp+tanDown)/(tanUp-tanDown); m[10]=-(zFar+zNear)/(zFar-zNear); m[11]=-1; m[14]=-(2*zFar*zNear)/(zFar-zNear);` with `zNear=0.05f`, `zFar=100.0f`. Implement column-major to match GL `uniformMatrix4fv(.., GL_FALSE, ..)`.
>    - View matrix = inverse of the eye-pose transform. Build the 4×4 from `eyePose.orientation` (quaternion → 3×3 rotation) and `eyePose.position`, then invert by transposing the rotation and negating the rotated translation.
>    - Multiply `mvp = proj * view` (column-major matrix multiply).
> 4. Build a vertex buffer for the active rays:
>    - Each line: 2 vertices (`origin`, `endpoint`) — keep the line as `GL_LINES` for simplicity (≤ 16-vertex budget honoured: 2 lines × 2 = 4 verts; 2 cursor quads × 4 verts = 8 verts; total 12). Strategic §3.2 mentions billboard for the line — relax to `GL_LINES` for v1; it satisfies the visible-feedback goal §2.1 with simpler GL state.
>    - Cursor: a quad in the HUD plane (z = `cursorZ`) of side 0.02 m, axis-aligned in HUD x/y — 4 vertices, `GL_TRIANGLE_STRIP`.
>    - Pack into a temporary `std::array<float, 16 * 3>` (or `std::vector<float>` reserved to 48 floats).
> 5. Bind the shader program / VAO / VBO (`glUseProgram`, `glBindVertexArray`, `glBindBuffer(GL_ARRAY_BUFFER, vbo)`); upload via `glBufferSubData(GL_ARRAY_BUFFER, 0, used*sizeof(float), data)`.
> 6. Set `glUniformMatrix4fv(uMvpLoc, 1, GL_FALSE, mvp);`.
> 7. Disable depth test + depth write while drawing (`glDisable(GL_DEPTH_TEST); GLboolean prevDepthMask; glGetBooleanv(GL_DEPTH_WRITEMASK, &prevDepthMask); glDepthMask(GL_FALSE);`). Enable blending: `glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);`.
> 8. Draw lines first: `glUniform4f(uColorLoc, 0.4f, 0.85f, 1.0f, 0.6f); glLineWidth(2.0f); glDrawArrays(GL_LINES, 0, lineVertCount);` — `lineVertCount` = `2 * (active hand count)`.
> 9. Draw cursors: for each hand with `hasCursor` true, `glUniform4f(uColorLoc, 0.95f, 0.95f, 0.95f, 0.85f); glDrawArrays(GL_TRIANGLE_STRIP, lineVertCount + i*4, 4);`.
> 10. Restore state: `glDepthMask(prevDepthMask); glEnable(GL_DEPTH_TEST); glDisable(GL_BLEND); glBindVertexArray(0); glUseProgram(0);`.
>
> Use only GLES3 calls. No new headers required beyond `<array>` / `<cmath>` already pulled via `OpenXrCtx.h` chain. Do not log per-frame.

**Verification:**

- `Grep` — `glUseProgram\(ctx\.rayResources\.program\)` present in `OpenXrRayDraw.cpp`.
- `Grep` — `glBufferSubData\(GL_ARRAY_BUFFER` present.
- `Grep` — `glDrawArrays\(GL_LINES` present.
- `Grep` — `glDrawArrays\(GL_TRIANGLE_STRIP` present.
- `Grep` — `glUniformMatrix4fv\(.*uMvpLoc` present.
- `Grep` — `controllerRayEnabled\.load\(\)` present (early-exit guard).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: app_v2/src/vr/cpp/OpenXrRayDraw.cpp (+175 LOC, total 301). Added `<array>` and `<cmath>` includes. Anonymous-namespace helpers for projection/view/mvp matrices. Dev log recorded.

---

### Step 04.2 — Hook draw call into per-eye render loop

**Files:** `app_v2/src/vr/cpp/OpenXrFrame.cpp`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `app_v2/src/vr/cpp/OpenXrFrame.cpp::renderFrame`, the per-eye loop runs at lines 124-194. After `invokeRenderCallback(..)` returns at line 143 (which leaves the eye FBO bound) and BEFORE the stereo-snapshot block at line 145, insert a single call:
>
> ```cpp
>     drawControllerRays(ctx, views[eye].pose, views[eye].fov);
> ```
>
> The FBO bound at line 137 is still current; the viewport set at line 138 is correct for the eye. The snapshot block at line 145-180 then sees the rendered ray as part of `glReadPixels` output, which is desired (snapshots reflect the user's actual view).
>
> Add `#include "OpenXrRayDraw.h"` near the existing `OpenXr*` includes at the top of the file. Do not call `drawControllerRays` from any other site.

**Verification:**

- `Grep` — `drawControllerRays\(ctx, views\[eye\]\.pose, views\[eye\]\.fov\)` matches exactly once in `OpenXrFrame.cpp`.
- `Grep` — `#include "OpenXrRayDraw.h"` present in `OpenXrFrame.cpp`.
- `Grep` — `invokeRenderCallback\(ctx, env, static_cast<int>\(eye\)` still present (sanity — not removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: app_v2/src/vr/cpp/OpenXrFrame.cpp (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-04\)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 04, on Quest 3 with controllers raised the user sees a thin cyan line from the controller aim-pose to the HUD plane (or 5 m into space when off-HUD), with a small white square cursor at the hit point on the HUD. Same behaviour for hand-tracking aim. The `nativeSetControllerRayEnabled(false)` API kills the rendering at the source. Phase 05 only updates documentation and the catalog; no further code edits needed.

Note: per-frame draw ordering — runtime composites `XrCompositionLayerProjection` (with our ray) below `XrCompositionLayerQuad` (HUD), so the line visually terminates at the HUD edge. This matches strategic §3.2 intent ("ray visible up to the HUD plane") via composition-layer ordering rather than the literal "draw after HUD" wording, which is impossible inside a composition-layer architecture.

---

## Rollback Plan

Revert phase commit(s). Phase 02 leaves `ctx.rayResources.ready == true` after rollback but the draw call is gone, so no GL is issued — safe.
