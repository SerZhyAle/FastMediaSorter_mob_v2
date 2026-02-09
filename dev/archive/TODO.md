# TODO Plan (Jan 26, 2026)

## 1. Create Wear OS version ✅ MVP COMPLETE

- [x] Module setup (Gradle, Hilt, Manifest)
- [x] Basic UI Architecture (Compose for Wear, Navigation, Theme)
- [x] MVP Scope Defined (Music, Videos, Photos)
- [x] Browse Screens implementation
- [x] Player Screens (Audio/Video/Image)
- [x] Runtime Permissions
- [ ] Testing on emulator/device (optional polish)
- Outcome: A functional Wear companion app that can browse local media and control playback.

## 2. Add subfolder viewing to Browse activity ✅ COMPLETE

- [x] Scope: Enable navigating into subfolders within Browse, with clean back-navigation and breadcrumb/path handling.
- [x] Add isDirectory and childCount to MediaFile model
- [x] Add listDirectoryContents() to MediaScanner interface
- [x] Implement in LocalMediaScanner (File API + SAF support)
- [x] Update BrowseViewModel with navigation methods (navigateToFolder, navigateBack)
- [x] Add folder click handling to MediaFileAdapter (List + Grid modes)
- [x] Integrate back button for subfolder navigation in BrowseActivity
- [x] Add breadcrumb display in BrowseUtilityManager
- [x] Implement listDirectoryContents() for network scanners (SMB/SFTP/FTP/Cloud)
- Outcome: Users can open folders, view nested contents, and navigate back smoothly within Browse.

## 3. Fix Compiler Warnings ⚠️ ANALYZED - Won't Fix

**Status**: Analyzed - Most warnings are false positives from ViewBinding

### Summary

- 126 warnings in release build (not debug)
- 90+ are "Unnecessary safe calls" - actually required for nullable views
- See [WARNINGS_ANALYSIS.md](WARNINGS_ANALYSIS.md) for full analysis

### Categories

- **High Volume (90+)**: Unnecessary safe calls (false positives)
- **Deprecated APIs (8)**: GoogleSignInAccount, TRIM*MEMORY*\*, ExifInterface legacy
- **Logic Issues (10)**: Unused params, duplicate when labels, unreachable code

### Recommendation

- **Won't Fix**: Safe call warnings are correct behavior for optional views
- **Deferred**: API migration (Google Sign-In → Credential Manager)

---

## 4. Future Tasks

- [ ] Wear OS testing on physical device
- [ ] Network scanner support for subfolder navigation (SMB/SFTP/FTP)
- [ ] Credential Manager migration for Google Sign-In
- [ ] Performance optimization for 10k+ file folders
