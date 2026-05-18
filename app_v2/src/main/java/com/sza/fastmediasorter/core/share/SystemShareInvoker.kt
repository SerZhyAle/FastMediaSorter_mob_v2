package com.sza.fastmediasorter.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
        Timber.d("S0189: SystemShareInvoker.invoke")
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
