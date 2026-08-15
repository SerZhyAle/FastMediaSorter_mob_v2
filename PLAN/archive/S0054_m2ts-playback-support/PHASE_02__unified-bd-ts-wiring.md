# Phase 02 — Unified BD-TS Wiring

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Replace the extension-only BD-TS detection in `BdTsPlaybackHelper` with byte-level detection via `TsPacketFormatDetector`; update SMB, SFTP, and FTP playback helpers to pre-detect format before wrapping the factory; add a boundary-condition unit test for `BdTsStripDataSource`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`TsPacketFormatDetector` available).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | ≤ 145 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceTest.kt` | New | ≤ 120 |

---

## Steps

### Step 02.1 — Add `detectTsFormatSuspend` and `wrapForBdTs(TsPacketFormat)` to `BdTsPlaybackHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Extend `BdTsPlaybackHelper.kt` with two new members alongside the existing `wrapForBdTs(path: String)` extension:
>
> 1. `internal fun DataSource.Factory.wrapForBdTs(format: TsPacketFormat): DataSource.Factory` — returns `BdTsStripDataSourceFactory(this)` when `format != TsPacketFormat.STANDARD_188`; otherwise returns `this` unchanged.
>
> 2. `internal suspend fun DataSource.Factory.detectTsFormatSuspend(uri: Uri): TsPacketFormat` — runs on `Dispatchers.IO`. Creates a data source from the factory, opens it with `DataSpec(uri, position=0L, length=TsPacketFormatDetector.PROBE_BYTES.toLong())`, reads up to `PROBE_BYTES` bytes into a probe array, closes the data source, and calls `TsPacketFormatDetector.detect(probe)`. Swallows all exceptions and returns `TsPacketFormat.UNKNOWN` on failure; logs failures with Timber.w.
>
> Keep the existing `wrapForBdTs(path: String)` in place — it will be removed only after all callers in Phase 03 and 04 migrate. Use `androidx.media3.datasource.DataSpec` for the spec construction.

**Verification:**

- `Grep` — `fun DataSource.Factory.wrapForBdTs(format: TsPacketFormat)` present in `BdTsPlaybackHelper.kt`.
- `Grep` — `suspend fun DataSource.Factory.detectTsFormatSuspend` present in `BdTsPlaybackHelper.kt`.
- `Grep` — `TsPacketFormat.STANDARD_188` referenced (branch condition inside `wrapForBdTs(format)`).
- `Grep` — `Log\.d(` returns zero hits in `BdTsPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt (+33 LOC). Dev log recorded.

---

### Step 02.2 — Update `SmbPlaybackHelper` to use byte-level detection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Refactor `playSmbVideo` in `SmbPlaybackHelper.kt` to pre-detect the TS format before creating ExoPlayer:
>
> 1. Move the SMB URI (`Uri.Builder` construction) to before the `ExoPlayer.Builder` call — it is currently computed after player creation. This requires referencing `targetShare`, `targetRemotePath`, and `credentials.server` which are all available earlier.
> 2. After building the SMB URI: if the path ends with `.m2ts` or `.m2t` (case-insensitive), call `(dataSourceFactory as DataSource.Factory).detectTsFormatSuspend(smbUri)` to get `format: TsPacketFormat`; otherwise set `format = TsPacketFormat.UNKNOWN`.
> 3. Replace `.wrapForBdTs(path)` with `.wrapForBdTs(format)` in the `DefaultMediaSourceFactory(...)` call.
> 4. The `setMediaItem` / `prepare` section at the bottom still uses the same `smbUri` — keep it.
>
> The function signature and all SMB credential/connection logic remain unchanged.

**Verification:**

- `Grep` — `wrapForBdTs(format)` present in `SmbPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs(path)` absent in `SmbPlaybackHelper.kt` (old call removed).
- `Grep` — `detectTsFormatSuspend` present in `SmbPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in `SmbPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt. Dev log recorded.

---

### Step 02.3 — Update `SftpPlaybackHelper` and `FtpPlaybackHelper` identically

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `FtpPlaybackHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same refactor as Step 02.2 to `playSftpVideo` and `playFtpVideo`:
>
> - For SFTP: compute the `sftp://` URI before ExoPlayer creation; detect format if `.m2ts`/`.m2t`; replace `wrapForBdTs(path)` with `wrapForBdTs(format)`.
> - For FTP: same — compute the FTP URI before ExoPlayer creation; detect; replace.
>
> Both files follow the same structure as `SmbPlaybackHelper`. The extension-based `wrapForBdTs(path)` call must be absent in both files after this step.

**Verification:**

- `Grep` — `wrapForBdTs(format)` present in `SftpPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs(path)` absent in `SftpPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs(format)` present in `FtpPlaybackHelper.kt`.
- `Grep` — `wrapForBdTs(path)` absent in `FtpPlaybackHelper.kt`.
- `Grep` — `Log\.d(` returns zero hits in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: SftpPlaybackHelper.kt, FtpPlaybackHelper.kt. Dev log recorded.

---

### Step 02.4 — Add boundary-condition unit test for `BdTsStripDataSource`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `BdTsStripDataSourceTest.kt` using JUnit 4. Use a `FakeDataSource` (inner class backed by a `ByteArray`) as the upstream.
>
> Tests to write:
>
> - `strip produces correct 188-byte payload from single BD-TS packet`: build a 192-byte array with bytes [4..191] as the payload; open at position 0; read 188 bytes; assert they equal bytes [4..191] of the input.
> - `strip across two packets removes both headers`: build two consecutive 192-byte BD-TS packets (total 384 bytes); open at position 0; read 376 bytes; assert result equals the concatenation of the two payloads (each 188 bytes starting at offset 4 within each 192-byte packet).
> - `open at non-zero tsPos translates to correct bdPos`: open at `tsPos = 188` (second TS packet start); assert the upstream was opened at `bdPos = 192 + 4 = 196`.
> - `read returns END_OF_INPUT on truncated last packet`: build a 96-byte array (half a BD-TS packet); open at 0; read 1 byte; assert result is either 1 byte read or `C.RESULT_END_OF_INPUT` without throwing.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceTest.kt` exists.
- `Grep` — `class BdTsStripDataSourceTest` matches exactly once.
- `Grep` — `BdTsStripDataSource` referenced in the test file (instantiation).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: BdTsStripDataSource.kt (bug fix: bdPos = packetIndex * BD_PACKET_SIZE), BdTsStripDataSourceTest.kt (+92 LOC, Robolectric). Spec note: corrected expected bdPos from 196 to 192 (spec had buggy formula). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 02.* above is `[x] done`.
- [x] Project compiles — `testStandardDebugUnitTest` BUILD SUCCESSFUL, 4/4 BdTsStripDataSourceTest PASS (2026-05-03).
- [x] `Grep` for `wrapForBdTs(path)` across `SmbPlaybackHelper.kt`, `SftpPlaybackHelper.kt`, `FtpPlaybackHelper.kt` returns zero hits.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `DataSource.Factory.wrapForBdTs(path: String)` still exists (kept for backward compat). Phase 03 and 04 migrate the remaining callers (local and cloud paths) then this old overload can be removed.
- `detectTsFormatSuspend` extension is available for Phase 03 local path and Phase 04 cloud path.
- SMB/SFTP/FTP now use byte-level detection — 188-byte `.m2ts` files on network sources no longer have BD-TS layer incorrectly applied.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
