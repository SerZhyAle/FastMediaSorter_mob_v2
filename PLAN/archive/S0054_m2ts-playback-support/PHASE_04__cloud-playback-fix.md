# Phase 04 — Cloud Playback Fix

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** —

---

## Objective

Enable BD-TS `.m2ts` playback for cloud sources (Google Drive, OneDrive, Dropbox) by adding a 576-byte range pre-detection request to `playCloudVideo` and conditionally wrapping the cloud data-source factory.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`TsPacketFormatDetector` available).
- [ ] Phase 02 is ✅ Done (`detectTsFormatSuspend` and `wrapForBdTs(TsPacketFormat)` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt` | Modified | ≤ 100 |

---

## Steps

### Step 04.1 — Make `playCloudVideo` a `suspend fun`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Change the signature of `playCloudVideo` from `internal fun` to `internal suspend fun`. The only caller is `VideoPlayerManager.playVideo()` inside `managerScope.launch { ... }`, which is already a coroutine context, so no caller changes are needed. Verify that the project still compiles after this signature change alone before continuing to Step 04.2.

**Verification:**

- `Grep` — `internal suspend fun VideoPlayerManager.playCloudVideo` matches exactly once in `CloudPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `CloudPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: CloudPlaybackHelper.kt (signature change only). Dev log recorded.

---

### Step 04.2 — Add BD-TS detection and conditional factory wrapping

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inside `playCloudVideo`, after `val cloudUri = PathUtils.safeParseUri(path)` and before the `ExoPlayer.Builder` call, insert BD-TS detection and conditional wrapping:
>
> ```
> val isMts = path.endsWith(".m2ts", ignoreCase = true) || path.endsWith(".m2t", ignoreCase = true)
> val tsFormat: TsPacketFormat = if (isMts) {
>     CloudDataSourceFactory(clients).detectTsFormatSuspend(cloudUri)
> } else {
>     TsPacketFormat.STANDARD_188
> }
> ```
>
> Then change the `ExoPlayer.Builder` `setMediaSourceFactory` line from:
> ```kotlin
> DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
> ```
> to:
> ```kotlin
> DefaultMediaSourceFactory((dataSourceFactory as DataSource.Factory).wrapForBdTs(tsFormat))
> ```
>
> The existing `dataSourceFactory` variable is `CloudDataSourceFactory(clients)` — keep it as-is; the detection uses a separate short-lived instance.
>
> Imports to add: `TsPacketFormat`, `TsPacketFormatDetector` (via `detectTsFormatSuspend`), `wrapForBdTs`.

**Verification:**

- `Grep` — `detectTsFormatSuspend` present in `CloudPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs(tsFormat)` present in `CloudPlaybackHelper.kt`.
- `Grep` — `isMts` present (guard variable).
- `Grep` — `Log\.d(` returns zero hits in `CloudPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Files: CloudPlaybackHelper.kt (+9 LOC, 1 new import). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 04.* above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-04 (assembleStandardDebug).
- [ ] Manual smoke-test: open a cloud-stored BD-TS `.m2ts` file → playback starts or shows a meaningful error (not silent hang). _(confirm before marking done; a real cloud file is required)_ MANUAL-REQUIRED
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Cloud BD-TS `.m2ts` detection adds at most one extra range-read round-trip (~576 bytes) before player creation.
- The old `wrapForBdTs(path: String)` extension in `BdTsPlaybackHelper.kt` has no remaining callers after Phases 02–04. It may be removed in Phase 07 cleanup if desired.
- Phase 05 and 06 (string fixes and audio diagnostics) are independent of this phase and can proceed in parallel.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
