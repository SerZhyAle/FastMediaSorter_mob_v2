package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Intent
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.data.files.WearSendToLauncher
import com.sza.fastmediasorter.wear.data.files.WearWatchFilePublisher
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.data.wear.PhoneResourceClient
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearFileNameConflictResolver
import com.sza.fastmediasorter.wear.domain.files.WearSendToReachability
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileCollection
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileTarget
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceDeleteOutcome
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.model.kind
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
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
// Each parameter is the one collaborator a single operation needs, and the operations are fixed by
// the model rather than by this class. Grouping any of them would name a thing that does not exist -
// the watch has no "file service", only a sender, a publisher, a stager and a downloader.
@Suppress("LongParameterList")
class PerformWearFileOperationUseCase @Inject constructor(
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val senderRepository: WearFileSenderRepository,
    private val openOnPhoneRepository: WearOpenOnPhoneRepository,
    private val stager: WearMediaFileStager,
    private val mediaStoreWriter: WearMediaStoreFileWriter,
    private val watchPublisher: WearWatchFilePublisher,
    /** S3359: the one round trip that can ask the phone to destroy something, used by the move alone. */
    private val phoneResourceClient: PhoneResourceClient,
    /** S1687's protocol routing, reused rather than duplicated: a copy reads the share as a play does. */
    private val downloadNetworkFile: DownloadNetworkFileUseCase,
    /** S3359: the share's own half of the move - the only call here that removes anything on a server. */
    private val deleteNetworkFile: DeleteNetworkFileUseCase,
    private val sendToReceivers: WearSendToReceiversRepository,
    private val reachability: WearSendToReachability,
    private val sendToLauncher: WearSendToLauncher
) {

    /**
     * [networkSourceId] names the share a network file is read from and, on a move, removed from; only
     * the two operations onto the watch need it - every other one either stays on the watch or crosses
     * to the phone, where the listing's own connection has already been resolved.
     *
     * [phoneToken] addresses the phone's own original of a copy this watch holds, and only a move onto
     * the watch needs it: it is the one operation that asks the other side to destroy something. The
     * watch cannot re-derive it - the cached copy is named after the token's hash - so a caller that
     * does not have it leaves the original alone rather than guessing (S3359).
     */
    operator fun invoke(
        files: List<WearMediaFile>,
        operation: WearFileOperation,
        isNetworkSource: Boolean,
        networkSourceId: String? = null,
        phoneToken: String? = null
    ): Flow<WearFileOperationResult> = flow {
        for (file in files) {
            emit(runOne(file, operation, isNetworkSource, networkSourceId, phoneToken))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun runOne(
        file: WearMediaFile,
        operation: WearFileOperation,
        isNetworkSource: Boolean,
        networkSourceId: String?,
        phoneToken: String?
    ): WearFileOperationResult {
        val storageClass = capabilityPolicy.classify(file, isNetworkSource)
        if (operation.kind() !in capabilityPolicy.allowedOperations(file, isNetworkSource)) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
        return when (operation) {
            WearFileOperation.SendToPhone -> sendToPhone(file, storageClass, deleteSource = false)
            WearFileOperation.MoveToPhone -> sendToPhone(file, storageClass, deleteSource = true)
            WearFileOperation.CopyToWatch -> {
                Timber.d("S3359: copy to watch class=$storageClass")
                copyToWatch(file, storageClass, networkSourceId)
            }
            WearFileOperation.MoveToWatch -> {
                Timber.d("S3359: move to watch class=$storageClass")
                moveToWatch(file, storageClass, networkSourceId, phoneToken)
            }
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
     * Turns a file the watch is only borrowing into one of its own.
     *
     * Two sources reach here and neither is the watch's own storage: the phone's copy, whose bytes
     * are already on disk, and a network entry, which has to be read off the share first. A type with
     * no collection is refused before either path, because the destination is what decides whether a
     * copy can be found again at all.
     */
    private suspend fun copyToWatch(
        file: WearMediaFile,
        storageClass: WearFileStorageClass,
        networkSourceId: String?
    ): WearFileOperationResult {
        val mimeType = file.mimeType
        val collection = WearWatchFileTarget.collectionOf(mimeType)
        if (mimeType == null || collection == null) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
        return when (storageClass) {
            WearFileStorageClass.PHONE_COPY -> copyHeldFile(file, mimeType, collection)
            WearFileStorageClass.NETWORK -> copyNetworkFile(file, mimeType, collection, networkSourceId)
            WearFileStorageClass.APP_OWNED, WearFileStorageClass.MEDIA_STORE ->
                WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
    }

    /**
     * The move: the copy above, and then whoever still holds the original asked to let it go.
     *
     * The order is the whole safety property of strategic goal 5 - nothing is asked of either source
     * until the copy on this watch is written and published. Every answer other than a confirmed
     * removal, a lost answer included, reports [WearFileOperationOutcome.COPIED_SOURCE_KEPT], because
     * "the original may still be there" and "the original is gone" must never round to the same line.
     *
     * Which source is asked is the only difference between the two classes that reach here: the phone
     * answers a request, and a share is removed from by this watch itself.
     */
    private suspend fun moveToWatch(
        file: WearMediaFile,
        storageClass: WearFileStorageClass,
        networkSourceId: String?,
        phoneToken: String?
    ): WearFileOperationResult {
        val hasRemovableOriginal = storageClass == WearFileStorageClass.PHONE_COPY ||
            storageClass == WearFileStorageClass.NETWORK
        if (!hasRemovableOriginal) {
            return WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_UNSUPPORTED)
        }
        val copied = copyToWatch(file, storageClass, networkSourceId)
        return when {
            copied.outcome != WearFileOperationOutcome.SUCCEEDED -> copied
            storageClass == WearFileStorageClass.NETWORK -> askTheShareToLetGo(file, copied, networkSourceId)
            else -> askThePhoneToLetGo(file, copied, phoneToken)
        }
    }

    /**
     * The share's half of the move, kept beside the phone's so the one rule they share reads once:
     * nothing but a confirmed removal turns the stored copy into a move.
     *
     * A share refuses for reasons the watch cannot tell apart from a network fault - a read-only
     * account, a locked file, a connection lost between the read and the removal - and all of them
     * leave a file on the server that the owner must be told is still there.
     */
    private suspend fun askTheShareToLetGo(
        file: WearMediaFile,
        copied: WearFileOperationResult,
        networkSourceId: String?
    ): WearFileOperationResult {
        val removed = deleteNetworkFile(networkSourceId, file.uri.toString()).isSuccess
        return if (removed) {
            copied
        } else {
            copied.copy(outcome = WearFileOperationOutcome.COPIED_SOURCE_KEPT)
        }
    }

    /**
     * The half that can lose data, kept apart from the copy so its one rule reads alone: nothing but a
     * confirmed removal turns the stored copy into a move.
     *
     * A missing token or a cached file that is gone by now both mean the ask cannot be made truthfully,
     * so no ask is made at all - which is the same answer a refusal and a lost reply produce.
     */
    private suspend fun askThePhoneToLetGo(
        file: WearMediaFile,
        copied: WearFileOperationResult,
        phoneToken: String?
    ): WearFileOperationResult {
        val copiedLength = stager.localFileOf(file)?.length()
        val answer = if (phoneToken == null || copiedLength == null) {
            null
        } else {
            phoneResourceClient.requestDelete(phoneToken, copiedLength)
        }
        return if (answer == WearPhoneResourceDeleteOutcome.DELETED) {
            copied
        } else {
            copied.copy(outcome = WearFileOperationOutcome.COPIED_SOURCE_KEPT)
        }
    }

    /**
     * The phone's copy is already here, so this is a publish and not a transfer.
     *
     * The space is judged against the cached file's real length before the first byte, because the
     * refusal has to arrive while the phone's original is still the only complete copy there is.
     */
    private fun copyHeldFile(
        file: WearMediaFile,
        mimeType: String,
        collection: WearWatchFileCollection
    ): WearFileOperationResult {
        val cached = stager.localFileOf(file)
        return when {
            cached == null -> WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
            watchPublisher.freeBytes() < cached.length() ->
                WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_NO_SPACE)
            else -> publishToWatch(file, cached, mimeType, collection)
        }
    }

    /**
     * A share is read through the one use case that knows which protocol serves it (S1687), so a copy
     * needs no network verb of its own - research artifact 02's finding.
     *
     * The space is judged against the listing's size rather than a downloaded length: refusing after
     * the transfer would have spent exactly what the refusal exists to save. A missing source id is a
     * failure rather than a refusal - the id names the share the bytes live on, and its absence means
     * the menu and the list disagreed about where this entry came from.
     */
    private suspend fun copyNetworkFile(
        file: WearMediaFile,
        mimeType: String,
        collection: WearWatchFileCollection,
        networkSourceId: String?
    ): WearFileOperationResult = when {
        networkSourceId == null -> WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        watchPublisher.freeBytes() < file.size ->
            WearFileOperationResult(file.name, WearFileOperationOutcome.REFUSED_NO_SPACE)
        else -> downloadAndPublish(file, mimeType, collection, networkSourceId)
    }

    private suspend fun downloadAndPublish(
        file: WearMediaFile,
        mimeType: String,
        collection: WearWatchFileCollection,
        networkSourceId: String
    ): WearFileOperationResult {
        val selected = SelectedMedia(
            file = file,
            isNetworkSource = true,
            streamUri = file.uri.toString(),
            sourceId = networkSourceId
        )
        val downloaded = downloadNetworkFile(selected, downloadKindOf(collection)).getOrNull()
        return if (downloaded == null) {
            WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
        } else {
            publishToWatch(file, downloaded, mimeType, collection)
        }
    }

    /** The collection already answered the MIME prefix, so the cache the download uses follows from it. */
    private fun downloadKindOf(collection: WearWatchFileCollection): DownloadNetworkFileUseCase.Kind =
        when (collection) {
            WearWatchFileCollection.AUDIO -> DownloadNetworkFileUseCase.Kind.AUDIO
            WearWatchFileCollection.VIDEO -> DownloadNetworkFileUseCase.Kind.VIDEO
            WearWatchFileCollection.IMAGE -> DownloadNetworkFileUseCase.Kind.IMAGE
        }

    private fun publishToWatch(
        file: WearMediaFile,
        cached: File,
        mimeType: String,
        collection: WearWatchFileCollection
    ): WearFileOperationResult =
        when (val published = watchPublisher.publish(cached, file.name, mimeType, collection)) {
            is WearWatchFilePublisher.Result.Published -> WearFileOperationResult(
                fileName = file.name,
                outcome = WearFileOperationOutcome.SUCCEEDED,
                // Reported only when the store had to move the name: repeating the name the owner
                // just tapped says nothing, while a silent suffix leaves them looking for the wrong file.
                finalName = published.finalName.takeIf { it != file.name }
            )
            WearWatchFilePublisher.Result.Failed ->
                WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED)
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
