package com.sza.fastmediasorter.ui.player.dispatch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.standalone.AudioStandaloneActivity
import com.sza.fastmediasorter.ui.player.standalone.DocumentStandaloneActivity
import com.sza.fastmediasorter.ui.player.standalone.PhotoVideoStandaloneActivity
import com.sza.fastmediasorter.ui.player.standalone.TextStandaloneActivity
import timber.log.Timber

/**
 * S0380: no-UI trampoline for external intents that carry no MIME type or a generic wildcard
 * (`* / *`). It resolves the real media family from the URI (MIME via ContentResolver + filename
 * extension) and forwards to the matching specialized activity, so a typeless intent never loads
 * the heavy media SDKs (ExoPlayer / Glide / PDF / WebView) just to pick a viewer. It renders
 * nothing itself (NoDisplay theme, never inflates a layout) and finishes immediately after forwarding.
 */
@SuppressLint("UnsafeIntentLaunch")
class StandalonePlayerDispatcherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = extractUri(intent)
        if (uri == null) {
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mimeType = intent?.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment

        val target = when (MediaFamilyResolver.resolve(mimeType, fileName)) {
            MediaFamily.PHOTO_VIDEO -> PhotoVideoStandaloneActivity::class.java
            MediaFamily.AUDIO -> AudioStandaloneActivity::class.java
            MediaFamily.DOCUMENT -> DocumentStandaloneActivity::class.java
            MediaFamily.TEXT -> TextStandaloneActivity::class.java
            null -> {
                // Unknown / binary type from a typeless intent: route to the lightest text surface,
                // which shows the explicit unsupported-format message rather than loading any heavy SDK.
                Timber.w("Dispatcher: unresolved media family for mime=$mimeType name=$fileName, routing to text surface")
                TextStandaloneActivity::class.java
            }
        }

        Timber.d("S0380: dispatcher routing typeless intent to ${target.simpleName}")
        // Carry over action, data, type, extras and URI-read grants verbatim; only retarget the class.
        val forward = Intent(intent).apply {
            setClass(this@StandalonePlayerDispatcherActivity, target)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(forward)
        finish()
    }

    private fun extractUri(source: Intent?): Uri? = when (source?.action) {
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                source.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        else -> source?.data
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        Timber.w(e, "Dispatcher: failed to query display name")
        null
    }
}
