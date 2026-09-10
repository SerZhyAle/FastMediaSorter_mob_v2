package com.sza.fastmediasorter.wear.data.documents

import android.content.ContentResolver
import android.net.Uri
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentContent
import com.sza.fastmediasorter.wear.domain.documents.WearDocumentFailure
import com.sza.fastmediasorter.wear.domain.repository.WearDocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val BYTES_PER_KB = 1024L

/**
 * Maximum bytes of a document held in memory at once.
 *
 * This is a CONSERVATIVE BOUND, not a measured figure. It equals `WearThumbnailBudget`'s
 * `MAX_HEAD_READ_BYTES`, the only other place the module pulls a file's bytes straight into one
 * in-memory array, so the reader claims no more heap than a path already known to survive on the
 * watch; decoded to UTF-16 the text costs roughly twice this again, still an order of magnitude
 * under the module's decoded-thumbnail cache.
 *
 * S2532 step 02.3 replaces it with the largest size that still leaves headroom under
 * `adb shell dumpsys meminfo` on the development watch, run against text files of increasing size.
 * Until that run happens the number is deliberately low rather than plausible.
 */
private const val MAX_DOCUMENT_BYTES = 128L * BYTES_PER_KB

private const val READ_CHUNK_BYTES = 8 * 1024

/** Above this share of unusable characters the bytes are called binary instead of shown as text. */
private const val MAX_UNREADABLE_CHAR_RATIO = 0.05

private const val REPLACEMENT_CHAR = '\uFFFD'

/** The control characters that belong in a text file and must not count against it. */
private const val READABLE_CONTROLS = "\t\n\r"

/**
 * Reads a document through the platform's content resolver, bounded by a byte cap.
 *
 * S2532: the decision itself is [readFrom], which takes a stream factory rather than a [Uri]. The
 * watch module's unit suite runs on the plain JVM, where `Uri` and `ContentResolver` are stubs that
 * throw, so the capping and decoding rules would otherwise be testable only under an instrumented
 * run - which is to say, not on the machine that builds them.
 */
@Singleton
class WearDocumentRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver
) : WearDocumentRepository {

    override val defaultCapBytes: Long = MAX_DOCUMENT_BYTES

    override suspend fun readText(uri: Uri, capBytes: Long): WearDocumentContent =
        withContext(Dispatchers.IO) {
            val result = readFrom(capBytes) { contentResolver.openInputStream(uri) }
            result
        }

    /**
     * The whole read, over any source of bytes.
     *
     * A factory rather than an open stream: a resolver that cannot find the file throws from the
     * open itself, and that failure has to map to [WearDocumentFailure.NOT_FOUND] exactly like a
     * resolver that politely returns `null`.
     */
    internal fun readFrom(capBytes: Long, openStream: () -> InputStream?): WearDocumentContent = try {
        openStream()?.use { stream -> decode(readCapped(stream, capBytes)) }
            ?: WearDocumentContent.Failure(WearDocumentFailure.NOT_FOUND)
    } catch (e: FileNotFoundException) {
        Timber.i(e, "Document is no longer there")
        WearDocumentContent.Failure(WearDocumentFailure.NOT_FOUND)
    } catch (e: SecurityException) {
        Timber.i(e, "Not permitted to read document")
        WearDocumentContent.Failure(WearDocumentFailure.NO_ACCESS)
    } catch (e: IOException) {
        Timber.w(e, "Document read broke off")
        WearDocumentContent.Failure(WearDocumentFailure.IO_ERROR)
    }

    private fun readCapped(stream: InputStream, capBytes: Long): CappedBytes {
        val limit = capBytes.coerceAtLeast(0L)
        val collected = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0L
        while (total < limit) {
            val wanted = minOf(chunk.size.toLong(), limit - total).toInt()
            val read = stream.read(chunk, 0, wanted)
            if (read <= 0) {
                break
            }
            collected.write(chunk, 0, read)
            total += read
        }
        // One byte past the cap is the only thing that tells a file ending exactly at the cap from a
        // file that goes on - the read alone cannot, since both fill the buffer.
        val truncated = total >= limit && stream.read() != -1
        return CappedBytes(collected.toByteArray(), truncated)
    }

    private fun decode(capped: CappedBytes): WearDocumentContent {
        val text = decodeText(capped.bytes)
        return when {
            text == null -> WearDocumentContent.Failure(WearDocumentFailure.UNREADABLE_ENCODING)
            text.isEmpty() -> WearDocumentContent.Empty
            else -> WearDocumentContent.Text(text, capped.truncated, capped.bytes.size.toLong())
        }
    }

    /**
     * UTF-8 first, ISO-8859-1 once as the retry, `null` when neither reads as text.
     *
     * The measure is the share of characters no reader can use: the replacement character UTF-8
     * substitutes for a malformed sequence, plus the control characters ISO-8859-1 happily produces
     * for arbitrary bytes. ISO-8859-1 maps every byte and therefore never reports a failure of its
     * own, which is why a ratio and not an exception is what separates a Latin-1 text file from a
     * binary one. A cap landing mid-character costs a single replacement character in a file of many
     * thousands, so truncation stays far under the threshold and never reads as a broken encoding.
     */
    private fun decodeText(bytes: ByteArray): String? {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (unreadableRatio(utf8) <= MAX_UNREADABLE_CHAR_RATIO) {
            utf8
        } else {
            String(bytes, Charsets.ISO_8859_1)
                .takeIf { unreadableRatio(it) <= MAX_UNREADABLE_CHAR_RATIO }
        }
    }

    private fun unreadableRatio(text: String): Double = if (text.isEmpty()) {
        0.0
    } else {
        text.count { isUnreadable(it) }.toDouble() / text.length
    }

    private fun isUnreadable(character: Char): Boolean =
        character == REPLACEMENT_CHAR ||
            (character.isISOControl() && character !in READABLE_CONTROLS)

    /** Not a data class: it wraps an array, whose generated equality compares identity and misleads. */
    private class CappedBytes(val bytes: ByteArray, val truncated: Boolean)
}
