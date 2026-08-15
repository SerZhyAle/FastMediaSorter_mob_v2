# Phase 03 - Screenshot Flow Fallback

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-19

---

## Objective

Make gesture-screenshot save fall back to the public images collection when the selected network resource is unreachable, and post a background system notification on that fallback. Screenshot capture is `noLegal`-only; the shared decision lives in `src/main`, the call-site wiring in the flavor source sets.

---

## Prerequisites

- [ ] Phase 01, 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ScreenshotDestinationPolicy.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveScreenshotUseCase.kt` | Modified | ≤ 230 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenshotAccessibilityService.kt` | Modified | ≤ 500 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt` | Modified | ≤ 500 |

> Flavor placement: screenshot capture services are flavor-isolated (`noLegal` / `screenCapture` source sets) - do not move them to `src/main`. The reachability decision and use-case stay shared in `src/main`.

---

## Steps

### Step 03.1 - Reachability-aware screenshot target resolution

**Files:** `util/ScreenshotDestinationPolicy.kt`
**Depends on:** Phase 01 Step 01.1, 01.2

**Prompt for developer:**

> Extend `resolve(..)` with a parameter `isResourceReachable: (MediaResource) -> Boolean = { true }`. When the matched `selectedResource` is a network resource (`resource.type.isNetworkResource`) and `!isResourceReachable(resource)`, do NOT return `Target.SelectedResource`; instead fall through to the public-collection branch and tag it. Add a nullable `fallbackReason: SaveFallbackReason?` field to `Target.PublicCollection` (default `null`); set it to `ResourceUnavailable` for this network-unreachable case and leave `null` for the ordinary "no resource configured" path. Keep existing call signatures working via the default parameter.

**Verification:**

- `Grep` - `isResourceReachable` present in `ScreenshotDestinationPolicy.kt`.
- `Grep` - `fallbackReason` present on `PublicCollection`.
- `Grep` - `isNetworkResource` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 3/3 PASS. resolve() now takes isResourceReachable; PublicCollection carries fallbackReason.

---

### Step 03.2 - Surface fallback reason from SaveScreenshotUseCase

**Files:** `domain/usecase/SaveScreenshotUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a nullable `fallbackReason: SaveFallbackReason? = null` to `SaveResult.Success`. In `saveToPublicCollection`, propagate `target.fallbackReason` into the returned `Success`. Do not add notification logic here (domain layer stays UI-free) - only carry the reason out.

**Verification:**

- `Grep` - `fallbackReason` present in `SaveScreenshotUseCase.kt` on `SaveResult.Success`.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification 2/2 PASS. SaveResult.Success carries fallbackReason; propagated in saveToPublicCollection.

---

### Step 03.3 - Wire reachability + background notification at screenshot call sites

**Files:** `src/noLegal/.../ScreenshotAccessibilityService.kt`, `src/screenCapture/.../ScreenCaptureService.kt`
**Depends on:** Step 03.2, Phase 02 Step 02.2

**Prompt for developer:**

> In both capture services, inject/obtain `NetworkStateMonitor` and `SaveFallbackNotifier`. Pass `isResourceReachable = { networkStateMonitor.canReach(it.type) }` into `ScreenshotDestinationPolicy.resolve(..)`. After `SaveScreenshotUseCase` returns `Success` with a non-null `fallbackReason`, call `saveFallbackNotifier.notify(reason, folderLabel = success.destinationLabel, resourceName = <selected resource name>, background = true)`. Resolve the selected resource name from the already-loaded resource list. Touch only the save-result handling path; do not alter capture/permission logic.

**Verification:**

- `Grep` - `canReach(` present in both service files.
- `Grep` - `saveFallbackNotifier` (or injected notifier) `.notify(` present in both files with `background = true`.
- `Grep -n "Log\.d\("` - zero hits in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - Verification PASS in both services. canReach pre-check wired; background notify on fallbackReason. standard (fk) + noLegal (nd) builds SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for `standard` (`.\a.ps1 fk`) and `noLegal` (`.\a.ps1 nd` or `fk` on noLegal variant).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Screenshot flow is the reference wiring: pre-check via `canReach`, policy returns a tagged public-collection target, caller notifies with `background = true`. Capture flows (Phase 04) follow the same shape with foreground notification.

---

## Rollback Plan

Revert phase commit(s). The added optional parameters default to the prior behaviour, so a partial revert of call sites still compiles.
