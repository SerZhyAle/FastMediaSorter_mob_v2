package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendReport

/**
 * S4230: the watch sends one of its own files to one of its own network resources, with no phone involved.
 *
 * The file is read as a stream from its address: there is no staging copy, no size ceiling of the phone bridge
 * and no phone reachability check, because no byte of it crosses the phone link (contract DEVICE-EXCHANGE 23
 * items CF and CH). The failure is a report, never an exception: a refusal is an expected answer.
 */
interface WearResourceSendRepository {

    suspend fun send(sourceId: String, file: WearMediaFile): WearResourceSendReport
}
