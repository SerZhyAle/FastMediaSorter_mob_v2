package com.sza.fastmediasorter.core.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * S0189 (Phase 09): centralised entry point for `ACTION_SEND` flows.
 *
 * Hides the Keep-targeting + chooser fallback the operator agreed on (Keep for text,
 * generic chooser for images per S0191 §16) and the URI-permission boilerplate.
 *
 * Returns `true` when an Activity was launched (either the targeted package, or the chooser),
 * `false` when the targeted package was unavailable and no fallback chooser was requested.
 */
object SystemShareInvoker {

    /**
     * @param context          launching context (Activity preferred for direct
     *                         `startActivity`; Application context falls back via FLAG_ACTIVITY_NEW_TASK).
     * @param payload          what to share - text or image, per [SharePayload].
     * @param preferredPackage optional package id to target directly (e.g. `"com.google.android.keep"`).
     *                         When non-null, the chooser is bypassed; if the package is unavailable
     *                         the call returns `false` without falling back to the chooser.
     * @param chooserTitle     title for the system chooser. Required when [preferredPackage] is null,
     *                         ignored otherwise.
     */
    fun invoke(
        context: Context,
        payload: SharePayload,
        preferredPackage: String? = null,
        chooserTitle: String? = null,
    ): Boolean {
        val intent = buildIntent(payload)

        return if (preferredPackage != null) {
            intent.setPackage(preferredPackage)
            startSafely(context, intent)
        } else {
            val chooser = Intent.createChooser(intent, chooserTitle)
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startSafely(context, chooser)
        }
    }

    /**
     * S0303 (Phase 01): send one or more file [uris] under [mime], optionally targeting a resolved
     * [preferredPackage] (e.g. an installed Telegram client). Unlike [invoke], when the targeted
     * package cannot resolve the intent this falls back to the system chooser instead of silently
     * failing - the user always gets a way to complete the share.
     *
     * @return `true` when an Activity was launched (targeted package or chooser), `false` only when
     *         even the chooser could not be started or [uris] is empty.
     */
    fun invokeFiles(
        context: Context,
        uris: List<Uri>,
        mime: String = "*/*",
        preferredPackage: String? = null,
        chooserTitle: String? = null,
        emailAddresses: Array<String>? = null,
        subject: String? = null,
    ): Boolean {
        if (uris.isEmpty()) return false

        // Email extras are additive: a non-mail receiver simply ignores EXTRA_EMAIL/EXTRA_SUBJECT,
        // so the attachment path stays ACTION_SEND (never mailto, which drops the attachment).
        val intent = buildFilesIntent(uris, mime).apply {
            emailAddresses?.let { putExtra(Intent.EXTRA_EMAIL, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

        if (preferredPackage != null) {
            val targeted = Intent(intent).setPackage(preferredPackage)
            if (startSafely(context, targeted)) return true
            Timber.i("SystemShareInvoker: $preferredPackage unavailable, falling back to chooser")
        }

        val chooser = Intent.createChooser(intent, chooserTitle)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(context, chooser)
    }

    private fun buildFilesIntent(uris: List<Uri>, mime: String): Intent {
        val action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
        return Intent(action).apply {
            type = mime
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            // ClipData carries the same URIs so the read grant survives when a target reads
            // them outside the EXTRA_STREAM convention.
            clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildIntent(payload: SharePayload): Intent = Intent(Intent.ACTION_SEND).apply {
        when (payload) {
            is SharePayload.Text -> {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload.content)
                payload.streamUri?.let { putExtra(Intent.EXTRA_STREAM, it) }
                if (payload.grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            is SharePayload.Image -> {
                type = payload.mime
                putExtra(Intent.EXTRA_STREAM, payload.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun startSafely(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "SystemShareInvoker: no activity resolved for ${intent.action} pkg=${intent.`package`}")
        false
    }
}
