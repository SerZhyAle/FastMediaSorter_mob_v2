package com.sza.fastmediasorter.domain.usecase.exchange

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.broadcast.BroadcastSourceController
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeNotEnrolledException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.settings.BroadcastSettingsStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeAddressChange
import com.sza.fastmediasorter.domain.model.DeviceExchangeCredential
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import com.sza.fastmediasorter.domain.model.DeviceExchangePairingCode
import com.sza.fastmediasorter.domain.model.DeviceExchangeRemoval
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.domain.model.ExchangeServerCall
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * The account half of the "My devices" screen (S4116 phase 04): sign in to a server, list and revoke the
 * account's devices, issue a pairing code, move, share and remove an entry. Publishing through an entry is
 * [com.sza.fastmediasorter.domain.usecase.sftpserver.ManageExchangeProducerUseCase], the watch hand-over is
 * [com.sza.fastmediasorter.domain.usecase.wear.ConnectWatchToExchangeEntryUseCase].
 */
class ManageExchangeAccountsUseCase @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val capabilityAvailability: CapabilityAvailability,
    private val mediaCapabilities: MediaCapabilities,
    private val broadcastSource: BroadcastSourceController,
    @ApplicationContext private val context: Context,
) {

    val entries: Flow<List<DeviceExchangeAccountEntry>> get() = accounts.entries

    /** `host:port`, or a bare host on the contract's default port; null for anything else. */
    fun parseAddress(text: String): ExchangeEndpoint? {
        val trimmed = text.trim()
        return ExchangeEndpoint.parse(trimmed) ?: ExchangeEndpoint.parse("$trimmed:${SftpExchangeConfig.DEFAULT_PORT}")
    }

    fun isValidLogin(login: String): Boolean = DeviceExchangeAccountEntry.isValidLogin(login)

    /**
     * Enrolls with a password or a pairing code; [acceptedPin] is the leaf the user accepted after a
     * [DeviceExchangeEnrollResult.CertificateUntrusted] answer, null on the first attempt (amendment D).
     */
    suspend fun signIn(
        endpoint: ExchangeEndpoint,
        login: String,
        secret: String,
        pairingCode: Boolean,
        acceptedPin: String?,
    ): DeviceExchangeEnrollResult {
        val credential = if (pairingCode) {
            DeviceExchangeCredential.PairingCode(secret)
        } else {
            DeviceExchangeCredential.Password(secret)
        }
        return accounts.enroll(endpoint, login, acceptedPin, credential, enrollment())
    }

    suspend fun directory(entry: DeviceExchangeAccountEntry): ExchangeServerCall<DeviceExchangeDirectory> =
        call { accounts.directory(entry) }

    suspend fun revokeDevice(entry: DeviceExchangeAccountEntry, deviceId: String): ExchangeServerCall<Unit> =
        call { accounts.revokeDevice(entry, deviceId) }

    suspend fun issuePairingCode(entry: DeviceExchangeAccountEntry): ExchangeServerCall<DeviceExchangePairingCode> =
        call { accounts.issuePairingCode(entry) }

    suspend fun changeAddress(
        entry: DeviceExchangeAccountEntry,
        endpoint: ExchangeEndpoint,
    ): DeviceExchangeAddressChange = accounts.changeAddress(entry, endpoint)

    /** The address the server announced when it differs from the held one; offered, never applied (item AA). */
    fun announcedEndpoint(entry: DeviceExchangeAccountEntry): ExchangeEndpoint? = accounts.announcedEndpoint(entry)

    suspend fun remove(entry: DeviceExchangeAccountEntry): DeviceExchangeRemoval = accounts.remove(entry)

    suspend fun setAutoAttach(entry: DeviceExchangeAccountEntry, enabled: Boolean) =
        accounts.setAutoAttach(entry, enabled)

    /** The `FMSEXCH1` text the entry passes on (item AE), never carrying the login; null when it cannot. */
    fun connectionString(entry: DeviceExchangeAccountEntry): String? =
        accounts.connectionString(entry)?.let(ExchangeConnectionStringCodec::encode)

    private fun enrollment() = DeviceExchangeEnrollment(
        deviceName = BroadcastSettingsStore.defaultDeviceTitle(context),
        productVersion = BuildConfig.VERSION_NAME,
        roles = DeviceExchangeEnrollment.declaredRoles(
            producer = capabilityAvailability.isSftpServerAvailable(mediaCapabilities),
            broadcaster = broadcastSource.isAvailable,
        ),
    )

    private suspend fun <T> call(block: suspend () -> T): ExchangeServerCall<T> = try {
        ExchangeServerCall.Done(block())
    } catch (e: DeviceExchangeRefusedException) {
        Timber.w("Exchange server refused the request (%s)", e.reason)
        ExchangeServerCall.Refused(e.reason)
    } catch (e: DeviceExchangeNotEnrolledException) {
        Timber.i(e, "Exchange entry holds no device token")
        ExchangeServerCall.Refused(REASON_NOT_ENROLLED)
    } catch (e: IOException) {
        Timber.i(e, "Exchange server not reached")
        ExchangeServerCall.Unreachable
    }

    companion object {
        /** Not a 7.10 reason: the entry's token is gone and only signing in again helps. */
        const val REASON_NOT_ENROLLED = "not-enrolled"
    }
}
