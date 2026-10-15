package com.sza.fastmediasorter.ui.browse.transfer

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTarget
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.OwnDriveDevice
import com.sza.fastmediasorter.domain.usecase.transfer.GetOwnDriveDevicesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** S3040: how far a "send to my device" upload has got, for the surface that started it. */
sealed interface SendToDeviceState {

    data object Idle : SendToDeviceState

    data class Uploading(val fileName: String) : SendToDeviceState

    data class Sent(val fileName: String) : SendToDeviceState

    data class Failed(val fileName: String) : SendToDeviceState
}

/**
 * S3040: packages one local file into a cross-device packet and reports the upload's progress.
 *
 * Only a file the device can read byte for byte is sent: a cloud entry is already in a cloud the
 * other device can reach, so copying it through the transfer queue would spend the user's quota
 * twice for nothing.
 *
 * S4231: the file goes as a version 2 `FILES` packet through the Drive channel, to one own device or to
 * any of them; success means it waits in the queue, never that it was delivered (contract item CT).
 */
@Singleton
class SendToDeviceActionManager @Inject constructor(
    private val channel: DriveFileChannel,
    private val settings: DriveChannelSettings,
    private val getOwnDevices: GetOwnDriveDevicesUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val _state = MutableStateFlow<SendToDeviceState>(SendToDeviceState.Idle)
    val state: StateFlow<SendToDeviceState> = _state.asStateFlow()

    suspend fun isChannelOn(): Boolean = settings.enabled.first()

    /** The devices a file can be sent to; empty when no other own device ever wrote its record. */
    suspend fun targets(): Result<List<OwnDriveDevice>> = getOwnDevices()

    suspend fun send(
        filePath: String,
        target: CrossDeviceTarget? = null
    ): Result<Unit> = withContext(ioDispatcher) {
        Timber.d("S4231: send a file via Google Drive")
        val file = File(filePath)
        val fileName = file.name
        if (!file.isFile || !file.canRead()) {
            _state.value = SendToDeviceState.Failed(fileName)
            return@withContext Result.failure(IllegalArgumentException("$filePath is not a readable local file"))
        }
        _state.value = SendToDeviceState.Uploading(fileName)
        // Streamed from disk, so the size limit is the packet limit, not the heap.
        val files = listOf(OutgoingPacketFile(name = fileName, size = file.length(), open = file::inputStream))
        channel.sendFiles(target, files, CrossDevicePayloadKind.FILES).fold(
            onSuccess = {
                _state.value = SendToDeviceState.Sent(fileName)
                Result.success(Unit)
            },
            onFailure = { error ->
                _state.value = SendToDeviceState.Failed(fileName)
                Result.failure(error)
            }
        )
    }

    /** Return to idle once the surface has shown the last outcome. */
    fun consumeState() {
        _state.value = SendToDeviceState.Idle
    }
}
