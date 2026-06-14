package com.sza.fastmediasorter.ui.player.helpers

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber

/** Debug-only `BuildConfig.DEBUG` launch-conditions dump for [com.sza.fastmediasorter.ui.player.StandalonePlayerActivity]. */
internal object StandaloneLaunchDebugLogger {

    private val DEFAULT_PLAYER_COMPONENT_SUFFIXES = listOf(
        ".StandaloneAudioPlayer",
        ".StandaloneVideoPlayer",
        ".StandaloneImagePlayer",
        ".StandaloneDocsPlayer",
        ".StandaloneAudioSender",
        ".StandaloneVideoSender",
        ".StandaloneImageSender",
    )

    fun log(activity: ComponentActivity, incomingIntent: Intent?, supportsCloud: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (incomingIntent == null) {
            Timber.d("StandalonePlayer[debug]: launch intent is null")
            return
        }

        val categories = incomingIntent.categories?.joinToString(",") ?: "(none)"
        val clipCount = incomingIntent.clipData?.itemCount ?: 0
        val streamCount = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.size ?: 0
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.size ?: 0
            }
        }.getOrDefault(0)

        val resolvedUri = resolveIncomingUri(incomingIntent)

        val persistedGrant = resolvedUri?.let { uri ->
            activity.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        } ?: false
        val runtimeReadGrant = resolvedUri?.let { uri ->
            activity.checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
        } ?: false

        val aliasStates = DEFAULT_PLAYER_COMPONENT_SUFFIXES.joinToString(", ") { suffix ->
            val componentName = "${activity.packageName}$suffix"
            val stateLabel = try {
                when (activity.packageManager.getComponentEnabledSetting(ComponentName(activity.packageName, componentName))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
                    else -> "UNKNOWN"
                }
            } catch (e: Exception) {
                "ERROR:${e.javaClass.simpleName}"
            }
            "$suffix=$stateLabel"
        }

        Timber.i(
            "StandalonePlayer[debug]: launch action=%s component=%s categories=%s flags=0x%s type=%s hasData=%s clipItems=%d extraStreams=%d caller=%s referrer=%s",
            incomingIntent.action,
            incomingIntent.component?.className,
            categories,
            Integer.toHexString(incomingIntent.flags),
            incomingIntent.type,
            incomingIntent.data != null,
            clipCount,
            streamCount,
            activity.callingActivity?.flattenToShortString(),
            activity.referrer?.toString(),
        )
        Timber.i(
            "StandalonePlayer[debug]: uri=%s scheme=%s authority=%s readGrant=%s persistedReadGrant=%s",
            resolvedUri,
            resolvedUri?.scheme,
            resolvedUri?.authority,
            runtimeReadGrant,
            persistedGrant,
        )
        Timber.i(
            "StandalonePlayer[debug]: build debug=%s type=%s flavor=%s supportsDefaultPlayer=%s support(video=%s,audio=%s,images=%s,docs=%s,cloud=%s)",
            BuildConfig.DEBUG,
            BuildConfig.BUILD_TYPE,
            BuildConfig.FLAVOR,
            BuildConfig.SUPPORTS_DEFAULT_PLAYER,
            BuildConfig.SUPPORT_VIDEO,
            BuildConfig.SUPPORT_AUDIO,
            BuildConfig.SUPPORT_IMAGES,
            BuildConfig.SUPPORT_DOCUMENTS,
            // S0391: compile-tier cloud flag now comes from MediaCapabilities (Rule 14 migration).
            supportsCloud,
        )
        Timber.i("StandalonePlayer[debug]: default-player components: %s", aliasStates)
    }

    private fun resolveIncomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
        }
        else -> intent.data
    }
}
