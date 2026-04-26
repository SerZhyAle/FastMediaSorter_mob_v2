# Phase 03 — BrowseCameraCaptureManager

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Started:** 2026-04-25
**Completed:** 2026-04-25
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04

---

## Objective

Create `BrowseCameraCaptureManager` in `ui/browse/managers/` — the self-contained class that
owns `ActivityResultLauncher` registration, temp file creation, filename dialog, save routing
for all resource types, and post-save list refresh + scroll-to-new-file.

---

## Files Touched

| File | New/Mod | Budget |
| ---- | :-----: | -----: |
| `ui/browse/managers/BrowseCameraCaptureManager.kt` | New | ≤ 900 |

---

## Steps

### Step 03.1 — Class skeleton and constructor

**Status:** `[x] done`

**File:** `ui/browse/managers/BrowseCameraCaptureManager.kt` (new)

```kotlin
package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

Constructor:

```kotlin
class BrowseCameraCaptureManager(
    private val activity: FragmentActivity,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onFileSaved: (fileName: String) -> Unit  // triggers list refresh + scroll
)
```

---

### Step 03.2 — ActivityResultLauncher

**Status:** `[x] done`
**Depends on:** Step 03.1

Register in the constructor body (must be called before Activity `onStart`):

```kotlin
private var pendingTempFile: File? = null
private var pendingResource: MediaResource? = null

val launcher: ActivityResultLauncher<Intent> =
    activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleResult(result)
    }
```

---

### Step 03.3 — launch() entry point

**Status:** `[x] done`
**Depends on:** Steps 03.1, 03.2

```kotlin
fun launch(resource: MediaResource) {
    pendingResource = resource
    val captureVideo = resource.supportedMediaTypes.let { types ->
        !resource.allFiles &&
            types.none { it == MediaType.IMAGE || it == MediaType.GIF } &&
            types.any { it == MediaType.VIDEO }
    }
    val ext = if (captureVideo) ".mp4" else ".jpg"
    val action = if (captureVideo) MediaStore.ACTION_VIDEO_CAPTURE
                 else MediaStore.ACTION_IMAGE_CAPTURE
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val tempFile = createTemp(timestamp, ext) ?: run {
        Toast.makeText(activity, R.string.camera_capture_error_temp_file, Toast.LENGTH_SHORT).show()
        return
    }
    pendingTempFile = tempFile
    val uri = FileProvider.getUriForFile(
        activity, "${activity.packageName}.fileprovider", tempFile)
    launcher.launch(Intent(action).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) })
}
```

---

### Step 03.4 — createTemp helper

**Status:** `[x] done`
**Depends on:** Step 03.1

```kotlin
private fun createTemp(timestamp: String, ext: String): File? = try {
    val dir = activity.getExternalFilesDir(
        if (ext == ".mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
    ) ?: activity.filesDir
    File(dir, "CAP_$timestamp$ext").also { it.createNewFile() }
} catch (e: Exception) {
    Timber.e(e, "BrowseCameraCaptureManager: createTemp failed")
    null
}
```

---

### Step 03.5 — handleResult()

**Status:** `[x] done`
**Depends on:** Steps 03.2, 03.4

```kotlin
private fun handleResult(result: ActivityResult) {
    val tempFile = pendingTempFile ?: return
    val resource = pendingResource ?: return
    if (result.resultCode != Activity.RESULT_OK) {
        tempFile.delete()
        pendingTempFile = null
        return
    }
    coroutineScope.launch {
        val settings = settingsRepository.getSettings().first()
        val defaultName = tempFile.name
        if (settings.skipCameraFilenameDialog) {
            save(tempFile, defaultName, resource)
        } else {
            withContext(Dispatchers.Main) { showNameDialog(tempFile, defaultName, resource) }
        }
    }
}
```

---

### Step 03.6 — showNameDialog()

**Status:** `[x] done`
**Depends on:** Step 03.5

```kotlin
private fun showNameDialog(tempFile: File, defaultName: String, resource: MediaResource) {
    val input = EditText(activity).apply { setText(defaultName); selectAll() }
    AlertDialog.Builder(activity)
        .setTitle(R.string.camera_capture_filename_title)
        .setView(input)
        .setPositiveButton(R.string.ok) { _, _ ->
            val name = input.text.toString().trim().ifBlank { defaultName }
            coroutineScope.launch { save(tempFile, withExt(name, tempFile.extension), resource) }
        }
        .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete() }
        .setOnCancelListener { tempFile.delete() }
        .show()
}
```

---

### Step 03.7 — save() with destination routing

**Status:** `[x] done`
**Depends on:** Step 03.6

```kotlin
private suspend fun save(tempFile: File, name: String, resource: MediaResource) {
    val path = resource.path
    val isVirtualCameraTarget = VirtualPathUtils.isVirtualPath(path) ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
        path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES ||
        path == LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS
    val success = try {
        if (isVirtualCameraTarget) saveToDcim(tempFile, name)
        else when (resource.type) {
            ResourceType.LOCAL -> saveLocal(tempFile, name, path)
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP ->
                uploadNetwork(tempFile, name, resource)
            ResourceType.CLOUD -> uploadCloud(tempFile, name, resource)
        }
    } catch (e: Exception) {
        Timber.e(e, "BrowseCameraCaptureManager: save failed")
        false
    } finally {
        tempFile.delete()
        pendingTempFile = null
    }
    withContext(Dispatchers.Main) {
        val msg = if (success) activity.getString(R.string.camera_capture_saved, name)
                  else activity.getString(R.string.camera_capture_error_save_generic)
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        if (success) onFileSaved(name)
    }
}
```

---

### Step 03.8 — Destination implementations

**Status:** `[x] done`
**Depends on:** Step 03.7

**saveToDcim** (virtual paths → standard camera folder):

```kotlin
private suspend fun saveToDcim(tempFile: File, name: String): Boolean =
    withContext(Dispatchers.IO) {
        val cameraDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"
        ).also { it.mkdirs() }
        tempFile.copyTo(File(cameraDir, name), overwrite = true)
        activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(cameraDir, name))))
        true
    }
```

**saveLocal** (LOCAL resource):

```kotlin
private suspend fun saveLocal(tempFile: File, name: String, rootPath: String): Boolean =
    withContext(Dispatchers.IO) {
        tempFile.copyTo(File(rootPath, name), overwrite = true)
        true
    }
```

**uploadNetwork** (SMB/FTP/SFTP): Use the existing transfer strategy layer
(`data/transfer/strategy/`). Read the current upload entry point by grepping for
`uploadFile` or `transfer` in `data/transfer/`. Return `true` on success, `false` on
exception. If no single upload API exists yet, fall back to `saveLocal` to a temp path and
show a toast "Upload not yet supported for this resource type" — log as TODO.

**uploadCloud** (CLOUD): Use the existing cloud SDK upload. Same fallback strategy.

---

### Step 03.9 — withExt helper

**Status:** `[x] done`
**Depends on:** Step 03.1

```kotlin
private fun withExt(name: String, ext: String): String {
    val dotExt = if (ext.startsWith(".")) ext else ".$ext"
    return if (name.endsWith(dotExt, ignoreCase = true)) name else "$name$dotExt"
}
```

---

## Phase Done Criteria

- [x] `Glob "ui/browse/managers/BrowseCameraCaptureManager.kt"` → 1 hit
- [x] `Grep "launcher" ui/browse/managers/BrowseCameraCaptureManager.kt` → ≥ 2 hits (3)
- [x] `Grep "showNameDialog" ui/browse/managers/BrowseCameraCaptureManager.kt` → ≥ 2 hits (2)
- [x] `Grep "saveToDcim" ui/browse/managers/BrowseCameraCaptureManager.kt` → ≥ 2 hits (2)
- [x] `Grep "onFileSaved" ui/browse/managers/BrowseCameraCaptureManager.kt` → ≥ 2 hits (2)
- [x] File LOC ≤ 900 (~175 LOC)

**Phase Step Log:**

- 2026-04-25 — all 9 steps implemented in single file write (new file); uploadNetwork/uploadCloud use TODO fallback per spec since no unified upload UseCase exists at UI layer. All Phase Done Criteria PASS.
