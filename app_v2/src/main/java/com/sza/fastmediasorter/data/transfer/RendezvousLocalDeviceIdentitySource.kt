package com.sza.fastmediasorter.data.transfer

import android.os.Build
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4231: the id is the one the Drive device record carries; the name is `Build.MODEL` because the
 * device record and the shipped S3040 reader both use it, and that reader filters on the name.
 */
@Singleton
class RendezvousLocalDeviceIdentitySource @Inject constructor(
    private val identityStore: SftpRendezvousIdentityStore,
) : LocalDeviceIdentitySource {

    override suspend fun current(): LocalDeviceIdentity =
        LocalDeviceIdentity(deviceId = identityStore.deviceId(), displayName = Build.MODEL ?: FALLBACK_NAME)

    private companion object {
        const val FALLBACK_NAME = "Android"
    }
}
