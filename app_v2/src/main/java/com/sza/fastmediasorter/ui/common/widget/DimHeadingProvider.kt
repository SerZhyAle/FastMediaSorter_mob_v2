package com.sza.fastmediasorter.ui.common.widget

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveCompassUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3370: a synchronous heading lookup for the dim overlay's spark pair.
 *
 * The compass flow is collected only while a dim surface is up - [setActive] pairs with the
 * overlay's show/hide, so nothing collects while the dim screen is idle (strategic §3.2).
 * [current] is read on the main thread at tap time; the cache is `@Volatile` because the
 * collector runs on the application scope's IO dispatcher.
 */
@Singleton
class DimHeadingProvider @Inject constructor(
    private val observeCompass: ObserveCompassUseCase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {

    private var collectionJob: Job? = null

    @Volatile
    private var latest: CompassReading? = null

    fun setActive(active: Boolean) {
        if (active) {
            if (collectionJob?.isActive == true) return
            latest = null
            collectionJob = appScope.launch {
                observeCompass().collect { reading ->
                    if (reading.accuracy != SensorAccuracy.UNRELIABLE) latest = reading
                }
            }
        } else {
            collectionJob?.cancel()
            collectionJob = null
            latest = null
        }
    }

    fun current(): CompassReading? = latest
}
