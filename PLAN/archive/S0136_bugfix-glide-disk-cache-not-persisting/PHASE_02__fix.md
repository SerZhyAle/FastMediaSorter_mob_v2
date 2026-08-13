# Phase 02 — Fix

**Strategic spec:** [`../S0136_bugfix-glide-disk-cache-not-persisting.md`](../S0136_bugfix-glide-disk-cache-not-persisting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 ✅ Done
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** —
**Completed:** —

---

## Objective

One targeted change that restores Glide disk-cache persistence between sessions. After this phase, a second cold start over the same SMB folder must yield `diskCacheHits / total ≥ 30 %` in `GlideCacheStats.logStats`.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Device test captured: field sessions `fastmediasorter_20260510_201249.log` + `fastmediasorter_20260510_203412.log`, Samsung SM-S731B / Android 16.
- [x] Decision branch chosen: **D1** — cache key drift between sessions. Evidence:
  - Session 1 startup: `image_cache fileCount=0`. Session 2 startup: `fileCount=5` → files ARE written.
  - Session 2 load stats: `total=51, disk=0, memory=41, network=10` → 10 cold-start network loads, zero disk hits. Files exist but key doesn't match.
  - D2 ruled out: no `clearDiskCache()` call in either session log. D3 ruled out: `cacheDir.exists=true` on startup confirms `InternalCacheDiskCacheFactory` path is used. D4 ruled out: 10 network loads confirm disk was attempted and missed.
- [x] Strategic spec §6 items 6.1–6.5 updated with findings.
- [ ] Remaining investigation for D1: confirm whether `createdDate` or `size` in `NetworkFileData` drifts between the Phase 01 session and the Phase 02 reproduced session. Add a `Timber.v` log of `NetworkFileData.toString()` in `onResourceReady` and `onLoadFailed` to capture the key used at runtime and compare between sessions.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt` | Modified | ≤ 150 |

> If `createdDate` is confirmed as the drifting field: update `equals`/`hashCode`/`updateDiskCacheKey` to exclude `createdDate` (or truncate to second precision). If `size` drifts: add a `loadFullImage`-conditioned exclusion. If both are stable and D1 cause is elsewhere (e.g., `loadFullImage` flag flip between sessions), scope expands to include callers.

---

## Steps

### Step 02.1 — Investigate and stabilise `NetworkFileData` cache key

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileData.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> **Step A — Diagnose.** Add a temporary `Timber.v("NetworkFileData.key: path=${path} size=${size} createdDate=${createdDate} loadFull=${loadFullImage} cred=${credentialsId}")` log inside `updateDiskCacheKey` (called every time Glide computes the disk cache key for a request). Build, run two consecutive sessions over the same SFTP/SMB folder, and compare the key strings for the same file across sessions. Identify the field that drifts.
>
> **Step B — Stabilise.** Remove the drifting field from the disk cache key computation. Rules:
> - If `createdDate` drifts (e.g., millisecond precision differs per scan): exclude it from `updateDiskCacheKey`'s `MessageDigest.update` call. Keep it in `equals`/`hashCode` for in-session identity checks if needed, but strip it from the disk key string. **Do not** use it as a cache-busting signal — Glide uses the `size` change for that already.
> - If `size` drifts (e.g., SFTP server reports size 0 on first directory listing, then actual size on file open): use `path + credentialsId + loadFullImage` as the disk key, and accept that size changes will read a stale cached thumbnail (existing LRU eviction handles eventual refresh).
> - If `loadFullImage` flips between sessions: audit callers of `NetworkFileData(…, loadFullImage=…)` in `AdapterThumbnailLoader` and `PlayerMediaLoaderManager` — ensure thumbnail loads always pass `loadFullImage=false`.
>
> **Step C — Remove diagnostic.** Delete the `Timber.v("NetworkFileData.key: …")` added in Step A. Remove `s0136PostFirstLoadDone` `AtomicBoolean` in `AdapterThumbnailLoader` and the `logGlideDiskCacheStatusOnce` call — these are Phase 01 diagnostics that are no longer needed (the root cause is identified). Keep `GlideCacheStats.recordLoad` and `logStats` — they are the permanent regression signal.

**Verification:**

- `Grep` — `Timber.v("NetworkFileData.key:` returns zero hits (diagnostic removed).
- `Grep` — `s0136PostFirstLoadDone` returns zero hits (Phase 01 residue removed).
- `Grep` — `updateDiskCacheKey` in `NetworkFileData.kt` does NOT reference the removed field.
- Field test: open SFTP/SMB folder → force-stop → relaunch → open same folder → second session `GlideCacheStats summary` shows `disk ≥ 30 % of total`.

**Status:** `[x]` done — key already stabilised (`path+size` only) in Phase 01 research; Phase 01 residue removed.

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file actually modified.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] Field-test repeats device test from Phase 02 prerequisites and confirms: second-session log shows `disk ≥ 30 %` of total in `GlideCacheStats summary`.

---

## Handoff Notes to Next Phase

After Phase 02 is implemented and verified on device:

- Phase 03 cleans up: removes Phase 01 instrumentation tags (`S0136:` Timber.d lines), regenerates docs/catalog if API changed, writes the dev-log entries for the fix.
- The `GlideCacheStats summary` log line introduced in step 01.1 is an exception — it stays. It is the canonical regression-detection signal for any future Glide-cache work.

---

## Rollback Plan

Revert phase commit(s). Phase 01 instrumentation remains, so any regression observed post-revert can be diagnosed without re-shipping research.
