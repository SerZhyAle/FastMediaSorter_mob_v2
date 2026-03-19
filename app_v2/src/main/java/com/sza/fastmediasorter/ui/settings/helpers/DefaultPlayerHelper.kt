package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import timber.log.Timber

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
        button.isEnabled = !isDefault
        button.alpha = if (isDefault) 0.5f else 1.0f
        button.text = context.getString(
            if (isDefault) R.string.settings_already_default_player else normalTextRes
        )
    }

    // --- Type-specific entry point (Settings fragments) ---

    /**
     * For Settings fragments: enable aliases, show instructional dialog, then open the
     * system chooser for the given MIME type (e.g. audio, video, image, application/pdf).
     */
    fun showSetDefaultDialogForType(fragment: Fragment, mimeType: String) {
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
                activity.startActivity(Intent.createChooser(viewIntent, null))
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: createChooser failed for %s", mimeType)
            }
        }
        if (tryOpenMimeOnlyChooser(activity, mimeType)) {
            return
        }
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
                fragment.startActivity(Intent.createChooser(viewIntent, null))
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: createChooser failed for %s", mimeType)
            }
        }
        if (tryOpenMimeOnlyChooser(fragment, mimeType)) {
            return
        }
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

    private fun tryOpenMimeOnlyChooser(fragment: Fragment, mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return try {
            fragment.startActivity(Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            Timber.w(e, "DefaultPlayerHelper: MIME-only chooser failed for %s", mimeType)
            false
        }
    }

    private fun tryOpenMimeOnlyChooser(activity: Activity, mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return try {
            activity.startActivity(Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            Timber.w(e, "DefaultPlayerHelper: MIME-only chooser failed for %s", mimeType)
            false
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
