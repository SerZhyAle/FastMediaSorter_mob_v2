package com.sza.fastmediasorter.data.broadcast.auth

import com.sza.fastmediasorter.core.di.DefaultDispatcher
import com.sza.fastmediasorter.data.broadcast.BroadcastAuthDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The credential pairs of the live protected broadcast (contract DEVICE-EXCHANGE item BF), in memory only: the own
 * pair for the account's devices, the outward link's pair whose password the person hands over separately, and an
 * internal pair the phone's own relay uploader and RTSP gate use to reach its protected servers. Tunnelled
 * listeners arrive from loopback too, so no address may skip the check and the phone answers like anyone else.
 *
 * A broadcast is protected from [begin] - the moment it is announced through an exchange server - until [end]; a
 * LAN-only broadcast never begins one and stays the secret link. S4405: an announced broadcast whose person chose
 * [BroadcastLinkChoice.Open] holds no link pair and is not protected either; the own and guest pairs stay, so a
 * later choice of a password protects them again without a new broadcast.
 */
@Singleton
class BroadcastCredentialRegistry @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    private data class Live(
        val broadcastId: String,
        val own: BroadcastCredentialPair,
        val choice: BroadcastLinkChoice,
        val link: BroadcastCredentialPair?,
        val guests: Map<String, BroadcastCredentialPair> = emptyMap(),
    )

    /** Fixed for the process: the RTSP library server is closed with it before any broadcast is announced. */
    val internalPair: BroadcastCredentialPair = BroadcastCredentialPair(
        user = INTERNAL_USER_PREFIX + randomString(INTERNAL_USER_SUFFIX_LENGTH),
        password = randomString(PASSWORD_LENGTH),
    )

    @Volatile
    private var live: Live? = null
    private val verifierCache = ConcurrentHashMap<BroadcastCredentialPair, BroadcastVerifier>()

    private val _revision = MutableStateFlow(0L)

    /** Bumps on every change of the pair set, so the announcer sends `broadcast-update`. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val isProtected: Boolean get() = live?.link != null

    val ownPair: BroadcastCredentialPair? get() = live?.own

    val linkPair: BroadcastCredentialPair? get() = live?.link

    @Synchronized
    fun begin(broadcastId: String, choice: BroadcastLinkChoice = BroadcastLinkChoice.Generated) {
        if (live?.broadcastId == broadcastId) return
        verifierCache.clear()
        live = Live(broadcastId, newPair(OWN_USER), choice, linkPairFor(choice))
        _revision.value += 1
    }

    /**
     * S4405: replaces or drops only the outward link's pair, the way "New password" revokes one (LIVE-BROADCAST item
     * R); the own and guest pairs are untouched. Returns the new link pair, null for an open link or while nothing
     * is announced. The same choice again changes nothing, so re-confirming "automatic" keeps the handed-out password.
     */
    @Synchronized
    fun applyLink(choice: BroadcastLinkChoice): BroadcastCredentialPair? {
        val current = live ?: return null
        if (current.choice != choice) {
            current.link?.let(verifierCache::remove)
            live = current.copy(choice = choice, link = linkPairFor(choice))
            _revision.value += 1
        }
        return live?.link
    }

    @Synchronized
    fun end() {
        if (live == null) return
        live = null
        verifierCache.clear()
        _revision.value += 1
    }

    /** Revokes the outward link's password and makes a new one; the user name, and so the shared code, stays. */
    @Synchronized
    fun renewLink(): BroadcastCredentialPair? {
        val current = live?.takeIf { it.choice == BroadcastLinkChoice.Generated } ?: return null
        current.link?.let(verifierCache::remove)
        val renewed = current.copy(link = newPair(LINK_USER))
        live = renewed
        _revision.value += 1
        return renewed.link
    }

    /**
     * Makes the pair of one guest grant (item BD) and returns it; null while nothing is protected. The user name is
     * random, never the grant id, which is a capability.
     */
    @Synchronized
    fun addGuest(grantId: String): BroadcastCredentialPair? {
        val current = live ?: return null
        val pair = newPair(GUEST_USER_PREFIX + randomString(GUEST_USER_SUFFIX_LENGTH))
        live = current.copy(guests = current.guests + (grantId to pair))
        _revision.value += 1
        return pair
    }

    /** Revokes one guest grant's pair; the next `broadcast-update` drops its verifier (item BF). */
    @Synchronized
    fun removeGuest(grantId: String) {
        val current = live ?: return
        val pair = current.guests[grantId] ?: return
        verifierCache.remove(pair)
        live = current.copy(guests = current.guests - grantId)
        _revision.value += 1
    }

    fun guestPair(grantId: String): BroadcastCredentialPair? = live?.guests?.get(grantId)

    /** Every pair a server accepts, or null while the broadcast is not protected. */
    fun pairs(): List<BroadcastCredentialPair>? {
        val current = live
        val link = current?.link ?: return null
        return listOf(current.own, link) + current.guests.values + internalPair
    }

    /** The descriptor's `auth` marker: the link's user, the one an outsider answers with. */
    fun authMarker(): BroadcastAuthDto? = live?.link?.let { BroadcastAuthDto(user = it.user) }

    /** The relay's verifiers for the own, link and guest pairs; PBKDF2 runs once per pair, off the caller's thread. */
    suspend fun verifiers(): List<BroadcastVerifier> {
        val current = live
        val link = current?.link ?: return emptyList()
        return withContext(defaultDispatcher) {
            (listOf(current.own, link) + current.guests.values).map { pair ->
                verifierCache.getOrPut(pair) { BroadcastVerifiers.create(pair) }
            }
        }
    }

    private fun newPair(user: String) = BroadcastCredentialPair(user, randomString(PASSWORD_LENGTH))

    private fun linkPairFor(choice: BroadcastLinkChoice): BroadcastCredentialPair? = when (choice) {
        BroadcastLinkChoice.Generated -> newPair(LINK_USER)
        is BroadcastLinkChoice.Custom -> BroadcastCredentialPair(LINK_USER, choice.password)
        BroadcastLinkChoice.Open -> null
    }

    private companion object {
        const val OWN_USER = "account"
        const val LINK_USER = "listener"
        const val INTERNAL_USER_PREFIX = "fms-"
        const val GUEST_USER_PREFIX = "guest-"
        const val GUEST_USER_SUFFIX_LENGTH = 8
        const val INTERNAL_USER_SUFFIX_LENGTH = 8
        const val PASSWORD_LENGTH = 12

        // No look-alike characters: the link password is read off the screen and typed by a person.
        const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()

        fun randomString(length: Int): String = buildString {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }
}
