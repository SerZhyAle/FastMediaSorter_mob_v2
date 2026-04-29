# Phase 02 — Lifecycle subsystem extraction

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Populate `OpenXrCtx.h` with the shared structs (`LayerConfig`, `SwapchainImage`, `EyeSwapchain`, `XrCtx`, `InputSystem`, `HandSystem`, `StereoSnapshot`) under namespace `xrnative::`. Move all instance/system/session-lifecycle code (`enumerateAndCreateInstance`, `handleSessionStateChange`, `pollEvents`, `releaseCallback`, `destroyAll`) out of `OpenXrNative.cpp` into a new `OpenXrLifecycle.cpp`. After this phase `OpenXrNative.cpp` drops by ~480 LOC and the anonymous-namespace block is replaced by `using namespace xrnative;` at file scope.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (header skeleton present, log helpers extracted).
- [ ] Backup of `OpenXrNative.cpp` placed in `temp/` (file still > 500 LOC).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrCtx.h` | Modified | ≤ 350 |
| `app_v2/src/vr/cpp/OpenXrLifecycle.h` | New | ≤ 80 |
| `app_v2/src/vr/cpp/OpenXrLifecycle.cpp` | New | ≤ 700 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | starts ≤ 3380 → ends ≤ 2900 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | unchanged ≤ 60 |

---

## Steps

### Step 02.1 — Migrate shared types into `OpenXrCtx.h`

**Files:** `app_v2/src/vr/cpp/OpenXrCtx.h`, `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Move the following struct definitions from inside the anonymous namespace of `OpenXrNative.cpp` (lines ~291–520 of the pre-Phase-01 file) into `OpenXrCtx.h`, all under `namespace xrnative {}`:
>
> - `LayerConfig`
> - `SwapchainImage`
> - `EyeSwapchain`
> - `InputSystem` (forward-declared; full body too)
> - `HandSystem` (forward-declared; full body too)
> - `StereoSnapshot`
> - `XrCtx` — the central context aggregate, with all its member-method declarations (forward decls already present at lines 523–543). Method bodies do NOT move yet — only the struct skeleton with declarations.
>
> Preserve include directives required by these types (`<openxr/openxr.h>`, `<EGL/egl.h>`, `<GLES3/gl3.h>`, `<jni.h>`, `<atomic>`, `<chrono>`, `<mutex>`, `<vector>`, `<thread>`, `<android/native_window.h>`). Replace the anonymous namespace in `OpenXrNative.cpp` with `using namespace xrnative;` placed below the includes.
>
> Constructor/destructor of `XrCtx` (if any) and inline trivial methods may stay in the header. Larger method bodies remain in `OpenXrNative.cpp` for now — Phase 02 Step 02.3 below will move the lifecycle subset out.

**Verification:**

- `Grep` — `struct XrCtx` matches once and only inside `OpenXrCtx.h`.
- `Grep` — `struct LayerConfig` matches once in `OpenXrCtx.h`.
- `Grep` — `^\s*namespace\s*\{` (anonymous namespace opener) does NOT match in `OpenXrNative.cpp`.
- `Grep` — `using namespace xrnative` matches once in `OpenXrNative.cpp`.
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 3200.

**Status:** `[ ]` not done

---

### Step 02.2 — Author `OpenXrLifecycle.h`

**Files:** `app_v2/src/vr/cpp/OpenXrLifecycle.h`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `OpenXrLifecycle.h` declaring the public surface used by JNI exports for session lifecycle:
>
> ```cpp
> #pragma once
> #include "OpenXrCtx.h"
>
> namespace xrnative {
>     // Exposed by OpenXrLifecycle.cpp — JNI exports stay in OpenXrNative.cpp and call these.
>     bool enumerateAndCreateInstance(XrCtx& ctx, JNIEnv* env, jobject activity);
>     void handleSessionStateChange(XrCtx& ctx, XrEventDataSessionStateChanged* e);
>     bool pollEvents(XrCtx& ctx);
>     void releaseCallback(XrCtx& ctx, JNIEnv* env);
>     void destroyAll(XrCtx& ctx);
> }
> ```
>
> Signatures take `XrCtx&` explicitly so the implementation file stays free of file-scope globals beyond the singleton handle. Existing call sites in `OpenXrNative.cpp` adapt by passing `g_ctx` (the singleton already in scope).

**Verification:**

- `Glob` — `OpenXrLifecycle.h` exists.
- `Grep` — each of the five function declarations matches in the header.
- `Grep` — `Log\.d\(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.3 — Move method bodies into `OpenXrLifecycle.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrLifecycle.cpp`, `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `OpenXrLifecycle.cpp` in the same directory. It includes `"OpenXrLifecycle.h"`, `"OpenXrLog.h"`, plus any `<openxr/...>` headers the bodies need. Move the following method bodies from `OpenXrNative.cpp` to `OpenXrLifecycle.cpp`:
>
> - `XrCtx::enumerateAndCreateInstance(JNIEnv*, jobject)` → free function `bool xrnative::enumerateAndCreateInstance(XrCtx&, JNIEnv*, jobject)` that delegates: keep the original body, replace `this->` with `ctx.`.
> - `XrCtx::handleSessionStateChange(XrEventDataSessionStateChanged*)` → free function with `XrCtx&` first param.
> - `XrCtx::pollEvents()` → free function.
> - `XrCtx::releaseCallback(JNIEnv*)` → free function.
> - `XrCtx::destroyAll()` → free function.
>
> In `OpenXrNative.cpp`, replace the deleted bodies with one-line forwards if any internal callers still go through member syntax (use `xrnative::pollEvents(ctx)` instead). Confirm no member-style call to these five methods remains.
>
> Keep `XrCtx` member declarations for these methods only as stubs — wait, simpler: remove them from the struct (since they are now free functions). Update any `g_ctx.method()` call sites in `OpenXrNative.cpp` to `xrnative::method(g_ctx, ...)`.

**Verification:**

- `Glob` — `OpenXrLifecycle.cpp` exists, ≤ 700 LOC (`wc -l`).
- `Grep` — `bool xrnative::enumerateAndCreateInstance` matches in `OpenXrLifecycle.cpp`.
- `Grep` — `XrCtx::enumerateAndCreateInstance` does NOT match anywhere in the cpp directory.
- `Grep` — `g_ctx\.enumerateAndCreateInstance\(` does NOT match in `OpenXrNative.cpp`.
- `Grep` — `xrnative::enumerateAndCreateInstance` matches at least once in `OpenXrNative.cpp` (call site preserved).
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 2900.

**Status:** `[ ]` not done

---

### Step 02.4 — Update CMake + build verification

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Append `OpenXrLifecycle.cpp` to the `add_library(openxr_native SHARED ...)` source list. Run `/build` for `vr debug`. Confirm clean compile and no `UnsatisfiedLinkError` in logcat on Quest 3 startup (manual smoke step, deferred to user — flag as `[manual — deferred to human]`).

**Verification:**

- `Grep` — `OpenXrLifecycle.cpp` appears in `CMakeLists.txt`.
- Build output indicates VR flavor compiles without errors.
- `Grep` — `TODO(phase-02)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build vr debug` PASS.
- [ ] `OpenXrNative.cpp` ≤ 2900 LOC.
- [ ] `OpenXrLifecycle.cpp` ≤ 700 LOC.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`XrCtx` and friends now live in `OpenXrCtx.h` with `xrnative::` namespace; `using namespace xrnative` is at file scope of `OpenXrNative.cpp`. New `.cpp` files in Phases 03/04 follow the same pattern: include the header, reference free functions, delegate from JNI exports.

---

## Rollback Plan

Revert phase commit(s); the Phase 01 backup in `temp/` is still valid (the cpp body retained byte-equivalent semantics, just moved between TUs).

---

## Revision History

- **2026-04-29** — by `/spec-update` (`claude-sonnet-4-6`, focus: verifiability)
  - Applied: 1. Proposed (DISCUSS): 0.
  - Step 02.1 Verification: fixed grep pattern `namespace\s*$` → `^\s*namespace\s*\{` (the old pattern does not match `namespace {` same-line style).
