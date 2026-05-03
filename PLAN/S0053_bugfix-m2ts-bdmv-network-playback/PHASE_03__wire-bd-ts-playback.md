# Phase 03 — Wire BD-TS DataSource into Network Playback Helpers

**Strategic spec:** [`../S0053_bugfix-m2ts-bdmv-network-playback.md`](../S0053_bugfix-m2ts-bdmv-network-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** 2026-05-02

---

## Objective

Wrap SFTP, SMB, and FTP `DataSource.Factory` instances with `BdTsStripDataSourceFactory` when the file path ends with `.m2ts` or `.m2t`, enabling ExoPlayer's `TsExtractor` to parse 192-byte BD-TS packets as standard 188-byte MPEG-TS.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`BdTsStripDataSource` + `BdTsStripDataSourceFactory` compile).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | ≤ 135 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | ≤ 120 |

---

## Steps

### Step 3.1 — Create BdTsPlaybackHelper with wrapForBdTs extension

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file with an extension function on `DataSource.Factory` that wraps the factory with `BdTsStripDataSourceFactory` for `.m2ts`/`.m2t` paths:
>
> ```kotlin
> package com.sza.fastmediasorter.ui.player.helpers
>
> import androidx.media3.datasource.DataSource
> import com.sza.fastmediasorter.data.network.datasource.BdTsStripDataSourceFactory
>
> internal fun DataSource.Factory.wrapForBdTs(path: String): DataSource.Factory {
>     val lower = path.lowercase()
>     return if (lower.endsWith(".m2ts") || lower.endsWith(".m2t")) {
>         BdTsStripDataSourceFactory(this)
>     } else {
>         this
>     }
> }
> ```

**Verification:**

- `Glob` — `BdTsPlaybackHelper.kt` exists in `ui/player/helpers/`.
- `Grep` — `fun DataSource.Factory.wrapForBdTs` present.
- `Grep` — `BdTsStripDataSourceFactory(this)` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: BdTsPlaybackHelper.kt (new, 13 LOC). wrapForBdTs extension + BdTsStripDataSourceFactory(this) present.

---

### Step 3.2 — Wrap SFTP DataSource in SftpPlaybackHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `SftpPlaybackHelper.kt`, locate the block where `dataSourceFactory` is created and passed to `DefaultMediaSourceFactory`:
> ```kotlin
> val dataSourceFactory = SftpDataSourceFactory(...)
> ...
> exoPlayer = ExoPlayer.Builder(context)
>     .setMediaSourceFactory(
>         DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)
>     )
> ```
>
> Change the `DefaultMediaSourceFactory` call to:
> ```kotlin
> exoPlayer = ExoPlayer.Builder(context)
>     .setMediaSourceFactory(
>         DefaultMediaSourceFactory((dataSourceFactory as DataSource.Factory).wrapForBdTs(path))
>     )
> ```
>
> Add the import for `wrapForBdTs` at the top of the file — since it is in the same package (`ui.player.helpers`), no explicit import is needed.

**Verification:**

- `Grep` — `wrapForBdTs(path)` present in `SftpPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `SftpPlaybackHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. wrapForBdTs(path) present, no Log.d.

---

### Step 3.3 — Wrap SMB DataSource in SmbPlaybackHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Apply the same change as Step 3.2 to `SmbPlaybackHelper.kt`. Locate the `DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)` call and replace with `DefaultMediaSourceFactory((dataSourceFactory as DataSource.Factory).wrapForBdTs(path))`.

**Verification:**

- `Grep` — `wrapForBdTs(path)` present in `SmbPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `SmbPlaybackHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. wrapForBdTs(path) present, no Log.d.

---

### Step 3.4 — Wrap FTP DataSource in FtpPlaybackHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Apply the same change as Step 3.2 to `FtpPlaybackHelper.kt`. Locate the `DefaultMediaSourceFactory(dataSourceFactory as DataSource.Factory)` call and replace with `DefaultMediaSourceFactory((dataSourceFactory as DataSource.Factory).wrapForBdTs(path))`.
>
> Note: `FtpPlaybackHelper.kt` uses `path` as the parameter name — confirm before applying.

**Verification:**

- `Grep` — `wrapForBdTs(path)` present in `FtpPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `FtpPlaybackHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. wrapForBdTs(path) present, no Log.d.

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly). (auto-build — PASS, 2026-05-02)
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new class `BdTsPlaybackHelper`). [deferred to Phase 04]

---

## Handoff Notes to Next Phase

Phase 03 establishes:
- All three network playback helpers transparently strip BD-TS headers for `.m2ts`/`.m2t` files.
- Non-`.m2ts` files: zero-overhead passthrough (same factory returned).
- The `onBdTsFormatError` dialog from Phase 01 remains as a last-resort fallback if ExoPlayer still cannot parse the stream (e.g., device codec missing).

Phase 04 updates docs and regenerates the catalog.

---

## Rollback Plan

Revert phase commit(s). `BdTsPlaybackHelper.kt` deletion + one-line revert in each of the three helper files. No data migration or schema change.
