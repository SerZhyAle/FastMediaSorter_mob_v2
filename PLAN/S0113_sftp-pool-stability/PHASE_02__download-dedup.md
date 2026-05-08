# Phase 02 — Download Deduplication

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — independent phase
**Blocks:** Phase 05
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Introduce `NetworkDownloadDeduplicator` — a protocol-agnostic deduplication component that collapses concurrent download requests for the same URL into a single in-flight coroutine; wire it into `NetworkFileDownloader.downloadToTemp()`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] No open research items block this phase.
- [ ] Working tree is clean or on a feature branch.
- [ ] Backup of `NetworkFileDownloader.kt` created in `temp/` (file is 302 lines).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkDownloadDeduplicator.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/UtilModule.kt` | Modified | ≤ existing + 20 |

---

## Steps

### Step 02.1 — Backup NetworkFileDownloader.kt

**Files:** `temp/NetworkFileDownloader_<timestamp>.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt` to `temp/NetworkFileDownloader_<YYYYMMDD_HHmmss>.kt` before any edits (302 lines → backup required).

**Verification:**

- `Glob` — `temp/NetworkFileDownloader_*.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 02.2 — Create NetworkDownloadDeduplicator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkDownloadDeduplicator.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `NetworkDownloadDeduplicator.kt` in `com.sza.fastmediasorter.core.util`. The class is `@Singleton` and `@Inject constructor()`. It exposes one `suspend` method:
>
> ```kotlin
> suspend fun <T> deduplicate(key: String, block: suspend () -> T?): T?
> ```
>
> Internally maintain a `ConcurrentHashMap<String, Deferred<Any?>>`. On call:
> 1. Look up `key` in the map.
> 2. If found and the `Deferred` is active → `await()` it and cast to `T?`.
> 3. If not found (or completed/cancelled) → create a new `Deferred` via `CoroutineScope(Dispatchers.IO + SupervisorJob()).async { block() }`, put it in the map, `await()` it, then remove the key.
> 4. Use `getOrPut` with a `synchronized` block on the map to avoid a TOCTOU race between "not found" and "put".
> 5. In the `finally` of the `async` block: remove the key from the map unconditionally.
>
> Import: `kotlinx.coroutines.*`, `java.util.concurrent.ConcurrentHashMap`. Use `Timber.d` to log deduplication hits: `"NetworkDownloadDeduplicator: reusing in-flight download for $key"`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkDownloadDeduplicator.kt` exists.
- `Grep` — `class NetworkDownloadDeduplicator` matches exactly once.
- `Grep` — `fun deduplicate` matches in that file.
- `Grep` — `@Singleton` present in that file.
- `Grep -n "Log\.d\("` — zero hits in `NetworkDownloadDeduplicator.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Wire deduplicator into NetworkFileDownloader

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `private val deduplicator: NetworkDownloadDeduplicator` as a constructor parameter of `NetworkFileDownloader`. In `downloadToTemp()`, wrap the entire protocol-dispatch block (the `when { ... }` expression starting at line 73 of the original file, and the cache-check block above it) inside `deduplicator.deduplicate(networkPath) { ... }`. The cache-miss path (`unifiedCache.getCachedFile`) that currently runs before the `when` should remain outside the `deduplicate` call (cache hit bypasses the download entirely). The deduplication key is `networkPath`.
>
> The method signature stays unchanged: `suspend fun downloadToTemp(networkPath: String, fileType: MediaType, fileSize: Long, useExtendedSize: Boolean): File?`

**Verification:**

- `Grep` — `deduplicator` appears in `NetworkFileDownloader.kt`.
- `Grep` — `deduplicator.deduplicate` appears in `downloadToTemp` body.
- `Grep` — `NetworkDownloadDeduplicator` imported in `NetworkFileDownloader.kt`.
- `Grep -n "Log\.d\("` — zero hits in `NetworkFileDownloader.kt`.

**Status:** `[ ]` not done

---

### Step 02.4 — Register NetworkDownloadDeduplicator in DI

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/UtilModule.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Locate the Hilt module that provides `NetworkFileDownloader` (likely `UtilModule.kt` or `NetworkModule.kt`; use catalog query `-ClassMatches "*UtilModule*"` to confirm). Add a `@Provides @Singleton` method for `NetworkDownloadDeduplicator` if one does not exist — the class is `@Inject` so Hilt can auto-provide it; add an explicit `@Provides` only if the module uses constructor-less provision. If `NetworkFileDownloader` is constructed via `@Provides` in that module, add `deduplicator: NetworkDownloadDeduplicator` to its parameter list and pass it through. If `NetworkFileDownloader` is `@Inject constructor`, update the constructor to accept `NetworkDownloadDeduplicator` and Hilt will wire it automatically.

**Verification:**

- `Grep` — `NetworkDownloadDeduplicator` appears in DI module file.

**Status:** `[ ]` not done

---

### Step 02.5 — Remove Timber.d("S0113:") debug tags

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkDownloadDeduplicator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/util/NetworkFileDownloader.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> This step is deferred to `/spec-check` when the spec moves to `Verified`. For now, add the following debug verification tag at the entry of `NetworkDownloadDeduplicator.deduplicate()` (only once, at the top before the map lookup):
> ```kotlin
> Timber.d("S0113: NetworkDownloadDeduplicator.deduplicate key=$key")
> ```
> This tag will be removed when the spec is verified in production.

**Verification:**

- `Grep` — `S0113: NetworkDownloadDeduplicator.deduplicate` appears in `NetworkDownloadDeduplicator.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for all new/modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `NetworkDownloadDeduplicator` is now in the DI graph as a `@Singleton`.
- `NetworkFileDownloader.downloadToTemp()` deduplicates by URL; concurrent identical requests share one in-flight `Deferred`.
- This eliminates `JobCancellationException` spam from the metadata-dialog-open flow.

---

## Rollback Plan

Revert `NetworkFileDownloader.kt` from backup; delete `NetworkDownloadDeduplicator.kt`; remove the DI provision. No data migration.
