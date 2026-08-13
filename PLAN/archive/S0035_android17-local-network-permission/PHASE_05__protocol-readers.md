# Phase 05 — Protocol Readers

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-04

---

## Objective

Guard every direct LAN reader outside Add Resource so playback, browse scans, and network thumbnails fail with an explicit permission-denied path instead of timeout loops, empty lists, or poisoned failed caches.

---

## Prerequisites

- [ ] Phase 01, Phase 02, and Phase 03 are ✅ Done.
- [ ] Add Resource entrypoints already prove the permission contract end-to-end.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt` | Modified | >500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | 234 current LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt` | Modified | 257 current LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt` | Modified | >500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt` | Modified | n/a |
| `temp/SmbDataSource.kt.<YYYYMMDD_HHmmss>.backup` | New | n/a |
| `temp/SmbMediaScanner.kt.<YYYYMMDD_HHmmss>.backup` | New | n/a |

---

## Steps

### Step 05.1 — Backup the >500-LOC SMB readers and thread context into playback factories

**Files:** `temp/SmbDataSource.kt.<YYYYMMDD_HHmmss>.backup`, `temp/SmbMediaScanner.kt.<YYYYMMDD_HHmmss>.backup`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups for `SmbDataSource.kt` and `SmbMediaScanner.kt` before any edit. Then thread the minimal `Context` or application-context access needed by `SmbDataSourceFactory`, `SftpDataSourceFactory`, and `FtpDataSourceFactory` so the datasource `open()` methods can check local-network permission before acquiring pooled connections.

**Verification:**

- `Glob` — `temp/SmbDataSource.kt.*.backup` returns at least one match.
- `Glob` — `temp/SmbMediaScanner.kt.*.backup` returns at least one match.
- `Grep` — `SmbDataSourceFactory(` still appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` with the new context-aware signature.
- `Grep` — `SftpDataSourceFactory(` appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` with the new context-aware signature.
- `Grep` — `FtpDataSourceFactory(` appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` with the new context-aware signature.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Backups created, context threaded to all 3 factories + playback helpers. Dev log recorded.

---

### Step 05.2 — Gate datasource `open()` before socket acquisition

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In each datasource `open()`, check local-network permission before the first pool acquisition, socket timeout setup, or remote stat/open call. Missing permission must throw `LocalNetworkPermissionDeniedException`, not `IOException("Failed to open ..")`. Keep existing stale-connection / watchdog logic intact for real transport failures.

**Verification:**

- `Grep` — `hasLocalNetworkPermission` appears in `SmbDataSource.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `SftpDataSource.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `FtpDataSource.kt`.
- `Grep` — `LocalNetworkPermissionDeniedException` appears in all three datasource files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS (hasLocalNetworkPermission+LocalNetworkPermissionDeniedException in Smb, Sftp, Ftp DataSource). Dev log recorded.

---

### Step 05.3 — Gate media scanners and network thumbnail readers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkEpubCoverLoader.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add the same permission gate to scan and thumbnail entrypoints before remote `listFiles`, `scanMediaFiles`, PDF / EPUB download, or `MediaMetadataRetriever` work begins. Missing permission must not be cached as a permanent thumbnail failure and must not silently degrade into an empty media list that looks like a valid result.

**Verification:**

- `Grep` — `hasLocalNetworkPermission` appears in `SmbMediaScanner.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `SftpMediaScanner.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `FtpMediaScanner.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `NetworkVideoFrameDecoder.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `NetworkPdfThumbnailLoader.kt`.
- `Grep` — `hasLocalNetworkPermission` appears in `NetworkEpubCoverLoader.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 6/6 PASS (hasLocalNetworkPermission in SmbMediaScanner, SftpMediaScanner, FtpMediaScanner, NetworkVideoFrameDecoder, NetworkPdfThumbnailLoader, NetworkEpubCoverLoader). Context injected via @ApplicationContext in all 3 scanners. Dev log recorded.

---

### Step 05.4 — Run the narrow build gate for the reader slice

**Files:** none modified — verification only
**Depends on:** Step 05.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> This phase is complete only when the playback helpers, datasource factories, scanners, and Glide readers compile together.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification PASS. `./gradlew.bat :app_v2:compileStandardDebugKotlin` → BUILD SUCCESSFUL (44s).

---

## Phase Done Criteria

- [x] Every Step 05.* above is `[x] done`.
- [x] All three protocol datasources gate before remote socket acquisition.
- [x] SMB / SFTP / FTP scanners no longer treat missing permission as a valid empty result.
- [x] Network thumbnail readers do not poison permanent failed-cache entries on missing permission.

---

## Handoff Notes to Next Phase

Phase 06 reuses the same strings and helper contract for Cast. Do not introduce a second permission vocabulary for Cast-only messages.

---

## Rollback Plan

Restore the SMB backups if the reader sweep goes unstable, then revert the playback-helper and thumbnail/scanner changes together.