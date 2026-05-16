package com.sza.fastmediasorter.core.init

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-level first-frame gate used to defer non-critical startup work until the UI is visible.
 */
@Singleton
class FirstFrameSignal @Inject constructor() {

    private val fired = AtomicBoolean(false)
    private val state = MutableStateFlow(false)

    fun signal(): Boolean {
        val first = fired.compareAndSet(false, true)
        if (first) {
            state.value = true
            Timber.i("FirstFrameSignal: fired (uptime=%dms)", SystemClock.uptimeMillis())
        }
        return first
    }

    fun hasFired(): Boolean = fired.get()

    suspend fun await(timeoutMs: Long = 60_000) {
        if (fired.get()) return
        withTimeoutOrNull(timeoutMs) {
            state.filter { it }.first()
        }
    }
}