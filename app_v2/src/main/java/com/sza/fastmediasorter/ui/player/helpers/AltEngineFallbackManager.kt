package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import android.view.ViewGroup
import android.view.ViewStub
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.playback.AltPlaybackEngine
import timber.log.Timber
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
    private var inflatedContainer: ViewGroup? = null
    private var viewStub: ViewStub? = null

    val isFallbackActive: Boolean
        get() = activeEngine != null

    val isPlaying: Boolean
        get() = activeEngine?.isPlaying == true

    val positionMs: Long
        get() = activeEngine?.positionMs ?: 0L

    val durationMs: Long
        get() = activeEngine?.durationMs ?: 0L

    fun bindViewStub(stub: ViewStub?) {
        this.viewStub = stub
    }

    fun canFallback(file: MediaFile): Boolean {
        return findEngineFor(file) != null
    }

    fun tryFallback(
        file: MediaFile,
        uri: Uri,
        startPositionMs: Long,
        stubOverride: ViewStub? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        val targetStub = stubOverride ?: viewStub
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

            val container = inflatedContainer ?: targetStub?.inflate() as? ViewGroup
            if (container == null) {
                Timber.w("AltEngineFallbackManager: failed to inflate ViewStub")
                false
            } else {
                inflatedContainer = container

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
