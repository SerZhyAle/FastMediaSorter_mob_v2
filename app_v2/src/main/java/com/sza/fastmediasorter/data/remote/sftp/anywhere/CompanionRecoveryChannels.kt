package com.sza.fastmediasorter.data.remote.sftp.anywhere

import android.content.Context
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.CompanionRecoveryChannel
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which recovery channels cannot work right now for a companion resource (S4513). Both channels match the
 * PC by its pinned host key, so an unpinned resource has none to lose. A connection error that does not
 * name a dead channel let an owner wait a month for a reconnect neither channel could deliver.
 */
@Singleton
class CompanionRecoveryChannels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rendezvous: SftpRendezvousDirectory,
) {

    fun deadChannels(resource: MediaResource): List<CompanionRecoveryChannel> {
        if (resource.type != ResourceType.SFTP || resource.hostKeyFingerprint.isNullOrBlank()) return emptyList()
        return listOfNotNull(
            CompanionRecoveryChannel.LOCAL_NETWORK.takeUnless { PermissionHelper.hasLocalNetworkPermission(context) },
            CompanionRecoveryChannel.GOOGLE_DRIVE.takeIf { rendezvous.needsDriveSignIn },
        )
    }
}
