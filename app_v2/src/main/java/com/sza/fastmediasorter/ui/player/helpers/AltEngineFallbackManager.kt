package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.view.ViewGroup
import android.view.ViewStub
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.playback.AltPlaybackEngine
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1060: manages fallback to alternative playback engines (e.g. libVLC) when primary player fails.
 */
@Singleton
class AltEngineFallbackManager @Inject constructor(
    private val engines: Set<@JvmSuppressWildcards AltPlaybackEngine>
) {

    private var activeEngine: AltPlaybackEngine? = null

    // S3157: this manager is @Singleton for its process-wide engine set, so both UI-bound fields are held
    // weakly - release() still clears them from PlayerActivity.onDestroy, and the weak reference only
    // decides what happens when a teardown path is missed. Both stay strongly reachable while the activity
    // lives: the container sits in the view tree after inflate(), and the stub is a ViewBinding field.
    private var inflatedContainer: WeakReference<ViewGroup>? = null
    private var viewStub: WeakReference<ViewStub>? = null

    val isFallbackActive: Boolean
        get() = activeEngine != null

    val isPlaying: Boolean
        get() = activeEngine?.isPlaying == true

    val positionMs: Long
        get() = activeEngine?.positionMs ?: 0L

    val durationMs: Long
        get() = activeEngine?.durationMs ?: 0L

    fun bindViewStub(stub: ViewStub?) {
        this.viewStub = stub?.let(::WeakReference)
    }

    fun canFallback(file: MediaFile): Boolean {
        return findEngineFor(file) != null
    }

    /**
     * S1971: the set a user would have to install for [file] to become playable, or null when no engine
     * is merely waiting on a download.
     *
     * Answers the case [canFallback] cannot: an engine that handles this kind of file but whose native
     * payload is not on the device yet. Without it that engine is indistinguishable from "no engine at
     * all", and the user meets a plain playback error for a file the app could play after a download.
     */
    fun pendingInstallSetFor(file: MediaFile): DeliverableSet? =
        engines.firstOrNull {
            it.engineId != NOOP_ENGINE_ID &&
                it.requiredDeliverableSet != null &&
                !it.canPlay(file) &&
                it.couldPlay(file)
        }?.requiredDeliverableSet

    fun tryFallback(
        file: MediaFile,
        uri: Uri,
        startPositionMs: Long,
        stubOverride: ViewStub? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        val targetStub = stubOverride ?: viewStub?.get()
        val engine = findEngineFor(file)
        if (engine == null) {
            return false
        }

        return performFallback(engine, uri, startPositionMs, targetStub, onSuccess, onError)
    }

    private fun performFallback(
        engine: AltPlaybackEngine,
        uri: Uri,
        startPositionMs: Long,
        targetStub: ViewStub?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        return try {
            releaseActiveEngine()

            val container = inflatedContainer?.get() ?: targetStub?.inflate() as? ViewGroup
            if (container == null) {
                Timber.w("AltEngineFallbackManager: failed to inflate ViewStub")
                false
            } else {
                inflatedContainer = WeakReference(container)
                Timber.d("S3157: alt-engine container resolved through the weakly held stub")

                engine.setListener(object : AltPlaybackEngine.Listener {
                    override fun onEnded() {
                        Timber.d("AltEngineFallbackManager: engine playback ended")
                    }

                    override fun onError(message: String) {
                        Timber.e("AltEngineFallbackManager: engine error: %s", message)
                        onError(message)
                    }
                })

                engine.attach(container)
                engine.play(uri, startPositionMs)
                activeEngine = engine
                Timber.d("AltEngineFallbackManager: fallback activated with engine %s", engine.engineId)
                onSuccess()
                true
            }
        } catch (e: IllegalStateException) {
            Timber.e(e, "AltEngineFallbackManager: illegal state during fallback attempt")
            releaseActiveEngine()
            false
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "AltEngineFallbackManager: illegal argument during fallback attempt")
            releaseActiveEngine()
            false
        }
    }

    fun pause() {
        activeEngine?.pause()
    }

    fun resume() {
        activeEngine?.resume()
    }

    fun seekTo(positionMs: Long) {
        activeEngine?.seekTo(positionMs)
    }

    fun release() {
        releaseActiveEngine()
        inflatedContainer = null
        viewStub = null
    }

    private fun releaseActiveEngine() {
        activeEngine?.let { engine ->
            Timber.d("AltEngineFallbackManager: releasing active engine %s", engine.engineId)
            try {
                engine.setListener(null)
                engine.release()
            } catch (e: IllegalStateException) {
                Timber.e(e, "AltEngineFallbackManager: error releasing engine %s", engine.engineId)
            }
            activeEngine = null
        }
    }

    private fun findEngineFor(file: MediaFile): AltPlaybackEngine? {
        return engines.firstOrNull { it.engineId != NOOP_ENGINE_ID && it.canPlay(file) }
    }

    companion object {
        private const val NOOP_ENGINE_ID = "noop"
    }
}
