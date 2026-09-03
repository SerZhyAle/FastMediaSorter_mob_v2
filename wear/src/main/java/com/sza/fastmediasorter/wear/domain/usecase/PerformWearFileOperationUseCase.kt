package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Intent
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.data.files.WearSendToLauncher
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearFileNameConflictResolver
import com.sza.fastmediasorter.wear.domain.files.WearSendToReachability
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.model.kind
import com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** What a file with no declared type is offered as, so a receiver accepting anything still sees it. */
private const val ANY_MIME_TYPE = "*/*"

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
    private val stager: WearMediaFileStager,
    private val mediaStoreWriter: WearMediaStoreFileWriter,
    private val sendToReceivers: WearSendToReceiversRepository,
    private val reachability: WearSendToReachability,
    private val sendToLauncher: WearSendToLauncher
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
        Timber.d("S2142: file operation %s on a %s file", operation.kind(), storageClass)
        if (operation.kind() !in capabilityPolicy.allowedOperations(storageClass)) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
        return when (operation) {
            WearFileOperation.SendToPhone -> sendToPhone(file, storageClass, deleteSource = false)
            WearFileOperation.MoveToPhone -> sendToPhone(file, storageClass, deleteSource = true)
            WearFileOperation.Delete -> deleteLocal(file, storageClass)
            is WearFileOperation.Rename -> renameLocal(file, operation.newName, storageClass)
            is WearFileOperation.OpenOnPhone -> openOnPhone(file, operation.token)
            is WearFileOperation.SendToReceiver -> sendToReceiver(file, operation.receiverId)
        }
    }

    /**
     * S2142: hands the file to one receiver, on whichever side actually serves it.
     *
     * The fork is read off the receiver's own declaration and then re-checked here, because
     * `servedOnWatch` is the phone's claim about a class of device and [WearSendToReachability] is
     * this watch's answer about itself - a receiver the phone marked may still have no handler
     * installed here, and firing at one would end in the refusal ADR-3 forbids.
     *
     * An id no published receiver carries is refused rather than guessed: the list this came from is
     * the phone's, and it can be replaced between the menu opening and the tap landing.
     */
    private suspend fun sendToReceiver(file: WearMediaFile, receiverId: String): WearFileOperationResult {
        val entry = sendToReceivers.observe().value.firstOrNull { it.id == receiverId }
            ?: return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        val sendIntent = WearSendToReachability.sendIntentFor(file.mimeType ?: ANY_MIME_TYPE)
        return if (reachability.isServedHere(entry, sendIntent)) {
            sendHere(file, entry, sendIntent)
        } else {
            sendThroughPhone(file, entry)
        }
    }

    /**
     * The watch branch: the file is staged to a readable copy and handed to a local receiver.
     *
     * Staged for the same reason the phone branch stages - a MediaStore row has no path a provider
     * can serve - and discarded afterwards on either outcome, so an abandoned send leaves no copy.
     */
    private suspend fun sendHere(
        file: WearMediaFile,
        entry: WearSendToReceiverEntry,
        sendIntent: Intent
    ): WearFileOperationResult {
        Timber.d("S2142: send-to served on the watch, receiver=%s", entry.id)
        val staged = stager.stage(file)
            ?: return WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        return try {
            val launched = sendToLauncher.launch(staged, sendIntent)
            WearFileOperationResult(
                fileName = file.name,
                outcome = if (launched) {
                    WearFileOperationOutcome.SUCCEEDED
                } else {
                    WearFileOperationOutcome.FAILED
                },
                destination = entry.title
            )
        } finally {
            stager.discard(staged, file)
        }
    }

    /**
     * The phone branch: the file crosses the bridge carrying the receiver it is meant for.
     *
     * The errand rides on the transfer's own announcement rather than on a second message, so the
     * phone cannot receive the bytes without also knowing what they are for - a file that arrived
     * with its errand lost would be filed away silently while the watch reported it on its way.
     *
     * The size is refused here, before the channel opens, for the same reason
     * [sendToPhone] refuses it: telling the owner afterwards costs a whole transfer first.
     */
    private suspend fun sendThroughPhone(
        file: WearMediaFile,
        entry: WearSendToReceiverEntry
    ): WearFileOperationResult {
        Timber.d("S2142: send-to routed through the phone, receiver=%s", entry.id)
        val refusal = preflightRefusal(file)
        if (refusal != null) {
            return WearFileOperationResult(file.name, refusal, destination = entry.title)
        }
        val staged = stager.stage(file)
        return if (staged == null) {
            WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED, destination = entry.title)
        } else {
            try {
                val result = senderRepository.sendFile(staged, sendToReceiverId = entry.id)
                WearFileOperationResult(
                    fileName = file.name,
                    outcome = result.outcome.toOperationOutcome(),
                    // The receiver's own name, not the phone folder the transfer landed in: what the
                    // owner asked for was the receiver, and the folder is an implementation detail of
                    // the errand they never chose.
                    destination = entry.title
                )
            } finally {
                stager.discard(staged, file)
            }
        }
    }

    /**
     * The two answers the phone branch can give before a single byte is copied, or `null` to proceed.
     *
     * Both are asked here rather than inline for one reason each. The size is refused before the
     * channel opens for [sendToPhone]'s reason: telling the owner afterwards costs a whole transfer
     * first. The reachability is asked before the copy is staged so an out-of-reach phone is the
     * answer to the tap rather than the end of a transfer that never started (strategic 11
     * criterion 9).
     */
    private suspend fun preflightRefusal(file: WearMediaFile): WearFileOperationOutcome? = when {
        file.size > WEAR_FILE_TRANSFER_MAX_BYTES -> WearFileOperationOutcome.REFUSED_TOO_LARGE
        !senderRepository.isPhoneReachable() -> WearFileOperationOutcome.PHONE_UNREACHABLE
        else -> null
    }

    /**
     * The one operation that stages nothing: the phone already holds the file this watch copy came
     * from, so the request carries its address and the answer says what the phone did with it.
     */
    private suspend fun openOnPhone(file: WearMediaFile, token: String): WearFileOperationResult {
        val outcome = openOnPhoneRepository.requestOpen(
            WearOpenOnPhoneRequest(token = token, displayName = file.name)
        )
        return WearFileOperationResult(file.name, outcome.toOperationOutcome())
    }

    private suspend fun sendToPhone(
        file: WearMediaFile,
        storageClass: WearFileStorageClass,
        deleteSource: Boolean
    ): WearFileOperationResult {
        if (file.size > WEAR_FILE_TRANSFER_MAX_BYTES) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_TOO_LARGE)
        }
        val staged = stager.stage(file)
        return if (staged == null) {
            WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        } else {
            deliver(file, staged, storageClass, deleteSource)
        }
    }

    /**
     * The source is removed only against a confirmed [WearFileSendOutcome.SENT] or
     * [WearFileSendOutcome.QUEUED_ON_PHONE]; every other answer leaves it where it is.
     * Reporting a move that deleted a file the phone never received is the one failure strategic §7
     * rates as losing data outright.
     */
    private suspend fun deliver(
        file: WearMediaFile,
        staged: File,
        storageClass: WearFileStorageClass,
        deleteSource: Boolean
    ): WearFileOperationResult = try {
        val result = senderRepository.sendFile(staged)
        val confirmedHandOff = result.outcome == WearFileSendOutcome.SENT ||
            result.outcome == WearFileSendOutcome.QUEUED_ON_PHONE
        if (deleteSource && confirmedHandOff) {
            removeSource(file, storageClass, result)
        } else {
            WearFileOperationResult(
                fileName = file.name,
                outcome = result.outcome.toOperationOutcome(),
                destination = result.destination
            )
        }
    } finally {
        // Also on cancellation: an abandoned run must not leave the copy behind in the cache.
        stager.discard(staged, file)
    }

    /**
     * Removes the watch's own copy once the phone has confirmed it holds the file.
     *
     * A MediaStore row has no path, so [WearMediaFileStager.localFileOf] answers null for it and the
     * file branch silently removes nothing - which would report a move as done while the row is
     * still on the watch, the same lie in the other direction from the one [deliver] guards against.
     *
     * A row needing the owner's confirmation asks to be retried as a plain delete: the bytes already
     * reached the phone, and repeating the move would deliver the file there twice.
     */
    private fun removeSource(
        file: WearMediaFile,
        storageClass: WearFileStorageClass,
        result: WearFileSendResult
    ): WearFileOperationResult {
        val sent = result.outcome.toOperationOutcome()
        if (storageClass != WearFileStorageClass.MEDIA_STORE) {
            stager.localFileOf(file)?.delete()
            return WearFileOperationResult(file.name, sent, destination = result.destination)
        }
        return when (val removed = mediaStoreWriter.delete(file.uri)) {
            WearMediaStoreFileWriter.Result.Succeeded ->
                WearFileOperationResult(file.name, sent, destination = result.destination)
            is WearMediaStoreFileWriter.Result.NeedsConsent -> WearFileOperationResult(
                fileName = file.name,
                outcome = WearFileOperationOutcome.NEEDS_CONSENT,
                destination = result.destination,
                consentRequest = removed.request,
                retryAs = WearFileOperation.Delete
            )
            WearMediaStoreFileWriter.Result.Failed ->
                WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED, destination = result.destination)
        }
    }

    /**
     * A MediaStore row has no file path, so the file branch below cannot reach it at all - it is the
     * absence of that address, not a policy choice, that makes the second branch necessary.
     */
    private fun deleteLocal(
        file: WearMediaFile,
        storageClass: WearFileStorageClass
    ): WearFileOperationResult {
        if (storageClass == WearFileStorageClass.MEDIA_STORE) {
            Timber.d("S2142: MediaStore delete asks the owner to confirm %s", file.name)
            return mediaStoreWriter.delete(file.uri).toOperationResult(file.name)
        }
        val target = stager.localFileOf(file)
        val deleted = target != null && target.delete()
        return WearFileOperationResult(
            file.name,
            if (deleted) WearFileOperationOutcome.SUCCEEDED else WearFileOperationOutcome.FAILED
        )
    }

    /**
     * The MediaStore branch resolves no name conflict and reports no `finalName`.
     *
     * Not because the store settles duplicates on its behalf - it does that on insert, not on the
     * update this performs, where a colliding display name is rejected outright. The rejection
     * surfaces as a failed operation rather than a silent suffix, and the name the row ended up with
     * is never read back, so there is nothing truthful to put in `finalName`.
     */
    private fun renameLocal(
        file: WearMediaFile,
        newName: String,
        storageClass: WearFileStorageClass
    ): WearFileOperationResult {
        if (storageClass == WearFileStorageClass.MEDIA_STORE) {
            Timber.d("S2142: MediaStore rename asks the owner to confirm %s", file.name)
            return mediaStoreWriter.rename(file.uri, newName).toOperationResult(file.name)
        }
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

/**
 * The confirmation travels on the result, because only the screen can start it.
 *
 * A refused write leaves the row untouched, so it is reported apart from a failure: the retry after
 * the owner confirms is the same call, not a recovery from a half-applied change.
 */
private fun WearMediaStoreFileWriter.Result.toOperationResult(
    fileName: String
): WearFileOperationResult = when (this) {
    WearMediaStoreFileWriter.Result.Succeeded ->
        WearFileOperationResult(fileName, WearFileOperationOutcome.SUCCEEDED)
    is WearMediaStoreFileWriter.Result.NeedsConsent -> WearFileOperationResult(
        fileName = fileName,
        outcome = WearFileOperationOutcome.NEEDS_CONSENT,
        consentRequest = request
    )
    WearMediaStoreFileWriter.Result.Failed ->
        WearFileOperationResult(fileName, WearFileOperationOutcome.FAILED)
}

private fun WearFileSendOutcome.toOperationOutcome(): WearFileOperationOutcome = when (this) {
    WearFileSendOutcome.SENT -> WearFileOperationOutcome.SUCCEEDED
    WearFileSendOutcome.QUEUED_ON_PHONE -> WearFileOperationOutcome.QUEUED_ON_PHONE
    WearFileSendOutcome.NO_DESTINATION -> WearFileOperationOutcome.NO_DESTINATION
    WearFileSendOutcome.UNCONFIRMED -> WearFileOperationOutcome.UNCONFIRMED
    WearFileSendOutcome.TOO_LARGE -> WearFileOperationOutcome.REFUSED_TOO_LARGE
    WearFileSendOutcome.PHONE_UNREACHABLE -> WearFileOperationOutcome.PHONE_UNREACHABLE
    WearFileSendOutcome.AWAITING_PHONE_ACTION -> WearFileOperationOutcome.AWAITING_PHONE_ACTION
    WearFileSendOutcome.PHONE_NOTIFICATIONS_OFF ->
        WearFileOperationOutcome.REFUSED_PHONE_NOTIFICATIONS_OFF
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
