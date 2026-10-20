package com.sza.fastmediasorter.wear.data.exchange

import android.content.SharedPreferences
import com.sza.fastmediasorter.wear.di.EncryptedPrefs
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** S4228: one exchange folder as contract DEVICE-EXCHANGE 20 item BA names it - its host key and its root. */
data class WearFolderIdentity(val fingerprint: String, val root: String) {

    // Both name a private folder (DIAGNOSTIC-REPORT section 10).
    override fun toString(): String = "WearFolderIdentity(***)"

    companion object {
        /** Null for a source with no host key: such a folder has no identity to keep a PIN under. */
        fun of(source: NetworkSource): WearFolderIdentity? =
            source.hostKeyFingerprint?.takeIf { it.isNotBlank() }?.let { WearFolderIdentity(it, source.identityRoot) }
    }
}

/**
 * S4228: where the watch keeps the original PIN of a protected exchange folder (contract DEVICE-EXCHANGE 17 items AI
 * and AJ). The PIN only ever comes from the phone, which the person typed it into; the watch shows it nowhere.
 */
interface WearResourcePinStore {

    /**
     * S4230: ticks whenever a PIN is stored or forgotten, so a list built from "which folders have their PIN" is
     * rebuilt when the PIN arrives after the folder did. No tick carries a value: the PIN is never published.
     */
    val changes: Flow<Unit> get() = emptyFlow()

    suspend fun pin(identity: WearFolderIdentity): String?

    /** False when the secret store refused to keep it, so the caller never believes a lost write. */
    suspend fun save(identity: WearFolderIdentity, pin: String): Boolean

    suspend fun forget(identity: WearFolderIdentity)
}

/**
 * The PINs in the watch's Keystore-backed preferences, the same file the exchange entries live in, which backup and
 * device transfer exclude. The key is a digest of the identity, so the file names no folder.
 */
@Singleton
class EncryptedWearResourcePinStore @Inject constructor(
    @EncryptedPrefs private val prefs: SharedPreferences,
) : WearResourcePinStore {

    private val changed = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val changes: Flow<Unit> = changed

    override suspend fun pin(identity: WearFolderIdentity): String? =
        withContext(Dispatchers.IO) { prefs.getString(key(identity), null) }

    override suspend fun save(identity: WearFolderIdentity, pin: String): Boolean =
        withContext(Dispatchers.IO) {
            prefs.edit().putString(key(identity), pin).commit().also { stored -> if (stored) changed.tryEmit(Unit) }
        }

    override suspend fun forget(identity: WearFolderIdentity) {
        withContext(Dispatchers.IO) {
            prefs.edit().remove(key(identity)).commit()
            changed.tryEmit(Unit)
        }
    }

    private fun key(identity: WearFolderIdentity): String {
        val digest = MessageDigest.getInstance(DIGEST)
            .digest((identity.fingerprint + SEPARATOR + identity.root).toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + digest.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private companion object {
        const val KEY_PREFIX = "resource_pin_v1/"
        const val DIGEST = "SHA-256"
        const val SEPARATOR = "|"
    }
}
