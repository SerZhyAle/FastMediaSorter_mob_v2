package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.PlaybackException
import com.bumptech.glide.load.engine.GlideException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.player.ImageLoadingDiagnostics
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class SlideshowResourceAvailabilityManager(
    private val activity: PlayerActivity,
    private val networkStateMonitor: NetworkStateMonitor,
    lifecycleScope: LifecycleCoroutineScope
) : NetworkStateMonitor.NetworkChangeCallback {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var imageFailureStreak = 0
    private var playbackFailureStreak = 0
    private var lastPlaybackReadyAtMs: Long? = null
    private var isNetworkCallbackRegistered = false

    init {
        lifecycleScope.launch {
            activity.viewModel.state
                .map { it.isSlideShowActive }
                .distinctUntilChanged()
                .collect { isActive ->
                    if (isActive) {
                        resetFailureTracking("slideshow activated")
                    } else {
                        lastPlaybackReadyAtMs = null
                    }
                    syncNetworkCallbackRegistration()
                }
        }

        lifecycleScope.launch {
            activity.viewModel.state
                .map { it.resource?.id }
                .distinctUntilChanged()
                .collect {
                    resetFailureTracking("resource changed")
                    syncNetworkCallbackRegistration()
                }
        }
    }

    fun cleanup() {
        if (isNetworkCallbackRegistered) {
            networkStateMonitor.unregisterCallback(this)
            isNetworkCallbackRegistered = false
        }
    }

    fun onManualNavigation() {
        resetFailureTracking("manual navigation")
    }

    fun onImageLoadSucceeded() {
        resetFailureTracking("image load succeeded")
    }

    fun onPlaybackReady() {
        resetFailureTracking("playback ready")
        lastPlaybackReadyAtMs = SystemClock.elapsedRealtime()
    }

    fun handleImageLoadFailure(throwable: Throwable?): Boolean {
        if (!activity.viewModel.state.value.isSlideShowActive) return false
        if (!isLikelyUnavailableImageFailure(throwable)) return false

        imageFailureStreak += 1
        Timber.w(
            "S0188: image failure streak=%d resource=%s file=%s",
            imageFailureStreak,
            currentResourceLabel(),
            activity.viewModel.state.value.currentFile?.path
        )
        return imageFailureStreak >= FAILURE_THRESHOLD && stopForUnavailableResource()
    }

    fun handlePlaybackError(throwable: Throwable?, userMessage: String? = null): Boolean {
        if (!shouldTrackPlaybackFailures()) return false
        if (!isLikelyUnavailablePlaybackFailure(throwable, userMessage)) return false

        playbackFailureStreak += 1
        lastPlaybackReadyAtMs = null
        Timber.w(
            "S0188: playback failure streak=%d resource=%s file=%s",
            playbackFailureStreak,
            currentResourceLabel(),
            activity.viewModel.state.value.currentFile?.path
        )
        return playbackFailureStreak >= FAILURE_THRESHOLD && stopForUnavailableResource()
    }

    fun handlePlaybackEnded(): Boolean {
        if (!shouldTrackPlaybackFailures()) return false

        val currentFile = activity.viewModel.state.value.currentFile ?: return false
        if (currentFile.duration != null && currentFile.duration <= QUICK_END_THRESHOLD_MS) {
            resetFailureTracking("short media ended normally")
            return false
        }

        val readyAtMs = lastPlaybackReadyAtMs
        lastPlaybackReadyAtMs = null
        val quickEnd = readyAtMs == null ||
            SystemClock.elapsedRealtime() - readyAtMs < QUICK_END_THRESHOLD_MS
        if (!quickEnd) {
            resetFailureTracking("media ended after stable playback")
            return false
        }

        playbackFailureStreak += 1
        Timber.w(
            "S0188: quick-end streak=%d resource=%s file=%s readyAt=%s",
            playbackFailureStreak,
            currentResourceLabel(),
            currentFile.path,
            readyAtMs?.toString() ?: "<none>"
        )
        return playbackFailureStreak >= FAILURE_THRESHOLD && stopForUnavailableResource()
    }

    override fun onNetworkChanged() {
        syncNetworkCallbackRegistration()
    }

    override fun onNetworkLost() {
        if (!activity.viewModel.state.value.isSlideShowActive || !isRemoteResource()) return

        mainHandler.post {
            if (!activity.viewModel.state.value.isSlideShowActive || !isRemoteResource()) return@post
            Timber.w("S0188: network lost while slideshow is active for %s", currentResourceLabel())
            activity.stopSlideshowDueToResourceIssue(
                activity.getString(
                    R.string.slideshow_stopped_connection_lost,
                    currentResourceLabel()
                )
            )
            resetFailureTracking("network lost")
        }
    }

    private fun stopForUnavailableResource(): Boolean {
        val stopMessage = activity.getString(
            R.string.slideshow_stopped_resource_unavailable,
            currentResourceLabel()
        )

        mainHandler.post {
            activity.stopSlideshowDueToResourceIssue(stopMessage)
            resetFailureTracking("slideshow stopped due to unavailable resource")
        }
        return true
    }

    private fun shouldTrackPlaybackFailures(): Boolean {
        val state = activity.viewModel.state.value
        val mediaType = state.currentFile?.type
        return state.isSlideShowActive &&
            state.playToEndInSlideshow &&
            (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO)
    }

    private fun isLikelyUnavailableImageFailure(throwable: Throwable?): Boolean {
        if (isLocalFileMissing()) return true
        if (!isRemoteResource()) return false

        val glideException = throwable as? GlideException
        if (glideException != null && ImageLoadingDiagnostics.isNonCriticalNetworkImageError(glideException)) {
            return false
        }

        val diagnosticText = collectDiagnosticText(throwable)
        return diagnosticText.any { candidate -> UNAVAILABLE_MARKERS.any(candidate::contains) }
    }

    private fun isLikelyUnavailablePlaybackFailure(
        throwable: Throwable?,
        userMessage: String?
    ): Boolean {
        if (isLocalFileMissing()) return true

        val playbackException = throwable as? PlaybackException
        if (playbackException != null) {
            return playbackException.errorCode in PLAYBACK_UNAVAILABLE_ERROR_CODES
        }

        if (!isRemoteResource()) return false

        val diagnosticText = collectDiagnosticText(throwable, userMessage)
        return diagnosticText.any { candidate -> UNAVAILABLE_MARKERS.any(candidate::contains) }
    }

    private fun collectDiagnosticText(
        throwable: Throwable?,
        extraMessage: String? = null
    ): List<String> {
        val values = mutableListOf<String>()
        if (!extraMessage.isNullOrBlank()) values += extraMessage.lowercase()

        fun visit(error: Throwable?) {
            if (error == null) return
            values += error.javaClass.name.lowercase()
            error.message?.takeIf { it.isNotBlank() }?.let { values += it.lowercase() }
            if (error is GlideException) {
                error.rootCauses.forEach(::visit)
            }
            visit(error.cause)
        }

        visit(throwable)
        return values
    }

    private fun syncNetworkCallbackRegistration() {
        val shouldRegister = activity.viewModel.state.value.isSlideShowActive && isRemoteResource()
        if (shouldRegister == isNetworkCallbackRegistered) return

        if (shouldRegister) {
            networkStateMonitor.registerCallback(this)
        } else {
            networkStateMonitor.unregisterCallback(this)
        }
        isNetworkCallbackRegistered = shouldRegister
    }

    private fun isRemoteResource(): Boolean =
        activity.viewModel.state.value.resource?.type?.isNetworkResource == true

    private fun isLocalFileMissing(): Boolean {
        val currentFile = activity.viewModel.state.value.currentFile ?: return false
        val currentResource = activity.viewModel.state.value.resource
        if (currentResource?.type != ResourceType.LOCAL) return false
        if (currentFile.path.startsWith("content://")) return false
        return currentFile.path.startsWith("/") && !File(currentFile.path).exists()
    }

    private fun currentResourceLabel(): String {
        val state = activity.viewModel.state.value
        return state.resource?.name ?: state.currentFile?.name ?: activity.getString(R.string.app_name)
    }

    private fun resetFailureTracking(reason: String) {
        if (imageFailureStreak == 0 && playbackFailureStreak == 0 && lastPlaybackReadyAtMs == null) return
        Timber.d(
            "S0188: reset failure tracking (%s) image=%d playback=%d",
            reason,
            imageFailureStreak,
            playbackFailureStreak
        )
        imageFailureStreak = 0
        playbackFailureStreak = 0
        lastPlaybackReadyAtMs = null
    }

    companion object {
        private const val FAILURE_THRESHOLD = 3
        private const val QUICK_END_THRESHOLD_MS = 5_000L

        private val PLAYBACK_UNAVAILABLE_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_TIMEOUT
        )

        // The network/image loaders often wrap transport failures into generic IO text, so
        // classification must rely on conservative transport/auth markers, not exception types.
        private val UNAVAILABLE_MARKERS = listOf(
            "connect",
            "connection",
            "socket",
            "timeout",
            "timed out",
            "unknownhost",
            "unreachable",
            "reset by peer",
            "broken pipe",
            "failed to load network file",
            "authentication",
            "access token",
            "unauthorized",
            "forbidden",
            "http 401",
            "http 403",
            "http 404"
        )
    }
}