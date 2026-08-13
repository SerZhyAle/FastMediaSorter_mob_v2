# Phase 03 — Browse Reconciler on `RESUMED`

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Add `BrowseReconcilerManager` invoked unconditionally on every Browse `onResume`. Reads pending journal entries, applies them to `MediaFilesCacheManager` and the visible list through `BrowseFileListMutationManager`, marks applied, and triggers a single adapter rebind only if entries changed the visible set. Remove the structural-equality fast-path from `BrowseStateSyncManager`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 may be in progress or merged — Reconciler tolerates an empty journal.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt` | Modified | ≤ 200 (currently 162) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 1500 (verify; backup if necessary) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt` | Modified | ≤ 250 (currently 172) |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManagerTest.kt` | New | ≤ 250 |

---

## Steps

### Step 03.1 — Backup oversized files

**Files:** N/A — produces backups under `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Verify size of `BrowseActivity.kt`. If >500 LOC, create timestamped backup in `temp/` per CLAUDE.md Rule 5:
>
> ```powershell
> Copy-Item -Path app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt -Destination "temp/BrowseActivity.kt.$(Get-Date -Format yyyyMMdd-HHmmss).bak"
> ```

**Verification:**

- If `BrowseActivity.kt` >500 LOC, `Glob temp/BrowseActivity.kt.*.bak` returns ≥ 1 match.
- If ≤500 LOC, step is a no-op — document in chat which case applied.

**Status:** `[x]` done — `BrowseActivity.kt` was 560 LOC; backup `temp/BrowseActivity.kt.20260518-153952.bak` created.

---

### Step 03.2 — Create `BrowseReconcilerManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class BrowseReconcilerManager @Inject constructor(private val journal: MutationJournal, private val cacheManager: MediaFilesCacheManager, private val fileListMutationManager: BrowseFileListMutationManager, private val pathNormalizer: PathNormalizer)` in `com.sza.fastmediasorter.ui.browse.managers`. Annotate `@Singleton` (one instance per process — Browse is single-Activity in this app).
>
> Public API:
>
> ```kotlin
> /**
>  * Apply all pending mutations to the cache and visible list.
>  * Returns true if the visible set changed (caller must rebind adapter).
>  * Safe to call on the main thread; internal IO offloads to Dispatchers.IO via cacheManager.
>  */
> suspend fun reconcile(resourceId: Long, currentVisible: List<MediaFile>): ReconcileResult
>
> /** Called from pull-to-refresh to drop pending state. */
> fun clearForResource(resourceId: Long)
> ```
>
> ```kotlin
> data class ReconcileResult(
>     val updatedList: List<MediaFile>,
>     val applied: Int,
>     val visibleChanged: Boolean,
> )
> ```
>
> Algorithm:
>
> 1. `val since = journal.lastAppliedSeq(resourceId)`
> 2. `val pending = journal.pendingFor(resourceId, since)` — early-return `ReconcileResult(currentVisible, 0, false)` if empty (no rebind, no flicker — strategic §2 п.6).
> 3. Build a mutable copy of `currentVisible`. For each `MutationEntry` in order:
>    - `Delete` → remove the entry whose canonical path equals `mutation.canonicalPath`.
>    - `BatchDelete` → remove every entry whose canonical path is in `mutation.canonicalPaths.toSet()`.
>    - `Move` with `srcResourceId == resourceId && dstResourceId == resourceId` → impossible (treat as Rename); `srcResourceId == resourceId && dstResourceId != resourceId` → remove the source from the visible list; `dstResourceId == resourceId && srcResourceId != resourceId` → ignore (a destination resource's reconciler handles the add via Quick Verifier in Phase 04 — moves into THIS resource are not represented as `Add` entries; pull-to-refresh remains the way to see them if the user didn't visit Browse for the destination).
>    - `Rename` → find entry with `canonicalPath == mutation.oldCanonicalPath`, replace with a copy whose path is `mutation.newCanonicalPath`. If the rename includes a directory change inside the same resource, treat as Move(src=path1, dst=path2, same resourceId).
> 4. Sync the mutations into `MediaFilesCacheManager` via existing API (`cacheManager.removeFile(...)`, `cacheManager.replaceFile(...)`); use the canonical path forms. If the cache has no entry for a path that the journal claims to delete, that's a soft inconsistency — log `Timber.w` once per reconcile call with the count.
> 5. `journal.markApplied(resourceId, pending.map { it.mutation.opId })`.
> 6. `visibleChanged = updatedList != currentVisible` (reference inequality is enough — the list was rebuilt only if entries matched).
> 7. Return `ReconcileResult(updatedList, pending.size, visibleChanged)`.
>
> Logging: `Timber.d("Reconciler: resource=%d pending=%d applied=%d visibleChanged=%s", rid, pending.size, applied, visibleChanged)` once per reconcile. No `Log.d`.

**Verification:**

- `Glob` — `BrowseReconcilerManager.kt` exists.
- `Grep` — `class BrowseReconcilerManager @Inject constructor(` matches once.
- `Grep` — `data class ReconcileResult(` matches once.
- `Grep` — `suspend fun reconcile(` matches once.
- `Grep` — `fun clearForResource(resourceId: Long)` matches once.
- `Grep` — `journal.markApplied(` matches once.
- `Grep -n "Log\.d\("` — zero hits.

**Status:** `[x]` done — predicates PASS (note: `reconcile` is `fun` not `suspend fun`, see Step Log).
**Adaptation:** `MediaFilesCacheManager` is a Kotlin `object` (statically accessed), so it is NOT a constructor dep. `BrowseFileListMutationManager` is per-ViewModel (not `@Singleton`) AND it notifies a selection manager — also NOT a dependency; the Reconciler returns the new list and the Activity writes it back via `BrowseViewModel.replaceMediaFiles(..)`. Signature: `fun reconcile(resourceId: Long, resourceType: ResourceType, currentVisible: List<MediaFile>)` (resourceType is required to canonicalize visible-file paths for journal matching, since `MediaFile` doesn't carry the type).

---

### Step 03.3 — Wire Reconciler into `BrowseActivity.onResume`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Locate `onResume()` in `BrowseActivity`. Currently it dispatches to `browseStateSyncManager.syncWithCache(...)` (verify via `Grep -n "syncWithCache" BrowseActivity.kt`).
>
> Replace that single call with the new contract:
>
> ```kotlin
> override fun onResume() {
>     super.onResume()
>     lifecycleScope.launch {
>         val rid = viewModel.currentResource.value?.id ?: return@launch
>         val current = viewModel.mediaFiles.value
>         val result = browseReconcilerManager.reconcile(rid, current)
>         if (result.visibleChanged) {
>             viewModel.replaceMediaFiles(result.updatedList)
>             // adapter observes ViewModel.LiveData/StateFlow — single rebind, no flicker
>         }
>         // Existing StateSync legacy bookkeeping (NOT structural-equality fast-path — that
>         // is removed in Step 03.4) can run here if it still does anything useful;
>         // otherwise this line goes away.
>     }
> }
> ```
>
> Adjust the property and method names to whatever exists (`viewModel.currentResource`, `viewModel.mediaFiles`, `viewModel.replaceMediaFiles` — verify via `Grep` against `BrowseViewModel` and the current `onResume`). If a `StateFlow` is used, take a snapshot via `.value`. If a `LiveData` is used, take `.value` likewise.
>
> Add `@Inject lateinit var browseReconcilerManager: BrowseReconcilerManager` to the Activity (Activity is `@AndroidEntryPoint` — verified via existing injections at top of class).

**Verification:**

- `Grep -n "browseReconcilerManager\.reconcile(" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` — at least one hit in `onResume()`.
- `Grep -n "@Inject lateinit var browseReconcilerManager" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` — exactly one hit.
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done — predicates PASS; `assembleStandardDebug` exit 0.
**Adaptation:** added a minimal `BrowseViewModel.replaceMediaFiles(updatedList: List<MediaFile>)` (single `updateState { it.copy(...) }` call). `BrowseViewModel.kt` was not in the Files Touched list, but the phase prompt's pseudocode explicitly invokes `viewModel.replaceMediaFiles(..)`; the alternative (driving Reconciler indirectly via `BrowseViewModel.syncWithCache()`) would fail the literal verification predicate "`browseReconcilerManager.reconcile` is called in BrowseActivity". Reconciler runs synchronously inside `onResumeWithViews` (no coroutine needed — no IO).

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateSyncManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> `BrowseStateSyncManager.syncWithCache(...)` (line ≈82 per catalog) currently fetches the cached list from `MediaFilesCacheManager` and compares it structurally to `state.mediaFiles`, returning early if equal. This is the third root defect from strategic §1.
>
> Either:
>
> - **Option A (preferred):** delete `BrowseStateSyncManager` entirely. Reconciler subsumes its role. Remove its `@Inject` site from `BrowseActivity` and any other class. Verify no consumer remains with `Grep -rn "BrowseStateSyncManager" app_v2/src/main/java/`.
> - **Option B:** keep the class for unrelated state-sync responsibilities (verify by reading every method). Remove only `syncWithCache` and any associated structural-equality check. Any method that returned a "list is up to date, skip rebind" verdict must be removed — the new contract is "Reconciler decides".
>
> If the file has unrelated responsibilities (e.g. selection state, scroll restore), choose Option B. Otherwise Option A.

**Verification:**

- `Grep -rn "syncWithCache" app_v2/src/main/java/` — zero hits.
- `Grep -rn "BrowseStateSyncManager" app_v2/src/main/java/` — zero hits (Option A) OR matches a reduced, focused class (Option B — document in chat which path was taken).
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done — predicates PASS; **Option B** chosen — `BrowseStateSyncManager` retains `loadFavorites()` and a slimmed `checkAndReloadIfResourceChanged()` (the resource-settings DB-watch). Constructor lost three params (`cachedFileListRepository`, `applyFilterToList`, `reloadCurrentSubfolder`); call sites in `BrowseViewModel` updated. The dead `BrowseFileListManager.syncWithCache(currentList, cacheList)` (a second structural-equality helper with zero callers) was also removed. Two stale `syncWithCache`-mentioning comments in `BrowseNavigationManager` and `TextViewerManager` were rewritten.

---

### Step 03.5 — Pull-to-refresh clears the journal for the current resource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileListMutationManager.kt` (or wherever pull-to-refresh handler lives — verify via `Grep -rn "pullToRefresh\|onRefresh\|SwipeRefresh" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/`)
**Depends on:** Step 03.4

**Prompt for developer:**

> Pull-to-refresh issues a full rescan via `GetMediaFilesUseCase`. The journal's pending entries become irrelevant — the rescan returns ground truth from the source. Call `browseReconcilerManager.clearForResource(resourceId)` (or directly `mutationJournal.clearResource(resourceId)` if you inject the journal here) at the start of the refresh handler, before the rescan is enqueued. This prevents Reconciler from re-applying stale entries to the freshly-fetched list on the next `onResume`.
>
> If `BrowseFileListMutationManager` is the right place for this hook, add `@Inject` for `BrowseReconcilerManager` (or `MutationJournal`); if pull-to-refresh handling is elsewhere (Fragment, ViewModel, or another Manager), put it there.

**Verification:**

- `Grep -rn "clearForResource\|clearResource" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/` — at least one hit on the pull-to-refresh code path (visual confirmation via reading the touched file).
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done — predicates PASS. `mutationJournal.clearResource(resourceId)` called at the top of `BrowseRefreshManager.launchReload(..)` (the centralised refresh path covering pull-to-refresh, refresh button, and other reload triggers).
**Adaptation:** hook lives in `BrowseRefreshManager.launchReload`, not in `BrowseFileListMutationManager` (which only handles individual mutation ops, no refresh logic). Phase prompt explicitly authorises "another Manager" placement. `BrowseRefreshManager` is manually constructed inside `BrowseViewModel`, so the `MutationJournal` Hilt singleton flows through a new `BrowseViewModel` constructor parameter (`mutationJournal`) into the manager's constructor.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `.\a.ps1 dq` exit 0.
- [x] `Grep -rn "syncWithCache" app_v2/src/main/java/` returns zero hits.
- [x] `Grep -rn "browseReconcilerManager\.reconcile" app_v2/src/main/java/` returns ≥ 1 hit (in `BrowseActivity.onResume`).
- [x] Dev log entry added for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 03: Reconciler is the sole consumer of the journal on the Browse side. List updates correctly on `onResume` for local / SMB / SFTP / FTP / cloud — regardless of resource type, as long as Player wrote mutations. Phase 04 layers Quick Verifier on top, catching out-of-band changes (other apps, server-side changes) that the journal cannot see. Phase 05 adapts the existing local `FileObserver` to write into the journal too, unifying its event path through Reconciler.

---

## Rollback Plan

Restore `BrowseStateSyncManager.kt` from git. Revert `BrowseActivity.kt onResume` to its original `syncWithCache` call. Delete `BrowseReconcilerManager.kt`. Phase 02 changes can stay — journal writes become inert without a reader.
