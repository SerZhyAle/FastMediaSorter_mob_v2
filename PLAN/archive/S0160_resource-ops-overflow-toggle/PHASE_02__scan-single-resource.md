# Phase 02 — scan-single-resource

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Expose a public API on `ResourceScanCoordinator` for scanning one resource, and add the corresponding `scanSingleResource()` method to `MainViewModel` that drives the operation and surfaces the unavailability toast.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 710 |

---

## Steps

### Step 02.1 — Add `SingleScanResult` sealed class and public `scanAndRefreshSingleResource()` to `ResourceScanCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceScanCoordinator.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `ResourceScanCoordinator`:
>
> 1. Add a sealed class inside the companion class scope (or at file level):
>    ```kotlin
>    sealed class SingleScanResult {
>        data class Available(val resource: MediaResource) : SingleScanResult()
>        data class Unavailable(val resource: MediaResource) : SingleScanResult()
>    }
>    ```
>
> 2. Add a public suspend function:
>    ```kotlin
>    suspend fun scanAndRefreshSingleResource(resource: MediaResource): SingleScanResult {
>        return try {
>            val isWritable = scanSingleResource(resource)
>            if (isWritable != null) SingleScanResult.Available(resource)
>            else SingleScanResult.Unavailable(resource)
>        } catch (e: Exception) {
>            Timber.w(e, "Single resource scan failed: ${resource.name}")
>            SingleScanResult.Unavailable(resource)
>        }
>    }
>    ```
>
> The existing private `scanSingleResource(resource)` method is left unchanged. No other modifications.

**Verification:**

- `Grep` — `sealed class SingleScanResult` matches in `ResourceScanCoordinator.kt`.
- `Grep` — `suspend fun scanAndRefreshSingleResource` matches in `ResourceScanCoordinator.kt`.
- `Grep` — `class Available` and `class Unavailable` both match in `ResourceScanCoordinator.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceScanCoordinator.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 4/4 PASS. Files: ui/main/helpers/ResourceScanCoordinator.kt (+20 LOC). Dev log recorded.

---

### Step 02.2 — Add `scanSingleResource()` to `MainViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `MainViewModel`, add the following public method after `forceRescanAllResources()`:
>
> ```kotlin
> fun scanSingleResource(resource: MediaResource) {
>     viewModelScope.launch(ioDispatcher + exceptionHandler) {
>         when (scanCoordinator.scanAndRefreshSingleResource(resource)) {
>             is ResourceScanCoordinator.SingleScanResult.Unavailable ->
>                 sendEvent(MainEvent.ShowMessage(
>                     context.getString(R.string.resource_unavailable_name, resource.name)
>                 ))
>             is ResourceScanCoordinator.SingleScanResult.Available -> {
>                 // DB updated via updateResourceUseCase inside scanCoordinator;
>                 // the resource-list observer refreshes the UI automatically.
>             }
>         }
>     }
> }
> ```
>
> The string key `R.string.resource_unavailable_name` is added in Phase 04. Add the call site now; the compiler will flag a missing-resource error until Phase 04 is done — that is expected.

**Verification:**

- `Grep` — `fun scanSingleResource\(resource: MediaResource\)` matches in `MainViewModel.kt`.
- `Grep` — `scanCoordinator.scanAndRefreshSingleResource` matches in `MainViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `MainViewModel.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Files: ui/main/MainViewModel.kt (+16 LOC). Backup: temp/MainViewModel_20260513_184119.kt.backup. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles after Phase 04 strings land — acceptable compile error on `R.string.resource_unavailable_name` until then.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `ResourceScanCoordinator.scanAndRefreshSingleResource()` is public and ready.
- `MainViewModel.scanSingleResource()` exists and dispatches on IO dispatcher.
- Toast string `R.string.resource_unavailable_name` is expected — added in Phase 04.

---

## Rollback Plan

Revert phase commit — no data migration, no Room change, no user-visible surface yet.
