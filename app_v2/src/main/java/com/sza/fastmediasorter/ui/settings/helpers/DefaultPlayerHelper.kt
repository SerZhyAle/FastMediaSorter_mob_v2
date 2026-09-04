package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.di.MediaCapabilitiesEntryPoint
import com.sza.fastmediasorter.ui.dialog.SimpleValueChoiceDialog
import com.sza.fastmediasorter.ui.player.DefaultPlayerProbe
import com.sza.fastmediasorter.util.resolveActivityCompat
import com.sza.fastmediasorter.util.showBoundTo
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

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
 * 2. If no other app already owns the default, fire a bare ACTION_VIEW on a private technical probe
 *    of that type so the OS can present its native "Open with / Always" sheet without opening user media.
 * 3. Otherwise (no probe, or a foreign default already set) the sheet cannot be shown, so route the
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

    // This is an object (no DI reach), so the flavor capability surface is resolved via the Hilt entry
    // point instead of reading BuildConfig.SUPPORT_* directly (CLAUDE.md Rule 14).
    private fun capabilities(context: Context): MediaCapabilities =
        EntryPointAccessors.fromApplication(
            context.applicationContext, MediaCapabilitiesEntryPoint::class.java
        ).mediaCapabilities()

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
            val resolved = pm.resolveActivityCompat(probe, PackageManager.MATCH_DEFAULT_ONLY)
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
        DefaultPlayerManager.applyPrimaryPlayerState(context, true, capabilities(context))
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_default_player_dialog_title)
            .setMessage(R.string.settings_default_player_dialog_message)
            .setPositiveButton(R.string.settings_default_player_dialog_confirm) { _, _ ->
                openChooserOrFallback(fragment, mimeType)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    /**
     * Lets the user register the app as default for a document type. PDF and plain text (TXT, LOG) are
     * always offered; EPUB is added when [includeEpub]; Office/DOCX is added only when the current
     * flavor declares office MIME types (noLegal).
     */
    fun showSetDefaultDocumentDialog(fragment: Fragment, includeEpub: Boolean = true) {
        if (!fragment.isAdded || fragment.activity?.isFinishing == true || fragment.activity?.isDestroyed == true) return
        val context = fragment.requireContext()
        DefaultPlayerManager.applyPrimaryPlayerState(context, true, capabilities(context))

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

        SimpleValueChoiceDialog(
            context = context,
            lifecycleOwner = fragment.viewLifecycleOwner,
            title = context.getString(R.string.settings_default_document_type_title),
            options = options.map { (labelRes, mimeType) ->
                SimpleValueChoiceDialog.Option(mimeType, context.getString(labelRes))
            },
            currentKey = null,
            allowClear = false,
            onSelected = { key ->
                key?.let { showSetDefaultDialogForType(fragment, it) }
            },
        ).show()
    }

    // Keep overload without mimeType for legacy callers
    fun showSetDefaultDialog(fragment: Fragment) = showSetDefaultDialogForType(fragment, "audio/*")

    // --- Activity overload (Welcome screen) ---

    /**
     * For Activities: enable aliases then open the system "Open with" sheet directly (no dialog).
     * The Welcome screen already provides on-page instructions, so the dialog is redundant.
     */
    fun openChooserOrFallbackFromActivity(activity: Activity, mimeType: String) {
        DefaultPlayerManager.applyPrimaryPlayerState(activity, true, capabilities(activity))
        // Probe materialization writes to cache and PackageManager resolves handlers, so keep this off main.
        (activity as LifecycleOwner).lifecycleScope.launch {
            val openWith = withContext(Dispatchers.IO) { resolveOpenWithIntent(activity, mimeType) }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (openWith != null) {
                try {
                    activity.startActivity(openWith)
                    return@launch
                } catch (e: Exception) {
                    Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
                }
            }
            guideToDefaultAppsSettings(activity)
        }
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
        DefaultPlayerManager.applyPrimaryPlayerState(activity, true, capabilities(activity))
        // Probe materialization writes to cache and PackageManager resolves handlers, so keep this off main.
        (activity as LifecycleOwner).lifecycleScope.launch {
            val openWith = withContext(Dispatchers.IO) { resolveOpenWithIntent(activity, mimeType) }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (openWith != null) {
                try {
                    launcher.launch(openWith)
                    return@launch
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
    }

    // --- Internal helpers ---

    private fun openChooserOrFallback(fragment: Fragment, mimeType: String) {
        val context = fragment.requireContext()
        // Probe materialization writes to cache and PackageManager resolves handlers, so keep this off main.
        fragment.lifecycleScope.launch {
            val openWith = withContext(Dispatchers.IO) { resolveOpenWithIntent(context, mimeType) }
            if (!fragment.isAdded) return@launch
            if (openWith != null) {
                try {
                    fragment.startActivity(openWith)
                    return@launch
                } catch (e: Exception) {
                    Timber.w(e, "DefaultPlayerHelper: startActivity failed for %s", mimeType)
                }
            }
            Toast.makeText(context, R.string.default_player_choose_in_settings, Toast.LENGTH_LONG).show()
            openDefaultAppsSettings(fragment)
        }
    }

    /**
     * Build the bare ACTION_VIEW that lets the OS present its "Open with / Always" sheet, or null when
     * the sheet cannot be shown (the private probe could not be prepared, or another app owns the default).
     * Returning null is the signal to fall back to the default-apps settings screen.
     */
    private fun resolveOpenWithIntent(context: Context, mimeType: String): Intent? {
        if (foreignDefaultExists(context, mimeType)) {
            return null
        }
        Timber.d("S2379: preparing private default-handler probe for %s", mimeType)
        val actualMime = concreteMime(mimeType)
        val uri = createProbeUri(context, actualMime) ?: return null
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
            .resolveActivityCompat(typeIntent, PackageManager.MATCH_DEFAULT_ONLY)
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

    /** Creates the harmless content URI that drives the system resolver without exposing user media. */
    private fun createProbeUri(context: Context, mimeType: String): Uri? = runCatching {
        val directory = File(context.cacheDir, DefaultPlayerProbe.PROBE_DIR_NAME).apply { mkdirs() }
        val file = File(directory, "$PROBE_FILE_NAME.${probeExtensionFor(mimeType)}")
        file.outputStream().use { output -> output.write(PROBE_CONTENT) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure { error ->
        Timber.w(error, "DefaultPlayerHelper: failed to create default-player probe")
    }.getOrNull()

    private fun probeExtensionFor(mimeType: String): String = when {
        mimeType.startsWith("audio/") -> "mp3"
        mimeType.startsWith("video/") -> "mp4"
        mimeType.startsWith("image/") -> "jpg"
        mimeType == PDF_MIME_TYPE -> "pdf"
        else -> "bin"
    }

    private fun defaultOfficeMimeType(): String? {
        val officeMimeTypes = MediaTypeUtils.OFFICE_DOCUMENT_MIME_TYPES
        return when {
            OFFICE_DOCX_MIME_TYPE in officeMimeTypes -> OFFICE_DOCX_MIME_TYPE
            else -> officeMimeTypes.firstOrNull()
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

    private const val PROBE_FILE_NAME = "default-handler-probe"
    private val PROBE_CONTENT = byteArrayOf(0)
}
