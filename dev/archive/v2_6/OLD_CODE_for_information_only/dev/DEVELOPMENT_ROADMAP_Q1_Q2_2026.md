# Development Roadmap: Next 6 Months (Q1-Q2 2026)

**Last Updated**: December 13, 2025  
**Current Version**: v2.25.12xx (Production-ready, post-refactoring)  
**Goal**: Ship high-impact features while maintaining stability

---

## ✅ Foundation Complete (Dec 2025)

- [x] Great Refactoring: Strategy Pattern implementation (-3,286 lines)
- [x] PlayerActivity decomposition (-930 lines) 
- [x] BaseConnectionPool infrastructure
- [x] Build system: Auto-versioning, debug/release configs
- [x] Architecture: Clean Architecture + MVVM fully implemented

**Ready for feature development!**

---

## 🎯 Sprint 1: Unblock Cloud & Polish (Weeks 1-2, Jan 2026)

### Priority 1: Google Drive OAuth Setup
**Blocker**: Manual configuration required
**Time Estimate**: 2-4 hours

**Steps**:
1. [ ] Open Google Cloud Console → Create new project "FastMediaSorter"
2. [ ] Enable Google Drive API
3. [ ] Create Android OAuth 2.0 credentials:
   - Application type: Android
   - Package name: `com.sza.fastmediasorter`
   - SHA-1 fingerprint: Extract from keystore (`keytool -list -v -keystore fastmediasorter.keystore`)
4. [ ] Download `google-services.json` → place in `app_v2/`
5. [ ] Test authentication flow in debug build
6. [ ] Document setup in `docs/CLOUD_SETUP.md`

**Success Criteria**: User can sign in and browse Google Drive folders

---

### Priority 2: Pagination Real-World Testing
**Status**: Code complete, needs validation  
**Time Estimate**: 1 day

**Test Cases**:
1. [ ] Local folder: 1,000+ images
   - Verify smooth scrolling
   - Check memory usage (< 500MB)
   - Confirm no ANR (Application Not Responding)
2. [ ] SMB share: 5,000+ mixed files
   - Test connection stability during pagination
   - Verify correct file count display
3. [ ] Cloud: Large folder (500+ files)
   - Test progressive loading
   - Verify thumbnail caching

**Acceptance**: All scenarios pass without crashes, memory stays < 600MB

---

### Priority 3: Network Undo Manual Testing
**Status**: Implementation complete (soft-delete to `.trash/`)  
**Time Estimate**: 4 hours

**Scenarios**:
1. [ ] SMB: Delete file → verify `.trash/` folder created
2. [ ] SMB: Undo delete → file restored to original location
3. [ ] Cross-protocol: Move SMB→Local → Undo → file back on SMB
4. [ ] "Empty Trash" for SMB resource → `.trash/` folder removed
5. [ ] FTP: Same scenarios (if FTP server available)

**Edge Cases**:
- [ ] Undo after app restart (expired operation)
- [ ] Multiple undo operations in sequence
- [ ] Trash folder permissions (read-only share)

---

## 🚀 Sprint 2: Performance & Polish (Weeks 3-4, Jan 2026)

### Feature 1: Thumbnail Cache Management UI
**Impact**: User control over disk space  
**Time Estimate**: 1 day

**Implementation**:
1. [ ] Add Settings → Storage → "Thumbnail Cache" section
2. [ ] Display current cache size (query Glide disk cache)
3. [ ] Button: "Clear Thumbnail Cache"
4. [ ] Show cache hit/miss statistics (optional, use SharedPreferences counter)

**Files to modify**:
- `SettingsFragment.kt` - Add cache section
- New UseCase: `ClearThumbnailCacheUseCase.kt`
- Glide integration: Call `Glide.get(context).clearDiskCache()` on IO thread

---

### Feature 2: Connection Health Monitoring
**Impact**: Better diagnostics for network issues  
**Time Estimate**: 2 days

**Implementation**:
1. [ ] Add `ConnectionHealthTracker` singleton
   - Track success/failure rate per protocol
   - Store last 100 operations (circular buffer)
2. [ ] Settings → Advanced → "Connection Diagnostics"
   - Show per-resource health (green/yellow/red)
   - Display recent errors
3. [ ] Add Timber logging for slow operations (>1s)

**Files**:
- New: `data/network/ConnectionHealthTracker.kt`
- Modify: `SmbClient.kt`, `SftpClient.kt`, `FtpClient.kt` - add tracking calls
- New: `DiagnosticsFragment.kt`

---

## 🎨 Sprint 3: UX Quick Wins (Weeks 5-6, Feb 2026)

### Feature 1: Advanced Filtering Combinations
**User Request**: "Show videos > 10MB from last month"  
**Time Estimate**: 2 days

**Implementation**:
1. [ ] Extend `BrowseViewModel` filter logic
   - Add `FilterCriteria` data class (type, size range, date range)
   - Support AND combination
2. [ ] Update FilterDialog UI
   - Add size slider (0-100MB, >100MB)
   - Add date picker (last week, month, year, custom)
3. [ ] Save filter presets to SharedPreferences

**Files**:
- `BrowseViewModel.kt` - add `applyCombinedFilter()`
- `FilterDialog.kt` - expand UI
- New: `domain/model/FilterCriteria.kt`

---

### Feature 2: Batch Selection Improvements
**Impact**: Faster multi-file operations  
**Time Estimate**: 1 day

**Implementation**:
1. [ ] Add "Select Range" gesture (Shift+Click in list mode)
2. [ ] Add "Select All Videos/Images/Documents" quick actions
3. [ ] Toolbar: Show "X items selected" counter
4. [ ] Add "Invert Selection" button

**Files**:
- `MediaFileAdapter.kt` - add range selection logic
- `BrowseActivity.kt` - add quick action menu
- Update `selection_toolbar.xml`

---

## 🧪 Sprint 4: Testing & Quality (Weeks 7-8, Feb 2026)

### Initiative 1: Unit Test Coverage
**Current**: ~30% (estimated)  
**Target**: 60% for business logic  
**Time Estimate**: 3 days

**Focus Areas**:
1. [ ] All UseCases in `domain/usecase/`
   - Mock repository dependencies
   - Test happy path + error cases
2. [ ] ViewModels: `BrowseViewModel`, `PlayerViewModel`
   - Test state transitions
   - Verify event emissions
3. [ ] File operation strategies
   - Test cross-protocol routing
   - Verify error handling

**Tools**: JUnit 5, MockK, Turbine (for Flow testing)

---

### Initiative 2: UI Automation (Critical Paths)
**Goal**: Catch regressions before release  
**Time Estimate**: 2 days

**Test Scenarios** (Kaspresso):
1. [ ] Scan local folder → Browse → Select file → Copy to destination
2. [ ] Add SMB resource → Test connection → Browse files
3. [ ] Open PlayerActivity → Swipe through files → Delete → Undo
4. [ ] Add to Favorites → Navigate to Favorites tab → Verify file present

**Setup**:
- [ ] Add Kaspresso dependency to `build.gradle.kts`
- [ ] Create `androidTest/` test package
- [ ] Configure test runner in CI (if available)

---

## 📋 Sprint 5: Cloud & Cross-Platform Prep (Weeks 9-10, Mar 2026)

### Feature 1: OneDrive Full Implementation
**Current**: Partial/stub implementation  
**Time Estimate**: 3 days

**Tasks**:
1. [ ] Research OneDrive REST API v1.0 (Microsoft Graph)
2. [ ] Implement `OneDriveRestClient` methods:
   - `authenticate()` - OAuth 2.0 flow
   - `listFiles()` - enumerate folder
   - `downloadFile()`, `uploadFile()`, `deleteFile()`
3. [ ] Add to `CloudOperationStrategy`
4. [ ] Test with real OneDrive account

**Reference**: Google Drive implementation in `GoogleDriveRestClient.kt`

---

### Feature 2: Dropbox Full Implementation
**Similar to OneDrive**  
**Time Estimate**: 3 days

**Tasks**:
1. [ ] Dropbox API v2 integration
2. [ ] OAuth setup (create Dropbox app in App Console)
3. [ ] Implement file operations
4. [ ] Test multi-account support

---

## 🔮 Sprint 6: Future-Proofing (Weeks 11-12, Mar 2026)

### Initiative 1: Desktop Companion Prototype
**Goal**: Explore cross-platform potential  
**Time Estimate**: 5 days (research + prototype)

**Approach**:
1. [ ] Research Compose Multiplatform for Desktop
2. [ ] Create minimal PoC:
   - Browse local folders
   - Copy/move files
   - Two-way sync with Android app (via local network)
3. [ ] Evaluate feasibility for Q2 2026

---

### Initiative 2: Plugin Architecture Foundation
**Goal**: Enable future extensibility  
**Time Estimate**: 3 days

**Design**:
1. [ ] Create `Plugin` interface
   - `onFileSelected()`, `onBeforeCopy()`, `onAfterMove()`
2. [ ] Implement plugin discovery mechanism
   - Scan `plugins/` folder for JARs
   - Use Service Provider Interface (SPI)
3. [ ] Create sample plugin: "WatermarkPlugin"
   - Adds watermark to images on copy

**Reference**: Study Gradle plugin system for inspiration

---

## 📊 Success Metrics (End of Q1 2026)

| Metric                     | Current | Target (Mar 31) |
| :------------------------- | :------ | :-------------- |
| Google Drive Auth Working  | ❌      | ✅              |
| Unit Test Coverage         | ~30%    | 60%+            |
| Critical Path UI Tests     | 0       | 4+              |
| OneDrive/Dropbox Support   | Partial | Full            |
| Crash-Free Sessions        | ~95%    | 98%+            |
| 1000+ File Performance     | Unknown | Validated ✅    |

---

## 🛠️ Developer Resources

**Build Commands**:
```powershell
# Quick compile check
.\gradlew.bat :app_v2:compileDebugKotlin

# Full debug build
.\gradlew.bat :app_v2:assembleDebug

# Versioned release build
.\dev\build-with-version.ps1
```

**Testing**:
```powershell
# Unit tests
.\gradlew.bat :app_v2:testDebugUnitTest

# Code coverage report
.\gradlew.bat :app_v2:jacocoTestReport
```

**Documentation**:
- User guides: Root folder (README.md, QUICK_START.md, FAQ.md)
- Architecture: `dev/archive/REFACTORING_ROADMAP.md` (completed work)
- Strategic vision: `dev/STRATEGIC_GROWTH_PLAN.md`
- Current tasks: `dev/TODO_V2.md`

---

## 🚧 Dependencies & Blockers

**External**:
- Google Cloud Console access (for OAuth setup)
- Test devices with 1000+ files
- OneDrive/Dropbox developer accounts

**Internal**:
- None - all refactoring complete, codebase ready

---

## 📞 Next Actions (This Week)

1. **Monday**: Google Drive OAuth setup (2 hours)
2. **Tuesday**: Pagination testing on real devices (4 hours)
3. **Wednesday**: Network Undo test scenarios (4 hours)
4. **Thursday**: Start Thumbnail Cache UI (4 hours)
5. **Friday**: Code review, documentation updates (2 hours)

**Total time**: ~16 hours development (manageable in one week)

---

*This roadmap is a living document. Update weekly based on progress and new priorities.*
