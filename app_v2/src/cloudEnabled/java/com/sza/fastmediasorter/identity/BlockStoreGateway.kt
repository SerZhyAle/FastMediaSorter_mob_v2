package com.sza.fastmediasorter.identity

/**
 * The four Block Store calls [BlockStoreTransferableSignInStore] makes, behind one narrow seam.
 *
 * It exists so the store's own behaviour - encode, size refusal, encryption gating, silent failure -
 * can be tested on the JVM. The GMS `BlockstoreClient` is final and its `Task` results are not
 * constructible in a plain unit test, so without this seam the store's error handling would be
 * verifiable only on a device, which is where the silent-failure requirement is hardest to observe.
 *
 * Implementations throw on failure; the store is what converts a throw into the port's failure value.
 */
interface BlockStoreGateway {

    /** True when the Block Store client can be obtained on this device. */
    suspend fun isClientAvailable(): Boolean

    /** True when a cloud backup of stored bytes would be end-to-end encrypted. */
    suspend fun isEndToEndEncryptionAvailable(): Boolean

    suspend fun storeBytes(key: String, bytes: ByteArray, backupToCloud: Boolean)

    /** The bytes stored under [key], or null when the key holds nothing. */
    suspend fun retrieveBytes(key: String): ByteArray?

    suspend fun deleteBytes(key: String)
}
