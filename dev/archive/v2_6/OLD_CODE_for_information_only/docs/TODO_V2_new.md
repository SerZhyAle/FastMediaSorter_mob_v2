# Project Roadmap: Step-by-Step Implementation Plan

This document outlines the detailed plan to stabilize the application and implement new features in small, safe increments.
Each step is designed to be verifiable independently.

## 🛑 Phase 1: Critical Stability & Compliance (Immediate Priority)
**Goal:** Fix crashes and Play Store policy violations. Ensure the app builds and runs safely.

### Step 1.1: Fix SMB/Network Stability
- [x] **Action:** Open `app_v2/build.gradle.kts` (Lines 144-145).
- [x] **Detail:** Remove the Lines:
      ```kotlin
      excludes += "org/bouncycastle/pqc/**"
      excludes += "**/lowmcL5.bin.properties"
      ```
- [x] **Why:** These exclusions cause `NoClassDefFoundError` on some SMB connections.
- [ ] **Verification:** Build app, connect to SMB share.
- [ ] **COMMIT:** "Fix: Remove Bouncy Castle PQC exclusions for SMB stability"

### Step 1.2: Remove Restricted Permissions (Play Store Policy)
- [x] **Action:** Open `app_v2/src/main/AndroidManifest.xml`.
  - Delete Line 12: `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" ... />`
  - Edit Line 33: Remove `android:requestLegacyExternalStorage="true"`.
- [x] **Action:** Open `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`.
  - **Refactor** `hasStoragePermission`:
    - Remove `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` block checking `Environment.isExternalStorageManager()`.
    - Change to check `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` for Android 13+.
  - **Refactor** `requestStoragePermission`:
    - Remove `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` intent.
    - Request granular permissions for Android 13+.
- [x] **Why:** Mandatory for Play Store approval.
- [ ] **Verification:** App installs without requesting "All Files Access".
- [ ] **COMMIT:** "Refactor: Remove MANAGE_EXTERNAL_STORAGE permission"

### Step 1.3: Fix Local File Scanning (MediaStore Migration)
*Breaking this down as it's the most complex task.*
- [x] **Step 1.3a (Data Source):** Create new class `MediaStoreRepository` in `data/repository`.
  - **Method:** `scanDirectory(path: String, allowedTypes: Set<MediaType>): List<MediaFile>`
  - **Implementation:** Use `context.contentResolver.query(MediaStore.Files.getContentUri("external"), ...)`
  - **Selection:** `MediaStore.MediaColumns.DATA + " LIKE ?"` (for path matching). 
- [x] **Step 1.3b (UseCase):** Refactor `ScanLocalFoldersUseCase.kt`.
  - **Inject:** `MediaStoreRepository`.
  - **Replace:** `File.listFiles()` loops with calls to `MediaStoreRepository.scanDirectory()`.
  - **Logic:** Instead of recursive `File` walking, query MediaStore for all files under the root path using `LIKE 'path/%'`.
- [x] **Step 1.3d (Standard Folders):** Add `getStandardFolders()` to `MediaStoreRepository`.
  - **Method:** Returns list of standard Android folders (Downloads, Camera, Pictures, Music, Movies).
  - **Implementation:** Uses `Environment.getExternalStoragePublicDirectory()` for each standard directory.
  - **Behavior:** Returns folders ALWAYS, even if empty. Integrated in `ScanLocalFoldersUseCase` with `getFoldersWithMedia()`.
- [x] **Step 1.3c (SAF Writing):** Verify `LocalTransferProvider.kt` uses `DocumentFile` for writes (copy/move/delete).  
- [x] **Why:** Direct `File` access to public folders (DCIM, etc.) is blocked on Android 11+ without `MANAGE_EXTERNAL_STORAGE`.
- [ ] **Verification:** "Scan" button correctly finds files in Camera/Downloads on Android 14 device.
- [ ] **COMMIT:** "Feat: Migrate local scanning to MediaStore and transfer to SAF"

## 🛠️ Phase 2: Build Fixes & Essential Debugging
**Goal:** Ensure the codebase is clean and compilation is error-free.

### Step 2.1: Enforce "When" Exhaustiveness
- [ ] **Action:** Open `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`.
- [ ] **Detail:** In `loadThumbnail` (Line 470) and other `when(file.type)` blocks:
  - Add branch: `MediaType.FOLDER -> { imageView.setImageResource(R.drawable.ic_folder); applyPlaceholderStyle(...) }`
- [ ] **Why:** Kotlin compiler errors were reported previously regarding this.
- [ ] **Verification:** Clean Build succeeds.

### Step 2.2: Fix Lyrics Search Timeouts
- [ ] **Action:** Open `SearchLyricsUseCase.kt`.
- [ ] **Detail:** In `searchLyricsOvhApi` (Line 530), wrap the `httpClient.newCall...` in `withTimeout(5000L) { ... }`.
- [ ] **Action:** Verify `fixEncoding` (Line 162) is actually called. (It is called in `extractMetadata`).
- [ ] **Verification:** Search for a Russian song, ensure text is readable and app doesn't freeze.

## 🚀 Phase 3: New Features (Incremental Implementation)
**Goal:** Add requested features one by one.

### Feature A: "Show Folders" in Browse
- [ ] **Step 3.1 (Settings UI):** Add `SwitchPreference` "Show Folders" key `pref_show_folders` in `root_preferences.xml`.
- [ ] **Step 3.2 (Data):** Update `ScanLocalFoldersUseCase` to return `MediaType.FOLDER` items derived from unique relative paths in MediaStore.
- [ ] **Step 3.3 (UI Adapter):** In `MediaFileAdapter.kt`, update `bind` to handle `MediaType.FOLDER`.
  - Icon: Folder icon.
  - Text: Folder name.
  - Click: Trigger `onFolderClick` callback.
- [ ] **Step 3.4 (Navigation):** Update `BrowseFragment` to handle `onFolderClick` -> Navigate to same fragment with new `path` argument.

### Feature B: Read-Only Resources
*Mark specific resources as non-editable.*
- [ ] **Step 3.5 (DB Schema):**
  - *Note:* `ResourceEntity` already has `isReadOnly` (Line 47).
  - **Action:** Check `AppDatabase.kt`. It has `version = 1` and `fallbackToDestructiveMigration()`.
  - **Decision:** Since `isReadOnly` exists in Entity but maybe not in current user DB, we might need to bump version to 2 and provide migration if we want to preserve data. OR, since `fallbackToDestructiveMigration` is on, we accept data loss on update? **Wait, user expects preservation.**
  - **Correction:** We must implement `MIGRATION_1_2` (or relevant version) in `DatabaseModule.kt` to safely add the column if missing.
- [ ] **Step 3.6 (Logic):**
  - `ResourceEntity` has the field. Ensure `ResourceMapper` maps it.
- [ ] **Step 3.7 (UI - Settings):** Add "Read Only" checkbox to `AddResourceActivity` / `EditResourceActivity`.
- [ ] **Step 3.8 (Enforcement):**
  - In `MediaFileAdapter.bind`: `if (isReadOnly) { btnDelete.isVisible = false; ... }`

### Feature C: PDF Editing Entry Point
- [ ] **Step 3.9 (Layout):** Edit `activity_player.xml`. Add `ImageButton` `@+id/btn_edit_pdf` inside the top command bar layout.
- [ ] **Step 3.10 (Logic):** In `PlayerActivity.kt`:
  - `binding.btnEditPdf.isVisible = (currentFile.type == MediaType.PDF)`
  - `binding.btnEditPdf.setOnClickListener { showPdfToolsDialog() }`

### Feature D: Update Welcome Screens
- [ ] **Step 3.11 (Content):** Update welcome screen pages to include information about document format support.
  - Add section about supported document types (PDF, TXT, etc.)
  - Mention document viewing and editing capabilities
  - Update relevant string resources for all localizations (EN, RU, UK)

## 🧹 Phase 4: Clean Up
- [ ] **Step 4.1:** Verify all Russian/Ukrainian translations for pending strings.
- [ ] **Step 4.2:** Remove unused imports in `MediaFileAdapter` and others.