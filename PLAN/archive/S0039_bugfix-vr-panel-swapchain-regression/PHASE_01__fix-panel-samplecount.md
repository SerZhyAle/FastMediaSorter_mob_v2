# Phase 01 — fix-panel-samplecount

**Strategic spec:** [`../S0039_bugfix-vr-panel-swapchain-regression.md`](../S0039_bugfix-vr-panel-swapchain-regression.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-04-30
**Completed:** 2026-04-30

---

## Objective

Add `sc.sampleCount = 1` and a pre-creation diagnostic log to `createPanelSwapchainImpl` in `OpenXrNative.cpp`; this makes `xrCreateSwapchain` succeed for the panel layer.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/vr/cpp/OpenXrNative.cpp` is readable (file is >1 000 lines — bugfix only touches 2 lines inside an existing function; no split required).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | existing 3 487 lines — 2-line addition only |

> File is >500 lines — create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 — Backup OpenXrNative.cpp

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/cpp/OpenXrNative.cpp` to `temp/OpenXrNative_backup_20260430.cpp` before making any edits.

**Verification:**

- `Glob` — `temp/OpenXrNative_backup_20260430.cpp` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-04-30 — Verification 1/1 PASS. Files: temp/OpenXrNative_backup_20260430.cpp (copy). Dev log recorded.

---

### Step 01.2 — Set sampleCount and add diagnostic log in createPanelSwapchainImpl

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `createPanelSwapchainImpl` (around line 1262), after the `XrSwapchainCreateInfo sc{...}` initialization block and before the `xrCreateSwapchain` call, make two changes:
>
> 1. Add `sc.sampleCount = 1;` immediately after the existing `sc.mipCount = 1;` line.
> 2. Add a `LOGD` call after all `sc.*` assignments but before `xrCreateSwapchain`:
>    ```cpp
>    LOGD("createPanelSwapchain: format=0x%x usageFlags=0x%x sampleCount=%u width=%u height=%u",
>         static_cast<unsigned>(sc.format), static_cast<unsigned>(sc.usageFlags),
>         sc.sampleCount, sc.width, sc.height);
>    ```
>
> The resulting block should look like:
> ```cpp
> XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
> sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
> sc.format = GL_RGBA8;
> sc.width = requestedWidth;
> sc.height = requestedHeight;
> sc.faceCount = 1;
> sc.arraySize = 1;
> sc.mipCount = 1;
> sc.sampleCount = 1;
> LOGD("createPanelSwapchain: format=0x%x usageFlags=0x%x sampleCount=%u width=%u height=%u",
>      static_cast<unsigned>(sc.format), static_cast<unsigned>(sc.usageFlags),
>      sc.sampleCount, sc.width, sc.height);
> XrResult r = xrCreateSwapchain(g_ctx.session, &sc, &g_ctx.panelSwapchain);
> ```

**Verification:**

- `Grep` — `sc.sampleCount = 1;` appears in `createPanelSwapchainImpl` context (within ~10 lines of `sc.mipCount`).
- `Grep` — `LOGD("createPanelSwapchain: format=` present in the file.
- `Grep` — `Panel swapchain xrCreateSwapchain failed` still present (error path untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-04-30 — Verification 3/3 PASS. sc.sampleCount=1 at line 1270, LOGD at line 1271, error path intact at line 1278. Files: app_v2/src/vr/cpp/OpenXrNative.cpp (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (`vr debug` flavor).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `app_v2/src/vr/cpp/OpenXrNative.cpp` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`createPanelSwapchainImpl` now passes `sampleCount = 1` to `xrCreateSwapchain`; pre-creation LOGD emitted on every call for future diagnostics.

---

## Rollback Plan

Revert to `temp/OpenXrNative_backup_20260430.cpp`. No data migration; no user-facing surface changed.
