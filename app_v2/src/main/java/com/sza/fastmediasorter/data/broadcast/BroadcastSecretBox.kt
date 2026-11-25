package com.sza.fastmediasorter.data.broadcast

import com.sza.fastmediasorter.data.local.db.CryptoHelper
import java.io.IOException

/**
 * S4675: the lock a saved broadcast's secrets (a relay group key, a tunnel pin) sit behind. A separate seam so the
 * storage format and its failure handling are testable without the Android Keystore.
 */
interface BroadcastSecretBox {

    /** The sealed form of [plain], or null when it cannot be sealed. */
    fun seal(plain: String): String?

    /** The text inside [box], or null when it cannot be opened: a lost or replaced device key, a damaged value. */
    fun open(box: String): String?
}

/**
 * A write was refused because a relay key or tunnel pin could not be sealed. The message names no value: a caller
 * that logs it, or the log of one that does not catch it, must not learn a secret.
 */
class BroadcastSecretStorageException : IOException("a broadcast secret could not be sealed for storage")

/** The device-bound box: AES-GCM under a key that never leaves the Android Keystore. */
object KeystoreBroadcastSecretBox : BroadcastSecretBox {

    // CryptoHelper answers "" for empty input and null for any failure; neither is a usable box or secret.
    override fun seal(plain: String): String? = CryptoHelper.encrypt(plain)?.takeIf { it.isNotEmpty() }

    override fun open(box: String): String? = CryptoHelper.decrypt(box)?.takeIf { it.isNotEmpty() }
}
