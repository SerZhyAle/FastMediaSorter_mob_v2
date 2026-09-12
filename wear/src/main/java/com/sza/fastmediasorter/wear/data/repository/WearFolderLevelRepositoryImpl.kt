package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import com.sza.fastmediasorter.wear.domain.model.WearFolderPage
import com.sza.fastmediasorter.wear.domain.repository.WearFolderLevelRepository
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkFolderRepository
import javax.inject.Inject

/**
 * S2694: routes a level to the repository that can list it.
 *
 * The network repository is held lazily on purpose. A walk over the watch's own storage must not
 * drag the protocol clients into its object graph merely because the two share a screen - that
 * would make every local walk pay for a share it never opens.
 */
class WearFolderLevelRepositoryImpl @Inject constructor(
    private val local: WearLocalFolderRepository,
    private val network: dagger.Lazy<WearNetworkFolderRepository>
) : WearFolderLevelRepository {

    override suspend fun listLevel(address: WearFolderAddress, offset: Int): Result<WearFolderPage> =
        when (address) {
            is WearFolderAddress.NetworkLevel -> network.get().listLevel(address, offset)
            else -> local.listLevel(address, offset)
        }
}
