package com.sza.fastmediasorter.data.tourist

import com.sza.fastmediasorter.domain.model.tourist.WatchHealthReading
import com.sza.fastmediasorter.domain.repository.WatchHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** S4150: process memory only - a reading is worthless a minute later, so nothing is persisted. */
@Singleton
class WatchHealthRepositoryImpl @Inject constructor() : WatchHealthRepository {

    private val state = MutableStateFlow<WatchHealthReading?>(null)

    override val latest: StateFlow<WatchHealthReading?> = state.asStateFlow()

    override fun publish(reading: WatchHealthReading) {
        state.value = reading
    }
}
