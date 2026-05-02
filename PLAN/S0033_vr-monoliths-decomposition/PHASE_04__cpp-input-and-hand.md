# Phase 04 — Input + hand-tracking extraction

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (manual device smoke deferred)
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05 (only because the cpp track must finish before the Activity track to keep diffs reviewable; no semantic dependency)
**Steps done:** 5 / 5
**Started:** 2026-04-30
**Completed:** 2026-04-30

---

## Objective

Move all controller-input methods (`setupActionSet`, `createControllerAimSpaces`, `syncInputActions`, `destroyInputHandles`, `releaseInputCallback`, `emitInputEvent`, `emitPointerMove`, `emitControllerPointerMove`, `syncControllerAimRay`) into `OpenXrInput.cpp`, and all hand-tracking methods (`initHandTracking`, `destroyHandTracking`, `syncHandTracking`) into `OpenXrHandTracking.cpp`. The `InputSystem` and `HandSystem` structs already live in `OpenXrCtx.h` after Phase 02. After this phase `OpenXrNative.cpp` ≤ 700 LOC, satisfying the strategic ≤ 1000 LOC cpp budget.

---

## Prerequisites

- [x] Phase 03 ✅ Done (swapchain + frame already extracted; `OpenXrNative.cpp` ≤ 1900 LOC).
- [x] Backup of `OpenXrNative.cpp` placed in `temp/`.
- [x] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrInput.h` | New | ≤ 80 |
| `app_v2/src/vr/cpp/OpenXrInput.cpp` | New | ≤ 750 |
| `app_v2/src/vr/cpp/OpenXrHandTracking.h` | New | ≤ 60 |
| `app_v2/src/vr/cpp/OpenXrHandTracking.cpp` | New | ≤ 600 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | starts ≤ 1900 → ends ≤ 700 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | unchanged ≤ 60 |

---

## Steps

### Step 04.1 — Author `OpenXrInput.h` + body

**Files:** `app_v2/src/vr/cpp/OpenXrInput.h`, `app_v2/src/vr/cpp/OpenXrInput.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Header:
>
> ```cpp
> #pragma once
> #include "OpenXrCtx.h"
>
> namespace xrnative {
>     bool setupActionSet(XrCtx& ctx);
>     bool createControllerAimSpaces(XrCtx& ctx);
>     void syncInputActions(XrCtx& ctx, JNIEnv* env);
>     void syncControllerAimRay(XrCtx& ctx, JNIEnv* env);
>     void destroyInputHandles(XrCtx& ctx);
>     void releaseInputCallback(XrCtx& ctx, JNIEnv* env);
>     void emitInputEvent(XrCtx& ctx, JNIEnv* env, int32_t type, int32_t hand, float value, int32_t source);
>     void emitPointerMove(XrCtx& ctx, JNIEnv* env, int32_t hand, float ndcX, float ndcY);
>     void emitControllerPointerMove(XrCtx& ctx, JNIEnv* env, int32_t hand, float ndcX, float ndcY);
> }
> ```
>
> Move the bodies of all nine methods from `OpenXrNative.cpp` into `OpenXrInput.cpp`, replacing `this->`/`m_input.` with `ctx.input.` (or whatever the field name is). Remove the declarations from `XrCtx` in `OpenXrCtx.h` (keep `InputSystem` struct definition intact — only the methods migrate; if methods were on `InputSystem` itself, switch them to free functions taking `InputSystem&`).

**Verification:**

- `Glob` — `OpenXrInput.h`, `OpenXrInput.cpp` exist.
- `Grep` — all nine `xrnative::` definitions present in `OpenXrInput.cpp`.
- `Grep` — `XrCtx::setupActionSet` does NOT match anywhere.
- `Grep` — `XrCtx::syncInputActions` does NOT match.
- `wc -l app_v2/src/vr/cpp/OpenXrInput.cpp` ≤ 750.

**Status:** `[x]` done

---

### Step 04.2 — Author `OpenXrHandTracking.h` + body

**Files:** `app_v2/src/vr/cpp/OpenXrHandTracking.h`, `app_v2/src/vr/cpp/OpenXrHandTracking.cpp`
**Depends on:** Step 04.1

**Prompt for developer:**

> Header:
>
> ```cpp
> #pragma once
> #include "OpenXrCtx.h"
>
> namespace xrnative {
>     bool initHandTracking(XrCtx& ctx);
>     void destroyHandTracking(XrCtx& ctx);
>     void syncHandTracking(XrCtx& ctx, JNIEnv* env);
> }
> ```
>
> Move the bodies of `initHandTracking`, `destroyHandTracking`, `syncHandTracking` from `OpenXrNative.cpp` into `OpenXrHandTracking.cpp`. Replace `this->`/member access with `ctx.hand.` as appropriate. Keep the `XrHandMicrogestureFlagsMETA` constants (lines 129–132) inside `OpenXrHandTracking.cpp` as TU-static — they are only used by hand-tracking. Same treatment for `XrHandTrackingAimFlagsFB` constants (lines 99–107) if they're only consumed in hand-tracking; otherwise hoist them to `OpenXrCtx.h`.

**Verification:**

- `Glob` — `OpenXrHandTracking.h`, `OpenXrHandTracking.cpp` exist.
- `Grep` — three `xrnative::` definitions present in `OpenXrHandTracking.cpp`.
- `Grep` — `XrCtx::syncHandTracking` does NOT match anywhere.
- `wc -l app_v2/src/vr/cpp/OpenXrHandTracking.cpp` ≤ 600.

**Status:** `[x]` done

---

### Step 04.3 — Update callers + remaining JNI exports in `OpenXrNative.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `#include "OpenXrInput.h"` and `#include "OpenXrHandTracking.h"` to the include block. Replace any remaining call sites of the migrated methods (most live inside `xrnative::renderFrame` — already moved in Phase 03 — and inside JNI exports here) with the free-function form: `xrnative::syncInputActions(g_ctx, env)`, `xrnative::syncHandTracking(g_ctx, env)`, etc. Confirm `wc -l` ≤ 700.

**Verification:**

- `Grep` — `#include "OpenXrInput.h"` matches once.
- `Grep` — `#include "OpenXrHandTracking.h"` matches once.
- `Grep` — `g_ctx\.syncInputActions\(` does NOT match.
- `Grep` — `g_ctx\.syncHandTracking\(` does NOT match.
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 700.

**Status:** `[x]` done

---

### Step 04.4 — Update CMake + build verification (vr + standard)

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Append `OpenXrInput.cpp` and `OpenXrHandTracking.cpp` to the source list. Run `/build` for `vr debug` AND `standard debug` (the standard flavor has `ENABLE_OPENXR=OFF` and must continue to compile without errors after the source list grows).

**Verification:**

- `Grep` — both new `.cpp` files appear in `CMakeLists.txt`.
- `assembleVrDebug` PASS.
- `assembleStandardDebug` PASS.
- `Grep` — `TODO(phase-04)` returns zero hits.

**Status:** `[x]` done

---

### Step 04.5 — Manual on-device smoke (Quest 3)

**Files:** —
**Depends on:** Step 04.4

**Prompt for developer:**

> Install the VR debug APK on Quest 3 (`adb install -r app_v2/build/outputs/apk/vr/debug/*.apk`). Launch a media file in immersive mode. Confirm:
>
> 1. App reaches immersive scene without `UnsatisfiedLinkError` in logcat.
> 2. Both controllers track and the hand-ray draws (if hand-tracking is enabled in OS settings).
> 3. Trigger and grip events still register (poke the panel zones, swipe the seek-bar).
> 4. Hand-tracking pinch still toggles play/pause if previously bound.
>
> Mark this step `[manual — deferred to human]` in the spec; flagging here keeps the audit honest.

**Verification:**

- Step status flipped to `[manual — deferred to human]` in this file.

**Status:** `[manual — deferred to human]`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done` (Step 04.5 may be `[manual — deferred to human]`; spec status moves to `BlockNeedUserTest` if so).
- [x] Project compiles — `/build vr debug` PASS, `/build standard debug` PASS.
- [x] `OpenXrNative.cpp` ≤ 700 LOC. Coordinator role only.
- [x] All extracted `OpenXr*.cpp` files ≤ 800 LOC each.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

cpp track is complete. `OpenXrNative.cpp` is now a thin coordinator (globals + JNI dispatch). Phase 05 picks up the Kotlin track — `VrPlayerActivity.kt` decomposition into Manager classes — independent of any further cpp work.

---

## Rollback Plan

Revert phase commit(s); previous backups remain valid. JNI signatures preserved verbatim — no cross-language coupling at risk.

---

## Revision History

- **2026-04-30** — Phase 04 completed
	- Step 04.1: added `OpenXrInput.h/.cpp` and moved controller input, pointer, and controller-ray helpers out of `OpenXrNative.cpp`.
	- Step 04.2: added `OpenXrHandTracking.h/.cpp` and moved the hand-tracking subsystem out of `OpenXrNative.cpp`.
	- Step 04.3: rewired `OpenXrFrame.cpp`, `OpenXrSwapchain.cpp`, `OpenXrLifecycle.cpp`, and `OpenXrNative.cpp` to use the extracted `xrnative` APIs.
	- Step 04.4: added both new TUs to CMake; `assembleVrDebug` and `assembleStandardDebug` passed.
	- Step 04.5: Quest 3 smoke remains manual and is deferred to human validation.
