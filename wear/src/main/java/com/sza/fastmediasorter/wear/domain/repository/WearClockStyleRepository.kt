package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import kotlinx.coroutines.flow.Flow

/**
 * S3557: the last clock style the phone published, which the dim clock, the backdrop and the watch
 * face's style complication all read. Emits [WearClockStyle.DEFAULT] until the phone sends one.
 */
interface WearClockStyleRepository {
    val style: Flow<WearClockStyle>

    suspend fun save(style: WearClockStyle)
}
