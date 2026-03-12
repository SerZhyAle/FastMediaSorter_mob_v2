# Network Delete Confirmation & Trash Implementation Specification (III.2)

**Date**: March 11, 2026  
**Status**: Design Phase  
**Priority**: Medium  
**Complexity**: Medium  
**Estimated Effort**: 2–4 hours (Phase 1: confirmation dialog only)  
**Target Module**: `app_v2/src/main/java/com/sza/fastmediasorter/`

---

## 1. OVERVIEW

This specification defines the enhanced confirmation flow for deleting files from network and cloud resources (SMB, SFTP, FTP, Azure Files, Google Drive, OneDrive, Dropbox, etc.). Currently, the app shows the same confirmation dialog for all resource types, but network deletes are **permanent** and cannot be undone.

### Goals (MVP — Phase 1)
1. Distinguish network/cloud resource deletion UX from local deletion
2. Display prominent warning: "Undo is NOT available for network/cloud resources"
3. Require explicit confirmation before network deletion
4. Provide clear feedback on what will happen (permanent deletion)

### Goals (Extended — Phase 2)
5. Implement network trash feature (move to `/.trash` on server instead of permanent delete)
6. Allow users to recover files from network trash within configurable TTL

### Out of Scope (for Phase 1)
- Automatic trash cleanup on server
- Trash size limits
- Complex conflict resolution for trash recovery
- Multi-file trash management UI

---

## 2. USER SCENARIOS

### Scenario 2.1: Local Resource Delete (Current Behavior — No Change)
**Actor**: User  
**Resource Type**: LOCAL (e.g., `/storage/emulated/0/DCIM`)  
**Goal**: Delete unwanted photo

**Steps**:
1. User opens Browse → selects local folder
2. Long-press photo → "Delete" from context menu
3. Dialog appears: "Delete 'photo.jpg'?"
   - Icon: ⚠
   - Buttons: [Cancel] [Delete]
4. User taps Delete
5. File is deleted, can be recovered via [Undo] button in Snackbar

**Expected Outcome**:
- User can undo within ~30 seconds
- Behavior unchanged from current implementation

---

### Scenario 2.2: Network Resource Delete with Confirmation (Phase 1)
**Actor**: User  
**Resource Type**: SMB (e.g., `smb://192.168.1.100/photos`)  
**Goal**: Delete unwanted file from NAS

**Steps**:
1. User opens Browse → selects SMB resource
2. Long-press file → "Delete" from context menu
3. **ENHANCED** dialog appears: "Delete file from network?"
   - Heading: 🚨 **This action cannot be undone**
   - Content:
     ```
     Delete 'vacation.jpg' from:
     mark common (smb://192.168.1.100/common)
     
     ⚠️ Network and cloud resources do NOT support Undo.
     This file will be permanently deleted.
     
     Are you sure you want to continue?
     ```
   - Buttons: [Cancel] [Delete permanently]
   - Checkbox: "Don't show this again for [resource name]" (optional)
4. User re-reads warning and taps "Delete permanently"
5. File is immediately deleted on network
6. No Undo option offered (Snackbar omitted or shows "Deleted" without undo)

**Expected Outcome**:
- User clearly understands deletion is permanent
- Extra confirmation step reduces accidental deletions
- User can opt-out of warning for trusted resources

---

### Scenario 2.3: Batch Delete from Network (Phase 1)
**Actor**: User  
**Resource Type**: SFTP  
**Goal**: Delete 5 old backup files from SFTP server

**Steps**:
1. User selects 5 files in Browse
2. Taps Delete button (or context menu)
3. Dialog: "Delete 5 files from network?"
   - Same warning as Scenario 2.2
   - Includes file count in message
4. User confirms
5. All 5 deleted on server
6. No undo available

**Expected Outcome**:
- Batch delete shows count in confirmation
- One confirmation dialog for all files (not N dialogs)

---

### Scenario 2.4: Network File to Trash (Phase 2)
**Actor**: User  
**Resource Type**: SMB  
**Goal**: Delete file but preserve recovery option

**Steps**:
1. User long-press file → "Delete" 
2. Dialog offers two actions:
   - ◉ **Move to Trash** (recoverable, 30-day TTL)
   - ○ Delete Permanently (no recovery)
3. User selects "Move to Trash" (default)
4. File moved to `smb://192.168.1.100/common/.trash/` with timestamp
5. Snackbar: "Moved to trash. [Recover] [Permanent]"
6. If user taps Recover → File restored to original location
7. After 30 days → File auto-deleted from trash

**Expected Outcome**:
- User has grace period to recover
- Trash is transparent (user sees `.trash` folder in Browse)
- Clear recovery workflow

---

### Scenario 2.5: Out-of-Space Warning (Phase 2)
**Actor**: User  
**Resource Type**: FTP server (limited storage)  
**Goal**: Delete file, but server is nearly full

**Steps**:
1. User tries to move file to trash
2. Server response: insufficient space for `.trash`
3. Dialog shows two options:
   - "Delete Permanently (no trash available on this resource)"
   - "Cancel"
4. User chooses action

**Expected Outcome**:
- App gracefully handles trash unavailability
- User informed of space constraints

---

## 3. TECHNICAL ARCHITECTURE

### 3.1 Resource Type Classification

**Current RM Resource Types**:
```
enum ResourceType {
    LOCAL,           // Device storage
    SMB,             // Windows/Samba shares
    SFTP,            // SSH File Transfer
    FTP,             // FTP server
    CLOUD            // Generic cloud (abstraction)
}

// Cloud subtypes (part of path/URI)
CLOUD:
  - google_drive   (cloud://google_drive/...)
  - onedrive       (cloud://onedrive/...)
  - dropbox        (cloud://dropbox/...)
  - azure_blob     (cloud://azure_blob/...)
```

**Classification for Delete UX**:
```
isNetworkResource(resourceType: ResourceType): Boolean {
    return resourceType in [SMB, SFTP, FTP, CLOUD]
}

isLocalResource(resourceType: ResourceType): Boolean {
    return resourceType == LOCAL
}
```

### 3.2 Delete Flow Architecture (Phase 1)

**Current Delete Implementation** (simplified):
```
User selects file(s)
    ↓
Taps Delete
    ↓
Standard confirmation dialog shown
    ↓
IF local:
    • Add to Trash folder in app
    • Show Undo in Snackbar
ELSE IF network:
    • Delete immediately on server
    • No undo available
```

**Enhanced Delete Flow** (Phase 1):
```
User selects file(s)
    ↓
Taps Delete
    ↓
Check: isNetworkResource(resource.type)?
    ├→ YES: Show ENHANCED confirmation dialog
    │       ├→ Include warning
    │       ├→ Large red heading: "THIS CANNOT BE UNDONE"
    │       ├→ Show resource name/path
    │       └→ Require explicit "Delete Permanently" button
    │
    └→ NO (LOCAL): Show standard dialog
                   └→ Allow Undo after deletion
    ↓
IF user confirms network delete:
    • Call networkDeleteManager.deleteFiles()
    • Show NO undo button (or gray it out)
    • Log action for audit
    ↓
IF user confirms local delete:
    • Move to local Trash (unchanged)
    • Show Undo button
```

### 3.3 Enhanced Confirmation Dialog Structure (Phase 1)

**Layout** (Material Design 3):
```
┌────────────────────────────────────────────┐
│ 🚨                                         │
│ THIS ACTION CANNOT BE UNDONE               │
├────────────────────────────────────────────┤
│                                            │
│ Delete file from network?                  │
│                                            │
│ Files:                                     │
│  • vacation.jpg                            │
│  • backup_2025.zip                         │
│ (2 files total)                            │
│                                            │
│ Resource: mark common                      │
│ Type: SMB (Network)                        │
│ Path: smb://192.168.1.100/common           │
│                                            │
│ ⚠️ IMPORTANT:                               │
│ Network and cloud resources do NOT support │
│ Undo. Files will be PERMANENTLY deleted.   │
│                                            │
│ If you need to test, consider moving to    │
│ local storage first or enabling network    │
│ trash (if available).                      │
│                                            │
│ ☐ Don't show this warning again for        │
│   [mark common]                            │
│                                            │
├────────────────────────────────────────────┤
│   [Cancel]    [Delete Permanently]         │
└────────────────────────────────────────────┘
```

**Dialog Fields**:
- **Icon**: 🚨 (warning triangle or exclamation in red)
- **Main Heading**: "THIS ACTION CANNOT BE UNDONE" (red/bold)
- **Question**: "Delete X file(s) from network?" (varies by count)
- **File List**: Show file names (max 5, then "+N more")
- **Resource Info**: Resource name, type, path
- **Warning Block**: Bold, caution-colored, explain consequence
- **Checkbox**: "Don't show again" (saves per resource)
- **Buttons**:
  - Primary: "Delete Permanently" (red/destructive)
  - Secondary: "Cancel" (neutral)

---

### 3.4 Delete Manager Components (Phase 1)

#### DeleteOperationManager.kt

**Responsibilities**:
- Determine resource type
- Select appropriate deletion strategy
- Show appropriate confirmation dialog
- Coordinate file deletion
- Generate audit log

**Key Methods**:
```
suspend fun deleteFiles(
    files: List<MediaFileEntity>,
    resource: ResourceEntity
): DeleteResult

fun showDeleteConfirmation(
    context: Context,
    files: List<MediaFileEntity>,
    resource: ResourceEntity,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
)

private fun isNetworkResource(resourceType: ResourceType): Boolean
private fun buildConfirmationDialog(...): AlertDialog
```

**Input**:
- `List<MediaFileEntity>` (files to delete)
- `ResourceEntity` (source resource)

**Output**:
```
data class DeleteResult(
    val successCount: Int,          // Successfully deleted
    val failureCount: Int,          // Failed to delete
    val isNetworkDelete: Boolean,   // Is network resource
    val isUndoAvailable: Boolean,   // Can undo (local only)
    val deletedAt: LocalDateTime,
    val failureMessages: List<String> = emptyList(),
    val isSuccess: Boolean
)
```

#### NetworkDeleteHandler.kt (Phase 1)

**Responsibilities**:
- Perform actual file deletion on network
- Handle network errors gracefully
- Log deletion for audit trail
- Report success/failure per file

**Key Methods**:
```
suspend fun deleteFilesFromNetwork(
    files: List<MediaFileEntity>,
    resource: ResourceEntity
): NetworkDeleteResult

private fun deleteFromSMB(path: String, credentials: ...): Result
private fun deleteFromSFTP(path: String, credentials: ...): Result
private fun deleteFromFTP(path: String, credentials: ...): Result
private fun deleteFromCloud(uri: String, token: String): Result
```

**Error Handling**:
- Network timeout → Show retry dialog
- Permission denied → Show error message
- File not found → Treat as success (already gone)
- Partial success (3 of 5) → Show itemized report

---

### 3.5 UI: Enhanced Confirmation Dialog (Phase 1)

#### NetworkDeleteConfirmationDialog.kt

**Fragment-based Dialog**:
```
// Built as DialogFragment for lifecycle safety
class NetworkDeleteConfirmationDialog : DialogFragment() {
    
    // Arguments
    val files: List<MediaFileEntity>
    val resource: ResourceEntity
    
    // UI Elements
    val headingTextView: TextView          // Red warning text
    val fileListRecycler: RecyclerView     // Show files to delete
    val resourceInfoView: View             // Resource name/path
    val warningBlockView: View             // ⚠️ block
    val dontShowCheckbox: CheckBox         // Opt-out
    val deleteButton: MaterialButton       // Destructive action
    val cancelButton: MaterialButton
    
    // Callbacks
    fun setOnConfirmListener(callback: () -> Unit)
    fun setOnCancelListener(callback: () -> Unit)
}
```

**Compose Alternative** (if project adopts Compose):
```
@Composable
fun NetworkDeleteConfirmationDialog(
    files: List<MediaFileEntity>,
    resource: ResourceEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
        title = { Text("THIS ACTION CANNOT BE UNDONE", color = Color.Red, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Delete ${files.size} file(s) from network?")
                // File list
                // Resource info
                // Warning block
                // Checkbox
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                Text("Delete Permanently")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## 4. FEATURE REQUIREMENTS

### 4.1 Functional Requirements (Phase 1)

#### FR1: Distinguish Network Delete
- **FR1.1**: App must detect resource type (LOCAL vs NETWORK/CLOUD)
- **FR1.2**: For network resources, show enhanced confirmation dialog
- **FR1.3**: For local resources, show standard dialog (unchanged)

#### FR2: Enhanced Confirmation Dialog
- **FR2.1**: Dialog must include prominent red warning: "THIS ACTION CANNOT BE UNDONE"
- **FR2.2**: Dialog must show:
  - Number of files being deleted
  - File names (up to 5, truncate if more)
  - Resource name and type (e.g., "SMB", "SFTP")
  - Resource path
  - Explanation: "Network/cloud resources do NOT support Undo"
- **FR2.3**: Dialog must have two buttons:
  - "Delete Permanently" (destructive, red)
  - "Cancel" (neutral)
- **FR2.4**: "Delete Permanently" button should have **minimum delay** (1 second) before being enabled (UX best practice)
- **FR2.5**: Dialog should have checkbox: "Don't show this warning again for [resource name]"
  - When checked, skip confirmation for future deletes from same resource
  - Preference saved in SharedPreferences per resource ID

#### FR3: Delete Operation Handling
- **FR3.1**: After confirmation, no Undo button should be shown in Snackbar
- **FR3.2**: Snackbar should show: "Deleted X file(s) from [resource name]"
- **FR3.3**: On network error, show error dialog with:
  - Clear error message
  - Retry option (if appropriate)
  - Option to see technical log

#### FR4: Batch Delete
- **FR4.1**: Single confirmation dialog for batch delete (not N dialogs)
- **FR4.2**: Dialog shows total count and file list (truncated if > 5)
- **FR4.3**: Deletion proceeds in background with progress indicator (if > 10 files)

#### FR5: Logging & Audit
- **FR5.1**: Every network delete must be logged to Timber
- **FR5.2**: Log must include:
  - File name(s)
  - Resource name and type
  - User ID (if available)
  - Timestamp
  - Success/failure status
- **FR5.3**: Logs should be accessible via app debug menu (if available)

### 4.2 Non-Functional Requirements (Phase 1)

#### NFR1: Performance
- Dialog must appear within 500ms of user action
- Delete operation must complete in < 1 second per file (local network speed dependent)
- No ANR warnings

#### NFR2: UX
- Warning must be unmistakable (red color, bold text, icon)
- Button labels must be clear and action-oriented
- No jargon (explain in user-friendly terms)

#### NFR3: Localization
- UI strings in RU, UK, EN
- Resource type names translated (e.g., "SMB" vs "Сетевой диск")
- Error messages localized

#### NFR4: Accessibility
- Dialog compatible with screen readers
- Color contrast meets WCAG AA
- Button labels descriptive (not just "Yes/No")

---

## 5. IMPLEMENTATION ROADMAP

### Phase 1: Enhanced Confirmation (2–3 hours)

**Step 1: Resource Type Detection** (30 min)
- Add helper function: `isNetworkResource(resourceType: ResourceType): Boolean`
- Add helper function: `getResourceTypeDisplayName(type: ResourceType): String`

**Step 2: Create Dialog Component** (1 hour)
- Create `NetworkDeleteConfirmationDialog.kt` (or Compose variant)
- Layout: heading, file list, resource info, warning block, checkbox
- Styling: red warning colors, proper spacing

**Step 3: Integrate into Delete Flow** (1 hour)
- Modify `BrowseViewModel` or `DeleteOperationManager`
- Check resource type before showing dialog
- Show enhanced dialog for network, standard for local

**Step 4: Handle "Don't Show Again"** (30 min)
- Save per-resource preferences: `SharedPreferences` key = `delete_no_warn_{resourceId}`
- Skip confirmation if preference set

**Step 5: Testing & Localization** (30 min)
- Test with SMB, SFTP, FTP, LOCAL resources
- Add strings to `strings.xml`, `strings-ru.xml`, `strings-uk.xml`

---

### Phase 2: Network Trash (2–4 hours) — **OUT OF SCOPE FOR MVP**

**Step 1: Trash Manager**
- Create `NetworkTrashManager.kt`
- Implement: `moveToTrash()`, `restoreFromTrash()`, `emptyTrash()`

**Step 2: Trash Folder Convention**
- Standard: `/.trash/` at resource root
- Format: `{original_path}__TIMESTAMP__{deleted_date}.{ext}`

**Step 3: UI: Trash Recovery**
- Show "Move to Trash" / "Delete Permanently" radio buttons in dialog
- After delete, show Snackbar: "[Recover] [Delete Permanently]"
- Implement recovery flow

**Step 4: Trash Auto-Cleanup**
- Background job (daily): scan trash folders, delete files older than 30 days
- User configurable TTL in Settings

**Step 5: Browse Trash**
- Add "View Trash" option in resource menu
- Show trash contents, allow restore or permanent delete

---

## 6. DETAILED COMPONENTS

### 6.1 DeleteOperationManager.kt (Coordinator)

```kotlin
class DeleteOperationManager(
    private val localDeleteHandler: LocalDeleteHandler,
    private val networkDeleteHandler: NetworkDeleteHandler,
    private val filesDao: MediaFileDAO,
    private val resourcesDao: ResourceDAO,
    private val timber: Timber
) {
    
    suspend fun deleteFiles(
        files: List<MediaFileEntity>,
        resource: ResourceEntity,
        showConfirmation: suspend () -> Boolean
    ): DeleteResult {
        
        // Step 1: Show appropriate confirmation
        if (!showConfirmation()) {
            return DeleteResult(
                successCount = 0,
                failureCount = 0,
                isSuccess = false
            )
        }
        
        // Step 2: Route to correct handler
        val result = if (isNetworkResource(resource.type)) {
            networkDeleteHandler.deleteFilesFromNetwork(files, resource)
        } else {
            localDeleteHandler.deleteFilesLocally(files)
        }
        
        // Step 3: Log and return
        logDeleteOperation(files, resource, result)
        return result
    }
    
    fun showDeleteConfirmationDialog(
        context: Context,
        files: List<MediaFileEntity>,
        resource: ResourceEntity,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (isNetworkResource(resource.type)) {
            showNetworkDeleteDialog(context, files, resource, onConfirm, onCancel)
        } else {
            showLocalDeleteDialog(context, files, onConfirm, onCancel)
        }
    }
    
    private fun showNetworkDeleteDialog(
        context: Context,
        files: List<MediaFileEntity>,
        resource: ResourceEntity,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val dialog = NetworkDeleteConfirmationDialog().apply {
            arguments = bundleOf(
                "files" to files,
                "resource" to resource
            )
            setOnConfirmListener(onConfirm)
            setOnCancelListener(onCancel)
        }
        dialog.show(/* fragmentManager */)
    }
    
    private fun isNetworkResource(type: ResourceType): Boolean {
        return type in listOf(
            ResourceType.SMB,
            ResourceType.SFTP,
            ResourceType.FTP,
            ResourceType.CLOUD
        )
    }
    
    private fun logDeleteOperation(
        files: List<MediaFileEntity>,
        resource: ResourceEntity,
        result: DeleteResult
    ) {
        timber.d(
            "Delete operation: resource=${resource.name}, " +
            "type=${resource.type}, files=${files.size}, " +
            "success=${result.successCount}, failed=${result.failureCount}"
        )
    }
}
```

### 6.2 NetworkDeleteConfirmationDialog.kt

```kotlin
class NetworkDeleteConfirmationDialog : DialogFragment() {
    
    private lateinit var files: List<MediaFileEntity>
    private lateinit var resource: ResourceEntity
    private var onConfirm: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    
    fun setOnConfirmListener(callback: () -> Unit) {
        onConfirm = callback
    }
    
    fun setOnCancelListener(callback: () -> Unit) {
        onCancel = callback
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        files = arguments?.getParcelableArray("files")?.toList() as List<MediaFileEntity>
        resource = arguments?.getParcelable("resource") as ResourceEntity
        
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        
        // Create custom view
        val view = LayoutInflater.from(context).inflate(
            R.layout.dialog_network_delete_confirmation,
            null
        )
        
        configureDialogView(view, context)
        
        builder.setView(view)
            .setPositiveButton(R.string.delete_permanently) { _, _ ->
                onConfirm?.invoke()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onCancel?.invoke()
                dismiss()
            }
        
        return builder.create()
    }
    
    private fun configureDialogView(view: View, context: Context) {
        // Heading
        view.findViewById<TextView>(R.id.heading).apply {
            text = context.getString(R.string.delete_action_cannot_be_undone)
            setTextColor(Color.RED)
            typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        
        // Question
        view.findViewById<TextView>(R.id.question).text =
            context.getString(R.string.delete_n_files_from_network, files.size)
        
        // File list
        val fileListView = view.findViewById<RecyclerView>(R.id.fileList)
        fileListView.adapter = FileListAdapter(files.take(5))
        if (files.size > 5) {
            view.findViewById<TextView>(R.id.moreFilesIndicator).text =
                context.getString(R.string.and_n_more_files, files.size - 5)
        }
        
        // Resource info
        view.findViewById<TextView>(R.id.resourceName).text = resource.name
        view.findViewById<TextView>(R.id.resourceType).text = resource.type.displayName
        view.findViewById<TextView>(R.id.resourcePath).text = resource.path
        
        // Warning block
        view.findViewById<TextView>(R.id.warningText).text = 
            context.getString(R.string.network_delete_warning)
        
        // Checkbox
        view.findViewById<CheckBox>(R.id.dontShowAgain).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val prefs = context.getSharedPreferences("delete_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("delete_no_warn_${resource.id}", true).apply()
            }
        }
    }
}
```

### 6.3 NetworkDeleteHandler.kt

```kotlin
class NetworkDeleteHandler(
    private val smbManager: SmbConnectionManager,
    private val sftpManager: SftpConnectionManager,
    private val ftpManager: FtpConnectionManager,
    private val cloudManager: CloudStorageManager,
    private val timber: Timber
) {
    
    suspend fun deleteFilesFromNetwork(
        files: List<MediaFileEntity>,
        resource: ResourceEntity
    ): NetworkDeleteResult = withContext(Dispatchers.IO) {
        
        val results = files.map { file ->
            try {
                when (resource.type) {
                    ResourceType.SMB -> deleteFromSMB(file, resource)
                    ResourceType.SFTP -> deleteFromSFTP(file, resource)
                    ResourceType.FTP -> deleteFromFTP(file, resource)
                    ResourceType.CLOUD -> deleteFromCloud(file, resource)
                    else -> FileDeleteResult(file, false, "Unsupported type")
                }
            } catch (e: Exception) {
                timber.e(e, "Failed to delete ${file.filePath} from ${resource.name}")
                FileDeleteResult(file, false, e.message ?: "Unknown error")
            }
        }
        
        return@withContext NetworkDeleteResult(
            successCount = results.count { it.success },
            failureCount = results.count { !it.success },
            failedFiles = results.filter { !it.success },
            isSuccess = results.all { it.success }
        )
    }
    
    private suspend fun deleteFromSMB(
        file: MediaFileEntity,
        resource: ResourceEntity
    ): FileDeleteResult {
        return try {
            smbManager.deleteFile(resource.credentials, file.filePath)
            FileDeleteResult(file, true)
        } catch (e: Exception) {
            FileDeleteResult(file, false, e.message)
        }
    }
    
    // Similar implementations for SFTP, FTP, CLOUD...
}

data class NetworkDeleteResult(
    val successCount: Int,
    val failureCount: Int,
    val failedFiles: List<FileDeleteResult>,
    val isSuccess: Boolean
)

data class FileDeleteResult(
    val file: MediaFileEntity,
    val success: Boolean,
    val errorMessage: String? = null
)
```

---

## 7. DATA FLOW DIAGRAM

### Delete with Network Confirmation (Phase 1)
```
User long-press file(s) in Browse
    ↓
Taps "Delete" from context menu
    ↓
BrowseViewModel.onDeleteClick(files, resource)
    ↓
DeleteOperationManager.deleteFiles()
    ├→ Check: isNetworkResource(resource.type)?
    │
    ├→ YES (Network):
    │   ├→ Show NetworkDeleteConfirmationDialog
    │   ├→ Dialog displays:
    │   │  • Red warning heading
    │   │  • File names
    │   │  • Resource info
    │   │  • "Cannot be undone" message
    │   │  • Checkbox: "Don't show again"
    │   │
    │   └→ User confirms or cancels
    │       ├→ CONFIRM:
    │       │   └→ NetworkDeleteHandler.deleteFilesFromNetwork()
    │       │       ├→ Send delete command to SMB/SFTP/FTP/Cloud
    │       │       ├→ Await response
    │       │       └→ Return NetworkDeleteResult
    │       │           ↓
    │       │       Show Snackbar: "Deleted X file(s)"
    │       │       (NO undo button)
    │       │
    │       └→ CANCEL:
    │           └→ Dismiss dialog, no action
    │
    └→ NO (Local):
        └→ Show standard confirmation dialog
            └→ Delete to local trash (existing behavior)
                └→ Show Snackbar with [Undo] button
```

---

## 8. TESTING STRATEGY

### Unit Tests

**DeleteOperationManagerTest.kt**:
- `testNetworkResourceDetected()` → isNetworkResource returns true for SMB/SFTP/FTP/CLOUD
- `testLocalResourceDetected()` → isNetworkResource returns false for LOCAL
- `testNetworkDeleteShowsEnhancedDialog()` → wrong dialog not shown for local
- `testLocalDeleteShowsStandardDialog()` → standard dialog shown for local
- `testDontShowAgainPreference()` → preference saved/retrieved correctly
- `testBatchDeleteSingleDialog()` → single dialog for multiple files

**NetworkDeleteHandlerTest.kt**:
- `testDeleteFromSmbSuccess()` → file deleted correctly
- `testDeleteFromSftpSuccess()` → SFTP delete works
- `testDeleteFromFtpSuccess()` → FTP delete works
- `testDeleteFromCloudSuccess()` → Cloud delete works
- `testDeletePartialFailure()` → 3 of 5 succeed, report shows both
- `testDeleteNetworkTimeout()` → graceful error handling
- `testDeletePermissionDenied()` → clear error message

**DialogTest.kt**:
- `testDialogShowsRedWarning()` → heading is red
- `testDialogShowsFileNames()` → files displayed correctly
- `testDialogShowsResourceInfo()` → resource name and path shown
- `testDialogShowsCheckbox()` → checkbox renders correctly
- `testDeleteButtonDisabledOnLoad()` → button disabled for 1 second
- `testDeleteButtonEnabledAfterDelay()` → button enabled after 1 second

---

### E2E Tests (Maestro or Manual)

**E2E_NetworkDeleteConfirmation**:
1. Navigate to Browse → SMB resource
2. Select any file
3. Tap Delete from context menu
4. Verify dialog shows:
   - Red "THIS ACTION CANNOT BE UNDONE" heading
   - File name
   - "No undo" message
5. Tap Cancel → Dialog closes, file still exists
6. Repeat: Tap Delete, confirm → File deleted on server
7. Verify Snackbar shows no Undo button

**E2E_DontShowAgainPreference**:
1. Delete from SMB resource → see warning
2. Check "Don't show again for [resource]"
3. Select another file from same resource
4. Tap Delete → Dialog should NOT appear
5. Confirm delete without dialog

---

## 9. LOCALIZATION STRINGS

**English** (`strings.xml`):
```xml
<string name="delete_action_cannot_be_undone">THIS ACTION CANNOT BE UNDONE</string>
<string name="delete_n_files_from_network">Delete %1$d file(s) from network?</string>
<string name="network_delete_warning">⚠️ Network and cloud resources do NOT support Undo. Files will be PERMANENTLY deleted.</string>
<string name="delete_permanently">Delete Permanently</string>
<string name="cancel">Cancel</string>
<string name="dont_show_again_for_resource">Don\'t show this warning again for %1$s</string>
<string name="deleted_files_count">Deleted %1$d file(s) from %2$s</string>
<string name="and_n_more_files">and %1$d more</string>
<string name="resource_type_smb">SMB Network</string>
<string name="resource_type_sftp">SFTP Server</string>
<string name="resource_type_ftp">FTP Server</string>
<string name="resource_type_cloud">Cloud Storage</string>
```

**Russian** (`strings-ru.xml`):
```xml
<string name="delete_action_cannot_be_undone">ЭТО ДЕЙСТВИЕ НЕЛЬЗЯ ОТМЕНИТЬ</string>
<string name="delete_n_files_from_network">Удалить %1$d файл(ы) из сети?</string>
<string name="network_delete_warning">⚠️ Сетевые и облачные ресурсы НЕ поддерживают отмену. Файлы будут НАВСЕГДА удалены.</string>
<string name="delete_permanently">Удалить навсегда</string>
<string name="cancel">Отмена</string>
<string name="dont_show_again_for_resource">Не показывать это предупреждение для %1$s</string>
<string name="deleted_files_count">Удалено %1$d файл(ов) из %2$s</string>
<string name="and_n_more_files">и ещё %1$d</string>
<string name="resource_type_smb">Сетевой диск</string>
<string name="resource_type_sftp">SFTP сервер</string>
<string name="resource_type_ftp">FTP сервер</string>
<string name="resource_type_cloud">Облачное хранилище</string>
```

---

## 10. ERROR SCENARIOS & HANDLING

| Scenario | Root Cause | User Message | Action |
|----------|-----------|--------------|--------|
| Delete from SMB — permission denied | User lacks write perms | "Permission denied on [resource]. Contact admin." | Show error, no retry |
| Delete from SFTP — connection timeout | Network down | "Connection timeout. Check network and retry." | Show retry button |
| Delete — file not found | Already deleted elsewhere | "File not found (already deleted?). Proceeding…" | Treat as success |
| Delete from FTP — quota exceeded | Server full | "Storage full on [resource]. Free space and retry." | Show error, suggest trash |
| Delete — mixed success (3/5) | Some files in use | "Deleted 3 file(s), failed 2. See details." | Show itemized report |
| Delete — user cancels | User decided not to | Dialog closes, no action | No confirmation needed |
| Delete — "Don't show again" prefs corrupt | SharedPref corrupt | Warning shown normally | Fallback to standard behavior |

---

## 11. FUTURE ENHANCEMENTS (Phase 2+)

1. **Network Trash**: Move to `/.trash/` instead of permanent delete
2. **Trash TTL**: Auto-delete from trash after 30 days
3. **Browse Trash**: Show trash contents, allow restore
4. **Trash Stats**: Show how much space trash occupies
5. **Space Warnings**: "Server only has 100MB left after delete"
6. **Parallel Deletes**: Delete multiple files in parallel (if safe)
7. **Delete History**: Audit log of all deletions (with timestamps)
8. **Recovery Window**: "You can recover within 24 hours" message

---

## 12. ACCEPTANCE CRITERIA

- [ ] Network resources show enhanced confirmation dialog
- [ ] Local resources show standard dialog (unchanged)
- [ ] Dialog heading is prominent, red, and says "CANNOT BE UNDONE"
- [ ] Dialog shows file names, resource name, and resource path
- [ ] Dialog shows warning: "Network/cloud do NOT support Undo"
- [ ] "Delete Permanently" button is disabled for 1 second on dialog load
- [ ] Checkbox "Don't show again for [resource]" saves and works
- [ ] After network delete, Snackbar shows NO undo button
- [ ] Batch delete (5 files) shows single dialog with count
- [ ] Network delete errors show user-friendly messages with retry option
- [ ] All strings localized (EN, RU, UK)
- [ ] No ANR warnings during delete
- [ ] E2E test passes: select file → delete → confirm → file gone from server
- [ ] Unit tests pass: dialog logic, handler logic, preferences

---

## 13. REFERENCES & DEPENDENCIES

- **Room**: `MediaFileEntity`, `ResourceEntity` DAOs
- **Timber**: Logging
- **Kotlin Coroutines**: Async file operations
- **Material3**: Dialog styling and theming
- **AndroidX**: Fragment compatibility
- **SMB/SFTP/FTP/Cloud managers**: Existing connection handlers
- **SharedPreferences**: "Don't show again" state

---

**Document Status**: READY FOR DEVELOPMENT  
**Last Updated**: March 11, 2026  
**Phase**: Phase 1 (Enhanced Confirmation) — ~2–3 hours

