# FastMediaSorter v2 - Comprehensive Refactoring Roadmap

**Last Updated**: December 13, 2025 - After Phase 2.5 (SFTP + FTP + Cloud handler migrations)

## Executive Summary

**Total Kotlin files**: 229  
**Lines in top 25 largest files**: ~32,000  
**Estimated reducible duplication**: ~6,500 lines (20%)

### Current Progress: Phase 2.5 completed (SFTP + FTP + Cloud done)

**Lines Saved So Far**: ~2,524 lines (Phase 1 + Phase 2.3 SMB migration)  
**Lines Added**: +690 lines (SftpOperationStrategy + FtpOperationStrategy)  
**Net Savings**: ~1,834 lines  
**Expected Total Savings**: ~5,000 lines after Cloud handler migration (Phase 2.5)

---

## ✅ Completed Work

### Phase 1: Quick Wins - COMPLETED (100%)

| Priority | Lines Saved | Status |
|----------|-------------|--------|
| Path Parsing Utilities | -112 lines | ✅ Done |
| SAF Helper Utility | -155 lines | ✅ Done |
| Common UI Events | -44 lines | ✅ Done |
| MIME Types Cleanup | -30 lines | ✅ Done |
| File Extension Constants | -39 lines | ✅ Done |
| Shared Constants | -11 lines | ✅ Done |

**Total Phase 1 Reduction**: ~391 lines

### Phase 2: File Operation Infrastructure - COMPLETED (Steps 2.1 & 2.2)

**Completed Components**:

1. **FileOperationStrategy Interface** (70 lines) - `data/transfer/FileOperationStrategy.kt`
   - Protocol-agnostic operation methods
   - `copyFile()`, `moveFile()`, `deleteFile()`, `exists()`
   - Protocol detection via `supportsProtocol()`

2. **BaseFileOperationHandler** (455 lines) - `data/transfer/BaseFileOperationHandler.kt`
   - Common `executeCopy()`, `executeMove()`, `executeDelete()` implementations
   - Eliminates duplicate loop structures
   - Standardized error handling and result building
   - Utility methods: filename extraction, SAF deletion, trash folder creation

3. **LocalOperationStrategy** (141 lines) - `data/transfer/strategy/LocalOperationStrategy.kt`
   - Local file system operations
   - Progress callback support
   - Atomic rename with copy+delete fallback

4. **SmbOperationStrategy** (330 lines) - `data/transfer/strategy/SmbOperationStrategy.kt`
   - SMB protocol operations via SmbClient
   - SMB↔Local and SMB↔SMB transfers
   - Credential management integration
   - Server-side rename optimization

**Commits**:
- `1aba9c1` - Base infrastructure (Strategy + BaseHandler)
- `33f5392` - Strategy implementations (Local + SMB)

---

## 🎯 Current Phase: File Operation Handlers Migration

### Phase 2.3: SMB Handler Migration (IN PROGRESS)

**Goal**: Migrate SmbFileOperationHandler to use BaseFileOperationHandler

**Current State**: 1,640 lines (extended from base class)  
**Target**: ~900 lines (-740 lines, -45%)

**Status: Phase 2.3 COMPLETED ✅** (All 3 sub-phases finished)

**Completed Steps**:
- ✅ Phase 2.3.1: Extended SmbFileOperationHandler from BaseFileOperationHandler (commit 274e29b)
  - Added SmbOperationStrategy and LocalOperationStrategy instances
  - Overridden copyFile() for cross-protocol transfers
  - Made execute methods 'open' in BaseFileOperationHandler
  
- ✅ Phase 2.3.2: Created SFTP and FTP operation strategies (commit f8d011d, 8895c43)
  - Created SftpOperationStrategy (~350 lines)
  - Created FtpOperationStrategy (~340 lines)
  - Registered all 4 strategies in SmbFileOperationHandler
  - Simplified copyFile() override - now delegates to base class
  - All cross-protocol combinations now handled automatically

- ✅ Phase 2.3.3: Removed duplicate code from SmbFileOperationHandler (commit 59fdd2f)
  - Deleted executeCopy() override (294 lines)
  - Deleted executeMove() override (565 lines)
  - Deleted executeDelete() override (160 lines)
  - Deleted copySftpToSmb(), copyFtpToSmb(), copySmbToFtp() (~286 lines)
  - Added executeRename() stub (delegates to executeMove)
  - **Total reduction: 1,324 lines** (82% reduction: 1,613 → 289 lines)

**Final State**:
- Handler extends base class with full strategy pattern
- 4 strategies registered: SMB, SFTP, FTP, Local
- Base class automatically routes all protocol combinations
- Retained helper methods: downloadFromSmb, uploadToSmb, copySmbToSmb, parseSmbPath
- Build successful ✅

**Actual Reduction**: 1,613 → 289 lines (-1,324 lines, -82%)

---

## 📋 Remaining Phases

### Phase 2.4: Create Remaining Strategies

- ✅ **SftpOperationStrategy** created (commit f8d011d)
- ✅ **FtpOperationStrategy** created (commit f8d011d)
- ✅ URL parsing/credentials compatibility updates (commit 2805591)

✅ **CloudOperationStrategy** created (commit f262c89)
   - `data/transfer/strategy/CloudOperationStrategy.kt`

### Phase 2.5: Migrate Remaining Handlers

1. ✅ **SftpFileOperationHandler**: migrated to BaseFileOperationHandler + strategies (commit c822881)
2. ✅ **FtpFileOperationHandler**: migrated to BaseFileOperationHandler + strategies (commit 2805591)
3. ✅ **CloudFileOperationHandler**: migrated to BaseFileOperationHandler + strategies (commit 16d306e)

**Total Phase 2 Reduction**: 6,786 → ~3,500 lines (-3,286 lines, -48%)

---

## Phase 3: PlayerActivity Decomposition

**Current State**: 2,731 lines (already reduced from 3,133)  
**Target**: ~1,000 lines (-1,731 lines, -63%)

### Completed Helpers (already extracted):
- PlayerKeyboardHandler (137 lines)
- NetworkFileManager (339 lines)
- PdfViewerManager (190 lines)
- TextViewerManager (commit bc8c18f)
- PlayerUiStateCoordinator (commit 1251e4e)
- DestinationButtonsManager (252 lines)
- CommandPanelController
- FileOperationsHandler
- ImageLoadingManager
- VideoPlayerManager
- PlayerGestureHelper
- PlayerDialogHelper
- SlideshowController

### Remaining Extractions:

**Step 3.2: PlayerUiStateCoordinator** (~200 lines) ✅
- Centralized `updateUI(state: PlayerState)` (moved to helper)
- View visibility management
- Control panel state updates

**Step 3.3: ExoPlayer Lifecycle** (~150 lines) ✅ (commits ffbad6c, 7de6a2f)
- Extend existing VideoPlayerManager (lifecycle + ownership)
- Track selection / settings application moved into manager

**Step 3.4: UndoOperationManager** (~100 lines) ✅ (commit 233ec11)
- Undo snackbar display (moved to helper)
- Timeout management remains in PlayerViewModel (5 minutes)
- ViewModel coordination (delegated via callback)

**Step 3.5: MediaDisplayCoordinator** (~250 lines) ✅ (commit 820b2b6)
- Route to appropriate managers (moved to helper)
- View preparation remains in PlayerActivity media methods
- Auto-advance coordination remains in SlideshowController + VideoPlayerManager callback

**Step 3.6: Lifecycle Consolidation** (~150 lines) ✅ (commit 2096e67)
- `initializeManagers()` method - all 13+ managers initialization
- `releaseResources()` method - cleanup logic consolidation
- Simplified onCreate/onDestroy lifecycle

**Step 3.7: PlayerSettingsManager** (~120 lines) ✅ (commit 5ee23a2)
- Settings dialog display
- Playback speed selection dialog
- ExoPlayer settings application (delegated to VideoPlayerManager)
- Settings persistence for session

---

## Phase 4: Connection Pool Abstraction

**Goal**: Consolidate ~300 lines of identical pooling logic

**Status**: Base infrastructure created ✅ (commit 183b19f)

**Current State**:
- ✅ BaseConnectionPool.kt created (205 lines) - generic connection pool abstraction
- SmbClient: Connection pool (~100 lines) - migration deferred (complex SMB-specific logic)
- SftpClient: Connection pool (~100 lines) - migration deferred (multi-channel architecture)
- FtpClient: No connection pool (stateless)

**Solution**:

1. ✅ **Create base pool** - `data/network/pool/BaseConnectionPool.kt` (~205 lines):
   ```kotlin
   abstract class BaseConnectionPool<K, C>(
       private val maxConnections: Int,
       private val idleTimeoutMs: Long
   ) {
       private val pool = ConcurrentHashMap<K, PooledConnection<C>>()
       private val semaphore = Semaphore(maxConnections)
       
       abstract suspend fun createConnection(key: K): C
       abstract fun isConnectionValid(connection: C): Boolean
       abstract fun closeConnection(connection: C)
       
       suspend fun <T> withConnection(key: K, block: suspend (C) -> T): T
       fun invalidate(key: K)
       fun cleanupIdle()
   }
   ```

2. **Migrate clients** - Remove ~100 lines from each, use base class

**Expected Reduction**: ~300 → ~150 lines (-150 lines)

---

## Overall Progress Summary

| Phase | Component | Current Lines | Target Lines | Reduction | Status |
|-------|-----------|---------------|--------------|-----------|--------|
| 1 | Quick Wins | ~391 | ~0 | -391 | ✅ Done |
| 2.1-2.2 | Infrastructure | ~0 | +996 | +996 (new) | ✅ Done |
| 2.3 | SMB Handler | 1,570 | 900 | -670 | ✅ Done |
| 2.4-2.5 | Other Handlers | 5,216 | 2,600 | -2,616 | ✅ Done (all strategies + handlers migrated) |
| 3 | PlayerActivity | 2,731 | ~1,800 | ~-930 | ✅ Done (7 helpers extracted) |
| 4 | Connection Pool | 300 | 150 | -150 | 🚧 In progress (base class created) |
| **Total** | | **10,208** | **5,646** | **-4,562** | **73%** |

**Note**: Infrastructure adds +996 lines but enables -3,286 lines reduction in handlers (net: -2,290 lines)

---

## Testing Strategy

For each refactoring phase:

1. **Before refactoring**:
   - Document current behavior
   - List all test scenarios
   - Capture edge cases

2. **During refactoring**:
   - Keep old code commented initially
   - Incremental commits after each sub-step
   - Build verification at each commit

3. **After refactoring**:
   - Test each protocol: Local, SMB, SFTP, FTP, Cloud
   - Test cross-protocol operations (all combinations)
   - Test error scenarios:
     - Network timeouts
     - Authentication failures
     - Disk space issues
     - Permission errors
   - Verify undo functionality
   - Verify soft delete (trash)
   - Test overwrite behavior
   - Check progress callbacks

---

## Risk Mitigation

1. **Incremental approach**: Each handler migrated separately
2. **Parallel implementations**: Keep old code until new is proven
3. **Commit after every successful build**: Easy rollback
4. **No pushes until fully tested**: All changes stay local
5. **Test-driven migration**: Test before/after each change

---

## Files NOT Recommended for Refactoring

| File | Lines | Reason |
|------|-------|--------|
| SmbClient.kt | 2,254 | Protocol-specific, complex state management |
| BrowseViewModel.kt | 1,539 | Already well-structured |
| SettingsFragments.kt | 1,879 | UI code, hard to abstract |
| MediaFileAdapter.kt | 1,057 | Standard RecyclerView pattern |
| FileOperationUseCase.kt | 997 | Business logic, not duplicated |

---

## Next Immediate Steps

1. ✅ ~~Create base infrastructure (FileOperationStrategy + BaseFileOperationHandler)~~
2. ✅ ~~Create LocalOperationStrategy~~
3. ✅ ~~Create SmbOperationStrategy~~
4. ✅ ~~Migrate SmbFileOperationHandler (pilot - validates pattern)~~
5. ✅ ~~Create SftpOperationStrategy~~
6. ✅ ~~Create FtpOperationStrategy~~
7. ✅ ~~Create CloudOperationStrategy~~
8. ✅ ~~Migrate CloudFileOperationHandler~~

**All major refactoring phases completed!**

**Remaining optional improvements**:
- Full migration of SmbClient/SftpClient to BaseConnectionPool (deferred - complex protocol-specific logic)
- Further PlayerActivity reduction (already reduced from 2,731 to ~1,800 lines)
- Additional helper extractions as needed
