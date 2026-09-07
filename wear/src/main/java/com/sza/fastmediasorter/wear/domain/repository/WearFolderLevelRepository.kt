package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage

/**
 * The one seam the folder walk lists through, whichever kind of level it is standing on (S2694).
 *
 * S2201 built the walk against the local contract directly, which was right while every level was
 * local. A network level is listed by a different contract, and the choice between them is a data
 * concern, not a screen one: the walk's trail, its windowing and its failure handling are identical
 * either way, and a view model that branched on the address kind would have to be told about every
 * future kind as well.
 *
 * A fourth kind of source is therefore a fourth implementation plus a branch in the dispatcher, and
 * no change here or in the view model.
 */
interface WearFolderLevelRepository {

    /** The entries of [address], windowed from [offset]; failure carried, never thrown. */
    suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage>
}
