package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearFileNameConflictResolver
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.model.kind
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Runs one requested operation over a selection, one file at a time, reporting each separately.
 *
 * A batch is expected to succeed partly, so nothing here collapses the run into a single verdict.
 * Anything leaving the watch goes through the S1861 sender that S1862 already calls - strategic
 * ADR-1 keeps that one transport, so this class adds a runner over it and never a second channel.
 */
class PerformWearFileOperationUseCase @Inject constructor(
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val senderRepository: WearFileSenderRepository,
    private val openOnPhoneRepository: WearOpenOnPhoneRepository,
    private val stager: WearMediaFileStager
) {

    operator fun invoke(
        files: List<WearMediaFile>,
        operation: WearFileOperation,
        isNetworkSource: Boolean
    ): Flow<WearFileOperationResult> = flow {
        for (file in files) {
            emit(runOne(file, operation, isNetworkSource))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun runOne(
        file: WearMediaFile,
        operation: WearFileOperation,
        isNetworkSource: Boolean
    ): WearFileOperationResult {
        val storageClass = capabilityPolicy.classify(file, isNetworkSource)
        if (operation.kind() !in capabilityPolicy.allowedOperations(storageClass)) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
        return when (operation) {
            WearFileOperation.SendToPhone -> sendToPhone(file, deleteSource = false)
            WearFileOperation.MoveToPhone -> sendToPhone(file, deleteSource = true)
            WearFileOperation.Delete -> deleteLocal(file)
            is WearFileOperation.Rename -> renameLocal(file, operation.newName)
            is WearFileOperation.OpenOnPhone -> openOnPhone(file, operation.token)
        }
    }

    /**
     * The one operation that stages nothing: the phone already holds the file this watch copy came
     * from, so the request carries its address and the answer says what the phone did with it.
     */
    private suspend fun openOnPhone(file: WearMediaFile, token: String): WearFileOperationResult {
        Timber.d("S2004: watch asks the phone to open %s", file.name)
        val outcome = openOnPhoneRepository.requestOpen(
            WearOpenOnPhoneRequest(token = token, displayName = file.name)
        )
        return WearFileOperationResult(file.name, outcome.toOperationOutcome())
    }

    private suspend fun sendToPhone(file: WearMediaFile, deleteSource: Boolean): WearFileOperationResult {
        if (file.size > WEAR_FILE_TRANSFER_MAX_BYTES) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_TOO_LARGE)
        }
        val staged = stager.stage(file)
        return if (staged == null) {
            WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        } else {
            deliver(file, staged, deleteSource)
        }
    }

    /**
     * The source is removed only against a confirmed [WearFileSendOutcome.SENT]; every other answer
     * leaves it where it is. Reporting a move that deleted a file the phone never received is the
     * one failure strategic §7 rates as losing data outright.
     */
    private suspend fun deliver(
        file: WearMediaFile,
        staged: File,
        deleteSource: Boolean
    ): WearFileOperationResult = try {
        val outcome = senderRepository.sendFile(staged)
        if (deleteSource && outcome == WearFileSendOutcome.SENT) {
            stager.localFileOf(file)?.delete()
        }
        WearFileOperationResult(file.name, outcome.toOperationOutcome())
    } finally {
        // Also on cancellation: an abandoned run must not leave the copy behind in the cache.
        stager.discard(staged, file)
    }

    private fun deleteLocal(file: WearMediaFile): WearFileOperationResult {
        val target = stager.localFileOf(file)
        val deleted = target != null && target.delete()
        return WearFileOperationResult(
            file.name,
            if (deleted) WearFileOperationOutcome.SUCCEEDED else WearFileOperationOutcome.FAILED
        )
    }

    private fun renameLocal(file: WearMediaFile, newName: String): WearFileOperationResult {
        val target = stager.localFileOf(file)
        val parent = target?.parentFile
        return if (target == null || parent == null) {
            WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        } else {
            applyRename(file, target, parent, newName)
        }
    }

    private fun applyRename(
        file: WearMediaFile,
        target: File,
        parent: File,
        newName: String
    ): WearFileOperationResult {
        val (resolvedName, suffixed) = WearFileNameConflictResolver.resolveLocal(parent, newName)
        val renamed = target.renameTo(File(parent, resolvedName))
        return WearFileOperationResult(
            fileName = file.name,
            outcome = if (renamed) WearFileOperationOutcome.SUCCEEDED else WearFileOperationOutcome.FAILED,
            // Surfaced only when the resolver had to move the name, so the user reads what actually landed.
            finalName = if (renamed && suffixed) resolvedName else null
        )
    }
}

private fun WearFileSendOutcome.toOperationOutcome(): WearFileOperationOutcome = when (this) {
    WearFileSendOutcome.SENT -> WearFileOperationOutcome.SUCCEEDED
    WearFileSendOutcome.TOO_LARGE -> WearFileOperationOutcome.REFUSED_TOO_LARGE
    WearFileSendOutcome.PHONE_UNREACHABLE -> WearFileOperationOutcome.PHONE_UNREACHABLE
    WearFileSendOutcome.FAILED -> WearFileOperationOutcome.FAILED
}

/**
 * The three answers the phone can give stay three answers here.
 *
 * Collapsing "shown" and "notified" would leave the user looking at a phone that shows nothing after
 * being told it was opened, and collapsing the refusal into silence is the failure strategic 11
 * criterion 9 names outright.
 */
private fun WearOpenOnPhoneOutcome?.toOperationOutcome(): WearFileOperationOutcome = when (this) {
    WearOpenOnPhoneOutcome.SHOWN -> WearFileOperationOutcome.OPENED_ON_PHONE
    WearOpenOnPhoneOutcome.NOTIFIED -> WearFileOperationOutcome.NOTIFIED_ON_PHONE
    WearOpenOnPhoneOutcome.REFUSED_NO_NOTIFICATION ->
        WearFileOperationOutcome.REFUSED_PHONE_NOTIFICATIONS_OFF
    WearOpenOnPhoneOutcome.NOT_FOUND -> WearFileOperationOutcome.FAILED
    // Nothing answered: the phone is out of range, asleep, or running an older companion that has no
    // twelfth path at all - all three read to the user as "bring the phone closer and try again".
    null -> WearFileOperationOutcome.PHONE_UNREACHABLE
}
