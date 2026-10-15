package com.sza.fastmediasorter.data.transfer.drive

import android.content.Context
import android.os.Build
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServedRoots
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerNode
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** S4231: what this device may serve for one requested resource (contract DEVICE-EXCHANGE item CO). */
sealed interface ServedResource {

    /** Not published on Drive, the server off, or a platform without the SAF server (below API 26). */
    data object Unavailable : ServedResource

    /** The root carries `protection`; Drive packets carry no PIN proof in this version. */
    data object PinRequired : ServedResource

    /** The roots the live server serves, the same namespace an SFTP client sees. */
    data class Served(val roots: List<SftpServerNode>) : ServedResource
}

/**
 * S4231: answers "may this device serve [ServedResource] for a resource id" from the same two facts the live
 * server stands on - the server's own settings and the resource record this device published on Drive.
 */
@Singleton
class DriveChannelServedResource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverSettings: SftpServerSettingsStore,
    private val records: GoogleDriveSftpRendezvousDataSource,
) {

    suspend fun resolve(resourceId: String, selfDeviceId: String): ServedResource {
        val config = serverSettings.snapshot()
        val record = if (config.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            records.snapshot().getOrNull()?.resources
                ?.firstOrNull { it.record.resourceId == resourceId && it.record.deviceId == selfDeviceId }
                ?.record
        } else {
            null
        }
        return when {
            record == null -> ServedResource.Unavailable
            record.pinProtected -> ServedResource.PinRequired
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                ServedResource.Served(SftpServedRoots.resolve(context, config.rootUris).map { it.second })
            else -> ServedResource.Unavailable
        }
    }
}
