package com.sza.fastmediasorter.wear.ui.player.common

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkBasePath
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.SOURCE_ID_STREAM
import com.sza.fastmediasorter.wear.domain.model.WearCastAttempt
import com.sza.fastmediasorter.wear.domain.model.WearCastMediaType
import com.sza.fastmediasorter.wear.domain.model.WearCastOutcome
import com.sza.fastmediasorter.wear.domain.model.WearCastState
import com.sza.fastmediasorter.wear.domain.model.WearCastSubject
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.WearCastRepository
import com.sza.fastmediasorter.wear.domain.usecase.RequestCastOnPhoneUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2531: the cast entry the three player screens share - one copy, because the three would drift.
 *
 * Holds no session of its own. The phone owns the broadcast and reports it, so [castState] is what
 * arrived over the bridge and [message] is the last answer put into words (strategic ADR-1).
 */
class PlayerCastManager @Inject constructor(
    private val requestCastOnPhone: RequestCastOnPhoneUseCase,
    private val wearCastRepository: WearCastRepository,
    private val networkSourceRepository: NetworkSourceRepository
) {

    private lateinit var scope: CoroutineScope

    private val _message = MutableStateFlow<Int?>(null)

    /** String resource of the last answer, or null when there is nothing to say. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    val castState: StateFlow<WearCastState> get() = wearCastRepository.castState

    private var requestJob: Job? = null

    fun bind(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Asks the phone to show [file] on the receiver it is connected to.
     *
     * [selection] is what the browse screen published for this file; a null one is watch-local by
     * definition, because every network origin on this watch publishes one.
     */
    fun castCurrentFile(file: WearMediaFile, selection: SelectedMedia?, mediaType: WearCastMediaType) {
        Timber.d("S2531: watch asked the phone to cast, type $mediaType, network=${selection?.isNetworkSource}")
        launchRequest { subjectFor(file, selection, mediaType) }
    }

    fun stopCasting() {
        Timber.d("S2531: watch asked the phone to stop casting")
        requestJob?.cancel()
        requestJob = scope.launch {
            _message.value = wearCastRepository.requestStop().toMessage()
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun launchRequest(subject: suspend () -> WearCastSubject) {
        requestJob?.cancel()
        requestJob = scope.launch {
            _message.value = when (val attempt = requestCastOnPhone(subject())) {
                is WearCastAttempt.Answered -> attempt.outcome.toMessage()
                WearCastAttempt.NotCastable -> R.string.wear_cast_not_castable
            }
        }
    }

    private suspend fun subjectFor(
        file: WearMediaFile,
        selection: SelectedMedia?,
        mediaType: WearCastMediaType
    ): WearCastSubject {
        val sourceId = selection?.takeIf { it.isNetworkSource }?.sourceId
            ?: return WearCastSubject.WatchLocalFile(file.name)
        // A stream carries the same handover shape as a share, and its "source id" is the sentinel the
        // favourites already address one by - its address is the public URL and needs no source at all.
        if (sourceId == SOURCE_ID_STREAM) {
            return WearCastSubject.Stream(
                url = selection.streamUri,
                displayName = file.name,
                mediaType = mediaType
            )
        }
        val source = networkSourceRepository.getSourceById(sourceId)
            ?: return WearCastSubject.WatchLocalFile(file.name)
        return WearCastSubject.NetworkFile(
            source = source,
            relativePath = relativePathOf(selection.streamUri, source),
            displayName = file.name,
            mediaType = mediaType
        )
    }

    /**
     * Both the file's address and the source root are reduced by the same normaliser before one is
     * subtracted from the other: the phone stores a source as a full URL and this watch stores the
     * part below the connection it opened, so subtracting the two raw strings would leave the share
     * name or a port in the path and the phone would rebuild an address that resolves to nothing.
     */
    private fun relativePathOf(
        streamUri: String,
        source: NetworkSource
    ): String {
        val filePath = NetworkBasePath.normalize(streamUri, source.type, source.shareName)
        val rootPath = NetworkBasePath.normalize(source.basePath, source.type, source.shareName)
        val root = rootPath.trim('/')
        val relative = filePath.trim('/')
        return if (root.isNotEmpty() && relative.startsWith("$root/")) {
            relative.removePrefix("$root/")
        } else {
            relative
        }
    }

    private fun WearCastOutcome.toMessage(): Int = when (this) {
        WearCastOutcome.CASTING -> R.string.wear_cast_casting
        WearCastOutcome.STOPPED -> R.string.wear_cast_stopped
        WearCastOutcome.PICKER_NEEDED -> R.string.wear_cast_picker_needed
        WearCastOutcome.CAST_UNAVAILABLE -> R.string.wear_cast_unavailable
        WearCastOutcome.UNSUPPORTED_CONTENT -> R.string.wear_cast_unsupported
        WearCastOutcome.NOT_FOUND -> R.string.wear_cast_not_found
        WearCastOutcome.PHONE_BUSY -> R.string.wear_cast_phone_busy
    }
}
