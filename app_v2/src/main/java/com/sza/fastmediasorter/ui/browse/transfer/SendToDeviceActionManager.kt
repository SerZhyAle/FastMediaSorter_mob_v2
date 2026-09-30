package com.sza.fastmediasorter.ui.browse.transfer

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
 */
@Singleton
class SendToDeviceActionManager @Inject constructor(
    private val sendPacket: SendCrossDevicePacketUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val _state = MutableStateFlow<SendToDeviceState>(SendToDeviceState.Idle)
    val state: StateFlow<SendToDeviceState> = _state.asStateFlow()

    suspend fun send(
        filePath: String,
        senderDeviceName: String,
        targetDeviceName: String? = null
    ): Result<Unit> = withContext(ioDispatcher) {
        val file = File(filePath)
        val fileName = file.name
        val refusal = when {
            !file.isFile || !file.canRead() -> "$filePath is not a readable local file"
            // The packet API takes the payload as one ByteArray, so the whole file lands in the heap.
            file.length() > MAX_PAYLOAD_BYTES -> "$filePath exceeds the $MAX_PAYLOAD_BYTES byte send limit"
            else -> null
        }
        if (refusal != null) {
            _state.value = SendToDeviceState.Failed(fileName)
            return@withContext Result.failure(IllegalArgumentException(refusal))
        }
        _state.value = SendToDeviceState.Uploading(fileName)
        sendPacket(
            payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
            senderDeviceName = senderDeviceName,
            targetDeviceName = targetDeviceName,
            payloadBytes = mapOf(fileName to file.readBytes())
        ).fold(
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

    internal companion object {
        const val MAX_PAYLOAD_BYTES: Long = 32L * 1024 * 1024
    }
}
