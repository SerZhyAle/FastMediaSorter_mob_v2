# TODO V2 - FastMediaSorter v2

## 🎯 Current Development Tasks

### 🚨 Critical Bugs


- [x] **Password migration: plaintext → encrypted (PARTIALLY)**
  - ✅ Fixed fallback logic: now catches `IllegalArgumentException` (bad base-64)
  - ✅ Plaintext passwords are used as is (backward compatibility)
  - ⚠️ TODO: Create a Room migration to re-encrypt existing plaintext passwords
  - ⚠️ TODO: Add a check when saving a resource (if plaintext → re-encrypt)

- [ ] **SMB Authentication failed after password migration (SHOULD WORK)**
  - Problem: `STATUS_OTHER (0xc0000234): Authentication failed for 'sza'`
  - Cause fixed: empty passwords replaced with plaintext fallback
  - Required: test SMB connection after the fix

- [x] **Resource Path is updated when editing an SMB/SFTP resource**
  - ✅ SMB: path is rebuilt as `smb://newServer/newShare` when server/share changes
  - ✅ SFTP: path is rebuilt as `sftp://newHost:port/newPath` when host/port/path changes
  - Fixed in `EditResourceViewModel.saveChanges()`

### 🔧 Improvements and Refinements

- [ ] **SFTP: Add getting file size and date**
  - SftpClient.getFileAttributes() to get stat() data
  - Update SftpMediaScanner to use real size/createdDate values
  - Currently: size=0, createdDate=0 (placeholder values)

- [ ] **Mixed operations SMB↔SFTP**
  - Copy: SMB→SFTP, SFTP→SMB, SFTP→Local, Local→SFTP
  - Move: same combinations
  - FileOperationUseCase: determine priority (destination type)

- [ ] **Progress bars for long operations**
  - Copy/Move: show file size and speed
  - Use WorkManager for background tasks
  - Notifications for completed operations

---

## 🌐 Network and Cloud Functions

- [ ] **Cloud: Google Drive API Integration**
  - Google Sign-In and Drive API
  - Folder browsing and file operations
  - OAuth2 flow and token storage
  - Adapt copy/move for cloud files

- [ ] **Cloud: Dropbox API Integration**
  - Dropbox SDK
  - Authentication and file access
  - Folder sync and operations
  - Compatibility with existing file operations

- [ ] **Network: Background synchronization**
  - WorkManager for periodic synchronization
  - Check for new/deleted files in network/cloud resources
  - Update fileCount and thumbnail cache
  - Synchronization status indicator

- [ ] **Network: Offline mode**
  - Cache thumbnails and metadata locally
  - Show cached data when there is no network
  - Offline status indication in the UI
  - Operation queue for subsequent synchronization

---

## 🎨 UI/UX Improvements

- [ ] **UI: Dark/Light theme**
  - Material Design 3 theming
  - System theme detection
  - Theme switcher in Settings
  - Test all screens in both themes

- [ ] **UI: Animations and transitions**
  - Screen transitions (slide, fade)
  - List animations (add, remove, reorder)
  - Button ripple effects
  - Progress indicators

- [ ] **UI: Empty states**
  - Empty resource list: "No resources added yet" + Add button
  - Empty file list: "No media files found in this folder"
  - Empty search: "No files match your criteria"
  - Network error: "Connection failed" + Retry button

- [ ] **UI: Accessibility**
  - Content descriptions for all images/icons
  - TalkBack screen reader support
  - Minimum touch target size (48dp) - check all buttons
  - High contrast mode support

---

## 🧪 Testing

- [ ] **Testing: Unit tests**
  - All UseCase classes with JUnit
  - ViewModels with kotlinx-coroutines-test
  - Repository with mock dependencies
  - Goal: >80% code coverage for the domain layer

- [ ] **Testing: Instrumented tests**
  - Database operations with Room testing library
  - UI flows with Espresso
  - Navigation between screens
  - File operations with temporary test folders

- [ ] **Testing: Manual testing**
  - Android versions (8.0 - 14.0)
  - Different screen sizes (phone, tablet)
  - Different file types and sizes
  - Connection scenarios (slow network, no internet)

- [ ] **Testing: Security audit**
  - Check for hardcoded credentials
  - Validate input sanitization
  - Test file path traversal prevention
  - Check permission usage

---

## 🐛 Optimization

- [ ] **Performance: Memory management**
  - Profiling with Android Profiler
  - Fix memory leaks (LeakCanary)
  - Optimize bitmap loading (downsampling)
  - Pagination for large file lists

- [ ] **Performance: Performance**
  - Profile CPU and frame drops
  - Optimize database queries (add indexes)
  - Background threads for heavy operations
  - Reduce overdraw in layouts

- [ ] **Performance: Battery**
  - Reduce background work
  - Efficient use of JobScheduler/WorkManager
  - Pause synchronization on low battery
  - Release resources in the background

- [ ] **Bug fix: Edge cases**
  - Empty folders, folders with many files (1000+)
  - Very long file names
  - Special characters in names
  - Corrupted media files

---

## 📦 Release Preparation

### Build & Configuration

- [ ] **ProGuard/R8 configuration**
  - ProGuard rules for release build
  - Test obfuscated APK
  - Keep classes for reflection
  - Check functionality after ProGuard

- [ ] **APK signing**
  - Create a release keystore (if it doesn't exist)
  - Securely store the keystore (not in git)
  - Configure signing in build.gradle.kts
  - Test installation of the signed APK

- [ ] **APK size optimization**
  - Resource shrinking
  - Code shrinking (R8)
  - Vector drawables instead of PNG
  - Remove unused resources and dependencies
  - Consider App Bundle (.aab)

- [ ] **Versioning**
  - Update versionCode in build.gradle.kts
  - Update versionName (e.g., 2.0.0)
  - Follow semantic versioning (MAJOR.MINOR.PATCH)

- [ ] **Update dependencies**
  - Update libraries to the latest stable versions
  - Test after each update
  - Check for deprecated APIs
  - Fix breaking changes

### Documentation

- [ ] **Update README**
  - Update README.md with v2 features
  - Update README.ru.md and README.ua.md
  - Add screenshots of the new UI
  - Update build instructions

- [ ] **Update CHANGELOG**
  - Document all changes
  - Group by: Added, Changed, Fixed, Removed
  - Specify version and release date
  - Mention breaking changes

- [ ] **Create user documentation**
  - User guide (how to use the application)
  - Documentation of all functions and gestures
  - FAQ section
  - Troubleshooting guide

---

## 🚀 Google Play Store

### Material Preparation

- [ ] **Store listing**
  - App title (max 30 characters)
  - Short description (max 80 characters)
  - Full description (max 4000 characters)
  - Translation into Russian and Ukrainian

- [ ] **Screenshots**
  - 4-8 screenshots per screen (phone and tablet)
  - Key features (Main, Browse, Player)
  - Device frames and annotations
  - Localized screenshots (en, ru, uk)

- [ ] **Feature graphic**
  - 1024x500px design
  - App branding and key visual
  - Google Play design guidelines
  - Localized versions

- [ ] **App icon**
  - Adaptive icon (foreground + background)
  - Test on different launchers
  - Google Play guidelines
  - All sizes (mipmap-*)

- [ ] **Privacy Policy**
  - Update with v2 data usage
  - Mention permissions and their purposes
  - Contact information
  - Host online (GitHub Pages)

- [ ] **Content rating**
  - IARC questionnaire in Play Console
  - Age rating (e.g., Everyone)

### Release Process

- [ ] **Internal testing**
  - Upload APK/AAB to Play Console (Internal Testing)
  - Test installation and updates
  - Check all functions in the production build
  - ProGuard mapping file uploaded

- [ ] **Closed beta**
  - Promote to Closed Testing track
  - Add beta testers
  - Monitor crash reports
  - Collect feedback and fix issues

- [ ] **Production release**
  - Promote to Production track
  - Rollout percentage (start with 10-20%)
  - Monitor crash-free rate and ANR rate
  - Gradually increase to 100%

- [ ] **Post-release monitoring**
  - Play Console metrics (installs, crashes, ratings)
  - Respond to user reviews
  - Firebase Analytics events
  - Firebase Crashlytics reports

---

## 📊 Project Status


**In progress:**
- 🔄 SFTP file attributes (size, date)
- 🔄 Mixed operations SMB↔SFTP
- 🔄 On-device testing

**Next priorities:**
1. Complete SFTP improvements
2. Manual on-device testing
3. Performance optimization
4. Prepare materials for Google Play
5. Beta testing
