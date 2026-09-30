package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Outcome of taking a user-picked image into app-private storage as the desktop wallpaper. */
sealed interface LauncherWallpaperImport {
    data class Stored(val absolutePath: String) : LauncherWallpaperImport
    data object Failed : LauncherWallpaperImport
}

/**
 * S1101: copies the user's picked image (still or GIF) into app-private storage and keeps exactly one
 * copy on disk.
 *
 * Copying rather than holding the picked `content://` Uri is deliberate (strategic ADR): the original
 * can be deleted, moved, or lose its SAF grant across a reboot, and a desktop whose wallpaper silently
 * vanishes reads as a bug. The previous copy is removed only after the new one is complete and non-empty,
 * so a change never leaves two copies behind and a failed import never deletes the one still in use.
 */
class StoreLauncherWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend operator fun invoke(uri: Uri): LauncherWallpaperImport = withContext(Dispatchers.IO) {
        var outFile: File? = null
        try {
            val dir = wallpaperDir()
            dir.mkdirs()
            val target = File(dir, "${FILE_BASE_NAME}_${System.currentTimeMillis()}.${resolveExtension(uri)}")
            outFile = target
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null || target.length() == 0L) {
                target.delete()
                LauncherWallpaperImport.Failed
            } else {
                // Settings keep pointing at the previous copy until the caller persists this path,
                // so it may only go once the replacement is known to be good.
                dir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
                LauncherWallpaperImport.Stored(target.absolutePath)
            }
        } catch (e: Exception) {
            // Unreadable Uri, revoked grant, or a full disk: only the partial new file goes and the caller
            // keeps the previous wallpaper, so a failed import degrades to "nothing changed".
            outFile?.delete()
            Timber.w(e, "StoreLauncherWallpaper: failed to copy %s", uri)
            LauncherWallpaperImport.Failed
        }
    }

    /** Drops the stored copy - called when the user leaves image mode. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { wallpaperDir().deleteRecursively() }
        Unit
    }

    private fun wallpaperDir() = File(context.filesDir, SUBFOLDER)

    private fun resolveExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        return DEFAULT_EXTENSION
    }

    private companion object {
        const val SUBFOLDER = "launcher_wallpaper"
        const val FILE_BASE_NAME = "wallpaper"
        const val DEFAULT_EXTENSION = "jpg"
    }
}
