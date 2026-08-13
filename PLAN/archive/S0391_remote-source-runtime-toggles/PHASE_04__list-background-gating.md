# Phase 04 - List & Background Gating

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 5 / 5
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Make existing resources of a disabled source invisible and inert in listing and background work: skip them in the resource-scan loop, the manual and background network sync, and thumbnail preload; gate Glide loads at the Browse boundary; and cancel in-flight background work when a source is disabled. No resource is deleted.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (gate available).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncNetworkResourcesUseCase.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/ThumbnailPreloadWorker.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceLoadManager.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/coordinator/RemoteSourceDisableCoordinator.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 470 |

---

## Steps

### Step 04.1 - Hide disabled-source resources from the main list and skip them in the scan loop

**Files:** `ui/main/MainViewModel.kt`, `ui/main/helpers/ResourceScanCoordinator.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Primary (invisibility): inject `RemoteSourceAvailabilityGate` into `MainViewModel`. In `observeResourcesFromDatabase`, add `remoteSourceGate.enabledRemoteSources()` to the existing `combine` (re-trigger + ordering guarantee so no read races the gate snapshot), and in `applyFiltersAndSorting` filter `resources` by `remoteSourceGate.isEnabled(resource)` before delegating to `ResourceFilterManager`. This makes a disabled source's existing resources vanish from every tab (incl. ALL) and refresh live when toggled. The `ResourceTab.ALL` branch in `ResourceFilterManager` stays an unfiltered pass-through - gating happens once, upstream.
>
> Secondary (no wasted work): pass the gate into `ResourceScanCoordinator` (constructed in `MainViewModel`) and filter the scanned list to `remoteSourceGate.isEnabled(resource)` so a disabled-source resource is never connection-tested or counted. Do not add a debug-verification tag here (deferred to the final `BlockNeedUserTest` transition, CLAUDE Rule 2).

**Verification:**

- `Grep` - `remoteSourceGate` referenced in `MainViewModel.kt` and `enabledRemoteSources` in the `combine`.
- `Grep` - `isEnabled(` referenced in `ResourceScanCoordinator.kt`.
- `Grep` - no `Timber.d("S0391:` tag in either file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. MainViewModel: injected gate, added `enabledRemoteSources()` to the `observeResourcesFromDatabase` combine, filter `resources` by `isEnabled` in `applyFiltersAndSorting` (upstream of all tab/type filters - disabled-source resources vanish from every tab incl. ALL, refresh live on toggle). ScanCoordinator: pre-filter scanned list by `isEnabled` (never connection-test/count a disabled source). `.\a.ps1 fk` BUILD SUCCESSFUL.

---

### Step 04.2 - Gate manual and background network sync

**Files:** `domain/usecase/SyncNetworkResourcesUseCase.kt`, `worker/NetworkFilesSyncWorker.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `gate.isEnabled(resource)` to the existing type-filter predicate in both the use case and the worker so disabled-source resources are never synced. Do not duplicate gate logic - both call the same gate.

**Verification:**

- `Grep` - `gate.isEnabled(` referenced in both `SyncNetworkResourcesUseCase.kt` and `NetworkFilesSyncWorker.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `SyncNetworkResourcesUseCase`: gate added to the `syncAll` network filter and a disabled-source guard in `syncSingle` (returns success/no-op, not an error). `NetworkFilesSyncWorker`: gate added to the `resourcesToSync` filter (LOCAL always passes); the thumbnail-preload enqueue derives from this already-gated list, satisfying 04.3's enqueue guard.

---

### Step 04.3 - Gate thumbnail preload

**Files:** `worker/ThumbnailPreloadWorker.kt`, `worker/NetworkFilesSyncWorker.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Gate the preload enqueue: do not schedule `ThumbnailPreloadWorker` for a disabled-source resource (check at the enqueue site in `NetworkFilesSyncWorker`), and inside `ThumbnailPreloadWorker` skip any resource where `!gate.isEnabled(resource)` as a defense-in-depth check.

**Verification:**

- `Grep` - `gate.isEnabled(` referenced in `ThumbnailPreloadWorker.kt`.
- `Grep` - the preload enqueue in `NetworkFilesSyncWorker.kt` is guarded by the gate.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `ThumbnailPreloadWorker`: injected gate, added a defense-in-depth `!isEnabled(resource)` skip right after the resource-exists check. The enqueue site (`NetworkFilesSyncWorker`) is already guarded because `networkResources` derives from the gated `resourcesToSync` (04.2) - no redundant check added.

---

### Step 04.4 - Gate Glide loads at the Browse boundary

**Files:** `ui/browse/managers/BrowseResourceLoadManager.kt`, `ui/browse/BrowseViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Gate at the entry boundary (in `loadResource`, right after the resource is fetched, before any scan/auth pre-check / Glide request), not inside the Glide model loaders. If `!gate.isEnabled(resource)`, turn back with the existing `error_resource_unavailable` message - covers reach-via-widget/deep-link even though the main list already filters these out. Inject the gate into `BrowseViewModel` and pass it into the manager's constructor. Back up `BrowseResourceLoadManager.kt` to `temp/` first (>500 LOC).

**Verification:**

- `Grep` - `gate.isEnabled(` referenced in `BrowseResourceLoadManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `BrowseResourceLoadManager`: backed up (>500 LOC), injected gate via ctor, added an entry-boundary turn-back (`!isEnabled(resource)` -> `error_resource_unavailable`) right after the resource fetch, before any scan/auth/Glide. Gate injected into `BrowseViewModel` and passed to the manager. Covers reach-via-widget/deep-link.

---

### Step 04.5 - Cancel in-flight work when a source is disabled

**Files:** `core/coordinator/RemoteSourceDisableCoordinator.kt` (New), `core/init/AppStartupInitializer.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `@Singleton class RemoteSourceDisableCoordinator @Inject constructor(...)` observing `SettingsRepository.getSettings()` on the application scope (`@ApplicationScope CoroutineScope`, never `GlobalScope`). On a transition of any network flag from enabled to disabled, cancel the in-flight per-resource thumbnail preloads (`WorkManagerScheduler.cancelAllThumbnailPreloads()`, which wraps `WorkManager.cancelUniqueWork`) and drop pooled connections (`SmbOperationsUseCase.clearAllConnectionPools()` - SMB/(S)FTP). Best-effort: items already synced in the current pass are not rolled back. Do NOT cancel the periodic network sync unique work - that would tear down the whole schedule; its next pass self-skips disabled sources via the gate (Step 04.2). Cloud sources have no background sync/preload, so only the network flags need a reaction. Start the observer from the existing app-init path (`AppStartupInitializer.initialize()`). Do not add a debug-verification tag here (deferred to the final `BlockNeedUserTest` transition, CLAUDE Rule 2).

**Verification:**

- `Glob` - `core/coordinator/RemoteSourceDisableCoordinator.kt` exists.
- `Grep` - `cancelAllThumbnailPreloads` and `clearAllConnectionPools` referenced in this file.
- `Grep -n "GlobalScope"` - zero hits in this file.
- `Grep` - `remoteSourceDisableCoordinator.start()` referenced in `AppStartupInitializer.kt`.
- `Grep` - no `Timber.d("S0391:` tag in either file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Created `@Singleton RemoteSourceDisableCoordinator` (app-scope settings collector, idempotent `start()`). On a network flag enabled->disabled it cancels in-flight thumbnail preloads + clears SMB/(S)FTP connection pools (best-effort); deliberately does NOT cancel the periodic sync (avoids tearing down the schedule - next pass self-gates). Cloud has no background tail. Started from `AppStartupInitializer.initialize()`. No `GlobalScope`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (Hilt graph resolves the gate across VMs/workers + the new coordinator).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `RemoteSourceDisableCoordinator`).

---

## Handoff Notes to Next Phase

Disabled-source resources are invisible to listing and inert to background work, and running work is cancelled on disable. Phase 05 closes the remaining interaction paths: playback, file operations, connection gates, and the verifier.

---

## Rollback Plan

Revert phase commit(s). No schema or data change - gating is additive filtering.
