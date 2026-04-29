# Phase 03 — Swapchain + frame loop extraction

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Move the swapchain creation/destruction code (`createSessionAndSwapchains`, `createHudSwapchainImpl`, `destroyHudSwapchainImpl`, `createPanelSwapchainImpl`, `destroyPanelSwapchainImpl`) into `OpenXrSwapchain.cpp` and the frame loop (`renderFrame`, `invokeRenderCallback`) into `OpenXrFrame.cpp`. After this phase `OpenXrNative.cpp` drops by ~1070 LOC.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (shared header populated with `XrCtx`/`EyeSwapchain`/`SwapchainImage`/`LayerConfig`).
- [ ] Backup of `OpenXrNative.cpp` placed in `temp/` (file still > 500 LOC).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrSwapchain.h` | New | ≤ 80 |
| `app_v2/src/vr/cpp/OpenXrSwapchain.cpp` | New | ≤ 600 |
| `app_v2/src/vr/cpp/OpenXrFrame.h` | New | ≤ 60 |
| `app_v2/src/vr/cpp/OpenXrFrame.cpp` | New | ≤ 600 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | starts ≤ 2900 → ends ≤ 1900 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | unchanged ≤ 60 |

---

## Steps

### Step 03.1 — Author `OpenXrSwapchain.h`

**Files:** `app_v2/src/vr/cpp/OpenXrSwapchain.h`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `OpenXrSwapchain.h` declaring the swapchain surface:
>
> ```cpp
> #pragma once
> #include "OpenXrCtx.h"
>
> namespace xrnative {
>     bool createSessionAndSwapchains(XrCtx& ctx);
>     bool createHudSwapchain(XrCtx& ctx, uint32_t requestedWidth, uint32_t requestedHeight);
>     void destroyHudSwapchain(XrCtx& ctx);
>     bool createPanelSwapchain(XrCtx& ctx, uint32_t requestedWidth, uint32_t requestedHeight);
>     void destroyPanelSwapchain(XrCtx& ctx);
> }
> ```
>
> The `Impl` suffix from the source is dropped — these are now the canonical entry points.

**Verification:**

- `Glob` — `OpenXrSwapchain.h` exists.
- `Grep` — five function declarations present inside `xrnative` namespace.

**Status:** `[ ]` not done

---

### Step 03.2 — Move swapchain bodies into `OpenXrSwapchain.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrSwapchain.cpp`, `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 03.1

**Prompt for developer:**

> Move method bodies from `OpenXrNative.cpp`:
>
> - `XrCtx::createSessionAndSwapchains()` → `bool xrnative::createSessionAndSwapchains(XrCtx&)`.
> - `XrCtx::createHudSwapchainImpl(uint32_t, uint32_t)` → `bool xrnative::createHudSwapchain(XrCtx&, uint32_t, uint32_t)`.
> - `XrCtx::destroyHudSwapchainImpl()` → `void xrnative::destroyHudSwapchain(XrCtx&)`.
> - `XrCtx::createPanelSwapchainImpl(uint32_t, uint32_t)` → `bool xrnative::createPanelSwapchain(XrCtx&, uint32_t, uint32_t)`.
> - `XrCtx::destroyPanelSwapchainImpl()` → `void xrnative::destroyPanelSwapchain(XrCtx&)`.
>
> Replace `this->` with `ctx.`. Update internal callers (e.g. `XrCtx::createSessionAndSwapchains` likely calls `createHudSwapchainImpl` / `createPanelSwapchainImpl` internally — translate those calls to free-function form). Remove the corresponding declarations from the `XrCtx` struct in `OpenXrCtx.h`. In `OpenXrNative.cpp` JNI exports / lifecycle code, replace any remaining call sites with `xrnative::createSessionAndSwapchains(g_ctx)` etc.

**Verification:**

- `Glob` — `OpenXrSwapchain.cpp` exists, ≤ 600 LOC.
- `Grep` — `bool xrnative::createSessionAndSwapchains` matches once in `OpenXrSwapchain.cpp`.
- `Grep` — `XrCtx::createSessionAndSwapchains` does NOT match in any file.
- `Grep` — `XrCtx::createHudSwapchainImpl` does NOT match anywhere.
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 2300.

**Status:** `[ ]` not done

---

### Step 03.3 — Author `OpenXrFrame.h` + body

**Files:** `app_v2/src/vr/cpp/OpenXrFrame.h`, `app_v2/src/vr/cpp/OpenXrFrame.cpp`
**Depends on:** Step 03.2

**Prompt for developer:**

> Header:
>
> ```cpp
> #pragma once
> #include "OpenXrCtx.h"
>
> namespace xrnative {
>     void renderFrame(XrCtx& ctx, JNIEnv* env);
>     void invokeRenderCallback(XrCtx& ctx, JNIEnv* env, int eye, int fbo, int width, int height);
> }
> ```
>
> Body: move `XrCtx::renderFrame(JNIEnv*)` and `XrCtx::invokeRenderCallback(JNIEnv*, int, int, int, int)` into `OpenXrFrame.cpp` as `xrnative::` free functions taking `XrCtx&`. The render-thread JNI guard logic (`AttachCurrentThread`, `clock_gettime`) stays inside the body verbatim. Remove the corresponding declarations from `XrCtx`.

**Verification:**

- `Glob` — `OpenXrFrame.h`, `OpenXrFrame.cpp` exist.
- `Grep` — `void xrnative::renderFrame` matches once in `OpenXrFrame.cpp`.
- `Grep` — `XrCtx::renderFrame` does NOT match.
- `wc -l app_v2/src/vr/cpp/OpenXrFrame.cpp` ≤ 600.

**Status:** `[ ]` not done

---

### Step 03.4 — Update remaining callers in `OpenXrNative.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 03.3

**Prompt for developer:**

> The render-thread loop and the JNI export `nativeRenderOnce` (or whichever JNI export drives the per-frame callback) still call `g_ctx.renderFrame(env)` style. Replace with `xrnative::renderFrame(g_ctx, env)`. Add `#include "OpenXrSwapchain.h"` and `#include "OpenXrFrame.h"` to the includes block. Verify file size ≤ 1900 LOC.

**Verification:**

- `Grep` — `#include "OpenXrSwapchain.h"` matches once in `OpenXrNative.cpp`.
- `Grep` — `#include "OpenXrFrame.h"` matches once.
- `Grep` — `g_ctx\.renderFrame\(` does NOT match.
- `Grep` — `xrnative::renderFrame\(g_ctx` matches at least once.
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 1900.

**Status:** `[ ]` not done

---

### Step 03.5 — Update CMake + build verification

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Append `OpenXrSwapchain.cpp` and `OpenXrFrame.cpp` to the `add_library(openxr_native SHARED ...)` source list. Run `/build vr debug`. Confirm clean compile.

**Verification:**

- `Grep` — `OpenXrSwapchain.cpp` and `OpenXrFrame.cpp` both appear in `CMakeLists.txt`.
- Build output indicates VR flavor compiles without errors.
- `Grep` — `TODO(phase-03)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build vr debug` PASS.
- [ ] `OpenXrNative.cpp` ≤ 1900 LOC.
- [ ] `OpenXrSwapchain.cpp` and `OpenXrFrame.cpp` each ≤ 600 LOC.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`OpenXrNative.cpp` is now ~1900 LOC and contains: input subsystem (~700 LOC), hand-tracking subsystem (~500 LOC), JNI exports surface (~530 LOC), plus globals/glue (~170 LOC). Phase 04 extracts the input + hand-tracking subsystems, dropping the file to ~700 LOC.

---

## Rollback Plan

Revert phase commit(s); previous backups remain valid. No JNI surface or Kotlin code was touched.
