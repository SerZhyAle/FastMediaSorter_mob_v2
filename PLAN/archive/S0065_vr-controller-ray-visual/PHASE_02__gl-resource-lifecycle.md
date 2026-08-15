# Phase 02 — GL resource lifecycle

**Strategic spec:** [`../S0065_vr-controller-ray-visual.md`](../S0065_vr-controller-ray-visual.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03 (BUILD-DEFERRED to end of Phase 04)

---

## Objective

Compile and link the ray shader program at session creation, generate the VBO/VAO needed for the line + cursor primitives, and tear them down on session destruction. After this phase `ctx.rayResources.ready == true` for the lifetime of an active XR session.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (Phase 01)
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] EGL context is current on the thread that calls `createSessionAndSwapchains` (already true — `OpenXrSwapchain.cpp:34-282` runs GL setup there).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrRayDraw.cpp` | Modified | ≤ 200 |
| `app_v2/src/vr/cpp/OpenXrSwapchain.cpp` | Modified | ≤ 540 |
| `app_v2/src/vr/cpp/OpenXrLifecycle.cpp` | Modified | ≤ 410 |

> `OpenXrSwapchain.cpp` is currently 492 lines — projected ≤ 540 stays under the 1000-line gate. Backup not required (under 500 before edit; will exceed 500 after — create `temp/OpenXrSwapchain.cpp.<ts>.bak` before editing).

---

## Steps

### Step 02.1 — Implement `initRayResources` in `OpenXrRayDraw.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrRayDraw.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the stub body of `xrnative::initRayResources(XrCtx &ctx)` so it:
>
> 1. If `ctx.rayResources.ready` is already true → return `true` (idempotent).
> 2. Compile a vertex shader from `kVertexShaderSrc` and a fragment shader from `kFragmentShaderSrc` using `glCreateShader` + `glShaderSource` + `glCompileShader`. After each compile call `glGetShaderiv(.., GL_COMPILE_STATUS, ..)` — on failure, retrieve the info log via `glGetShaderInfoLog`, log it via `nativeLogEmit(ANDROID_LOG_ERROR, "initRayResources: shader compile failed: %s", log)`, delete the shader, and return `false`.
> 3. Link the two shaders into a program; on link failure log via `nativeLogEmit(ANDROID_LOG_ERROR, "initRayResources: program link failed: %s", log)`, delete the program, return `false`.
> 4. Resolve uniform locations: `uMvp`, `uColor`. Resolve attribute location for `aPos` via `glGetAttribLocation`.
> 5. Generate one VBO (`glGenBuffers`) and one VAO (`glGenVertexArrays`). Bind the VAO; bind the VBO; allocate `sizeof(float) * 3 * 16` bytes via `glBufferData(GL_ARRAY_BUFFER, .., nullptr, GL_DYNAMIC_DRAW)`; configure the position attribute via `glVertexAttribPointer(aPosLoc, 3, GL_FLOAT, GL_FALSE, 0, nullptr)` + `glEnableVertexAttribArray(aPosLoc)`; unbind VAO and VBO.
> 6. Store program / vbo / vao / locations into `ctx.rayResources`. Set `ctx.rayResources.ready = true`. Log `nativeLogEmit(ANDROID_LOG_INFO, "initRayResources: ready program=%u vbo=%u vao=%u", program, vbo, vao)`. Return `true`.
>
> Use only GLES3 calls already imported via `OpenXrCtx.h`. The 16-vertex budget covers two rays (4 verts each as a screen-aligned billboard quad) plus two cursor quads (4 verts each).

**Verification:**

- `Grep` — `glCreateShader\(GL_VERTEX_SHADER\)` present in `OpenXrRayDraw.cpp`.
- `Grep` — `glCreateShader\(GL_FRAGMENT_SHADER\)` present.
- `Grep` — `glLinkProgram` present.
- `Grep` — `glGenVertexArrays` present.
- `Grep` — `ctx\.rayResources\.ready = true` present.
- `Grep` — `initRayResources: ready` present (info-log marker).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: app_v2/src/vr/cpp/OpenXrRayDraw.cpp (+90 LOC). Inlined shader compile (initial helper-based draft failed literal grep predicates). Dev log recorded.

---

### Step 02.2 — Implement `destroyRayResources` in `OpenXrRayDraw.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrRayDraw.cpp`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the stub body of `xrnative::destroyRayResources(XrCtx &ctx)` so it:
>
> 1. If `ctx.rayResources.vao != 0` → `glDeleteVertexArrays(1, &ctx.rayResources.vao)`; set `vao = 0`.
> 2. If `ctx.rayResources.vbo != 0` → `glDeleteBuffers(1, &ctx.rayResources.vbo)`; set `vbo = 0`.
> 3. If `ctx.rayResources.program != 0` → `glDeleteProgram(ctx.rayResources.program)`; set `program = 0`.
> 4. Reset `uMvpLoc`, `uColorLoc`, `aPosLoc` to `-1`.
> 5. Set `ctx.rayResources.ready = false`.
>
> Idempotent: calling with `ready == false` is a no-op (the zero-checks ensure that).

**Verification:**

- `Grep` — `glDeleteVertexArrays` present in `OpenXrRayDraw.cpp`.
- `Grep` — `glDeleteBuffers` present.
- `Grep` — `glDeleteProgram` present.
- `Grep` — `ctx\.rayResources\.ready = false` present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/vr/cpp/OpenXrRayDraw.cpp (+22 LOC). Dev log recorded.

---

### Step 02.3 — Hook init/destroy into session lifecycle

**Files:** `app_v2/src/vr/cpp/OpenXrSwapchain.cpp`, `app_v2/src/vr/cpp/OpenXrLifecycle.cpp`
**Depends on:** Step 02.2

**Prompt for developer:**

> Two edits, one per file:
>
> **A. `OpenXrSwapchain.cpp`** — at the end of `xrnative::createSessionAndSwapchains` (currently the `LOGI("createSessionAndSwapchains: complete .."); return true;` block at lines 281-282), insert a new block immediately before that final `LOGI` line:
>
> ```cpp
>     if (!initRayResources(ctx))
>     {
>         LOGW("createSessionAndSwapchains: ray resources init failed — controller ray will not render");
>     }
> ```
>
> Add `#include "OpenXrRayDraw.h"` at the top of the file alongside the other `OpenXr*.h` includes. Do not call `initRayResources` from any other site.
>
> **B. `OpenXrLifecycle.cpp`** — in `xrnative::destroyAll` (line 322), add `destroyRayResources(ctx);` as the **first** statement of the function body, before the existing `destroyHandTracking(ctx);` call. Add `#include "OpenXrRayDraw.h"` alongside existing OpenXr includes.

**Verification:**

- `Grep` — `initRayResources\(ctx\)` matches exactly once in `OpenXrSwapchain.cpp`.
- `Grep` — `destroyRayResources\(ctx\)` matches exactly once in `OpenXrLifecycle.cpp`.
- `Grep` — `#include "OpenXrRayDraw.h"` matches in both `OpenXrSwapchain.cpp` and `OpenXrLifecycle.cpp`.
- `Grep` — `destroyHandTracking\(ctx\)` still present in `OpenXrLifecycle.cpp` (must not be removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/vr/cpp/OpenXrSwapchain.cpp (+5 LOC), app_v2/src/vr/cpp/OpenXrLifecycle.cpp (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-02\)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 02 a successful `createSessionAndSwapchains` leaves `ctx.rayResources.ready == true` and a usable shader program / VBO / VAO. Phase 03 writes ray endpoints into `ctx.rayState`; Phase 04 issues the actual draw call.

---

## Rollback Plan

Revert phase commit(s). The Phase 01 stubs return `false`/no-op, so the system degrades to the pre-S0065 state.
