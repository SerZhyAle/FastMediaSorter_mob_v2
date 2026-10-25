package com.sza.fastmediasorter.data.broadcast.exchange

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.ProviderException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.X509KeyManager
import javax.security.auth.x500.X500Principal

/** The TLS server identity of one broadcast's tunnel: its context and the `SHA256:` pin of its leaf. */
class BroadcastTunnelIdentity(val context: SSLContext, val pin: String)

/** S4247: a tunnel key made before its broadcast: the Keystore [alias] and the `SHA256:` [pin] of its leaf. */
data class PreparedTunnelKey(val alias: String, val pin: String)

/**
 * S4240: the one-off tunnel identity of the live broadcast (contract DEVICE-EXCHANGE 27 item DM) - a P-256 key in
 * Android Keystore, whose self-signed leaf Keystore issues itself, made on first use and deleted when the broadcast
 * ends. The key never leaves Keystore; TLS signs through it.
 */
@Singleton
class BroadcastTunnelIdentities @Inject constructor() {

    private var held: Held? = null

    private class Held(val broadcastId: String, val alias: String, val identity: BroadcastTunnelIdentity)

    /** The identity of [broadcastId], made on first call; null when Keystore refused, so no tunnel is offered. */
    @Synchronized
    fun ensure(broadcastId: String): BroadcastTunnelIdentity? {
        held?.takeIf { it.broadcastId == broadcastId }?.let { return it.identity }
        end()
        val alias = ALIAS_PREFIX + UUID.randomUUID()
        val made = keystoreOrNull { create(alias) }
        held = made?.let { Held(broadcastId, alias, it) }
        return made
    }

    /**
     * S4247, DEVICE-EXCHANGE item DW: a tunnel key made before its broadcast starts, under its own alias prefix so
     * [end] never sweeps it. Null when Keystore refused, so the prepared link offers no tunnel.
     */
    @Synchronized
    fun prepare(): PreparedTunnelKey? {
        val alias = PREPARED_PREFIX + UUID.randomUUID()
        return keystoreOrNull { create(alias) }?.let { PreparedTunnelKey(alias, it.pin) }
    }

    /**
     * The prepared key under [alias] held as the live identity of [broadcastId], so the pin handed out before the
     * start is the one the tunnel presents. Null when the key is gone or Keystore refused.
     */
    @Synchronized
    fun adopt(broadcastId: String, alias: String): BroadcastTunnelIdentity? {
        held?.takeIf { it.broadcastId == broadcastId && it.alias == alias }?.let { return it.identity }
        end()
        val adopted = keystoreOrNull { identityOf(alias) }
        held = adopted?.let { Held(broadcastId, alias, it) }
        return adopted
    }

    /** Deletes the prepared key under [alias]; a revoked or expired prepared link keeps no key. */
    @Synchronized
    fun deletePrepared(alias: String) {
        if (alias.startsWith(PREPARED_PREFIX)) deleteWhere { it == alias }
    }

    /** Deletes every prepared key not in [keep], which a killed process or a lost record left behind. */
    @Synchronized
    fun sweepPrepared(keep: Set<String>) {
        deleteWhere { it.startsWith(PREPARED_PREFIX) && it !in keep && it != held?.alias }
    }

    /** The identity of [broadcastId] if it was made; never makes one. */
    @Synchronized
    fun current(broadcastId: String): BroadcastTunnelIdentity? =
        held?.takeIf { it.broadcastId == broadcastId }?.identity

    /** Deletes the live identity, and any a killed process left behind. */
    @Synchronized
    fun end() {
        // An adopted prepared key belongs to the broadcast that consumed it and dies with it.
        val adopted = held?.alias?.takeIf { it.startsWith(PREPARED_PREFIX) }
        held = null
        deleteWhere { it.startsWith(ALIAS_PREFIX) || it == adopted }
    }

    private fun deleteWhere(predicate: (String) -> Boolean) {
        try {
            val store = keyStore()
            store.aliases().toList().filter(predicate).forEach(store::deleteEntry)
        } catch (e: GeneralSecurityException) {
            Timber.w(e, "BroadcastTunnelIdentities: a tunnel key could not be deleted")
        } catch (e: IOException) {
            Timber.w(e, "BroadcastTunnelIdentities: Keystore could not be read to delete a tunnel key")
        }
    }

    private fun <T> keystoreOrNull(block: () -> T?): T? = try {
        block()
    } catch (e: GeneralSecurityException) {
        Timber.w(e, "BroadcastTunnelIdentities: Keystore refused the tunnel key, no tunnel is offered")
        null
    } catch (e: ProviderException) {
        Timber.w(e, "BroadcastTunnelIdentities: Keystore failed to make the tunnel key, no tunnel is offered")
        null
    } catch (e: IOException) {
        Timber.w(e, "BroadcastTunnelIdentities: Keystore could not be read, no tunnel is offered")
        null
    }

    private fun create(alias: String): BroadcastTunnelIdentity {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            // TLS signs a digest it computed itself through Keystore, so NONE is needed beside SHA-256.
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
            .setCertificateSubject(X500Principal(SUBJECT))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - CLOCK_SKEW_MS))
            .setCertificateNotAfter(Date(now + VALIDITY_MS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE).apply {
            initialize(spec)
            generateKeyPair()
        }
        return checkNotNull(identityOf(alias)) { "Keystore lost the tunnel key it has just made" }
    }

    private fun identityOf(alias: String): BroadcastTunnelIdentity? {
        val store = keyStore()
        val leaf = store.getCertificate(alias) as? X509Certificate ?: return null
        val key = store.getKey(alias, null) as? PrivateKey ?: return null
        val keyManager = SingleKeyManager(alias, key, leaf)
        val context = SSLContext.getInstance(TLS).apply { init(arrayOf(keyManager), null, null) }
        return BroadcastTunnelIdentity(context, BroadcastTunnelTls.pinOf(leaf))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    // Only this broadcast's key: a factory over the whole Keystore could pick another EC entry of the app.
    private class SingleKeyManager(
        private val alias: String,
        private val key: PrivateKey,
        private val leaf: X509Certificate,
    ) : X509KeyManager {
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String =
            alias

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(alias)

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(leaf)

        override fun getPrivateKey(alias: String?): PrivateKey = key

        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?):
            String? = null

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = emptyArray()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val ALIAS_PREFIX = "fms-broadcast-tunnel-"
        const val PREPARED_PREFIX = "fms-broadcast-prepared-"
        const val CURVE = "secp256r1"
        const val SUBJECT = "CN=fms-broadcast"
        const val TLS = "TLS"
        val CLOCK_SKEW_MS = TimeUnit.DAYS.toMillis(1)
        val VALIDITY_MS = TimeUnit.DAYS.toMillis(30)
    }
}
