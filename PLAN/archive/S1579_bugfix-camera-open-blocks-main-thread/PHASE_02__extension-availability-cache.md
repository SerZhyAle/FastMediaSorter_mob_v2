# Phase 02 - Extension availability answered once per lens and mode

**Strategic spec:** [`../S1579_bugfix-camera-open-blocks-main-thread.md`](../S1579_bugfix-camera-open-blocks-main-thread.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Answer `ExtensionsManager.isExtensionAvailable` once per "lens id + video mode" pair instead of on every rebind, and warm the remaining pairs off the main thread.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 1060 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 800 |

---

## Steps

### Step 02.1 - Cache the offered extensions by lens id and video mode

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a private `ConcurrentHashMap<String, CameraExtensionSelector.Intents>` to `CameraCaptureSessionManager`, keyed by the active lens id joined with the `videoMode` flag. In `bindToLifecycle`, read the offered set through `getOrPut` on that map instead of calling `extensionsManager.offeredExtensions(..)` directly. Add exactly one new member function, `warmOfferedExtensions()`, which fills the map for every lens in `availableLenses` in both modes and is called by the host on a background dispatcher; the class is two functions below detekt's `TooManyFunctions` ceiling, so no second function may be added. Keep the entries for the process lifetime.

**Why:**

Strategic §2 Cause B records that `offeredExtensions` runs on every rebind - every settings apply, mode switch and lens switch - and that its answer is a function of exactly the lens behind `baseSelector` and `videoMode`, so a repeat disk read returns no new bit; §3 Fix B names the key as the "lens id + mode" pair precisely so a lens switch misses the map rather than being served a stale entry.

**Verification:**

- `Grep` - `ConcurrentHashMap` present in that file.
- `Grep` - `getOrPut` present in `bindToLifecycle`.
- `Grep` - `fun warmOfferedExtensions` matches exactly once.
- `Grep` - `extensionsManager.offeredExtensions(` appears only inside the `getOrPut` lambda and inside `warmOfferedExtensions`.

**Status:** `[x]` done

---

### Step 02.2 - Warm the map off the main thread once the session is bound

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `bindCamera()`'s `onReady` callback, launch `sessionManager.warmOfferedExtensions()` on `lifecycleScope` with `Dispatchers.IO`. Add no new function to the Activity.

**Why:**

Strategic §3 Fix B requires the map to be warmed off the main thread so only the first bind pays the ~32 ms, and states that a bind reaching the map before the warm pays that cost once, exactly as today, rather than being made to wait for it.

**Verification:**

- `Grep` - `warmOfferedExtensions` present in that file, inside a `Dispatchers.IO` launch.
- `Grep` - `private fun ` count in that file is unchanged from before the step.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, "Fast check passed".
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

`bindToLifecycle` reads extension availability from a process-lifetime map; a device whose extension set changed mid-process would need the map cleared, which no current flow does.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
