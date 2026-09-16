package com.sza.fastmediasorter.ui.companionimport.helpers

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.sza.fastmediasorter.data.companion.CompanionConfigDto
import com.sza.fastmediasorter.data.companion.CompanionConfigException
import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.domain.usecase.companion.CompanionImportResult
import com.sza.fastmediasorter.domain.usecase.companion.ImportCompanionConfigUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * S0984/S1195: reads, validates and imports an incoming `.fmscfg` attachment. Extracted from
 * `CompanionConfigImportActivity`, which now only resolves the intent and drives the dialogs
 * (CLAUDE.md Rule 3).
 */
class CompanionConfigImportManager @Inject constructor(
    private val parser: CompanionConfigParser,
    private val importUseCase: ImportCompanionConfigUseCase,
) {

    /** Parses the file behind [uri], or null when it is unreadable, oversized or not a valid config. */
    suspend fun readConfig(
        contentResolver: ContentResolver,
        uri: Uri,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): CompanionConfigDto? = withContext(ioDispatcher) { parseConfig(contentResolver, uri) }

    /** Runs the insert-only import of an already-parsed config. */
    suspend fun import(config: CompanionConfigDto): Result<CompanionImportResult> = importUseCase.import(config)

    /**
     * S3052: a broadcast descriptor saved through SAF carries `application/vnd.fms.bcast+json`, but a
     * file manager reading it back through MediaStore reports `application/octet-stream` - the type this
     * activity claims for `.fmscfg`. The name is the only surviving discriminator at that point, so the
     * caller re-routes such a document to the streams importer instead of rejecting it as a bad config.
     */
    fun isBroadcastDescriptor(contentResolver: ContentResolver, uri: Uri): Boolean {
        val name = displayName(contentResolver, uri) ?: uri.lastPathSegment.orEmpty()
        return name.endsWith(BROADCAST_DESCRIPTOR_EXTENSION, ignoreCase = true)
    }

    private fun displayName(contentResolver: ContentResolver, uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.onFailure { error ->
            Timber.w(error, "Reading the incoming document name failed")
        }.getOrNull()

    // Broad catch is an intentional import-boundary guard: any read/parse failure rejects the file
    // (transparent host) instead of crashing. (S0988: surfaced by the diff-scoped detekt gate.)
    @Suppress("TooGenericExceptionCaught")
    private fun parseConfig(contentResolver: ContentResolver, uri: Uri): CompanionConfigDto? = try {
        val bytes = contentResolver.openInputStream(uri)?.use { readCapped(it) }
        if (bytes == null) null else parser.parse(bytes)
    } catch (e: CompanionConfigException) {
        Timber.w(e, "Companion config rejected: ${e.reason}")
        null
    } catch (e: Exception) {
        Timber.w(e, "Companion config read failed")
        null
    }

    /** Reads at most [MAX_CONFIG_BYTES]; returns null if the stream is larger (guards this exported entry). */
    private fun readCapped(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_CONFIG_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 64 * 1024
        const val READ_CHUNK_BYTES = 8 * 1024
        const val BROADCAST_DESCRIPTOR_EXTENSION = ".fmsbcast"
    }
}
