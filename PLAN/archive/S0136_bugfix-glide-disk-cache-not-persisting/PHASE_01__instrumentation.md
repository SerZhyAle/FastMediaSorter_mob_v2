# Phase 01 — Instrumentation

**Strategic spec:** [`../S0136_bugfix-glide-disk-cache-not-persisting.md`](../S0136_bugfix-glide-disk-cache-not-persisting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 6 / 6
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Add `S0136:`-tagged Timber instrumentation across the Glide disk-cache pipeline so a single device session yields enough log evidence to choose between hypotheses D1 (cache key drift), D2 (explicit `clearDiskCache`), D3 (`setDiskCache` not applied), D4 (all hits via `MEMORY_CACHE`). No behavioural change.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec is in `Status: Approved` or `Tactical`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | within current budget |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | within current budget |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt` | Modified | within current budget |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt` | Modified | ≤ 90 |

> No file in the table currently exceeds 500 lines on the affected sections; backups not required.

---

## Steps

### Step 01.1 — Emit single-line summary tag from `GlideCacheStats.logStats`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside `GlideCacheStats.logStats()`, after the `total == 0` early return and before the existing emoji-prefixed `Timber.i` lines, insert one structured debug line:
>
> ```kotlin
> Timber.d("S0136: GlideCacheStats summary total=$total disk=$disk memory=$memory repo=$repo network=$network local=$local")
> ```
>
> Do not delete the existing emoji lines — they remain for human-readable output. This added line is the canonical grep target for analysis.

**Verification:**

- `Grep` — `S0136: GlideCacheStats summary` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/utils/GlideCacheStats.kt`.
- `Grep` — `total=\$total disk=\$disk memory=\$memory repo=\$repo network=\$network local=\$local` matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: utils/GlideCacheStats.kt (+1 LOC). Dev log recorded.

---

### Step 01.2 — Tag every successful network thumbnail load with model + dataSource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `AdapterThumbnailLoader.loadImage` (or the corresponding network branch around the existing `RequestListener.onResourceReady` for `NetworkFileData`, lines ~478–483), after the existing `GlideCacheStats.recordLoad(dataSource)` line, add:
>
> ```kotlin
> Timber.d("S0136: net thumb ds=${dataSource.name} path=${file.path} size=${file.size}")
> ```
>
> Do not modify the existing `Timber.v("CACHE_HIT_DEBUG: ..")` line. This step targets the network branch only — local branch is out of scope for S0136.

**Verification:**

- `Grep` — `S0136: net thumb ds=` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Files: ui/browse/AdapterThumbnailLoader.kt (+1 LOC). Dev log recorded.

---

### Step 01.3 — Tag the implicit `clearDiskCache` call inside `PlayerMediaLoaderManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Around line 702 the post-edit reload block calls `Glide.get(activity).clearDiskCache()`. Add an `S0136:` debug tag **immediately before** the existing `Glide.get(activity).clearDiskCache()` call so we capture not only that the call fires (the existing `Timber.d("PlayerMediaLoaderManager: Cleared Glide disk cache after image edit")` already does that *after*) but also which file path triggered it:
>
> ```kotlin
> Timber.d("S0136: PlayerMediaLoaderManager about to clearDiskCache after image edit, currentFile=${currentFile.path}")
> ```
>
> Use whatever variable currently identifies the in-flight edited file in that scope (`currentFile`, `file`, or equivalent — pick the existing one, do not introduce a new one). Do not change behaviour.

**Verification:**

- `Grep` — `S0136: PlayerMediaLoaderManager about to clearDiskCache` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`.
- `Grep` — `Glide\.get\(activity\)\.clearDiskCache\(\)` still matches exactly once (call not removed).
- `Grep` — `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: ui/player/helpers/PlayerMediaLoaderManager.kt (+1 LOC). Dev log recorded.

---

### Step 01.4 — Tag the user-triggered `clearDiskCache` in `GeneralSettingsCacheHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `GeneralSettingsCacheHelper.clearCache()` immediately before the `com.bumptech.glide.Glide.get(fragment.requireContext()).clearDiskCache()` call (around line 110), add:
>
> ```kotlin
> Timber.d("S0136: GeneralSettingsCacheHelper user-triggered clearCache")
> ```
>
> This lets us distinguish user-initiated cache clears from automatic ones in the device log.

**Verification:**

- `Grep` — `S0136: GeneralSettingsCacheHelper user-triggered clearCache` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsCacheHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Files: ui/settings/helpers/GeneralSettingsCacheHelper.kt (+1 LOC). Dev log recorded.

---

### Step 01.5 — Log `image_cache` directory state after `mkdirs()` in `GlideAppModule.applyOptions`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `GlideAppModule.applyOptions`, in the existing `try { val cacheDir = File(context.cacheDir, "image_cache") .. cacheDir.mkdirs() }` block (lines ~68–75), append an `S0136:` debug tag **after** the conditional `mkdirs()` and **before** the closing brace of the `try`:
>
> ```kotlin
> Timber.d("S0136: GlideAppModule cacheDir setup exists=${cacheDir.exists()} fileCount=${cacheDir.listFiles()?.size ?: 0} path=${cacheDir.absolutePath}")
> ```
>
> This pins the cache-dir state at the exact moment Glide's options are applied — the canonical "before any thumbnail load" reference point.

**Verification:**

- `Grep` — `S0136: GlideAppModule cacheDir setup` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Files: di/GlideAppModule.kt (+1 LOC). Dev log recorded.

---

### Step 01.6 — Add post-first-load cache-dir dump triggered from `AdapterThumbnailLoader`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Goal: capture the state of `image_cache` shortly after the very first successful Glide load, to confirm whether Glide is actually writing to it.
>
> 1. In `CacheStatusHelper`, add a new public function:
>
>    ```kotlin
>    fun logGlideDiskCacheStatusOnce(context: Context, tag: String) {
>        Timber.d("S0136: CacheStatusHelper post-first-load dump tag=$tag")
>        logGlideDiskCacheStatus(context)
>    }
>    ```
>
>    Place it directly below the existing `logGlideDiskCacheStatus` function. No behaviour change to the existing function.
>
> 2. In `AdapterThumbnailLoader`, add a private companion-object `AtomicBoolean` named `s0136PostFirstLoadDone = AtomicBoolean(false)`. Inside the existing network branch `RequestListener.onResourceReady` (around line 480, after the new `S0136:` tag from step 01.2), call:
>
>    ```kotlin
>    if (s0136PostFirstLoadDone.compareAndSet(false, true)) {
>        com.sza.fastmediasorter.core.util.CacheStatusHelper.logGlideDiskCacheStatusOnce(context, "first-network-thumb")
>    }
>    ```
>
> Use `java.util.concurrent.atomic.AtomicBoolean` — add the import if not already present. The dump fires once per process start.

**Verification:**

- `Grep` — `fun logGlideDiskCacheStatusOnce` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt`.
- `Grep` — `s0136PostFirstLoadDone` matches at least twice (declaration + use) in `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`.
- `Grep` — `S0136: CacheStatusHelper post-first-load dump` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/CacheStatusHelper.kt`.
- `Grep` — `Log\.d\(` returns zero hits in either modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: core/util/CacheStatusHelper.kt (+5 LOC), ui/browse/AdapterThumbnailLoader.kt (+5 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `./build-debug.PS1` BUILD SUCCESSFUL (31s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and rendered.

---

## Handoff Notes to Next Phase

After Phase 01 is merged and shipped to a test device:

1. User runs the device test: open one SMB folder → wait for thumbnails to fully load → force-stop the app → relaunch → open the same folder → exit. Capture the full logcat to a file.
2. Run `/spec-update S0136` with the captured log. The skill rewrites Phase 02 with the chosen D-branch.
3. Phase 01 instrumentation **stays in code** until Phase 02 is implemented and verified — `/spec-check S0136` is responsible for removing all `S0136:` Timber.d tags as part of the `Verified` transition.

Invariants established by Phase 01:
- Every Glide-cache-related code path emits a grep-able `S0136:` line.
- Cache-dir state is logged at three checkpoints: GlideAppModule.applyOptions, FastMediaSorterApp.onCreate (existing), and post-first-thumb (new).
- Both explicit `clearDiskCache` call sites are traceable to their trigger.

---

## Rollback Plan

Revert phase commit(s) — only Timber.d additions and one new public function. No data migration, no user-facing surface changed.
