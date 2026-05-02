# Phase 01 — Native logging extraction + shared context header skeleton

**Strategic spec:** [`../S0033_vr-monoliths-decomposition.md`](../S0033_vr-monoliths-decomposition.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-04-30
**Completed:** 2026-04-30

---

## Objective

Extract the logging utilities (lines 167–220) and diagnostic name helpers (lines 230–273) of `OpenXrNative.cpp` into a standalone translation unit `OpenXrLog.cpp`. Introduce an empty header `OpenXrCtx.h` under a named namespace `xrnative::` reserved for the shared types Phase 02 will populate. After this phase the binary behaviour is unchanged and `OpenXrNative.cpp` shrinks by ~110 LOC; the header creates a stable include target the next phases attach onto.

---

## Prerequisites

- [x] S0024 Phase 01 closed (sanity — confirms current `OpenXrNative.cpp` line count baseline of 3487).
- [ ] Working tree clean or on a feature branch.
- [x] Backup of `OpenXrNative.cpp` placed in `temp/` (file >500 LOC — CLAUDE.md rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrLog.h` | New | ≤ 60 |
| `app_v2/src/vr/cpp/OpenXrLog.cpp` | New | ≤ 200 |
| `app_v2/src/vr/cpp/OpenXrCtx.h` | New | ≤ 40 (skeleton only — populated in Phase 02) |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | starts 3487 → ends ≤ 3380 |
| `app_v2/src/vr/cpp/CMakeLists.txt` | Modified | unchanged ≤ 60 |

> File `OpenXrNative.cpp` >1000 LOC throughout this phase — additions are forbidden; only deletions + small re-includes are allowed. Verify with `wc -l` before and after.

---

## Steps

### Step 01.1 — Backup current cpp + create skeleton header

**Files:** `temp/OpenXrNative.cpp.<timestamp>.bak`, `app_v2/src/vr/cpp/OpenXrCtx.h`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/cpp/OpenXrNative.cpp` to `temp/OpenXrNative.cpp.<YYYYMMDD-HHmm>.bak` (CLAUDE.md rule 5 — file > 500 LOC). Create `app_v2/src/vr/cpp/OpenXrCtx.h` containing only:
>
> ```cpp
> #pragma once
> // Shared XR types extracted from OpenXrNative.cpp during S0033.
> // Phase 02 fills this header with LayerConfig / SwapchainImage / EyeSwapchain /
> // InputSystem / HandSystem / StereoSnapshot / XrCtx struct declarations.
> namespace xrnative {}
> ```
>
> Do not include any OpenXR or Android headers yet — Phase 02 will add them along with the migrated structs.

**Verification:**

- `Glob` — `temp/OpenXrNative.cpp.*.bak` matches at least once.
- `Glob` — `app_v2/src/vr/cpp/OpenXrCtx.h` exists.
- `Grep` — `namespace xrnative` matches once in `OpenXrCtx.h`.

**Status:** `[x]` done

**Step Log:**

- 2026-04-30 — Backup created at `temp/OpenXrNative.cpp.20260430-1505.bak`; `OpenXrCtx.h` added with `namespace xrnative {}` only. Verification 3/3 PASS. Files: `app_v2/src/vr/cpp/OpenXrCtx.h` (+4 LOC), backup copy in `temp/`. Dev log entry recorded.

---

### Step 01.2 — Author `OpenXrLog.h`

**Files:** `app_v2/src/vr/cpp/OpenXrLog.h`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `OpenXrLog.h` with the public surface used elsewhere in the file:
>
> ```cpp
> #pragma once
> #include <android/log.h>
> #include <cstddef>
> #include <string>
> #include <vector>
>
> namespace xrnative {
>     constexpr size_t kLogBufferMaxEntries = 512;
>     void nativeLogEmit(int androidPrio, const char* fmt, ...) __attribute__((format(printf, 2, 3)));
>     // Snapshot of the in-memory ring buffer; returns a copy under lock.
>     std::vector<std::string> nativeLogBufferSnapshot();
> }
> ```
>
> Match the existing function semantics (`nativeLogEmit` is the only logging entry-point used by the file). The snapshot accessor replaces direct global access from JNI exports.

**Verification:**

- `Grep` — `OpenXrLog.h` exists.
- `Grep` — `void nativeLogEmit` declared inside `xrnative` namespace.
- `Grep` — `nativeLogBufferSnapshot` declared.
- `Grep` — `Log\.d\(` returns zero hits in the file (header-only sanity).

**Status:** `[x]` done

**Step Log:**

- 2026-04-30 — `OpenXrLog.h` added with `nativeLogEmit`, `kLogBufferMaxEntries`, and `nativeLogBufferSnapshot` declarations. Verification 4/4 PASS. Files: `app_v2/src/vr/cpp/OpenXrLog.h` (+13 LOC). Dev log entry recorded.

---

### Step 01.3 — Author `OpenXrLog.cpp`

**Files:** `app_v2/src/vr/cpp/OpenXrLog.cpp`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `OpenXrLog.cpp` that owns the previously-static state from `OpenXrNative.cpp`:
>
> - `g_logBufferMutex` (`std::mutex`)
> - `g_logBuffer` (`std::vector<std::string>`)
> - `nativeLogBufferAppend(char prioChar, const char* msg)` (now `static`/anonymous in this TU)
> - `nativeLogEmit(int androidPrio, const char* fmt, ...)` — defined in `xrnative::`
> - `nativeLogBufferSnapshot()` — returns `std::vector<std::string>(g_logBuffer)` under lock
>
> The diagnostic-name helpers move here too:
>
> - `xrSessionStateName(XrSessionState s)` → declare it in `OpenXrLog.h` under `xrnative::` and define here.
> - `xrEventTypeName(XrStructureType t)` → same treatment.
>
> Both helpers need `<openxr/openxr.h>` — include it from the `.cpp`. `__VA_ARGS__` plumbing inside `nativeLogEmit` is preserved verbatim from the source. Do not log anything different; goal is byte-equivalent behaviour.

**Verification:**

- `Glob` — `OpenXrLog.cpp` exists.
- `Grep` — `void xrnative::nativeLogEmit` matches once.
- `Grep` — `void xrnative::nativeLogBufferAppend` does not match (kept TU-local) — instead `static void nativeLogBufferAppend` matches.
- `Grep` — `xrSessionStateName` and `xrEventTypeName` definitions exist in this file.
- `Grep` — `nativeLogEmit` no longer matches in `OpenXrNative.cpp` *as a definition* (the call sites stay, but the definition is gone).

**Status:** `[x]` done

**Step Log:**

- 2026-04-30 — `OpenXrLog.cpp` added with extracted ring-buffer state, `nativeLogEmit`, diagnostic helper definitions, and a small `[silent fix]` drain accessor in `OpenXrLog.h` because `nativeDrainLog()` needs move-out semantics once globals leave `OpenXrNative.cpp`. Verification PASS after adjacent duplicate-removal in `OpenXrNative.cpp`. Files: `app_v2/src/vr/cpp/OpenXrLog.cpp` (+122 LOC), `app_v2/src/vr/cpp/OpenXrLog.h` (+4 LOC). Dev log entries recorded.

---

### Step 01.4 — Strip definitions from `OpenXrNative.cpp` and re-include

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `OpenXrNative.cpp`:
>
> 1. Add `#include "OpenXrLog.h"` and `#include "OpenXrCtx.h"` near the existing include block.
> 2. Add a `using xrnative::nativeLogEmit;` (and similar `using` for the two diagnostic helpers) at file scope, below includes, so existing call sites compile unchanged.
> 3. Delete lines that previously held the definitions of `g_logBuffer`, `g_logBufferMutex`, `kLogBufferMaxEntries`, `nativeLogBufferAppend`, `nativeLogEmit` (forward decl + body), `xrSessionStateName`, `xrEventTypeName`.
> 4. Confirm `wc -l` of `OpenXrNative.cpp` drops to ≤ 3380.
>
> Do not refactor any other code in this step; the goal is a pure extraction with one-to-one identical call surface.

**Verification:**

- `Grep` — `#include "OpenXrLog.h"` matches once in `OpenXrNative.cpp`.
- `Grep` — `using xrnative::nativeLogEmit` matches once.
- `Grep` — `static void nativeLogBufferAppend` does NOT match in `OpenXrNative.cpp` (moved out).
- `Grep` — `kLogBufferMaxEntries` does NOT match in `OpenXrNative.cpp` (moved to `OpenXrLog.cpp`).
- `wc -l app_v2/src/vr/cpp/OpenXrNative.cpp` ≤ 3380.

**Status:** `[x]` done

**Step Log:**

- 2026-04-30 — `OpenXrNative.cpp` now includes `OpenXrLog.h`/`OpenXrCtx.h`, uses `xrnative::nativeLogEmit` + diagnostic helpers, drains via accessor, and no longer owns logging definitions. Verification 5/5 PASS; final LOC = 3380. Files: `app_v2/src/vr/cpp/OpenXrNative.cpp` (-107 LOC net). Dev log entry recorded.

---

### Step 01.5 — Update CMake + build verification

**Files:** `app_v2/src/vr/cpp/CMakeLists.txt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Append `OpenXrLog.cpp` to the `add_library(openxr_native SHARED ...)` source list (between `OpenXrNative.cpp` and the closing paren). `OpenXrCtx.h` and `OpenXrLog.h` do not need to be listed — they are headers; AGP picks them up via `target_include_directories`. Run `/build` for `vr debug` and confirm a clean compile.

**Verification:**

- `Grep` — `OpenXrLog.cpp` matches inside `add_library(openxr_native SHARED` block of `CMakeLists.txt`.
- Build output indicates VR flavor compiles without errors.
- `Grep` — `TODO(phase-01)` returns zero hits across the cpp directory.

**Status:** `[x]` done

**Step Log:**

- 2026-04-30 — `OpenXrLog.cpp` added to `openxr_native` target in `CMakeLists.txt`; `assembleVrDebug` PASS; `TODO(phase-01)` grep returned zero hits. Files: `app_v2/src/vr/cpp/CMakeLists.txt` (+1 LOC). Dev log entry recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `/build vr debug` PASS.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `\.\scripts\add_to_dev_log.ps1`.
- [x] `OpenXrNative.cpp` is ≤ 3380 LOC (verified by `wc -l`).
- [x] No regression in `assembleVrDebug` link step (no `UnsatisfiedLinkError` symbol added/removed).

---

## Handoff Notes to Next Phase

`OpenXrLog.h` + `OpenXrCtx.h` are now reachable from any new `.cpp` in the same directory. Phase 02 will populate `OpenXrCtx.h` with the shared `XrCtx`/`InputSystem`/`HandSystem` structs and introduce `OpenXrLifecycle.cpp`.

---

## Rollback Plan

Revert phase commit(s); the backup copy in `temp/` provides a byte-equivalent restore for `OpenXrNative.cpp`. No JNI signatures, no Kotlin code, and no CMake link surface changed beyond the source-list append.
