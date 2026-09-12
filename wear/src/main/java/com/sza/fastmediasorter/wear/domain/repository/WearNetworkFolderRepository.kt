package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage

/**
 * Lists one level of a network source's directory tree (S2694).
 *
 * This is a second contract beside [WearLocalFolderRepository] rather than another implementation of
 * it, and deliberately so: that one's own documentation promises a *local* browsable source, and a
 * network implementation behind it would leave a false promise in the codebase for the next reader.
 * The walk reaches both through one dispatcher, which is where the choice is made.
 *
 * One level at a time, like the local contract, and for the same reason: S2130 forbids a full storage
 * walk on the watch, and over a network the cost of violating it is paid in radio time as well.
 */
interface WearNetworkFolderRepository {

    /**
     * The entries of [address], windowed from [offset].
     *
     * Failure is carried in the `Result` rather than thrown: a share that dropped between two taps,
     * or a directory removed under the wearer, must still leave the walk on screen with its trail
     * intact rather than replace it with an error.
     */
    suspend fun listLevel(address: WearFolderAddress.NetworkLevel, offset: Int): Result<WearFolderPage>
}
