package com.sza.fastmediasorter.data.hash

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.domain.hash.FileHasher
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject

class LocalFileHasher @Inject constructor(
    @ApplicationContext private val context: Context
) : FileHasher {

    override suspend fun computeHash(
        file: MediaFile,
        resource: MediaResource,
        maxBytes: Long
    ): String = withContext(Dispatchers.IO) {
        val stream = if (!file.contentUri.isNullOrBlank()) {
            context.contentResolver.openInputStream(Uri.parse(file.contentUri))
                ?: FileInputStream(file.path)
        } else {
            FileInputStream(file.path)
        }
        stream.use { inputStream ->
            md5Hex(inputStream, maxBytes)
        }
    }
}

/** Reads up to [maxBytes] from [stream] and returns MD5 hex digest. -1 = full stream. */
internal fun md5Hex(stream: java.io.InputStream, maxBytes: Long): String {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(32 * 1024) // 32 KB read buffer
    var remaining = maxBytes
    while (true) {
        val toRead = if (maxBytes < 0) buffer.size else minOf(buffer.size.toLong(), remaining).toInt()
        if (toRead <= 0) break
        val read = stream.read(buffer, 0, toRead)
        if (read == -1) break
        digest.update(buffer, 0, read)
        if (maxBytes >= 0) {
            remaining -= read
            if (remaining <= 0) break
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
