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
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import timber.log.Timber

/**
 * Helpers for "Set as default player" UI in Settings and Welcome screens.
 *
 * Platform reality: Android exposes no API to register a third-party app as the default ACTION_VIEW
 * handler for a media MIME type, and RoleManager has no media role (S0409 research 01). The system
 * "Open with / Always" sheet is shown solely at the OS's discretion - for a bare ACTION_VIEW on a real
 * file when no other app already owns that type's default and at least two apps can handle it.
 * `Intent.createChooser` never surfaces an "Always" button (it is designed to bypass defaults), so it
 * cannot register a default and is not used here.
 *
 * Flow (type-specific):
 * 1. Enable the activity-alias components via DefaultPlayerManager (app becomes visible to the OS).
 * 2. If a real sample file of that type exists AND no other app already owns the default, fire a bare
 *    ACTION_VIEW on it so the OS can present its native "Open with / Always" sheet.
 * 3. Otherwise (no sample, or a foreign default already set) the sheet cannot be shown, so route the
 *    user to the system default-apps screen with a short instruction instead of silently opening an
 *    unrelated app.
 */
object DefaultPlayerHelper {
    private const val PDF_MIME_TYPE = "application/pdf"
    // .txt and .log both resolve to text/plain - one registration covers both.
    private const val TEXT_MIME_TYPE = "text/plain"
    private const val EPUB_MIME_TYPE = "application/epub+zip"
    private const val OFFICE_DOCX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    // PackageManager reports this synthetic package for the system ResolverActivity, i.e. "no concrete
    // default is set yet" - which is exactly the state in which the OS will show the Always sheet.
    private const val ANDROID_RESOLVER_PACKAGE = "android"

    /**
     * Best-effort check: is this app the current default handler for media open intents?
     * Returns false on API below 29 (no reliable API).
     */
    fun isAlreadyDefaultPlayer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val packageName = context.packageName
        val pm = context.packageManager

        val mimeTypesToProbe = listOf(
            "audio/*",
            "video/*",
            "image/*",
            PDF_MIME_TYPE
        ) + MediaTypeUtils.OFFICE_DOCUMENT_MIME_TYPES
        return mimeTypesToProbe.any { mime ->
            val probe = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://"), mime)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val resolved = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
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
     * For Settings fragments: enable aliases, show instructional dialog, then either open the system
     * "Open with" sheet for a real sample of the given MIME type or fall back to the default-apps screen.
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

    /**
     * Lets the user register the app as default for a document type. PDF and plain text (TXT, LOG) are
     * always offered; EPUB is added when [includeEpub]; Office/DOCX is added only when the current
     * flavor declares office MIME types (noLegal).
     */
    fun showSetDefaultDocumentDialog(fragment: Fragment, includeEpub: Boolean = true) {
        if (!fragment.isAdded || fragment.activity?.isFinishing == true || fragment.activity?.isDestroyed == true) return
        val context = fragment.requireContext()
        DefaultPlayerManager.applyPrimaryPlayerState(context, true)

        val options = mutableListOf(
            R.string.settings_default_document_type_pdf to PDF_MIME_TYPE,
            R.string.settings_default_document_type_text to TEXT_MIME_TYPE,
        )
        if (includeEpub) {
            options += R.string.settings_default_document_type_epub to EPUB_MIME_TYPE
        }
        defaultOfficeMimeType()?.let { officeMimeType ->
            options += R.string.settings_default_document_type_office to officeMimeType
        }

        if (options.size == 1) {
            showSetDefaultDialogForType(fragment, options.first().second)
            return
        }

        // AlertDialog suppresses the item list when a message is also set, so the type list must be the
        // dialog's only content - the title alone carries the prompt.
        val labels = options.map { context.getString(it.first) }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_default_document_type_title)
            .setItems(labels) { _, which ->
                showSetDefaultDialogForType(fragment, options[which].second)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Keep overload without mimeType for legacy callers
    fun showSetDefaultDialog(fragment: Fragment) = showSetDefaultDialogForType(fragment, "audio/*")

    // --- Activity overload (Welcome screen) ---

    /**
     * For Activities: enable aliases then open the system "Open with" sheet directly (no dialog).
     * The Welcome screen already provides on-page instructions, so the dialog is redundant.
     */
    fun openChooserOrFallbackFromActivity(activity: Activity, mimeType: String) {
        DefaultPlayerManager.applyPrimaryPlayerState(activity, true)
        val openWith = resolveOpenWithIntent(activity, mimeType)
        if (openWith != null) {
            try {
                activity.startActivity(openWith)
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
            }
        }
        guideToDefaultAppsSettings(activity)
    }

    /**
     * Like [openChooserOrFallbackFromActivity] but launches the intent through [launcher] instead of
     * `startActivity`, so the caller gets a return callback to drive a sequence of type dialogs one at a
     * time (S0409 enable-all). The result code is irrelevant (the sheet returns CANCELED on stock
     * Android); the callback is used only as the "user returned, advance" signal.
     */
    fun openChooserOrFallbackForResult(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>,
        mimeType: String,
    ) {
        DefaultPlayerManager.applyPrimaryPlayerState(activity, true)
        val openWith = resolveOpenWithIntent(activity, mimeType)
        if (openWith != null) {
            try {
                launcher.launch(openWith)
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: launch failed for %s", mimeType)
            }
        }
        Toast.makeText(activity, R.string.default_player_choose_in_settings, Toast.LENGTH_LONG).show()
        try {
            launcher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            })
        }
    }

    // --- Internal helpers ---

    private fun openChooserOrFallback(fragment: Fragment, mimeType: String) {
        val context = fragment.requireContext()
        val openWith = resolveOpenWithIntent(context, mimeType)
        if (openWith != null) {
            try {
                fragment.startActivity(openWith)
                return
            } catch (e: Exception) {
                Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
            }
        }
        Toast.makeText(context, R.string.default_player_choose_in_settings, Toast.LENGTH_LONG).show()
        openDefaultAppsSettings(fragment)
    }

    /**
     * Build the bare ACTION_VIEW that lets the OS present its "Open with / Always" sheet, or null when
     * the sheet cannot be shown (no real sample of this type, or another app already owns the default).
     * Returning null is the signal to fall back to the default-apps settings screen.
     */
    private fun resolveOpenWithIntent(context: Context, mimeType: String): Intent? {
        if (foreignDefaultExists(context, mimeType)) {
            return null
        }
        val (uri, actualMime) = findSampleFile(context, mimeType) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, actualMime)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * True when a different app is already registered as the concrete default for [mimeType]. In that
     * state a bare ACTION_VIEW would silently open that app instead of showing the Always sheet, so the
     * caller must route to settings. A resolver result of [ANDROID_RESOLVER_PACKAGE] (no default set yet)
     * or our own package both count as "no foreign default".
     */
    private fun foreignDefaultExists(context: Context, mimeType: String): Boolean {
        val typeIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://"), concreteMime(mimeType))
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val pkg = context.packageManager
            .resolveActivity(typeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName ?: return false
        return pkg != ANDROID_RESOLVER_PACKAGE && pkg != context.packageName
    }

    /** Collapse a wildcard MIME (e.g. "audio/&#42;") to a concrete one so PackageManager resolution and the
     *  filters declared by the standalone aliases can match. Concrete document MIMEs pass through. */
    private fun concreteMime(mimeType: String): String = when {
        mimeType.startsWith("audio") -> "audio/mpeg"
        mimeType.startsWith("video") -> "video/mp4"
        mimeType.startsWith("image") -> "image/jpeg"
        else -> mimeType
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

    private fun defaultOfficeMimeType(): String? {
        val officeMimeTypes = MediaTypeUtils.OFFICE_DOCUMENT_MIME_TYPES
        return when {
            OFFICE_DOCX_MIME_TYPE in officeMimeTypes -> OFFICE_DOCX_MIME_TYPE
            else -> officeMimeTypes.firstOrNull()
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

    private fun guideToDefaultAppsSettings(activity: Activity) {
        Toast.makeText(activity, R.string.default_player_choose_in_settings, Toast.LENGTH_LONG).show()
        openDefaultAppsSettingsFromActivity(activity)
    }

    private fun openDefaultAppsSettings(fragment: Fragment) {
        try {
            // SettingsIntentLauncher.launch not needed - fire-and-forget link to default-apps page
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
            // SettingsIntentLauncher.launch not needed - fire-and-forget link to default-apps page
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
