package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.File

/**
 * Helpers for "Set as default player" UI in Settings and Welcome screens.
 *
 * Flow (type-specific):
 * 1. Enable activity-alias components via DefaultPlayerManager (app becomes visible to OS).
 * 2. Show a dialog explaining the next step.
 * 3. Query MediaStore for an existing file of that type and open Intent.createChooser so the
 *    user sees the "Open with - Always" dialog on stock Android (Pixel/AOSP).
 * 4. If no file exists on the device, fall back to ACTION_MANAGE_DEFAULT_APPS_SETTINGS
 *    (works on Samsung/Xiaomi ROMs that have a "Media player" category there).
 */
object DefaultPlayerHelper {

    /**
     * Best-effort check: is this app the current default handler for media open intents?
     * Returns false on API below 29 (no reliable API).
     */
    fun isAlreadyDefaultPlayer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val packageName = context.packageName
        val pm = context.packageManager

        val mimeTypesToProbe = listOf("audio/*", "video/*", "image/*", "application/pdf")
        return mimeTypesToProbe.any { mime ->
            val probe = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://"), mime)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val resolved = pm.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == packageName
        }
    }

    /**
     * Updates button enabled state and text based on current default-player status.
     * Call in setupViews() and onResume().
     */
    fun applyButtonState(button: TextView, context: Context, normalTextRes: Int) {
        val isDefault = isAlreadyDefaultPlayer(context)
        if (isDefault) {
            button.visibility = View.GONE
        } else {
            button.visibility = View.VISIBLE
            button.isEnabled = true
            button.alpha = 1.0f
            button.text = context.getString(normalTextRes)
        }
    }

    // --- Type-specific entry point (Settings fragments) ---

    /**
     * For Settings fragments: enable aliases, show instructional dialog, then open the
     * system chooser for the given MIME type (e.g. audio, video, image, application/pdf).
     */
    fun showSetDefaultDialogForType(fragment: Fragment, mimeType: String) {
        if (!fragment.isAdded || fragment.activity?.isFinishing == true || fragment.activity?.isDestroyed == true) return
        val context = fragment.requireContext()
        DefaultPlayerManager.applyPrimaryPlayerState(context, true)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_default_player_dialog_title)
            .setMessage(R.string.settings_default_player_dialog_message)
            .setPositiveButton(R.string.settings_default_player_dialog_confirm) { _, _ ->
                openChooserOrFallback(fragment, mimeType)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Keep overload without mimeType for legacy callers
    fun showSetDefaultDialog(fragment: Fragment) = showSetDefaultDialogForType(fragment, "audio/*")

    // --- Activity overload (Welcome screen) ---

    /**
     * For Activities: enable aliases then open the system chooser directly (no dialog).
     * The Welcome screen already provides on-page instructions, so the dialog is redundant.
     */
    fun openChooserOrFallbackFromActivity(activity: Activity, mimeType: String) {
        DefaultPlayerManager.applyPrimaryPlayerState(activity, true)
        val sample = findSampleFile(activity, mimeType)
        if (sample != null) {
            val (uri, actualMime) = sample
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, actualMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                activity.startActivity(viewIntent)
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
            }
        }
        if (tryOpenProbeChooser(activity, mimeType)) {
            return
        }
        Timber.w("DefaultPlayerHelper: probe intent failed, fallback to default apps settings for %s", mimeType)
        openDefaultAppsSettingsFromActivity(activity)
    }

    // --- Internal helpers ---

    private fun openChooserOrFallback(fragment: Fragment, mimeType: String) {
        val context = fragment.requireContext()
        val sample = findSampleFile(context, mimeType)
        if (sample != null) {
            val (uri, actualMime) = sample
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, actualMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                fragment.startActivity(viewIntent)
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
            }
        }
        if (tryOpenProbeChooser(fragment, mimeType)) {
            return
        }
        Timber.w("DefaultPlayerHelper: probe intent failed, fallback to default apps settings for %s", mimeType)
        openDefaultAppsSettings(fragment)
    }

    /**
     * Query MediaStore for the most recently modified file matching the given MIME type.
     * Returns a Pair of (contentUri, actualMimeType) or null if nothing found.
     */
    private fun findSampleFile(context: Context, mimeType: String): Pair<Uri, String>? {
        if (!canQueryMediaStore(context)) {
            Timber.d("DefaultPlayerHelper: skip MediaStore query, no read permission")
            return null
        }

        return when {
            mimeType.startsWith("audio") ->
                queryCollection(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            mimeType.startsWith("video") ->
                queryCollection(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            mimeType.startsWith("image") ->
                queryCollection(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            else ->
                queryFilesWithMime(context, mimeType)
        }
    }

    private fun tryOpenProbeChooser(fragment: Fragment, mimeType: String): Boolean {
        val context = fragment.requireContext()
        val probe = createProbeUri(context, mimeType) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(probe.first, probe.second)
            addCategory(Intent.CATEGORY_DEFAULT)
            clipData = ClipData.newUri(context.contentResolver, "default_player_probe", probe.first)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantReadPermissionToResolvers(context, intent, probe.first)
        return try {
            fragment.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.w(e, "DefaultPlayerHelper: probe intent failed for %s", mimeType)
            false
        }
    }

    private fun tryOpenProbeChooser(activity: Activity, mimeType: String): Boolean {
        val probe = createProbeUri(activity, mimeType) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(probe.first, probe.second)
            addCategory(Intent.CATEGORY_DEFAULT)
            clipData = ClipData.newUri(activity.contentResolver, "default_player_probe", probe.first)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantReadPermissionToResolvers(activity, intent, probe.first)
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.w(e, "DefaultPlayerHelper: probe intent failed for %s", mimeType)
            false
        }
    }

    private fun createProbeUri(context: Context, mimeType: String): Pair<Uri, String>? {
        val (extension, concreteMime) = when {
            mimeType.startsWith("audio") -> "mp3" to "audio/mpeg"
            mimeType.startsWith("video") -> "mp4" to "video/mp4"
            mimeType.startsWith("image") -> "jpg" to "image/jpeg"
            mimeType == "application/pdf" -> "pdf" to "application/pdf"
            else -> return null
        }

        return try {
            val dir = File(context.cacheDir, "default_player_probe").apply { mkdirs() }
            val file = File(dir, "probe.$extension")
            if (!file.exists()) {
                file.writeBytes(byteArrayOf(0x00))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            uri to concreteMime
        } catch (e: Exception) {
            Timber.w(e, "DefaultPlayerHelper: failed to create probe file for %s", mimeType)
            null
        }
    }

    private fun grantReadPermissionToResolvers(context: Context, intent: Intent, uri: Uri) {
        val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in matches) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            try {
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Timber.d(e, "DefaultPlayerHelper: could not pre-grant URI to %s", packageName)
            }
        }
    }

    private fun canQueryMediaStore(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canReadImages = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val canReadVideo = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            val canReadAudio = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            canReadImages || canReadVideo || canReadAudio
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun queryCollection(context: Context, collectionUri: Uri): Pair<Uri, String>? {
        return try {
            context.contentResolver.query(
                collectionUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE),
                null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                        ?: return@use null
                    Pair(ContentUris.withAppendedId(collectionUri, id), mime)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "DefaultPlayerHelper: MediaStore query failed")
            null
        }
    }

    private fun queryFilesWithMime(context: Context, mimeType: String): Pair<Uri, String>? {
        val filesUri = MediaStore.Files.getContentUri("external")
        return try {
            context.contentResolver.query(
                filesUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.MIME_TYPE),
                "${MediaStore.MediaColumns.MIME_TYPE} = ?",
                arrayOf(mimeType),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                        ?: return@use null
                    Pair(ContentUris.withAppendedId(filesUri, id), mime)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "DefaultPlayerHelper: MediaStore files query failed for %s", mimeType)
            null
        }
    }

    private fun openDefaultAppsSettings(fragment: Fragment) {
        try {
            fragment.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", fragment.requireContext().packageName, null)
                }
                fragment.startActivity(intent)
            } catch (ignored: Exception) {
                Timber.w(ignored, "DefaultPlayerHelper: could not open default apps settings")
            }
        }
    }

    private fun openDefaultAppsSettingsFromActivity(activity: Activity) {
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            } catch (ignored: Exception) {
                Timber.w(ignored, "DefaultPlayerHelper: could not open default apps settings")
            }
        }
    }
}
