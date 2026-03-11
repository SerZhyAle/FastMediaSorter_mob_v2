# Favorites Export/Import Feature Specification (III.3)

**Date**: March 11, 2026  
**Status**: Design Phase  
**Priority**: High  
**Complexity**: Medium  
**Estimated Effort**: 4–8 hours  
**Target Module**: `app_v2/src/main/java/com/sza/fastmediasorter/`

---

## 1. OVERVIEW

This specification defines the feature for exporting and importing user favorites across devices. Currently, favorites are stored exclusively in local Room database and are lost when switching devices or reinstalling the application.

### Goals
1. Enable users to export favorites to a portable format (JSON)
2. Enable users to import favorites from a previously exported file
3. Provide clear, detailed feedback on import operations (counts, conflicts, errors)
4. Establish a foundation for future cloud-based sync

### Out of Scope (for this iteration)
- Automatic cloud synchronization
- Delta sync / incremental updates
- Favorites versioning / timestamps
- Conflict resolution strategies (beyond skip/overwrite)

---

## 2. USER SCENARIOS

### Scenario 2.1: Export Favorites from Device A
**Actor**: User  
**Device**: Device A (phone with configured favorites)  
**Goal**: Backup favorites before reinstalling app

**Steps**:
1. User opens `Settings` → `Backup & Restore`
2. Taps `Export Favorites`
3. System shows dialog: "Choose export location"
4. User selects destination (Downloads folder)
5. System generates file: `favorites_export_2026-03-11_143022.json`
6. Dialog shows: "Exported 47 favorites to Downloads/favorites_export_2026-03-11_143022.json"
7. User can optionally share the file via email/WhatsApp/etc

**Expected Outcome**:
- JSON file created with all favorite media items
- File contains: resource IDs, file paths, media types, metadata
- User receives clear confirmation with file location and count

---

### Scenario 2.2: Import Favorites to Device B
**Actor**: User  
**Device**: Device B (clean install or different device)  
**Goal**: Restore favorites from exported file

**Steps**:
1. User opens `Settings` → `Backup & Restore`
2. Taps `Import Favorites`
3. File picker opens (default to Downloads)
4. User selects `favorites_export_2026-03-11_143022.json`
5. System validates file format
6. If valid:
   - Shows preview: "Import will add 47 favorites"
   - Option to choose conflict handling: "Skip duplicates" or "Overwrite existing"
7. User taps `Import`
8. System processes import with progress indicator
9. On completion, shows report:
   - ✓ Added: 45 favorites
   - ⚠ Skipped: 2 duplicates (already in favorites)
   - ✗ Failed: 0 (broken resource references)
10. User taps `Done` and navigates to Browse to verify

**Expected Outcome**:
- Favorites merged into local Room DB
- Duplicates handled per user choice
- Clear itemized report of what happened
- No data loss on import failure

---

### Scenario 2.3: Multi-Device Sync (Manual)
**Actor**: Power User  
**Devices**: Devices A, B, C  
**Goal**: Keep favorites synchronized across personal devices

**Steps**:
1. User configures favorites on Device A
2. User exports: `fav_A.json`
3. User shares file to Device B (email/cloud storage)
4. On Device B: Import `fav_A.json` → "Added: 30, Skipped: 5"
5. On Device B: Continue adding new favorites locally
6. User exports from Device B: `fav_B.json`
7. On Device C: Import `fav_B.json` → "Added: 35"
8. Repeats as needed for Device A ↔ C sync

**Expected Outcome**:
- User can manually orchestrate multi-device sync
- Conflict handling prevents duplicates
- Foundation for future automated sync

---

## 3. TECHNICAL ARCHITECTURE

### 3.1 Data Model: Favorite Entity (Current)

**Room Entity** (`FavoriteEntity.kt`):
```
data class FavoriteEntity(
    @PrimaryKey val id: Long,
    val resourceId: Long,              // FK to ResourceEntity
    val filePath: String,               // Full path to media file
    val fileName: String,               // Extracted from filePath
    val mediaType: MediaType,           // IMAGE, VIDEO, AUDIO, DOCUMENT
    val addedAt: Long                   // Timestamp (ms)
)
```

### 3.2 Data Model: Favorites Export Format

**Export File Structure** (`favorites_export_*.json`):
```json
{
  "version": "1.0",
  "exportDate": "2026-03-11T14:30:22Z",
  "appVersion": "2.9.5",
  "deviceName": "OnePlus 9 Pro",
  "totalCount": 47,
  "favorites": [
    {
      "resourceId": 123,
      "resourceName": "mark common",
      "resourcePath": "smb://192.168.1.100/common",
      "relativeFilePath": "photos/2025/vacation.jpg",
      "fileName": "vacation.jpg",
      "mediaType": "IMAGE",
      "addedAt": 1678540200000,
      "checksum": "abc123def456"
    },
    {
      "resourceId": 124,
      "resourceName": "Downloads",
      "resourcePath": "/storage/emulated/0/Download",
      "relativeFilePath": "Documents/report.pdf",
      "fileName": "report.pdf",
      "mediaType": "DOCUMENT",
      "addedAt": 1678540400000,
      "checksum": "ghi789jkl012"
    }
  ]
}
```

**Field Descriptions**:
- `version`: Format version for backward/forward compatibility
- `exportDate`: RFC 3339 timestamp for reference
- `appVersion`: App version that exported; helps detect compatibility issues
- `deviceName`: Human-readable device name for context
- `totalCount`: Quick count without parsing array
- `resourceId`: Original resource ID on export device
- `resourcePath`: Full resource path (enables path-based matching on import)
- `relativeFilePath`: Path relative to resource root (portable across devices)
- `checksum`: Optional SHA-256 hash for validation; can be used in future versioning
- `mediaType`: Enum value (serialized to string: "IMAGE", "VIDEO", etc.)

---

### 3.3 Import Mapping Strategy

**Challenge**: On import, resource IDs may differ from export device.

**Solution**: Multi-step matching algorithm:

1. **Exact Resource Match** (preferred):
   - Match by resource path + relative file path
   - If found: use new resource ID
   - If not found: mark as "unresolved resource"

2. **Fallback: File System Search** (optional, config flag):
   - If resource not found, search all local resources for matching file
   - Match by fileName + mediaType + fileSize (if available)
   - User decision: "Use this resource?" or "Skip"

3. **Skip Resolution** (user choice):
   - Option to skip favorites with unresolved resources
   - These are reported in conflict report

**Import Report Structure**:
```
Import Report
─────────────────────────────────────────
File: favorites_export_2026-03-11_143022.json
Import Date: 2026-03-11 14:35:00

Results:
  ✓ Added: 32 favorites
  ⚠ Skipped: 8 duplicates (already favorite)
  ⚠ Unresolved: 5 (resources not found on this device)
  ✗ Failed: 0 (other errors)

Details:
  - Skipped (duplicates):
    > filePath: /storage/emulated/0/Download/photo_1.jpg
    > filePath: smb://192.168.1.100/common/video.mp4
    (... more)

  - Unresolved Resources:
    > filename: vacation_2025.jpg (from "mark common" SMB)
    > filename: archive.zip (from "NAS"/sftp)
    (... more)
    → Action: Skipped (no matching resource on this device)

Summary:
  Total processed: 45 / 47 (95.7%)
  Action: 32 added, 13 ignored
```

---

## 4. FEATURE REQUIREMENTS

### 4.1 Functional Requirements

#### FR1: Export Favorites
- **FR1.1**: User must be able to access "Export Favorites" from Settings → Backup & Restore
- **FR1.2**: Export creates a JSON file with current timestamp in filename
- **FR1.3**: Default export location is `Downloads/` folder
- **FR1.4**: User can choose alternate location via file picker
- **FR1.5**: Export includes all fields from FavoriteEntity + resource metadata
- **FR1.6**: Export includes header metadata (app version, device name, export date)
- **FR1.7**: Completion dialog shows:
  - File path
  - Number of favorites exported
  - Option to share file (via Share intent)
- **FR1.8**: Export must complete in < 2 seconds for < 1000 favorites

#### FR2: Import Favorites
- **FR2.1**: User must be able to access "Import Favorites" from Settings → Backup & Restore
- **FR2.2**: File picker defaults to Downloads; allows navigation to any location
- **FR2.3**: System validates JSON format before import
- **FR2.4**: On successful validation, show preview:
  - Source device name (from export file)
  - Count of favorites to be imported
  - Conflict handling options
- **FR2.5**: Conflict handling options:
  - "Skip duplicates" (default): Do not overwrite existing favorites
  - "Overwrite existing": Replace if file path matches
- **FR2.6**: Import process shows progress indicator (% complete)
- **FR2.7**: After import, display detailed report (see 3.3)
- **FR2.8**: User can review report and choose to:
  - Save report to file (optional JSON)
  - Return to Favorites view
  - Delete imported file (optional)
- **FR2.9**: On import error (invalid JSON, DB error, etc.), show error dialog with:
  - Error message (user-friendly, not stack trace)
  - Option to view technical log
  - Rollback option (if partial import occurred)

#### FR3: Data Validation
- **FR3.1**: Export file name pattern: `favorites_export_YYYY-MM-DD_HHmmss.json`
- **FR3.2**: JSON schema validation using JsonSchema or manual parsing
- **FR3.3**: Reject files > 10 MB (sanity check)
- **FR3.4**: Reject files with version mismatch (major version only; minor backward compatible)
- **FR3.5**: Validate checksum if present (SHA-256)

#### FR4: Error Handling
- **FR4.1**: Graceful handling of:
  - Missing file
  - Corrupted JSON
  - Insufficient storage space
  - DB transaction failures
  - Network errors (if resources are remote)
- **FR4.2**: All errors logged to Timber with DEBUG level
- **FR4.3**: User-facing error messages are localized (RU, UK, EN)

### 4.2 Non-Functional Requirements

#### NFR1: Performance
- Export < 2 seconds for 1000 favorites
- Import < 5 seconds for 1000 favorites (including DB inserts)
- File size < 1 MB for 1000 favorites (average 1 KB per favorite)
- No ANR (Application Not Responding) warnings

#### NFR2: Reliability
- Export: 100% failure rate < 0.1% (logging + error recovery)
- Import: Atomic transaction (all or nothing, or partial with rollback option)
- No data corruption on interrupted import

#### NFR3: Security
- Files stored unencrypted in Downloads (user's decision)
- JSON export does NOT include Resource credentials (usernames/passwords)
- Checksum validation deters tampering
- File permissions follow system defaults (user can restrict via file manager)

#### NFR4: Localization
- UI strings in RU, UK, EN
- Date format: ISO 8601 (2026-03-11)
- Time format: 24-hour HH:mm:ss
- Error messages localized

#### NFR5: Testability
- Export logic unit-tested
- Import mapping logic unit-tested with mock data
- E2E test: export → import → verify count

---

## 5. IMPLEMENTATION ROADMAP

### Phase 1: Core Infrastructure (1–2 hours)
1. Create `FavoritesExporter.kt` (export logic, JSON generation)
2. Create `FavoritesImporter.kt` (import logic, mapping, conflict resolution)
3. Create `FavoritesExportModel.kt` (data classes for JSON serialization)
4. Add utility functions for file I/O, JSON parsing

### Phase 2: UI Layer (1–2 hours)
1. Add "Export Favorites" button to `GeneralSettingsFragment` or dedicated `BackupFragment`
2. Add "Import Favorites" button
3. Create `ExportFavoritesDialog.kt` (file picker, progress, confirmation)
4. Create `ImportFavoritesDialog.kt` (file picker, conflict options, preview)
5. Create `ImportReportFragment.kt` (detailed report display)

### Phase 3: ViewModel & Use Cases (0.5–1 hour)
1. Create `FavoritesExportUseCase.kt`
2. Create `FavoritesImportUseCase.kt`
3. Update `SettingsViewModel` to coordinate export/import operations
4. Add LiveData for operation status (idle, loading, success, error)

### Phase 4: Testing & Edge Cases (1–2 hours)
1. Unit tests for exporter (mock DB, file system)
2. Unit tests for importer (conflict resolution, mapping)
3. E2E test: export local favorites, import on same device, verify
4. Test with large datasets (1000+ favorites)
5. Test with invalid/corrupted JSON files
6. Test with missing resources

### Phase 5: Documentation & Localization (0.5–1 hour)
1. Add strings to `strings.xml`, `strings-ru.xml`, `strings-uk.xml`
2. Document user-facing feature in FAQ
3. Add help text to dialogs

---

## 6. DETAILED COMPONENTS

### 6.1 FavoritesExporter.kt

**Responsibilities**:
- Query all favorites from Room DB
- Fetch resource metadata (name, path) for each favorite
- Serialize to JSON with metadata
- Write JSON to file
- Return file path + stats

**Input**:
- `Context` (for file system access)
- `FavoritesDao` (Room DAO)
- `ResourceDao` (Room DAO)

**Output**:
```
data class ExportResult(
    val filePath: String,
    val totalExported: Int,
    val fileSize: Long,
    val exportDate: LocalDateTime,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
```

**Key Methods**:
- `suspend fun exportFavorites(): ExportResult`
- `private fun buildJsonStructure(favorites: List<FavoriteEntity>, resources: Map<Long, ResourceEntity>): String`
- `private fun getExportFilePath(): File`
- `private fun calculateChecksum(content: String): String`

---

### 6.2 FavoritesImporter.kt

**Responsibilities**:
- Parse and validate JSON file
- Map exported resource IDs to local resource IDs
- Detect duplicates
- Insert new favorites into Room DB
- Report detailed results

**Input**:
- `Context` (for file system access)
- `File` (source JSON file)
- `ConflictResolutionStrategy` (SKIP or OVERWRITE)
- `FavoritesDao`, `ResourceDao` (Room DAOs)

**Output**:
```
data class ImportResult(
    val imported: Int,           // Successfully added
    val skipped: Int,            // Duplicates or unresolved
    val failed: Int,             // Failed due to error
    val unresolved: Int,         // Resources not found
    val importDate: LocalDateTime,
    val report: ImportReportData, // For display
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

data class ImportReportData(
    val summary: String,
    val details: List<ImportDetail>
)

data class ImportDetail(
    val status: String,          // "ADDED", "SKIPPED", "UNRESOLVED", "FAILED"
    val fileName: String,
    val resourceName: String,
    val reason: String? = null   // Why skipped/failed
)
```

**Key Methods**:
- `suspend fun importFavorites(file: File, strategy: ConflictResolutionStrategy): ImportResult`
- `private fun parseJsonFile(file: File): FavoritesExportModel`
- `private fun validateJson(model: FavoritesExportModel): ValidationResult`
- `private fun matchResourcesLocal(exported: List<ExportedFavorite>): Map<ExportedFavorite, Long?>`
- `private fun insertFavoritesIntoDb(mappedFavorites: List<FavoriteEntity>): InsertResult`
- `private fun generateReport(result: ImportResult): ImportReportData`

---

### 6.3 UI: ExportFavoritesDialog.kt (Jetpack Compose or Dialog Fragment)

**Layout**:
```
┌──────────────────────────────────────┐
│ Export Favorites                     │
├──────────────────────────────────────┤
│                                      │
│ Exporting 47 favorites...            │
│ [████████████░░░░░░] 60%             │
│                                      │
│ Estimated time: 1s                   │
│                                      │
├──────────────────────────────────────┤
│              [Cancel]                │
└──────────────────────────────────────┘

[If successful]
┌──────────────────────────────────────┐
│ Export Successful                    │
├──────────────────────────────────────┤
│                                      │
│ ✓ Exported 47 favorites              │
│                                      │
│ Location:                            │
│ /storage/emulated/0/Download/        │
│ favorites_export_2026-03-11_143022.json
│                                      │
│ File size: 47 KB                     │
│                                      │
├──────────────────────────────────────┤
│  [Share]  [Close]  [View File]       │
└──────────────────────────────────────┘
```

**Functionality**:
- Show progress bar during export
- Display estimated completion time
- On success: show file path, size, export count
- Options: Share file (Intent.ACTION_SEND), Close, Open file manager
- Error handling: show error message with retry option

---

### 6.4 UI: ImportFavoritesDialog.kt

**Step 1: File Selection**
```
┌──────────────────────────────────────┐
│ Import Favorites                     │
├──────────────────────────────────────┤
│ Select JSON file to import:           │
│                                      │
│ [Browse Files...]                    │
│                                      │
│ Recent files:                        │
│ • favorites_export_2026-03-10_...   │
│ • favorites_export_2026-03-09_...   │
│                                      │
├──────────────────────────────────────┤
│              [Cancel]                │
└──────────────────────────────────────┘
```

**Step 2: Preview & Conflict Options**
```
┌──────────────────────────────────────┐
│ Import Preview                       │
├──────────────────────────────────────┤
│ Source: OnePlus 9 Pro                │
│ Exported: 2026-03-11 14:30:00        │
│ Favorites in file: 47                │
│                                      │
│ After import, you will have:         │
│ • Already have: 5                    │
│ • Will add: 42                       │
│ • Total: 52 favorites                │
│                                      │
│ Handle duplicates:                   │
│ ◉ Skip existing (safer)              │
│ ○ Overwrite existing                 │
│                                      │
├──────────────────────────────────────┤
│         [Cancel]  [Import]           │
└──────────────────────────────────────┘
```

**Step 3: Progress**
```
┌──────────────────────────────────────┐
│ Importing...                         │
├──────────────────────────────────────┤
│ Processing 47 favorites              │
│ [████████░░░░░░░░░░] 40%             │
│ Processed: 19 / 47                   │
│                                      │
├──────────────────────────────────────┤
│              [Cancel]                │
└──────────────────────────────────────┘
```

**Step 4: Results Report** (see 3.3 for detailed format)

---

### 6.5 SettingsViewModel Additions

**New LiveData/State**:
```
val exportState: LiveData<ExportUiState>  // IDLE, LOADING, SUCCESS, ERROR
val importState: LiveData<ImportUiState>  // IDLE, LOADING, SUCCESS, ERROR
val exportProgress: LiveData<Int>         // 0–100
val importProgress: LiveData<Int>         // 0–100
val importReport: LiveData<ImportReportData>
```

**New Methods**:
```
fun exportFavorites(destinationDir: File?)
fun importFavorites(sourceFile: File, strategy: ConflictResolutionStrategy)
fun cancelExport()
fun cancelImport()
```

---

## 7. DATA FLOW DIAGRAM

### Export Flow
```
User taps "Export Favorites"
    ↓
ExportFavoritesDialog opened
    ↓
User confirms location (optional file picker)
    ↓
SettingsViewModel.exportFavorites(context, location)
    ↓
FavoritesExporter.exportFavorites()
    ├→ Query FavoritesDao.getAll()
    ├→ Query ResourceDao for each favorite
    ├→ Build JSON structure
    ├→ Calculate checksum
    └→ Write to file: favorites_export_[timestamp].json
    ↓
ExportResult returned
    ↓
UI shows success dialog with file path + share option
```

### Import Flow
```
User taps "Import Favorites"
    ↓
File picker opened (defaults to Downloads)
    ↓
User selects *.json file
    ↓
ImportFavoritesDialog shows preview:
  - File metadata (source device, date)
  - Count to be imported
  - Conflict resolution options
    ↓
User selects conflict strategy (SKIP / OVERWRITE)
    ↓
User taps "Import"
    ↓
SettingsViewModel.importFavorites(file, strategy)
    ↓
FavoritesImporter.importFavorites()
    ├→ Parse JSON, validate format
    ├→ Match exported resources to local resources
    │  ├→ Try: exact path match
    │  ├→ Fallback: file search (if enabled)
    │  └→ Result: Map<ExportedFavorite, LocalResourceId?>
    ├→ Detect duplicates (check if favorite already exists)
    ├→ Apply conflict strategy
    ├→ Insert new favorites in Room DB (atomic transaction)
    ├→ Generate report
    └→ ImportResult returned
    ↓
UI shows ImportReportFragment with:
  - Summary (added / skipped / failed counts)
  - Details (why each was skipped/failed)
    ↓
User reviews, option to save report or delete source file
```

---

## 8. TESTING STRATEGY

### Unit Tests

**ExporterTest.kt**:
- Export with 0 favorites → empty array
- Export with 10 favorites → valid JSON generated
- File created in correct location
- Checksum matches content
- Export with remote resources (SMB, SFTP) → paths included

**ImporterTest.kt**:
- Parse valid JSON → ImportModel created
- Parse invalid JSON → error returned
- Map resources (exact match) → correct local ID assigned
- Map resources (missing resource) → null assigned
- Detect duplicates → skipped
- Insert with SKIP strategy → no duplicates
- Insert with OVERWRITE strategy → updated
- Large import (1000 items) → complete < 5s

**ValidationTest.kt**:
- Accept valid JSON with version 1.0
- Reject invalid JSON (missing fields)
- Reject file > 10 MB
- Checksum validation passes/fails correctly

---

### E2E Tests (Maestro or Manual)

**E2E_ExportImportRoundTrip**:
1. Add 5 favorites to local Favorites (Browse → Select → Heart)
2. Settings → Backup → Export Favorites
3. Confirm file created in Downloads
4. Manually delete from Favorites (clear DB)
5. Settings → Backup → Import Favorites
6. Select exported file
7. Confirm: "Import 5 favorites"
8. Verify all 5 re-imported
9. Check Browse → all marked as favorites

---

## 9. LOCALIZATION STRINGS

**English** (`strings.xml`):
```xml
<string name="backup_export_favorites">Export Favorites</string>
<string name="backup_import_favorites">Import Favorites</string>
<string name="export_progress">Exporting %1$d favorites…</string>
<string name="export_success">Exported %1$d favorites to %2$s</string>
<string name="import_preview_title">Import Favorites</string>
<string name="import_preview_source">Source Device: %1$s</string>
<string name="import_preview_count">Favorites to import: %1$d</string>
<string name="import_conflict_skip">Skip existing (safer)</string>
<string name="import_conflict_overwrite">Overwrite existing</string>
<string name="import_progress">Importing favorites…</string>
<string name="import_success_title">Import Complete</string>
<string name="import_result_added">Added: %1$d</string>
<string name="import_result_skipped">Skipped: %1$d (duplicates)</string>
<string name="import_result_failed">Failed: %1$d</string>
```

**Russian** (`strings-ru.xml`):
```xml
<string name="backup_export_favorites">Экспортировать избранное</string>
<string name="backup_import_favorites">Импортировать избранное</string>
<string name="export_progress">Экспорт %1$d избранных элементов…</string>
<string name="export_success">Экспортировано %1$d избранных в %2$s</string>
<string name="import_preview_title">Импорт избранного</string>
<string name="import_preview_source">Исходное устройство: %1$s</string>
<string name="import_preview_count">Избранное для импорта: %1$d</string>
<string name="import_conflict_skip">Пропустить существующие (безопаснее)</string>
<string name="import_conflict_overwrite">Перезаписать существующие</string>
<string name="import_progress">Импорт избранного…</string>
<string name="import_success_title">Импорт завершен</string>
<string name="import_result_added">Добавлено: %1$d</string>
<string name="import_result_skipped">Пропущено: %1$d (дубликаты)</string>
<string name="import_result_failed">Ошибок: %1$d</string>
```

---

## 10. ERROR SCENARIOS & HANDLING

| Scenario | Root Cause | User Message | Recovery |
|----------|-----------|--------------|----------|
| Export fails — no disk space | Insufficient storage | "Not enough storage. Free up space and try again." | Suggest clearing cache |
| Export — DB query timeout | Device very slow or DB locked | "Export taking longer than expected. Try again later." | Offer retry |
| Import — file not found | User deleted file / wrong path | "Selected file not found. Choose again." | Reopen file picker |
| Import — corrupted JSON | File edited or corrupted | "File is corrupted or invalid. Check and try again." | Show first 200 chars of file |
| Import — resource not found | Resource deleted on import device | 2 unresolved favorites (see report). Add resources manually or skip. | Show report detailing unresolved |
| Import — DB insert fails | Room transaction error | "Import failed. Check storage and try again." | Show partial report (what was added before failure) |
| Permission denied | File system permissions | "Permission denied. Check file permissions." | Guide user to check Settings |

---

## 11. FUTURE ENHANCEMENTS (Out of Scope)

1. **Cloud Sync**: Automatic sync via Google Drive / Dropbox
2. **Incremental Updates**: Only export/import changes since last sync
3. **Conflict Resolution UI**: User selects which version to keep (timestamp-based)
4. **Scheduled Backups**: Auto-export daily/weekly
5. **Compression**: Gzip exported files for smaller size
6. **Encryption**: Encrypt JSON with optional password
7. **Multi-Device Dashboard**: View/manage favorites across all devices
8. **Delta Sync**: Upload only additions/changes

---

## 12. ACCEPTANCE CRITERIA

- [ ] Export creates valid JSON with all 47 test favorites
- [ ] Import reads JSON and adds 45 new favorites (2 duplicates skipped)
- [ ] Export completes in < 2 seconds
- [ ] Import completes in < 5 seconds
- [ ] Report shows: "Added: 45, Skipped: 2, Unresolved: 0"
- [ ] Invalid JSON file rejected with user-friendly error
- [ ] Duplicates handled per user choice (skip vs overwrite)
- [ ] UI strings localized (EN, RU, UK)
- [ ] No ANR warnings during import
- [ ] E2E test passes: export → delete locally → import → verify

---

## 13. REFERENCES & DEPENDENCIES

- **Room**: Existing `FavoritesDao`, `ResourceDao`
- **Timber**: Logging
- **Kotlin Coroutines**: Async operations
- **Jetpack Compose** or **Material3 Dialogs**: UI (depends on project choice)
- **Gson** or **kotlinx.serialization**: JSON parsing (check existing dependencies in `gradle/libs.versions.toml`)

---

**Document Status**: READY FOR DEVELOPMENT  
**Last Updated**: March 11, 2026

