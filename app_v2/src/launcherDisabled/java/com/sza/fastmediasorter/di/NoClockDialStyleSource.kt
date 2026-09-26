package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.repository.ClockDialStyle
import com.sza.fastmediasorter.domain.repository.ClockDialStyleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/** S3557: a flavor without a launcher has no clock dial, so nothing is ever published to the watch face. */
class NoClockDialStyleSource @Inject constructor() : ClockDialStyleSource {

    override fun observe(): Flow<ClockDialStyle> = emptyFlow()
}
