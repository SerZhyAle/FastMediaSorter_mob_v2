package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage

/**
 * Lists one level of a browsable local source.
 *
 * S2201 goal 3 requires the browse surface to serve future local sources rather than the one screen it
 * ships with, so this contract names no storage mechanism: neither MediaStore nor the filesystem
 * appears in the signature. A second local source is added by supplying another implementation, and
 * the ViewModel that walks it does not change.
 *
 * The contract is deliberately one level at a time. S2130 §3.2 forbids a full storage walk on the
 * watch, and an interface that could express "list everything below here" would make violating that a
 * matter of which argument a caller passed.
 */
interface WearLocalFolderRepository {

    /**
     * The entries of [address], windowed from [offset].
     *
     * Failure is carried in the `Result` rather than thrown: a level that cannot be read - a directory
     * removed between two taps, a query the platform refuses - must still leave the walk on screen
     * with its trail intact.
     */
    suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage>
}
