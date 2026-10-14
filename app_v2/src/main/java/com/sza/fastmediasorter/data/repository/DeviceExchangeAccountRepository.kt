package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeTokenStore
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeAddressChange
import com.sza.fastmediasorter.domain.model.DeviceExchangeCredential
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollment
import com.sza.fastmediasorter.domain.model.DeviceExchangeInvitation
import com.sza.fastmediasorter.domain.model.DeviceExchangePairingCode
import com.sza.fastmediasorter.domain.model.DeviceExchangeRemoval
import com.sza.fastmediasorter.domain.model.DeviceExchangeWelcome
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeConnectionString
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.domain.model.SftpShareId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The lifecycle of the exchange account entries a device holds: enrollment by identity, the in-place address
 * edit, removal, and the connection string an entry passes on. Contract DEVICE-EXCHANGE items AA, AB and AD
 * are the rules; a screen calls these operations and never re-derives them.
 */
@Singleton
class DeviceExchangeAccountRepository @Inject constructor(
    private val client: DeviceExchangeAccountClient,
    private val store: DeviceExchangeAccountStore,
    private val tokens: DeviceExchangeTokenStore,
    private val pins: ExchangeResourcePinStore,
    private val resourceDao: ResourceDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    val entries: Flow<List<DeviceExchangeAccountEntry>> = store.entries

    /** The entry the device publishes and broadcasts through (item AC); null while none is chosen. */
    val outgoing: Flow<DeviceExchangeAccountEntry?> = store.entries.map { held -> held.firstOrNull { it.outgoing } }

    suspend fun outgoingEntry(): DeviceExchangeAccountEntry? = store.outgoing()

    /** Makes [entry] the one outgoing entry; leaving the previous one as a producer is the caller's first step. */
    suspend fun setOutgoing(entry: DeviceExchangeAccountEntry) = store.setOutgoing(entry.identity)

    /**
     * Enrolls [login] at [endpoint]. The same identity again re-enrolls the held entry with its `deviceId`, which
     * rotates its token (7.2); a new identity gets a fresh `deviceId`, regenerated once on `id-collision` (AD 3).
     * The entry is recorded only once a token was stored. [pin] is the leaf the user accepted, null to keep the
     * held one or to trust the platform.
     */
    suspend fun enroll(
        endpoint: ExchangeEndpoint,
        login: String,
        pin: String?,
        credential: DeviceExchangeCredential,
        enrollment: DeviceExchangeEnrollment,
    ): DeviceExchangeEnrollResult = admit(endpoint, login, pin, enrollment) { candidate ->
        client.enroll(candidate, credential, enrollment)
    }

    /**
     * Registers a new account [login] at [endpoint] with an invitation and enrolls this device as its first one
     * (19 item AQ). The entry is recorded exactly as by [enroll], only once a token was stored.
     */
    suspend fun register(
        endpoint: ExchangeEndpoint,
        login: String,
        pin: String?,
        password: DeviceExchangeCredential.Password,
        invitation: DeviceExchangeInvitation,
        enrollment: DeviceExchangeEnrollment,
    ): DeviceExchangeEnrollResult = admit(endpoint, login, pin, enrollment) { candidate ->
        client.register(candidate, password, invitation, enrollment)
    }

    private suspend fun admit(
        endpoint: ExchangeEndpoint,
        login: String,
        pin: String?,
        enrollment: DeviceExchangeEnrollment,
        exchange: suspend (DeviceExchangeAccountEntry) -> DeviceExchangeEnrollResult,
    ): DeviceExchangeEnrollResult {
        require(DeviceExchangeAccountEntry.isValidLogin(login)) { "login breaks the contract rule" }
        val fresh = DeviceExchangeAccountEntry(endpoint.host, endpoint.port, login, SftpShareId.generate(), pin)
        val held = store.entry(fresh.identity)
        val candidate = held?.copy(
            host = endpoint.host,
            port = endpoint.port,
            login = login,
            pinnedCertificate = pin ?: held.pinnedCertificate,
        ) ?: fresh
        var enrolled = candidate
        Timber.d("Exchange enrollment: %s:%d login=%s heldEntry=%s", endpoint.host, endpoint.port, login, held != null)
        var result = withContext(ioDispatcher) { exchange(candidate) }
        if (held == null && result.isIdCollision()) {
            Timber.d("Exchange enrollment: id-collision, retrying with a new deviceId")
            enrolled = candidate.copy(deviceId = SftpShareId.generate())
            result = withContext(ioDispatcher) { exchange(enrolled) }
        }
        if (result is DeviceExchangeEnrollResult.Enrolled) {
            val pinCapable = DeviceExchangeEnrollment.CAPABILITY_PIN_V1 in enrollment.capabilities
            store.put(
                enrolled.copy(
                    publicEndpoint = result.welcome.publicEndpoint,
                    pinCapable = pinCapable,
                    features = result.welcome.features,
                    accountKind = result.welcome.accountKind,
                )
            )
        }
        Timber.d("Exchange enrollment finished: %s", result.javaClass.simpleName)
        return result
    }

    /**
     * Moves [entry] to [endpoint] in place: the `deviceId`, the token, the settings and every attached resource
     * follow it. The pin is kept, so a server presenting another leaf is refused until the user replaces it
     * explicitly (item D), never silently.
     */
    suspend fun changeAddress(
        entry: DeviceExchangeAccountEntry,
        endpoint: ExchangeEndpoint,
    ): DeviceExchangeAddressChange {
        val moved = entry.copy(host = endpoint.host, port = endpoint.port)
        val identityTaken = moved.identity != entry.identity && store.entry(moved.identity) != null
        val token = tokens.token(entry)
        val outcome = when {
            identityTaken -> DeviceExchangeAddressChange.IdentityTaken
            token != null && !tokens.save(moved, token) -> DeviceExchangeAddressChange.TokenNotMoved
            else -> DeviceExchangeAddressChange.Changed(moved)
        }
        if (outcome is DeviceExchangeAddressChange.Changed && moved.identity != entry.identity) {
            tokens.forget(entry)
            // The PINs follow the binding in the same branch, or every protected resource would ask again.
            val bound = resourceDao.getAllResourcesSync()
                .filter { it.exchangeAccount == entry.identity }
                .mapNotNull { it.exchangeResourceId }
            pins.move(entry.identity, moved.identity, bound)
            resourceDao.rebindExchangeAccount(entry.identity, moved.identity)
        }
        if (outcome is DeviceExchangeAddressChange.Changed) store.replace(entry.identity, moved)
        return outcome
    }

    /**
     * Removes [entry] (item AB): revokes its own device when the server answers, then forgets the token and the
     * entry either way, without retrying in the background. Its attached resources stay and show as unavailable
     * until the same identity is added again.
     */
    suspend fun remove(entry: DeviceExchangeAccountEntry): DeviceExchangeRemoval {
        val removal = try {
            withContext(ioDispatcher) { client.revokeOwnDevice(entry) }
            DeviceExchangeRemoval.REVOKED
        } catch (e: IOException) {
            Timber.i(e, "Exchange entry removed locally; its device was not revoked on the server")
            DeviceExchangeRemoval.FORGOTTEN_LOCALLY
        }
        tokens.forget(entry)
        store.remove(entry.identity)
        return removal
    }

    /**
     * Changes [entry]'s account password (19 item AT); the fresh token replaces the held one and every other
     * device of the account signs in again.
     *
     * @throws IOException when the server cannot be reached or refused the change.
     */
    suspend fun changePassword(
        entry: DeviceExchangeAccountEntry,
        oldPassword: DeviceExchangeCredential.Password,
        newPassword: DeviceExchangeCredential.Password,
    ) = withContext(ioDispatcher) { client.changePassword(entry, oldPassword, newPassword) }

    /**
     * Deletes [entry]'s account on its server (19 item AU), then forgets the entry, its token and the PINs cached
     * for its resources. The attached resources stay and show as no longer published until the user removes them.
     *
     * @throws IOException when the server cannot be reached or refused the deletion; nothing is forgotten then.
     */
    suspend fun deleteAccount(entry: DeviceExchangeAccountEntry, password: DeviceExchangeCredential.Password) {
        withContext(ioDispatcher) { client.deleteAccount(entry, password) }
        resourceDao.getAllResourcesSync()
            .filter { it.exchangeAccount == entry.identity }
            .mapNotNull { it.exchangeResourceId }
            .forEach { resourceId -> pins.forget(ExchangeBinding(entry.identity, resourceId)) }
        tokens.forget(entry)
        store.remove(entry.identity)
    }

    /**
     * A one-time pairing code of [entry]'s account for another device - the watch - to enroll with (5.3). It is
     * handed on and never stored here.
     *
     * @throws IOException when the server cannot be reached or refused the request.
     */
    suspend fun issuePairingCode(entry: DeviceExchangeAccountEntry): DeviceExchangePairingCode =
        withContext(ioDispatcher) { client.requestPairingCode(entry) }

    /**
     * Every device, resource and broadcast of [entry]'s account as its server lists them now (7.4).
     *
     * @throws IOException when the server cannot be reached or refused the request.
     */
    suspend fun directory(entry: DeviceExchangeAccountEntry): DeviceExchangeDirectory =
        withContext(ioDispatcher) { client.listDirectory(entry) }

    /**
     * Revokes another device of [entry]'s account (5.2). Revoking the entry's own device is [remove], which also
     * forgets the token and the entry.
     *
     * @throws IOException when the server cannot be reached or refused the token.
     */
    suspend fun revokeDevice(entry: DeviceExchangeAccountEntry, deviceId: String) {
        require(deviceId != entry.deviceId) { "the own device leaves through remove()" }
        withContext(ioDispatcher) { client.revokeDevice(entry, deviceId) }
    }

    suspend fun setAutoAttach(entry: DeviceExchangeAccountEntry, enabled: Boolean) {
        store.entry(entry.identity)?.let { store.put(it.copy(autoAttach = enabled)) }
    }

    /**
     * Remembers what the latest `welcome` announced: the address [announcedEndpoint] offers, and the features and
     * account kind that decide whether the entry takes part in the clipboard (22 item BV). Writes nothing when
     * all three are unchanged.
     */
    suspend fun rememberWelcome(entry: DeviceExchangeAccountEntry, welcome: DeviceExchangeWelcome) {
        store.entry(entry.identity)
            ?.takeIf {
                it.publicEndpoint != welcome.publicEndpoint ||
                    it.features != welcome.features ||
                    it.accountKind != welcome.accountKind
            }
            ?.let {
                store.put(
                    it.copy(
                        publicEndpoint = welcome.publicEndpoint,
                        features = welcome.features,
                        accountKind = welcome.accountKind,
                    )
                )
            }
    }

    /** The announced address when it differs from the one [entry] connects to; the user decides (item AA). */
    fun announcedEndpoint(entry: DeviceExchangeAccountEntry): ExchangeEndpoint? {
        val announced = entry.publicEndpoint?.let(ExchangeEndpoint::parse)
        return announced?.takeIf { it != heldEndpoint(entry) }
    }

    /**
     * The `FMSEXCH1` string [entry] passes on (item AE): `x` from the last announced address, else the held one;
     * `fp` only with a pinned certificate; never the login. Null when neither address is of the contract's form.
     */
    fun connectionString(entry: DeviceExchangeAccountEntry): ExchangeConnectionString? {
        val endpoint = entry.publicEndpoint?.let(ExchangeEndpoint::parse) ?: heldEndpoint(entry) ?: return null
        return try {
            ExchangeConnectionString(endpoint = endpoint, fingerprint = entry.pinnedCertificate)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Exchange entry cannot be written as a connection string")
            null
        }
    }

    /**
     * What the sign-in form is filled with to sign [entry] in again after `signed-out` or a revoke: the held
     * address, the held pin and the login, so the same identity re-enrolls its own `deviceId` (19 item AT). It
     * stays on this device and is never passed on, unlike [connectionString].
     */
    fun signInAgainString(entry: DeviceExchangeAccountEntry): ExchangeConnectionString? {
        val endpoint = heldEndpoint(entry) ?: return null
        return try {
            ExchangeConnectionString(
                endpoint = endpoint,
                fingerprint = entry.pinnedCertificate,
                loginHint = entry.login.lowercase(Locale.ROOT),
            )
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Exchange entry cannot fill the sign-in form")
            null
        }
    }

    private fun heldEndpoint(entry: DeviceExchangeAccountEntry): ExchangeEndpoint? {
        val host = if (':' in entry.host) "[${entry.host}]" else entry.host
        return ExchangeEndpoint.parse("$host:${entry.port}")
    }

    private fun DeviceExchangeEnrollResult.isIdCollision(): Boolean =
        this is DeviceExchangeEnrollResult.Refused && reason == DeviceExchangeEnvelope.REASON_ID_COLLISION
}
