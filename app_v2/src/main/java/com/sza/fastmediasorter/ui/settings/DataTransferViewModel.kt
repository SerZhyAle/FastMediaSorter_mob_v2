package com.sza.fastmediasorter.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.IncompatibleTransferFile
import com.sza.fastmediasorter.domain.model.transfer.PreviewedKindNotAppliedHere
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferMedium
import com.sza.fastmediasorter.domain.model.transfer.TransferPayloadUnavailable
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import com.sza.fastmediasorter.domain.port.StagedFileTransferPort
import com.sza.fastmediasorter.domain.usecase.transfer.ApplyTransferPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.BuildTransferPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.GetTransferFileFromDriveUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.PutTransferFileToDriveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1565: why a transfer stopped, in the vocabulary the dialog turns into one sentence.
 *
 * An enum rather than a message string because the reason is decided off the UI thread, where no
 * `Context` is in reach, and strategic §3.2 requires the text to exist in EN, RU and UK.
 */
enum class TransferFailureReason {
    DRIVE_UNAVAILABLE,
    DRIVE_FILE_MISSING,
    INCOMPATIBLE_FILE,
    NOTHING_TO_EXPORT,
    DEVICE_FILE_UNREADABLE,
    DEVICE_FILE_UNWRITABLE,
    TRANSFER_FAILED
}

/** S1565: every outcome one transfer can reach, whatever its kind and medium. */
sealed interface DataTransferUiState {

    data object Idle : DataTransferUiState

    data class InProgress(
        val kind: TransferDataKind,
        val medium: TransferMedium
    ) : DataTransferUiState

    /** An export finished. There is no [TransferReport] to show - nothing was applied locally. */
    data class Exported(
        val kind: TransferDataKind,
        val medium: TransferMedium
    ) : DataTransferUiState

    data class Success(val report: TransferReport) : DataTransferUiState

    /**
     * The bytes are readable but this kind is applied through a preview the user answers, so they
     * are staged as a local document and handed back for the shipped import flow to open.
     */
    data class NeedsPreview(
        val kind: TransferDataKind,
        val source: Uri
    ) : DataTransferUiState

    data class Failure(
        val kind: TransferDataKind,
        val reason: TransferFailureReason
    ) : DataTransferUiState
}

/**
 * S1565: one entry point for every data kind, direction and medium.
 *
 * The medium is chosen after the bytes exist and before they are applied, so strategic §5.1 holds
 * by construction: a device file and a Google Drive file reach the same validation and the same
 * appliers. Nothing here knows a file format or a Drive verb - both live behind use cases.
 */
@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val buildTransferPayload: BuildTransferPayloadUseCase,
    private val applyTransferPayload: ApplyTransferPayloadUseCase,
    private val putTransferFileToDrive: PutTransferFileToDriveUseCase,
    private val getTransferFileFromDrive: GetTransferFileFromDriveUseCase,
    private val stagedFiles: StagedFileTransferPort
) : ViewModel() {

    private val _state = MutableStateFlow<DataTransferUiState>(DataTransferUiState.Idle)
    val state: StateFlow<DataTransferUiState> = _state.asStateFlow()

    // Touched only from the main dispatcher - `start` runs on the caller's thread and the removal
    // happens after `withContext` has come back - so a plain set needs no further synchronization.
    private val inFlight = mutableSetOf<TransferDataKind>()

    fun export(kind: TransferDataKind, medium: TransferMedium, target: Uri?) {
        start(kind, medium) { runExport(kind, medium, target) }
    }

    fun import(kind: TransferDataKind, medium: TransferMedium, source: Uri?) {
        start(kind, medium) { runImport(kind, medium, source) }
    }

    /** Return to [DataTransferUiState.Idle] once the user has read the outcome. */
    fun acknowledge() {
        _state.value = DataTransferUiState.Idle
    }

    private fun start(
        kind: TransferDataKind,
        medium: TransferMedium,
        block: suspend () -> DataTransferUiState
    ) {
        // Strategic §3.2 forbids a second operation on a data set already in flight.
        if (!inFlight.add(kind)) return
        Timber.d("S1565: transfer started for one kind and medium")
        _state.value = DataTransferUiState.InProgress(kind, medium)
        viewModelScope.launch {
            // The kind is released in `finally` so an escape nothing modelled as a Result - and a
            // cancellation - cannot leave it permanently in flight, which would refuse every retry.
            val outcome = try {
                withContext(Dispatchers.IO) { block() }
            } finally {
                inFlight.remove(kind)
            }
            _state.value = outcome
        }
    }

    private suspend fun runExport(
        kind: TransferDataKind,
        medium: TransferMedium,
        target: Uri?
    ): DataTransferUiState {
        val bytes = buildTransferPayload(kind).getOrElse { return failure(kind, it) }
        return when (medium) {
            TransferMedium.GOOGLE_DRIVE -> putTransferFileToDrive(kind, bytes)
                .fold({ DataTransferUiState.Exported(kind, medium) }, { failure(kind, it) })

            TransferMedium.DEVICE_FILE -> writeDeviceFile(kind, medium, target, bytes)
        }
    }

    private suspend fun writeDeviceFile(
        kind: TransferDataKind,
        medium: TransferMedium,
        target: Uri?,
        bytes: ByteArray
    ): DataTransferUiState {
        val written = target != null && stagedFiles.writeTo(target, bytes)
        return if (written) {
            DataTransferUiState.Exported(kind, medium)
        } else {
            DataTransferUiState.Failure(kind, TransferFailureReason.DEVICE_FILE_UNWRITABLE)
        }
    }

    private suspend fun runImport(
        kind: TransferDataKind,
        medium: TransferMedium,
        source: Uri?
    ): DataTransferUiState = when (medium) {
        TransferMedium.GOOGLE_DRIVE -> importFromDrive(kind)
        TransferMedium.DEVICE_FILE -> importFromDevice(kind, source)
    }

    private suspend fun importFromDrive(kind: TransferDataKind): DataTransferUiState {
        val bytes = getTransferFileFromDrive(kind).getOrElse { return failure(kind, it) }
        return if (bytes == null) {
            DataTransferUiState.Failure(kind, TransferFailureReason.DRIVE_FILE_MISSING)
        } else {
            applyBytes(kind, bytes)
        }
    }

    private suspend fun importFromDevice(
        kind: TransferDataKind,
        source: Uri?
    ): DataTransferUiState {
        val bytes = source?.let { stagedFiles.readFrom(it) }
        return if (bytes == null) {
            DataTransferUiState.Failure(kind, TransferFailureReason.DEVICE_FILE_UNREADABLE)
        } else {
            applyBytes(kind, bytes)
        }
    }

    private suspend fun applyBytes(
        kind: TransferDataKind,
        bytes: ByteArray
    ): DataTransferUiState {
        val applied = applyTransferPayload(kind, bytes)
        val error = applied.exceptionOrNull()
            ?: return DataTransferUiState.Success(applied.getOrThrow())
        return if (error is PreviewedKindNotAppliedHere) {
            stageForPreview(kind, bytes)
        } else {
            failure(kind, error)
        }
    }

    private suspend fun stageForPreview(
        kind: TransferDataKind,
        bytes: ByteArray
    ): DataTransferUiState {
        val staged = stagedFiles.stage(kind, bytes)
        return if (staged == null) {
            DataTransferUiState.Failure(kind, TransferFailureReason.DEVICE_FILE_UNREADABLE)
        } else {
            DataTransferUiState.NeedsPreview(kind, staged)
        }
    }

    private fun failure(kind: TransferDataKind, error: Throwable): DataTransferUiState.Failure {
        Timber.e(error, "Data transfer failed for %s", kind.name)
        return DataTransferUiState.Failure(kind, reasonOf(error))
    }

    private fun reasonOf(error: Throwable): TransferFailureReason = when (error) {
        is DriveTransferFailure -> TransferFailureReason.DRIVE_UNAVAILABLE
        is IncompatibleTransferFile -> TransferFailureReason.INCOMPATIBLE_FILE
        is TransferPayloadUnavailable -> TransferFailureReason.NOTHING_TO_EXPORT
        else -> TransferFailureReason.TRANSFER_FAILED
    }
}
