package com.sza.fastmediasorter.data.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.repository.INSTALLED_APP_ICON_DIR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1401: one image file per cached app icon, in a subdirectory of the app cache directory.
 *
 * Icons live on disk rather than in the database because a hundred of them would put megabytes of
 * binary into a file that every unrelated query, backup and migration then carries (strategic
 * research 02). The cache directory is also reclaimable, so the system can take the space back under
 * pressure and the next refresh simply writes the icons again.
 *
 * Everything is rasterised to one bounded square rather than the drawable's native size: an adaptive
 * icon reports whatever its author drew, and the largest surface that ever paints one of these is a
 * launcher cell.
 */
@Singleton
class InstalledAppIconStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val directory: File
        get() = File(context.cacheDir, INSTALLED_APP_ICON_DIR)

    /**
     * Writes [icon] for [packageName] and returns the stored file name, or null when the write did
     * not happen. A missing icon is a recoverable state - the list still shows the app - so a failure
     * is reported at info level and never propagates.
     */
    suspend fun write(packageName: String, icon: Drawable): String? = withContext(Dispatchers.IO) {
        val target = File(directory, fileNameFor(packageName))
        try {
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.i("Icon cache directory unavailable, skipping %s", packageName)
                return@withContext null
            }
            val size = context.resources.getDimensionPixelSize(R.dimen.installed_app_icon_cache_size)
            val bitmap = rasterise(icon, size)
            FileOutputStream(target).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
            }
            bitmap.recycle()
            target.name
        } catch (e: IOException) {
            Timber.i(e, "Icon cache write failed for %s", packageName)
            null
        }
    }

    /** The file a cached row's stored name points at, or null when it is gone or was never written. */
    fun fileFor(fileName: String?): File? =
        fileName?.let { File(directory, it) }?.takeIf { it.isFile }

    suspend fun delete(packageName: String) = withContext(Dispatchers.IO) {
        File(directory, fileNameFor(packageName)).delete()
        Unit
    }

    /**
     * Drops every icon whose app is no longer in [packageNames]. Called with the same list the cache
     * rows were just replaced with, so files and rows disappear together instead of the directory
     * silently accumulating icons of apps the user uninstalled long ago.
     */
    suspend fun retainOnly(packageNames: Set<String>) = withContext(Dispatchers.IO) {
        val keep = packageNames.map(::fileNameFor).toSet()
        directory.listFiles()
            ?.filter { it.isFile && it.name !in keep }
            ?.forEach { it.delete() }
        Unit
    }

    private fun rasterise(icon: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // The drawable comes straight from the package manager and is a fresh instance per query, so
        // resizing it here cannot disturb anything else that is drawing it.
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bitmap))
        return bitmap
    }

    private fun fileNameFor(packageName: String): String = "$packageName.png"

    private companion object {
        /** PNG ignores the quality argument; the value only has to be in range. */
        const val PNG_QUALITY = 100
    }
}
