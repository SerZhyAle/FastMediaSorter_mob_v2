package com.sza.fastmediasorter.data.link.nolegal

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures the Chaquopy Python runtime is initialised exactly once per process.
 *
 * Double-checked locking guards against concurrent first-init. Once [FAILED], the state
 * is permanent - repeated crash attempts are avoided. Callers must treat [FAILED] as
 * NotApplicable and fall through to the next extraction strategy.
 */
@Singleton
class ChaquopyRuntimeHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private enum class State { UNINITIALIZED, READY, FAILED }

    @Volatile
    private var state: State = State.UNINITIALIZED

    /**
     * Ensures the Chaquopy Python runtime is initialised. Returns `true` if the runtime
     * is ready for use, `false` if initialisation has failed (permanent - no retry).
     */
    fun ensureInitialized(): Boolean {
        if (state == State.READY) return true
        if (state == State.FAILED) return false
        synchronized(this) {
            return when (state) {
                State.READY -> true
                State.FAILED -> false
                State.UNINITIALIZED -> runCatching {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(context))
                    }
                    state = State.READY
                    true
                }.getOrElse { error ->
                    Timber.e(error, "ChaquopyRuntimeHolder: init failed")
                    state = State.FAILED
                    false
                }
            }
        }
    }
}
