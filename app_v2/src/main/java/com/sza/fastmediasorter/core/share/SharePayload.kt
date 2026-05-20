package com.sza.fastmediasorter.core.share

import android.net.Uri

/**
 * S0189 (Phase 09): the unit of share for [SystemShareInvoker]. Sealed so the invoker
 * can exhaustively switch on the variant without leaking variant-specific knobs into
 * the call sites.
 *
 * - [Text]  shares plain text. Optionally attaches a [streamUri] for a "share the file
 *   that contains this text" flow (used by text-note Save & Send so the chooser surface
 *   gets both the body and the file).
 * - [Image] shares a single image URI under the supplied [mime] type. Per S0191 §16,
 *   image shares never target a specific package - the system chooser is shown raw.
 */
sealed class SharePayload {

    data class Text(
        val content: String,
        val streamUri: Uri? = null,
        val grantReadPermission: Boolean = false,
    ) : SharePayload()

    data class Image(
        val uri: Uri,
        val mime: String,
    ) : SharePayload()
}
