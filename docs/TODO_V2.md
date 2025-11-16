# TODO V2 - FastMediaSorter v2

**Latest Build**: 2.0.2511162234  
**Version**: 2.0.0-build2511162234

---

## 🛠️ Recent Fixes

### Build 2.0.2511162234 ✅
- ✅ **FIXED: FTP video playback error** - Added FTP to network resource check in `playVideo()`
- Root cause: Condition checked only SMB/SFTP, FTP fell through to local file playback
- Solution: Added `ResourceType.FTP` to network resource condition (line 1121)
- Result: FTP videos now use FtpDataSource streaming (already implemented in build 2511162212)

### Build 2.0.2511162232 ✅
- ✅ **FIXED: FTP thumbnails not loading** - Added active mode fallback for `downloadFile()` on passive timeout
- Root cause: Parallel thumbnail requests create multiple data connections, emulator can't connect to passive ports
- Solution: Catch `SocketTimeoutException` in `downloadFile()`, retry with active mode, restore passive
- Test: FTP folder with images/videos → thumbnails load via active mode fallback

### Build 2.0.2511162226 ✅ CONFIRMED WORKING
- ✅ **FIXED: Loading state text bug** - "No media files found" during loading → now shows "Loading..."
- ✅ **FIXED: SMB file selection bug** - Clicked file opens correctly (indexOf by path instead of object reference)

### Build 2.0.2511162151 ✅ CONFIRMED WORKING
- ✅ **FIXED: submitList redundancy during navigation** - Moved list tracking from Activity to ViewModel
- ✅ **Root cause**: BrowseActivity destroyed/recreated on Back → local variables lost
- ✅ **Solution**: `BrowseViewModel.lastEmittedMediaFiles` survives Activity recreation
- ✅ **Test confirmed**: "Skipping submitList: list unchanged (size=12, sameRef=true)" in logs
- ✅ **Performance**: 32 skipped frames (was 48-67), NO redundant submitList calls
- 📊 **Metrics**: Same reference detection works perfectly (`shouldSubmit=false: Same reference (===)`)

## 🎯 Current Development Tasks

  - **See**: "Cloud storage support (Google Drive)" in Network Features section
  - **TODO - Testing required**:
    - [ ] Test cloud resource browsing: Add Google Drive folder → Navigate to BrowseActivity → Verify files display
    - [ ] Handle network errors and API quota limits gracefully
    - [ ] Test file operations: copy/move/delete local↔cloud, cloud↔cloud
    - [ ] Test undo operations for cloud files (if applicable)
    - [ ] Configure OAuth2 credentials in Google Cloud Console for production
  - **Next Steps**:
    1. Install APK and test Google Drive authentication flow
    2. Select folder from Google Drive and verify it appears in resource list
    3. Browse cloud resource and verify files load correctly
    4. Test file operations (copy file from local to Google Drive, move between folders)

- [ ] **Pagination for large datasets (1000+ files) - Testing**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Ready for real-world testing
  - **TODO**:
    - Test with 1000+, 5000+ files on all resource types (LOCAL, SMB, SFTP, FTP)
    - Verify pagination performance improvements vs full scan

### 🟠 High Priority

- [ ] **Resource availability indicator (red dot for unavailable resources)**
  - **Priority**: HIGH - User experience improvement
  - Add `isAvailable: Boolean` field to ResourceEntity (Room migration 6→7)
  - Show red dot overlay on unavailable resources in MainActivity
  - Red dot disappears after successful refresh or Browse

- [ ] **Network undo operations - Testing**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Needs real-world testing
  - Test undo for SMB/SFTP/FTP operations in PlayerActivity and BrowseViewModel
  - Handle permission errors for network trash folder creation
  - Add network-specific timeout handling for undo operations
  - Ensure trash cleanup works for network folders

- [ ] **Network image editing - Testing**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Needs performance testing
  - Test with large images over slow network
  - Add progress reporting during download/upload phases
  - Add cancellation support for download/upload operations

### 🟡 Medium Priority

- [ ] **Background sync - Testing**
  - **Status**: ✅ IMPLEMENTATION COMPLETED - Needs UI and real-world testing
  - Add UI indicator for missing/unavailable network files
  - Show sync status in resource list (last sync time, sync errors)
  - Test sync behavior after 4+ hours idle

## 🎨 UI/UX Improvements

- [ ] **Animations and transitions**
  - Screen transitions (slide, fade, shared element)
  - RecyclerView item animations (add, remove, reorder)
  - Ripple effects for buttons where missing
  - Smooth progress indicators

- [ ] **Settings: Player hint toggle**
  - Re-enable preference to show/hide player touch zones overlay
  - Allow user to re-trigger first-run hint

## ⚡ Performance Optimization (LOW PRIORITY)

- [ ] **ExoPlayer initialization off main thread** (~39ms blocking)
- [ ] **ExoPlayer audio discontinuity investigation** (warning in logs, не критично)
- [ ] **Background file count optimization** (duplicate SMB scans)
- [ ] **RecyclerView profiling** (onBind <1ms target, test on low-end devices)
- [ ] **Layout overdraw profiling** (<2x target)
- [ ] **Database indexes** (name, type, size, date columns)
- [ ] **Memory leak detection** (LeakCanary integration)
- [ ] **Battery optimization** (reduce sync on low battery)

## 🌐 Network Features

- [ ] **Cloud storage (OneDrive, Dropbox)**
  - OneDrive/Dropbox API integration with OAuth2
  - Reuse CloudStorageClient interface
  - Test multi-cloud operations

- [ ] **Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when network unavailable
  - Operation queue for delayed sync

## 🧪 Testing

- [ ] **Unit tests** (domain layer, >80% coverage)
- [ ] **Instrumented tests** (Room, Espresso UI flows)
- [ ] **Manual testing** (Android 8-14, tablets, file types, edge cases)
- [ ] **Security audit** (credentials, input validation, permissions)

## 🧰 Code Quality

- [ ] **Static analysis** (detekt/ktlint integration)
- [ ] **Edge cases** (empty folders, 1000+ files, long names, special chars)

## 📦 Release Preparation

### Build
- [ ] **ProGuard/R8** (rules, test obfuscated APK)
- [ ] **APK signing** (keystore, test signed APK)
- [ ] **Size optimization** (resource/code shrinking, AAB)
- [ ] **Versioning** (versionCode/Name, Git tag v2.0.0)
- [ ] **Dependencies** (update to latest stable)

### Documentation
- [ ] **README** (v2 features, screenshots, en/ru/uk)
- [ ] **CHANGELOG** (Added/Changed/Fixed/Removed)
- [ ] **User guide** (features, FAQ, troubleshooting)

## 🚀 Google Play Store

### Store Materials
- [ ] **Listing** (title, descriptions en/ru/uk)
- [ ] **Screenshots** (4-8 per device, localized)
- [ ] **Feature graphic** (1024x500px)
- [ ] **App icon** (adaptive, test launchers)
- [ ] **Privacy Policy** (v2 data usage, host online)
- [ ] **Content rating** (IARC questionnaire)

### Release
- [ ] **Internal testing** (APK/AAB upload, ProGuard mapping)
- [ ] **Closed beta** (5-20 testers, crash monitoring)
- [ ] **Production** (staged rollout 10→100%)
- [ ] **Post-release** (metrics, reviews, analytics)

---

## 📋 Next Priorities

1. **Test Google Drive integration** (OAuth, file operations)
2. **Test pagination** (1000+ files on all resource types)
3. **Test network undo/editing** (SMB/SFTP/FTP)
4. **Resource availability indicator** (red dot for unavailable)

