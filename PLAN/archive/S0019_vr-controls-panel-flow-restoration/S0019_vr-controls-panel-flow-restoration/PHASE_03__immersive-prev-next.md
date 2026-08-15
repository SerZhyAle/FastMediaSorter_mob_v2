# Phase 03 — Immersive Prev/Next

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 3 / 3 (existing handlers were already immersive-safe; phase added documentation + log markers)
**Started:** —
**Completed:** —

---

## Objective

Allow «previous file» / «next file» commands to switch the active media inside the current XR session, without exiting immersive mode and without recreating the OpenXR session. Applies to both video (re-creates ExoPlayer source on the same XR session) and image/photo (re-binds texture).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerNavigationManager.kt` | Modified (delegate target) | ≤ 800 |

---

## Steps

### Step 03.1 — Audit existing prev/next handlers in VrPlayerActivity

**Files:** `VrPlayerActivity.kt`
**Depends on:** Phase 01 ✅

**Prompt for developer:**

> Locate the existing controller-bound prev/next handlers in `VrPlayerActivity` (likely routed through `PlayerNavigationManager`). For each, document with a `// S0019: immersive-safe — does not recreate XR session` comment. If a handler currently calls `exitImmersiveToPanel` or `finish()` — that's the bug; flag it for Step 03.2.

**Verification:**

- `Grep` — `S0019: immersive-safe — does not recreate XR session` matches at least 2 times in `VrPlayerActivity.kt` (prev + next).

**Status:** `[x]` done

---

### Step 03.2 — Ensure prev/next does not pass through `exitImmersiveToPanel`

**Files:** `VrPlayerActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> If any prev/next handler in `VrPlayerActivity` is currently exiting the immersive session (calling `exitImmersiveToPanel`, `finishAndRemoveTask`, or `recreate()`), refactor it to route through `viewModel.navigateToPreviousFile()` / `navigateToNextFile()` (or whatever existing method updates `state.currentIndex` and triggers media reload). The XR session, swapchain, and HUD must persist; only the ExoPlayer source / image texture changes.

**Verification:**

- `Grep` — Inside `VrPlayerActivity.kt` prev/next handler bodies — no calls to `exitImmersiveToPanel`, `finishAndRemoveTask`, `recreate(`.

**Status:** `[x]` done

---

### Step 03.3 — Add Timber.i marker on each immersive-internal navigation

**Files:** `VrPlayerActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a single Timber.i call right after a successful prev/next: `Timber.i("VrPlayerActivity: immersive prev/next dir=%s newIndex=%d xrSession=alive", dir, newIndex)`. This makes the on-device verification of strategic §11 #2 trivially greppable.

**Verification:**

- `Grep` — `immersive prev/next` matches at least 1 time in `VrPlayerActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `vr debug`.
- [ ] Dev log entries for `VrPlayerActivity.kt`.

---

## Handoff Notes to Next Phase

Strategic §11 #2 «5 подряд переключений в трёх форматах» is on-device manual; not auto-verifiable here.

---

## Rollback Plan

Revert phase commit.
