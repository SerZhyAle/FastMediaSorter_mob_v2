package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Tracks PP-OCRv5 Paddle-Lite model files stored inside the app sandbox.
 */
class PaddleOcrModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class ModelVariant(val directoryName: String) {
        CYRILLIC("cyrillic"),
        EAST_SLAVIC("east_slavic")
    }

    data class ModelFiles(
        val detector: File,
        val classifier: File,
        val recognizer: File
    ) {
        val all: List<File> = listOf(detector, classifier, recognizer)
    }

    private val rootDir: File
        get() = File(context.filesDir, MODEL_ROOT_DIR)

    fun isModelInstalled(modelVariant: ModelVariant = ModelVariant.CYRILLIC): Boolean {
        return getModelFiles(modelVariant).all.all { file ->
            file.isFile && file.length() > 0L
        }
    }

    fun getModelDirectory(modelVariant: ModelVariant = ModelVariant.CYRILLIC): File {
        return File(rootDir, modelVariant.directoryName)
    }

    fun getModelFiles(modelVariant: ModelVariant = ModelVariant.CYRILLIC): ModelFiles {
        val modelDir = getModelDirectory(modelVariant)
        return ModelFiles(
            detector = File(modelDir, DET_MODEL_FILE),
            classifier = File(modelDir, CLS_MODEL_FILE),
            recognizer = File(modelDir, REC_MODEL_FILE)
        )
    }

    fun ensureModelDirectory(modelVariant: ModelVariant = ModelVariant.CYRILLIC): File {
        return getModelDirectory(modelVariant).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    suspend fun downloadModel(
        modelVariant: ModelVariant = ModelVariant.CYRILLIC,
        onProgress: ((String, Int, Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val modelDir = ensureModelDirectory(modelVariant)
        val artifacts = listOf(
            ModelArtifact.DETECTOR,
            ModelArtifact.CLASSIFIER,
            ModelArtifact.RECOGNIZER
        )

        artifacts.forEachIndexed { index, artifact ->
            onProgress?.invoke(artifact.outputName, index, artifacts.size)
            val outputFile = File(modelDir, artifact.outputName)
            downloadAndExtractNb(artifact, outputFile)
            if (!verifyChecksum(outputFile, artifact.sha256)) {
                outputFile.delete()
                return@withContext false
            }
        }

        onProgress?.invoke("complete", artifacts.size, artifacts.size)
        isModelInstalled(modelVariant)
    }

    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        return file.sha256().equals(expectedSha256, ignoreCase = true)
    }

    private fun downloadAndExtractNb(artifact: ModelArtifact, outputFile: File) {
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.download")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        URL(artifact.url).openStream().use { input ->
            GZIPInputStream(input).use { gzip ->
                extractTarEntry(gzip, artifact.entryName, tempFile)
            }
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }
        tempFile.renameTo(outputFile)
    }

    private fun extractTarEntry(input: GZIPInputStream, entryName: String, outputFile: File) {
        val header = ByteArray(TAR_BLOCK_SIZE)
        while (input.readFully(header)) {
            val name = header.readAscii(0, 100).trimEnd('\u0000')
            if (name.isBlank()) break

            val size = header.readAscii(124, 12).trim('\u0000', ' ').toLong(8)
            if (name.endsWith(entryName)) {
                FileOutputStream(outputFile).use { output ->
                    input.copyExactlyTo(output, size)
                }
                input.skipFully(size.paddingToTarBlock())
                return
            }

            input.skipFully(size + size.paddingToTarBlock())
        }

        error("PaddleOCR model entry not found: $entryName")
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun GZIPInputStream.readFully(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) return offset > 0
            offset += read
        }
        return true
    }

    private fun GZIPInputStream.copyExactlyTo(output: FileOutputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Unexpected EOF while extracting PaddleOCR model")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun GZIPInputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0L) {
                if (read() < 0) error("Unexpected EOF while skipping PaddleOCR model tar entry")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String {
        return copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
    }

    private fun Long.paddingToTarBlock(): Long {
        val remainder = this % TAR_BLOCK_SIZE
        return if (remainder == 0L) 0L else TAR_BLOCK_SIZE - remainder
    }

    private enum class ModelArtifact(
        val url: String,
        val entryName: String,
        val outputName: String,
        val sha256: String
    ) {
        DETECTOR(
            url = "https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models/PP-OCRv5_mobile_det.tar.gz",
            entryName = "PP-OCRv5_mobile_det.nb",
            outputName = DET_MODEL_FILE,
            sha256 = "BEE84FDB3D7C5F312A797DA81F2DD44C89088C08E1F58A51044A823CDDFBD90C"
        ),
        CLASSIFIER(
            url = "https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models/PP-LCNet_x0_25_textline_ori.tar.gz",
            entryName = "PP-LCNet_x0_25_textline_ori.nb",
            outputName = CLS_MODEL_FILE,
            sha256 = "A8955B3715620810789A012531F1E6DA796FB7D252B26B74D2886B350D698DBB"
        ),
        RECOGNIZER(
            url = "https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models/PP-OCRv5_mobile_rec.tar.gz",
            entryName = "PP-OCRv5_mobile_rec.nb",
            outputName = REC_MODEL_FILE,
            sha256 = "9D073B3EE01DEEE358BF929DD8952D4D355C9545F4A93D8070605581B4C21C0C"
        )
    }

    companion object {
        private const val TAR_BLOCK_SIZE = 512
        private const val MODEL_ROOT_DIR = "paddleocr"
        private const val DET_MODEL_FILE = "det.nb"
        private const val CLS_MODEL_FILE = "cls.nb"
        private const val REC_MODEL_FILE = "rec.nb"
    }
}
