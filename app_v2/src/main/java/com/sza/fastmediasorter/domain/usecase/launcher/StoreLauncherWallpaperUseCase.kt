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
 * vanishes reads as a bug. The folder is wiped before each write so a wallpaper change can never leave
 * the previous copy behind.
 */
class StoreLauncherWallpaperUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend operator fun invoke(uri: Uri): LauncherWallpaperImport = withContext(Dispatchers.IO) {
        try {
            val dir = wallpaperDir()
            // Wipe first: one wallpaper at a time, so a change never accumulates copies.
            dir.deleteRecursively()
            dir.mkdirs()
            val outFile = File(dir, "$FILE_BASE_NAME.${resolveExtension(uri)}")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            when {
                copied == null -> LauncherWallpaperImport.Failed
                outFile.length() == 0L -> {
                    outFile.delete()
                    LauncherWallpaperImport.Failed
                }
                else -> LauncherWallpaperImport.Stored(outFile.absolutePath)
            }
        } catch (e: Exception) {
            // Unreadable Uri, revoked grant, or a full disk: the caller keeps the previous wallpaper
            // mode, so a failed import degrades to "nothing changed" rather than a blank desktop.
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
