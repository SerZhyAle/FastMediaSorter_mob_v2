package com.sza.fastmediasorter.identity

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** [BlockStoreGateway] over the real GMS Block Store client (S2101). */
@Singleton
class GmsBlockStoreGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : BlockStoreGateway {

    private val client: BlockstoreClient by lazy { Blockstore.getClient(context) }

    override suspend fun isClientAvailable(): Boolean = runCatching { client }.isSuccess

    override suspend fun isEndToEndEncryptionAvailable(): Boolean =
        client.isEndToEndEncryptionAvailable.await()

    override suspend fun storeBytes(key: String, bytes: ByteArray, backupToCloud: Boolean) {
        val request = StoreBytesData.Builder()
            .setBytes(bytes)
            // Singular, unlike the retrieve and delete requests below - verified against
            // play-services-auth-blockstore 16.4.0, whose Builder declares setKey(String) only.
            .setKey(key)
            .setShouldBackupToCloud(backupToCloud)
            .build()
        client.storeBytes(request).await()
    }

    override suspend fun retrieveBytes(key: String): ByteArray? {
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(key)).build()
        return client.retrieveBytes(request).await().blockstoreDataMap[key]?.bytes
    }

    override suspend fun deleteBytes(key: String) {
        val request = DeleteBytesRequest.Builder().setKeys(listOf(key)).build()
        client.deleteBytes(request).await()
    }
}
