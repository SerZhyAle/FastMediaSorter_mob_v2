# Phase 02 — Startup Migration

**Strategic spec:** [`../S0130_virtual-resource-file-operations.md`](../S0130_virtual-resource-file-operations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Add a startup fixer that sets `isWritable = true` for any aggregate virtual resource already in the DB with the old `false` value. Existing users receive the fix silently on next app launch.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 415 |

> Current file: 386 LOC — adding ~25 lines stays well under 500 LOC budget.

---

## Steps

### Step 02.1 — Add `fixVirtualAggregateWritableFlag()` to `AppStartupInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** — start of phase (Phase 01 must be done)

**Prompt for developer:**

> Add a private function `fixVirtualAggregateWritableFlag()` to `AppStartupInitializer` following the same pattern as `fixLocalResourcesWritableFlag()`. The function launches a coroutine in `applicationScope`, fetches all resources, filters those where `VirtualPathUtils.isAggregateVirtualPath(it.path) && !it.isWritable`, and for each calls `resourceRepository.updateResource(resource.copy(isWritable = true))`. Include the standard `try/catch` with `Timber.e`. Add the import for `com.sza.fastmediasorter.util.VirtualPathUtils` if not already present.
>
> The function body (inside the `try` block, after the filter produces results):
> ```kotlin
> private fun fixVirtualAggregateWritableFlag() {
>     applicationScope.launch {
>         try {
>             val resources = resourceRepository.getAllResources().first()
>             val broken = resources.filter {
>                 VirtualPathUtils.isAggregateVirtualPath(it.path) && !it.isWritable
>             }
>             if (broken.isNotEmpty()) {
>                 broken.forEach { resource ->
>                     resourceRepository.updateResource(resource.copy(isWritable = true))
>                 }
>                 Timber.d("S0130: Fixed isWritable for ${broken.size} aggregate virtual resources")
>             }
>         } catch (e: Exception) {
>             Timber.e(e, "Failed to fix aggregate virtual resources isWritable flag")
>         }
>     }
> }
> ```

**Verification:**

- `Grep` — `fun fixVirtualAggregateWritableFlag` present in `AppStartupInitializer.kt`.
- `Grep` — `VirtualPathUtils.isAggregateVirtualPath(it.path) && !it.isWritable` present.
- `Grep` — `Timber.d("S0130:` present in `AppStartupInitializer.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `AppStartupInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. Files: AppStartupInitializer.kt (import + new function added). Dev log recorded.

---

### Step 02.2 — Call the fixer from `initialize()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `AppStartupInitializer.initialize()`, add a call to `fixVirtualAggregateWritableFlag()` immediately after `fixLocalResourcesWritableFlag()` (line 50). This preserves the existing fixer call order.

**Verification:**

- `Grep` with content — `fixVirtualAggregateWritableFlag()` appears in the `initialize()` function body (within 5 lines after `fixLocalResourcesWritableFlag()`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. fixVirtualAggregateWritableFlag() at line 52, immediately after fixLocalResourcesWritableFlag(). Dev log recorded.

---

### Step 02.3 — Verify no `Log.d` introduced

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Static check only — ensure no `Log.d(` call was accidentally introduced.

**Verification:**

- `Grep` — `Log\.d\(` returns zero hits in `AppStartupInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 1/1 PASS. Zero Log.d() hits in AppStartupInitializer.kt.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `AppStartupInitializer.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `fixVirtualAggregateWritableFlag()` is registered and runs at every app startup.
- After first run on an existing install, all aggregate virtual resources will have `isWritable = true`.
- The `Timber.d("S0130:` tag in this fixer plus the two tags from Phase 01 cover all three flow entry points for on-device verification.

---

## Rollback Plan

Revert phase commit(s). The fixer only writes to the DB — no structural change. Re-installing the old APK version will re-run the old provisioning on fresh installs only; existing DB records stay at `isWritable = true` (harmless).
