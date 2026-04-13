# Memory & Resource Leak Tasks — ML-001 to ML-011

**Research Date**: 2026-04-13  
**Total Tasks**: 11 (2 CRITICAL, 5 HIGH, 4 MEDIUM)  
**Implementation Status**: 11/11 COMPLETED ✅ (ALL DONE)

---

## Quick Status

| Task | Priority | Component | Status |
|------|----------|-----------|--------|
| [ML-001](task-ML-001-pdf-viewer-manager-not-released.md) | CRITICAL | `PlayerLifecycleManager` | ✅ COMPLETED (2026-04-13 16:35) |
| [ML-002](task-ML-002-global-scope-onedrive.md) | CRITICAL | `OneDriveRestClient` | ✅ COMPLETED (2026-04-13 16:37) |
| [ML-003](task-ML-003-ftp-temp-file-wrong-dir.md) | HIGH | `FtpFileOperationHandler` | ✅ COMPLETED (2026-04-13 16:41) |
| [ML-004](task-ML-004-sftp-datasource-inputstream-leak.md) | HIGH | `SftpDataSource` | ✅ COMPLETED (2026-04-13 16:43) |
| [ML-005](task-ML-005-dialog-scope-no-supervisor-no-cancel.md) | HIGH | Dialog scopes | ✅ COMPLETED (2026-04-13 16:50) |
| [ML-006](task-ML-006-unified-file-cache-no-size-limit.md) | HIGH | `UnifiedFileCache` | ✅ COMPLETED (2026-04-13 16:53) |
| [ML-007](task-ML-007-temp-file-manager-no-startup-sweep.md) | HIGH | `TempFileManager` | ✅ COMPLETED (2026-04-13 16:57) |
| [ML-008](task-ML-008-sftp-connection-pool-race-condition.md) | MEDIUM | `SftpClient` | ✅ COMPLETED (2026-04-13 17:01) |
| [ML-009](task-ML-009-unscoped-coroutines-singletons.md) | MEDIUM | Singleton scopes | ✅ COMPLETED (2026-04-13 17:05) |
| [ML-010](task-ML-010-epub-webview-no-database-cleanup.md) | MEDIUM | `EpubViewerManager` | ✅ COMPLETED (2026-04-13 17:08) |
| [ML-011](task-ML-011-connection-throttle-manager-bare-job.md) | MEDIUM | `ConnectionThrottleManager` | ✅ COMPLETED (2026-04-13 17:09) |

---

## Completed Tasks

### ✅ ML-001: PdfViewerManager Not Released (CRITICAL)
**Completion**: 2026-04-13 16:35:27  
**Impact**: Prevents `PdfRenderer` native memory leak + file handle leak on every PDF session  
**Changes**:
- Added `activity.pdfViewerManager.close()` call in `PlayerLifecycleManager.onDestroy()` (L216)
- Fixed misleading comment

**Next**: LeakCanary validation + heap monitoring on device

---

### ✅ ML-002: GlobalScope in OneDriveRestClient (CRITICAL)
**Completion**: 2026-04-13 16:37:30  
**Impact**: Prevents Activity reference leak during OAuth flow  
**Changes**:
- Injected `@ApplicationScope applicationScope: CoroutineScope`
- Replaced `GlobalScope.launch()` at L228, L245 with `applicationScope.launch()`
- Removed `GlobalScope` import

**Next**: Device rotation test + LeakCanary check

---

## All Tasks Completed

All 11 memory/resource leak fixes have been implemented and integrated into the codebase.

### Summary of Changes

**CRITICAL (2)** — Prevents Activity leaks and native memory leaks
- ML-001: PdfViewerManager cleanup
- ML-002: OneDrive GlobalScope replacement

**HIGH (5)** — Prevents cache/temp file accumulation and stream leaks
- ML-003: FTP temp file directory fix
- ML-004: SftpDataSource stream leak handling
- ML-005: Dialog scope lifecycle management
- ML-006: UnifiedFileCache size limit + LRU eviction
- ML-007: Temp file cleanup on startup + memory pressure

**MEDIUM (4)** — Prevents race conditions and coroutine lifecycle issues
- ML-008: SFTP connection pool synchronization
- ML-009: Managed scopes for singletons + BootReceiver goAsync()
- ML-010: WebViewDatabase credentials cleanup
- ML-011: ConnectionThrottleManager SupervisorJob

---

## Implementation Pattern (Template for Remaining Tasks)

Each task includes:
1. **Problem** — what's wrong and why it matters
2. **Fix** — exact code change with context
3. **Test Plan** — reproduction steps + verification
4. **Acceptance Criteria** — checkboxes for completion

After implementation:
1. Update task file with `## Implementation Status` section
2. Run dev-log: `.\scripts\add_to_dev_log.ps1 "file" "component" "description"`
3. Update this README with ✅ status + completion date

---

## Research Summary

**Audit Sources**:
- 4 parallel Explore agents:
  - SFTP/SSH resource leaks (11 issues: 3 CRITICAL, 4 HIGH, 4 MEDIUM)
  - Document readers cleanup (5 issues: 1 CRITICAL, 2 HIGH, 2 MEDIUM)
  - File caching/temp files (7 issues: 1 CRITICAL, 3 HIGH, 3 MEDIUM)
  - Coroutine scope patterns (11 issues: 2 CRITICAL, 4 HIGH, 5 MEDIUM)

- 4 false positives from initial spec (all verified SAFE in actual code)
- 11 actionable tasks created with specific file/line references

**Next Action**: Implement HIGH priority tasks in order (ML-003 → ML-007), then MEDIUM tasks.

---

## Validation & Testing Roadmap

After all tasks complete:

1. **Quick Validation** (30 min per scenario — Section 17.7 in research doc):
   - Playlist 1000 tracks
   - Slideshow 500 photos
   - PDF 100MB
   - Rapid network transitions

2. **LeakCanary Cycle** (1h):
   - 2-hour session with LeakCanary enabled
   - Document readers (PDF/EPUB/Text)
   - OneDrive auth + rotation
   - Expected: 0 leaked instances

3. **Production Monitoring** (ongoing):
   - Heap growth rate < 5 MB/hour alert threshold
   - Connections > 5 at idle → investigate
   - Cache size > 1GB/week → warn

---

**Owner**: FastMediaSorter v2 Engineering  
**Status**: ✅ COMPLETE (11/11)  
**Completion Time**: ~1 day (parallel implementation)  
**Next**: Validation & testing (LeakCanary, device testing)
