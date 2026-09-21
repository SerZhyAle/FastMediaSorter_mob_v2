package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.domain.model.WearPhoneResourceDeleteOutcome
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.domain.model.WearPhoneResourceRequestKind
import timber.log.Timber
import javax.inject.Inject

/**
 * S3359: removes the original behind a browse token, when the watch has asked for it and only then.
 *
 * The watch sends this after it has written and measured its own copy, so the file is already safe
 * somewhere else by the time this runs. What is left to guard is the token, which names a resource and
 * a path and nothing else: a file renamed or replaced in between resolves through the same token to a
 * different file, and deleting that one would be the data loss strategic §7 rates as the worst outcome.
 * Hence the size check, and hence the rule that anything unclear answers
 * [WearPhoneResourceDeleteOutcome.COPIED_ONLY] - the copy stands either way, so the cheap mistake is to
 * keep a file the owner wanted gone, never to delete one they wanted kept.
 */
class DeleteWatchRequestedFileUseCase @Inject constructor(
    private val openPhoneResourceChannel: OpenPhoneResourceChannelUseCase,
    private val localOperationStrategy: LocalOperationStrategy
) {

    /**
     * [expectedSizeBytes] is the length of the copy the watch published, measured there after the write.
     *
     * Resolved through the very use case that served the open request, so this phone cannot disagree
     * with itself about what a token names; `forWatchTransfer = false` because the transfer limits are
     * about what the watch can render and this file is not going anywhere.
     */
    suspend operator fun invoke(token: String, expectedSizeBytes: Long): WearPhoneResourceDeleteOutcome {
        val approved = openPhoneResourceChannel(
            WearPhoneResourceRequest(
                requestId = token,
                kind = WearPhoneResourceRequestKind.OPEN,
                itemToken = token
            ),
            forWatchTransfer = false
        ) as? PhoneResourceChannel.Approved
        return when {
            approved == null -> {
                Timber.i("Watch delete request: the token resolves to nothing this phone serves")
                WearPhoneResourceDeleteOutcome.NOT_FOUND
            }
            approved.sizeBytes != expectedSizeBytes -> {
                Timber.i(
                    "Watch delete request refused: %s is %d bytes, the watch copied %d",
                    approved.name,
                    approved.sizeBytes,
                    expectedSizeBytes
                )
                WearPhoneResourceDeleteOutcome.SIZE_MISMATCH
            }
            else -> remove(approved)
        }
    }

    /**
     * The delete goes through the app's own local strategy, which unindexes and rescans the path the
     * same way every other delete on this phone does - a row left behind would show the owner a file
     * that is no longer there.
     */
    private suspend fun remove(approved: PhoneResourceChannel.Approved): WearPhoneResourceDeleteOutcome {
        val path = approved.file.absolutePath
        if (localOperationStrategy.requiresDeleteConsent(path)) {
            Timber.i("Watch delete request: %s needs the system dialog, leaving the original", approved.name)
            return WearPhoneResourceDeleteOutcome.COPIED_ONLY
        }
        val deleted = localOperationStrategy.deleteFile(path)
        return if (deleted.isSuccess) {
            WearPhoneResourceDeleteOutcome.DELETED
        } else {
            // A refusal the owner can act on by hand, not a developer defect: the file is still on the
            // phone and the watch says so, which is the documented outcome of this branch.
            Timber.i(
                deleted.exceptionOrNull(),
                "Watch delete request: %s could not be removed, leaving the original",
                approved.name
            )
            WearPhoneResourceDeleteOutcome.COPIED_ONLY
        }
    }
}
