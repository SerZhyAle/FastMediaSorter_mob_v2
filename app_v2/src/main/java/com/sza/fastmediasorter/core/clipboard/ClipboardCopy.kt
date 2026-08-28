package com.sza.fastmediasorter.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S2192: the one way this app puts text on the system clipboard.
 *
 * [label] reaches the user - the system clipboard chooser shows it beside the entry - so callers pass
 * something that names the copied content rather than a generic word.
 *
 * The confirmation is deliberately one message for every caller: before this helper thirteen call sites
 * carried six different wordings, two of which were the same English text under two keys.
 *
 * Images do not belong here. A bitmap goes on the clipboard as a content URI through
 * [ImageClipboardWriter], which needs a file, an IO dispatcher and a failure result this function has no
 * use for.
 */
internal fun Context.copyTextToClipboard(label: String, text: String) {
    Timber.d("S2192: copyTextToClipboard label=$label sdk=${Build.VERSION.SDK_INT}")
    val clipboard = getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Android 13 shows its own clipboard preview on every copy, so a second confirmation there would
        // stack two messages over the same action. Below it the toast is the only feedback there is, and
        // it doubles as the screen-reader announcement - the announcement API is deprecated, while a
        // toast is read aloud already, so one call serves both readers.
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
