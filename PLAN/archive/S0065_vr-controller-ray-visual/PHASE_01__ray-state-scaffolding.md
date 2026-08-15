# Phase 01 — Ray state and GL scaffolding

**Strategic spec:** [`../S0065_vr-controller-ray-visual.md`](../S0065_vr-controller-ray-visual.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03 (BUILD-DEFERRED to end of Phase 04 — pure scaffolding, no executable code paths altered)

---

## Objective

Introduce shared per-hand ray-state plus GL-resource container in `OpenXrCtx.h`, declare the ray-draw API in a new header, and create an empty implementation file plus shader-source constants. No behaviour changes — pure scaffolding.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none)
- [ ] Strategic §6 research items blocking this phase are Resolved. (yes — see INDEX Pre-Implementation Blockers)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrCtx.h` | Modified | ≤ 320 |
| `app_v2/src/vr/cpp/OpenXrRayDraw.h` | New | ≤ 40 |
| `app_v2/src/vr/cpp/OpenXrRayDraw.cpp` | New | ≤ 80 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | ≤ 80 |

> No file projected past the 500/1500ine gates.

---

## Steps

### Step 01.1 — Add `RayState` and `RayRenderResources` to `OpenXrCtx.h`

**Files:** `app_v2/src/vr/cpp/OpenXrCtx.h`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/vr/cpp/OpenXrCtx.h`, immediately above the `struct XrCtx` declaration (currently at line 234), add two new types in the `xrnative` namespace:
>
> 1. `struct RayState { bool active = false; float originX = 0.0f, originY = 0.0f, originZ = 0.0f; float endX = 0.0f, endY = 0.0f, endZ = 0.0f; bool hasCursor = false; float cursorX = 0.0f, cursorY = 0.0f, cursorZ = 0.0f; };` — one instance per hand index (Left=0, Right=1). Plain POD; written from the input/hand-tracking thread, read from the render path. No atomics — both writers and the reader run in the same XR frame thread.
> 2. `struct RayRenderResources { bool ready = false; GLuint program = 0; GLuint vbo = 0; GLuint vao = 0; GLint uMvpLoc = -1; GLint uColorLoc = -1; GLint aPosLoc = -1; };` — owned by the render path; populated in Phase 02.
>
> Then inside `struct XrCtx`, add two members near `controllerRayEnabled` (line 261):
>
> - `RayState rayState[2]{};`
> - `RayRenderResources rayResources{};`
>
> Do not modify any existing members. Do not move or rename `controllerRayEnabled`.

**Verification:**

- `Grep` — `struct RayState` matches exactly once in `app_v2/src/vr/cpp/OpenXrCtx.h`.
- `Grep` — `struct RayRenderResources` matches exactly once in `app_v2/src/vr/cpp/OpenXrCtx.h`.
- `Grep` — `RayState rayState\[2\]` matches exactly once in `app_v2/src/vr/cpp/OpenXrCtx.h`.
- `Grep` — `RayRenderResources rayResources` matches exactly once in `app_v2/src/vr/cpp/OpenXrCtx.h`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/vr/cpp/OpenXrCtx.h (+22 LOC). Dev log recorded.

---

### Step 01.2 — Create `OpenXrRayDraw.h` with API declarations

**Files:** `app_v2/src/vr/cpp/OpenXrRayDraw.h`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new header file `app_v2/src/vr/cpp/OpenXrRayDraw.h` containing the declarations:
>
> ```cpp
> #pragma once
>
> #include "OpenXrCtx.h"
>
> #include <openxr/openxr.h>
>
> namespace xrnative
> {
>     bool initRayResources(XrCtx &ctx);
>     void destroyRayResources(XrCtx &ctx);
>     void drawControllerRays(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov);
> } // namespace xrnative
> ```
>
> No other content. The implementation file stays empty in this step — bodies arrive in Phase 02 / Phase 04.

**Verification:**

- `Glob` — `app_v2/src/vr/cpp/OpenXrRayDraw.h` exists.
- `Grep` — `bool initRayResources\(XrCtx &ctx\);` present in the file.
- `Grep` — `void destroyRayResources\(XrCtx &ctx\);` present.
- `Grep` — `void drawControllerRays\(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov\);` present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/vr/cpp/OpenXrRayDraw.h (new, 12 LOC). Dev log recorded.

---

### Step 01.3 — Create `OpenXrRayDraw.cpp` skeleton with shader sources

**Files:** `app_v2/src/vr/cpp/OpenXrRayDraw.cpp`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `app_v2/src/vr/cpp/OpenXrRayDraw.cpp` containing exactly:
>
> ```cpp
> #include "OpenXrRayDraw.h"
> #include "OpenXrLog.h"
>
> namespace
> {
>     constexpr const char *kVertexShaderSrc =
>         "#version 300 es\n"
>         "uniform mat4 uMvp;\n"
>         "in vec3 aPos;\n"
>         "void main() { gl_Position = uMvp * vec4(aPos, 1.0); }\n";
>
>     constexpr const char *kFragmentShaderSrc =
>         "#version 300 es\n"
>         "precision mediump float;\n"
>         "uniform vec4 uColor;\n"
>         "out vec4 fragColor;\n"
>         "void main() { fragColor = uColor; }\n";
> }
>
> bool xrnative::initRayResources(XrCtx &ctx)
> {
>     (void)ctx;
>     return false;
> }
>
> void xrnative::destroyRayResources(XrCtx &ctx)
> {
>     (void)ctx;
> }
>
> void xrnative::drawControllerRays(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov)
> {
>     (void)ctx;
>     (void)eyePose;
>     (void)eyeFov;
> }
> ```
>
> All three function bodies are stubs. They are filled in Phases 02 and 04. The shader-source constants stay at file scope so Phase 02 can use them.

**Verification:**

- `Glob` — `app_v2/src/vr/cpp/OpenXrRayDraw.cpp` exists.
- `Grep` — `kVertexShaderSrc` matches in `OpenXrRayDraw.cpp`.
- `Grep` — `kFragmentShaderSrc` matches in `OpenXrRayDraw.cpp`.
- `Grep` — `xrnative::initRayResources` present.
- `Grep` — `xrnative::destroyRayResources` present.
- `Grep` — `xrnative::drawControllerRays` present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 6/6 PASS. Files: app_v2/src/vr/cpp/OpenXrRayDraw.cpp (new, 36 LOC). Dev log recorded.

---

### Step 01.4 — Register `OpenXrRayDraw.cpp` in `CMakeLists.txt`

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `app_v2/src/vr/cpp/CMakeLists.txt`, the `add_library(openxr_native SHARED ..)` block currently lists seven sources ending with `OpenXrLog.cpp`. Append `OpenXrRayDraw.cpp` to that list (one new line, indented to match siblings). Do not change any other line.

**Verification:**

- `Grep` — `OpenXrRayDraw.cpp` matches exactly once in `app_v2/src/vr/cpp/CMakeLists.txt`.
- `Grep` — `add_library\(openxr_native SHARED` still present.
- `Grep` — `OpenXrLog\.cpp` still present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: app_v2/src/vr/cpp/CMakeLists.txt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-01\)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`ctx.rayState[2]` is now allocated and zero-initialised; `ctx.rayResources` exists but is `ready = false`. Phase 02 fills `initRayResources`/`destroyRayResources` to flip `ready` and wires them into the session lifecycle. Phase 03 starts populating `rayState` from input/hand paths. Drawing in Phase 04 is a no-op until `rayResources.ready == true`.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed. New file `OpenXrRayDraw.{h,cpp}` can be deleted; the new `RayState` / `RayRenderResources` members are inert.
