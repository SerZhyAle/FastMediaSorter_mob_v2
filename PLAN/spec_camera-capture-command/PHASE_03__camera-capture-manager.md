# Phase 03 — CameraCaptureManager

**Strategic spec:** [`../spec_camera-capture-command.md`](../spec_camera-capture-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Todo
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04

---

## Objective

Create `CameraCaptureManager` in `ui/player/helpers/` — the self-contained class that owns
the `ActivityResultLauncher` registration, temp file creation, filename dialog, and save
routing for all resource types.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `ui/player/helpers/CameraCaptureManager.kt` | New | ≤ 900 |

---

## Steps

### Step 03.1 — Create CameraCaptureManager skeleton

**File:** `ui/player/helpers/CameraCaptureManager.kt` (new)
**Depends on:** Phase 01 (AppSettings fields exist)

```kotlin
package com.sza.fastmediasorter.ui.player.helpers

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

Class signature:
```kotlin
class CameraCaptureManager(
    private val activity: FragmentActivity,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onSaveComplete: (fileName: String, success: Boolean) -> Unit
)
```

---

### Step 03.2 — ActivityResultLauncher registration

**Depends on:** Step 03.1

Register launcher inside the class (must be called before Activity is started — register in
`PlayerActivity.onCreate` via `CameraCaptureManager` constructor). Use
`ActivityResultContracts.StartActivityForResult()`.

```kotlin
private var pendingTempFile: File? = null
private var pendingResource: MediaResource? = null

val cameraLauncher: ActivityResultLauncher<Intent> =
    activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleCaptureResult(result)
    }
```

---

### Step 03.3 — launch() entry point

**Depends on:** Steps 03.1, 03.2

```kotlin
fun launch(resource: MediaResource) {
    pendingResource = resource
    val isVideo = resource.supportedMediaTypes.all {
        it == com.sza.fastmediasorter.domain.model.MediaType.VIDEO ||
        it == com.sza.fastmediasorter.domain.model.MediaType.AUDIO
    } && !resource.allFiles
    // prefer image capture by default unless resource is video-only
    val action = if (isVideo) MediaStore.ACTION_VIDEO_CAPTURE
                 else MediaStore.ACTION_IMAGE_CAPTURE
    val ext = if (isVideo) ".mp4" else ".jpg"
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val tempFile = createTempFile(timestamp, ext) ?: run {
        Toast.makeText(activity, R.string.camera_capture_error_temp_file, Toast.LENGTH_SHORT).show()
        return
    }
    pendingTempFile = tempFile
    val uri = FileProvider.getUriForFile(activity,
        "${activity.packageName}.fileprovider", tempFile)
    val intent = Intent(action).apply { putExtra(MediaStore.EXTRA_OUTPUT, uri) }
    cameraLauncher.launch(intent)
}
```

---

### Step 03.4 — Temp file creation

**Depends on:** Step 03.1

```kotlin
private fun createTempFile(timestamp: String, ext: String): File? = try {
    val dir = activity.getExternalFilesDir(
        if (ext == ".mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
    ) ?: activity.filesDir
    File(dir, "CAP_${timestamp}${ext}").also { it.createNewFile() }
} catch (e: Exception) {
    Timber.e(e, "CameraCaptureManager: failed to create temp file")
    null
}
```

---

### Step 03.5 — handleCaptureResult()

**Depends on:** Steps 03.2, 03.4

```kotlin
private fun handleCaptureResult(result: ActivityResult) {
    val tempFile = pendingTempFile ?: return
    val resource = pendingResource ?: return
    if (result.resultCode != Activity.RESULT_OK) {
        tempFile.delete()
        pendingTempFile = null
        return
    }
    coroutineScope.launch {
        val settings = settingsRepository.getSettings().first()
        val defaultName = tempFile.name // e.g. CAP_20260425_143025.jpg
        if (settings.skipCameraFilenameDialog) {
            saveCapture(tempFile, defaultName, resource)
        } else {
            withContext(Dispatchers.Main) {
                showFilenameDialog(tempFile, defaultName, resource)
            }
        }
    }
}
```

---

### Step 03.6 — Filename dialog

**Depends on:** Step 03.5

```kotlin
private fun showFilenameDialog(tempFile: File, defaultName: String, resource: MediaResource) {
    val input = EditText(activity).apply {
        setText(defaultName)
        selectAll()
    }
    AlertDialog.Builder(activity)
        .setTitle(R.string.camera_capture_filename_title)
        .setView(input)
        .setPositiveButton(R.string.ok) { _, _ ->
            val name = input.text.toString().trim().ifBlank { defaultName }
            coroutineScope.launch { saveCapture(tempFile, name, resource) }
        }
        .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete() }
        .setOnCancelListener { tempFile.delete() }
        .show()
}
```

---

### Step 03.7 — saveCapture() with destination routing

**Depends on:** Step 03.6

```kotlin
private suspend fun saveCapture(tempFile: File, name: String, resource: MediaResource) {
    try {
        val finalName = ensureExtension(name, tempFile.extension)
        val path = resource.path
        val isAllVideoOrImages = path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO ||
            path == LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES
        val success = if (VirtualPathUtils.isVirtualPath(path) || isAllVideoOrImages) {
            // Virtual: save to DCIM/Camera
            saveToDcim(tempFile, finalName)
        } else when (resource.type) {
            ResourceType.LOCAL -> saveToLocalPath(tempFile, finalName, path)
            ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP ->
                uploadToNetworkResource(tempFile, finalName, resource)
            ResourceType.CLOUD ->
                uploadToCloudResource(tempFile, finalName, resource)
        }
        withContext(Dispatchers.Main) {
            val msgRes = if (success) R.string.camera_capture_saved else R.string.camera_capture_error_save
            Toast.makeText(activity, activity.getString(msgRes, finalName), Toast.LENGTH_SHORT).show()
            onSaveComplete(finalName, success)
        }
    } catch (e: Exception) {
        Timber.e(e, "CameraCaptureManager: saveCapture failed")
        withContext(Dispatchers.Main) {
            Toast.makeText(activity, R.string.camera_capture_error_save_generic, Toast.LENGTH_SHORT).show()
            onSaveComplete(name, false)
        }
    } finally {
        tempFile.delete()
        pendingTempFile = null
    }
}
```

---

### Step 03.8 — Destination implementations

**Depends on:** Step 03.7

**saveToDcim** (virtual paths):
```kotlin
private suspend fun saveToDcim(tempFile: File, name: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val cameraDir = File(dcim, "Camera").also { it.mkdirs() }
        val dest = File(cameraDir, name)
        tempFile.copyTo(dest, overwrite = true)
        // Notify MediaStore
        val uri = Uri.fromFile(dest)
        activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
        true
    } catch (e: Exception) { Timber.e(e, "saveToDcim"); false }
}
```

**saveToLocalPath** (LOCAL resource):
```kotlin
private suspend fun saveToLocalPath(tempFile: File, name: String, rootPath: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val dest = File(rootPath, name)
            tempFile.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) { Timber.e(e, "saveToLocalPath"); false }
    }
```

**uploadToNetworkResource** (SMB/FTP/SFTP):
Upload via the `data/transfer/` strategy layer. Use the existing `FileTransferManager` or
equivalent upload path. If unavailable, copy to a local temp location and show a "save local
copy" toast as fallback. Return `true` on success.

**uploadToCloudResource** (CLOUD):
Use the existing cloud SDK upload. Return `true` on success.

> Implementation note: For network/cloud upload, use the same transfer infrastructure as the
> copy/move operations. The exact call site will be confirmed during implementation by reading
> the current transfer strategy classes.

---

### Step 03.9 — ensureExtension helper

**Depends on:** Step 03.1

```kotlin
private fun ensureExtension(name: String, ext: String): String {
    val dotExt = if (ext.startsWith(".")) ext else ".$ext"
    return if (name.endsWith(dotExt, ignoreCase = true)) name else "$name$dotExt"
}
```

---

## Phase Done Criteria

- [ ] `Glob "ui/player/helpers/CameraCaptureManager.kt"` → 1 hit
- [ ] `Grep "cameraLauncher" ui/player/helpers/CameraCaptureManager.kt` → ≥ 2 hits
- [ ] `Grep "showFilenameDialog" ui/player/helpers/CameraCaptureManager.kt` → ≥ 2 hits
- [ ] `Grep "saveCapture" ui/player/helpers/CameraCaptureManager.kt` → ≥ 2 hits
- [ ] `Grep "saveToDcim" ui/player/helpers/CameraCaptureManager.kt` → ≥ 2 hits
- [ ] File LOC ≤ 900
