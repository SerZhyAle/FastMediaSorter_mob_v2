package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.network.exceptions.NetworkPinRefusedException
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpRootProofRegistry
import com.sza.fastmediasorter.domain.model.ExchangeAttachPlanner
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangePinAttempt
import com.sza.fastmediasorter.domain.model.HostPort
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Presents a PIN to the owner of a protected resource (DEVICE-EXCHANGE 17 items AH and AJ): a session of its own
 * proves the root, through the resource's first address that answers. The PIN is never stored here - keeping it is
 * the caller's decision, taken only after [ExchangePinAttempt.ACCEPTED].
 */
class VerifyExchangePinUseCase @Inject constructor(
    private val sftp: SftpClient,
    private val proofs: SftpRootProofRegistry,
) {

    /** Bindings whose stored PIN the owner refused at its last use. */
    val refusals: StateFlow<Set<ExchangeBinding>> get() = proofs.refusals

    /** A PIN just accepted for [binding] ends an earlier refusal. */
    fun accepted(binding: ExchangeBinding) = proofs.clearRefusal(binding)

    // The first address that answers decides: a refusal is the owner's word, not a network failure.
    suspend operator fun invoke(access: ExchangeAttachPlanner.Access, pin: String): ExchangePinAttempt {
        val root = access.proofRoot ?: return ExchangePinAttempt.FAILED
        var outcome = ExchangePinAttempt.UNREACHABLE
        val hosts = access.credentialHosts.iterator()
        while (hosts.hasNext() && outcome == ExchangePinAttempt.UNREACHABLE) {
            outcome = proveOn(hosts.next(), access, root, pin)
        }
        return outcome
    }

    private suspend fun proveOn(
        host: HostPort,
        access: ExchangeAttachPlanner.Access,
        root: String,
        pin: String,
    ): ExchangePinAttempt {
        val info = SftpClient.SftpConnectionInfo(
            host = host.host,
            port = host.port,
            username = access.payload.username,
            password = access.payload.password.orEmpty(),
            expectedFingerprint = access.pin,
        )
        val failure = sftp.verifyRootProof(info, root, pin).exceptionOrNull()
        if (failure != null && failure !is NetworkPinRefusedException) {
            Timber.i("VerifyExchangePinUseCase: no proof on one address (%s)", failure.javaClass.simpleName)
        }
        return when (failure) {
            null -> ExchangePinAttempt.ACCEPTED
            is NetworkPinRefusedException -> ExchangePinAttempt.REFUSED
            else -> ExchangePinAttempt.UNREACHABLE
        }
    }
}
