package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.network.exceptions.HostKeyMismatchFinder
import com.sza.fastmediasorter.data.remote.sftp.SftpHostKeyPinRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S4037: the single writer behind the confirmed host-key re-pin action. Delegates to the pin
 * registry's replace-with-fan-out, which substitutes the stored pin for the anchor resource and
 * every SFTP resource sharing one of its addresses (strategic ADR-3: one server is one key, so a
 * partial replace would leave co-addressed resources refusing). Returns the number of resources
 * whose pin was replaced; 0 means nothing was written and the surface must not claim success.
 *
 * Both entries hop to the injected IO dispatcher: the registry's owner lookup reads the resources
 * table with a blocking DAO call, and the surfaces invoke this from a Main-scope confirm callback.
 */
class RepinSftpHostKeyUseCase @Inject constructor(
    private val pinRegistry: SftpHostKeyPinRegistry,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** The typed (expected, actual) pair in [throwable]'s cause chain, or null when none exists. */
    fun mismatchIn(throwable: Throwable?): Pair<String, String>? = HostKeyMismatchFinder.find(throwable)

    suspend operator fun invoke(resourceId: Long, fingerprint: String): Int = withContext(ioDispatcher) {
        pinRegistry.repin(resourceId, fingerprint)
    }

    /** The resource a re-pin anchors on, resolved from the pinned (expected) fingerprint of a mismatch. */
    suspend fun anchorFor(expectedFingerprint: String): Long? = withContext(ioDispatcher) {
        pinRegistry.anchorForPin(expectedFingerprint)
    }

    /** The count the mismatch dialog shows before the user confirms the fan-out. */
    suspend fun affectedCount(resourceId: Long): Int = withContext(ioDispatcher) {
        pinRegistry.affectedResourceCount(resourceId)
    }
}
