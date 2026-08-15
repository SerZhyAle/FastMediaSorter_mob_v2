# Tactical Spec: S0052 — bugfix-sftp-datasource-log-spam

**Ticket:** S0052  
**Status:** Tactical  
**Parent:** PLAN/S0052_bugfix-sftp-datasource-log-spam.md

---

## Phases

- [x] Phase 1: Audit all 4 DataSource implementations
- [x] Phase 2: Fix SftpDataSource — replace read() spam with counter+VERBOSE
- [x] Phase 3: Fix FtpDataSource — same pattern as SFTP
- [x] Phase 4: Document SMB/Cloud audit results; update strategic spec

---

## Phase 1: Audit — DONE

| Source | Hot-path logging | Verdict |
|--------|-----------------|---------|
| SFTP   | `read()`: logs every call while `totalBytesRead <= 10 000`, then every 500 KB | **SPAM** |
| FTP    | `read()`: identical condition to SFTP | **SPAM** |
| SMB    | `logProgress()`: every 10 MB OR if `BuildConfig.LOG_SMB_IO`; summary in `close()` | OK (no change) |
| Cloud  | `read()`: no per-read logging at all | OK (no change) |

---

## Phase 2: Fix SftpDataSource — DONE

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`

Verification:
- [x] `read()` contains no `totalBytesRead <= 10000` condition
- [x] `read()` uses `Timber.v()` for per-read events (first 3 + every 1000th)
- [x] `close()` emits summary with `totalRead`, `calls`, `elapsed`
- [x] `companion object` has `LOG_INITIAL_CALLS = 3L` and `LOG_PERIODIC_INTERVAL = 1000L`

---

## Phase 3: Fix FtpDataSource — DONE

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt`

Verification:
- [x] `read()` contains no `totalBytesRead <= 10000` condition
- [x] `read()` uses `Timber.v()` for per-read events (first 3 + every 1000th)
- [x] `close()` emits summary with `totalRead`, `calls`, `elapsed`
- [x] `companion object` has same constants as SFTP

---

## Phase 4: Documentation — DONE

Strategic spec `PLAN/S0052_bugfix-sftp-datasource-log-spam.md` section 6 updated:
- SMB audit: OK, no changes needed
- FTP audit: SPAM found, fixed in Phase 3
- Cloud audit: OK, no changes needed

---

## Last Audit

**Date:** 2026-05-02  
**Result:** Verified

| Criterion | Status |
|-----------|--------|
| No `totalBytesRead <= 10000` condition in any DataSource | PASS — grep returns 0 files |
| SFTP `read()` uses `Timber.v()` | PASS |
| FTP `read()` uses `Timber.v()` | PASS |
| SFTP `close()` emits summary (totalRead, calls, elapsed) | PASS |
| FTP `close()` emits summary (totalRead, calls, elapsed) | PASS |
| SFTP + FTP companion object has `LOG_INITIAL_CALLS`, `LOG_PERIODIC_INTERVAL` | PASS |
| SMB audit: already OK (`logProgress()` every 10 MB) | PASS — no change needed |
| Cloud audit: already OK (no per-read logging) | PASS — no change needed |
| `assembleStandardDebug` build succeeds | PASS — BUILD SUCCESSFUL in 39s |
