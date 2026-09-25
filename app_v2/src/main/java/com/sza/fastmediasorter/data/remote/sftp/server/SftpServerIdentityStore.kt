package com.sza.fastmediasorter.data.remote.sftp.server

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerClientCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.util.security.SecurityUtils
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the embedded SFTP server's durable identity: the SSH host keypair and the credential a
 * client needs to log in.
 *
 * The host keypair is generated once and then reused for the life of the installation. A key that
 * changed between server starts would make every paired client - FMS pins the fingerprint from the
 * pairing code - refuse the connection as a possible man-in-the-middle.
 *
 * ECDSA P-256 through the platform JCA is used rather than Ed25519: it needs neither Bouncy Castle
 * nor EdDSA on the classpath, both of which MINA SSHD treats as optional.
 */
@Singleton
class SftpServerIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val settingsStore: SftpServerSettingsStore,
) {

    private val mutex = Mutex()

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    suspend fun hostKeyPair(): KeyPair = cachedKeyPair ?: mutex.withLock {
        cachedKeyPair ?: (loadKeyPair() ?: generateAndStoreKeyPair()).also { cachedKeyPair = it }
    }

    suspend fun hostKeyFingerprint(): String = KeyUtils.getFingerPrint(hostKeyPair().public)

    /**
     * Returns the credential set, generating a random password the first time password mode is
     * read without one, so a freshly enabled server is never reachable with an empty password.
     */
    suspend fun clientCredentials(): SftpServerClientCredentials {
        val settings = settingsStore.snapshot()
        val password = if (settings.authMode == SftpServerAuthMode.PASSWORD) {
            settings.password ?: generatePassword().takeIf { settingsStore.setPassword(it) }
        } else {
            null
        }
        return SftpServerClientCredentials(
            username = settings.username,
            authMode = settings.authMode,
            password = password,
            hostKeyFingerprint = hostKeyFingerprint(),
        )
    }

    /** Replaces the password with a fresh random one; false when the Keystore refused to store it. */
    suspend fun regeneratePassword(): Boolean = settingsStore.setPassword(generatePassword())

    private suspend fun loadKeyPair(): KeyPair? {
        val prefs = dataStore.data.first()
        val publicEncoded = prefs[keyHostPublic]
        val privatePlain = prefs[keyHostPrivateEncrypted]?.let(CryptoHelper::decrypt)
        if (publicEncoded == null || privatePlain.isNullOrEmpty()) {
            if (publicEncoded != null) {
                Timber.w("SftpServerIdentityStore: stored host key unreadable, generating a new one")
            }
            return null
        }
        return decodeKeyPair(publicEncoded, privatePlain)
    }

    private fun decodeKeyPair(publicEncoded: String, privateEncoded: String): KeyPair? = try {
        val factory = KeyFactory.getInstance(KEY_ALGORITHM)
        KeyPair(
            factory.generatePublic(X509EncodedKeySpec(Base64.decode(publicEncoded, Base64.NO_WRAP))),
            factory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateEncoded, Base64.NO_WRAP))),
        )
    } catch (e: GeneralSecurityException) {
        Timber.w(e, "SftpServerIdentityStore: stored host key corrupt, generating a new one")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "SftpServerIdentityStore: stored host key not valid base64, generating a new one")
        null
    }

    private suspend fun generateAndStoreKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        val keyPair = generator.generateKeyPair()
        val privateEncrypted = CryptoHelper.encrypt(
            Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP),
        )
        if (privateEncrypted == null) {
            // The key still serves this process; the next start regenerates, which paired clients report.
            Timber.e("SftpServerIdentityStore: host key could not be encrypted, not persisted")
            return keyPair
        }
        dataStore.edit { prefs ->
            prefs[keyHostPublic] = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            prefs[keyHostPrivateEncrypted] = privateEncrypted
        }
        return keyPair
    }

    private fun generatePassword(): String {
        val random = SecureRandom()
        return buildString(GENERATED_PASSWORD_LENGTH) {
            repeat(GENERATED_PASSWORD_LENGTH) { append(PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)]) }
        }
    }

    private companion object {
        init {
            // The APK bundles bcprov, so MINA's BC registrar looks supported and then asks for algorithms by the
            // name "BC" - which on Android resolves to the stripped platform provider that lost SHA-256 and
            // SHA256withECDSA in Android 9. Disabling the registrar sends MINA to the unnamed platform providers.
            // It must run before MINA's first security call: this class is initialised before the controller
            // touches MINA, because the controller receives it through its constructor.
            SecurityUtils.setAPrioriDisabledProvider(SecurityUtils.BOUNCY_CASTLE, true)
        }

        const val KEY_ALGORITHM = "EC"
        const val CURVE_NAME = "secp256r1"
        const val GENERATED_PASSWORD_LENGTH = 16

        // No 0/O/1/l/I: the password is read off a screen and typed on another device.
        const val PASSWORD_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        val keyHostPublic = stringPreferencesKey("sftp_server_host_public")
        val keyHostPrivateEncrypted = stringPreferencesKey("sftp_server_host_private_enc")
    }
}
