package com.sza.fastmediasorter.wear.data.exchange

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sza.fastmediasorter.wear.domain.broadcast.WearBroadcastSessionStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton

/** Whether any activity of the app is started, from the process-wide activity callbacks. */
@Singleton
class WearAppVisibility @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var started = 0
    private val mutableVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    // Delivered on the main thread only, so the counter needs no lock.
    override fun onActivityStarted(activity: Activity) {
        started++
        mutableVisible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        mutableVisible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * When the watch holds its exchange control streams. Until the battery cost of several streams with the screen off
 * is measured on a watch, they live only for a reason: the app on screen (and a minute after, so a glance away
 * does not drop and redial them), a broadcast to announce, or an accepted cast whose `cast-stop` must be heard.
 */
@Singleton
class WearExchangePresence @Inject constructor(
    private val visibility: WearAppVisibility,
    private val broadcast: WearBroadcastSessionStateHolder,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun wanted(castPlaying: Flow<Boolean>): Flow<Boolean> {
        val lingeringVisible = visibility.visible
            .transformLatest { visible ->
                if (!visible) delay(VISIBLE_LINGER_MS)
                emit(visible)
            }
            .onStart { emit(false) }
        return combine(lingeringVisible, broadcast.state.map { it.isActive }, castPlaying, ::isWanted)
            .distinctUntilChanged()
    }

    companion object {
        const val VISIBLE_LINGER_MS = 60_000L

        fun isWanted(visible: Boolean, broadcasting: Boolean, castPlaying: Boolean): Boolean =
            visible || broadcasting || castPlaying
    }
}
