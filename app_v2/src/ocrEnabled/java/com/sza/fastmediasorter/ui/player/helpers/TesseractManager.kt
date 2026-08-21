package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.sza.fastmediasorter.domain.ocr.OcrTextBlock
import com.sza.fastmediasorter.domain.ocr.OcrWord
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages Tesseract OCR engine for offline text recognition.
 * Provides better support for Cyrillic than ML Kit's Latin recognizer.
 * Automatically downloads traineddata files from GitHub (tessdata_fast) if missing.
 */
class TesseractManager(private val context: Context) : OfflineOcrEngine {

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false
    private var initializationFailed = false
    private var currentLanguage: String = "" // Track current language to allow re-init

    companion object {
        private const val TESS_DATA_DIR = "tessdata"
        // URL for fast models (smaller, faster, slightly less accurate)
        private const val TESS_DATA_URL_BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
        // Match TesseractModelManager connect/read timeout to prevent indefinite hang on slow networks.
        private const val CONNECT_READ_TIMEOUT_MS = 15_000
    }

    /**
     * Initialize Tesseract engine for specific language.
     * Downloads training data if missing.
     * @param language Language code: "rus" for Russian, "eng" for English, etc.
     * @return true if initialization successful
     */
    suspend fun init(language: String = "rus"): Boolean {
        // Re-initialize if language changed
        if (isInitialized && currentLanguage != language) {
            Timber.d("Language changed from $currentLanguage to $language, re-initializing")
            release()
        }
        
        if (isInitialized && currentLanguage == language) return true
        if (initializationFailed) return false

        return withContext(Dispatchers.IO) {
            val modelManager = TesseractModelManager(context)

            try {
                // S0923: TessBaseAPI() triggers the native static initializer (System.loadLibrary of
                // jpeg/pngx/leptonica/tesseract). On a device where the delivered libs are not
                // name-resolvable it throws UnsatisfiedLinkError - a LinkageError, not an Exception - which
                // the catch (Exception) below would not catch. Guard it so it degrades to init-failure.
                tessApi = newTessBaseApiOrNull()
                if (tessApi == null) {
                    initializationFailed = true
                    return@withContext false
                }

                // Try to use high-quality model (best) if installed
                if (modelManager.isModelInstalled(language)) {
                    val bestDataPath = modelManager.getBestDataDir()
                    Timber.d("Attempting to initialize Tesseract with high-quality model ($language) from: ${bestDataPath.absolutePath}")
                    
                    val bestSuccess = tessApi?.init(bestDataPath.absolutePath, language) ?: false
                    if (bestSuccess) {
                        isInitialized = true
                        currentLanguage = language
                        // S1715 pillar 1: read the mode we have been recognising in before anything sets it.
                        // The native default is not statically readable from the 4.8.0 artefact, so this is
                        // the only way to learn it. Two init paths exist and neither is guaranteed to match.
                        Timber.d("S1715: page-seg mode after init (best model): ${tessApi?.pageSegMode}")
                        Timber.d("Tesseract initialized successfully using high-quality model for $language")
                        return@withContext true
                    } else {
                        Timber.w("Tesseract initialization failed with high-quality model for $language, cleaning up and falling back")
                        modelManager.deleteModel(language)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error initializing Tesseract with high-quality model for $language, cleaning up and falling back")
                modelManager.deleteModel(language)
                // Re-create API instance as it might be in an invalid state
                try {
                    tessApi?.recycle()
                } catch (_: Exception) {}
                tessApi = newTessBaseApiOrNull()
            }

            // Fallback to standard fast models
            try {
                val dataPath = File(context.filesDir, "tesseract")
                val tessDataPath = File(dataPath, TESS_DATA_DIR)
                if (!tessDataPath.exists()) {
                    tessDataPath.mkdirs()
                }

                // Download data for requested language (fast model)
                val dataDownloaded = checkAndDownloadData(tessDataPath, language)
                if (!dataDownloaded) {
                    Timber.e("Could not download Tesseract data for $language")
                    initializationFailed = true
                    return@withContext false
                }
                
                // Initialize for SINGLE language only (no mixing!)
                Timber.d("Initializing Tesseract with standard fast language model: $language")
                val success = tessApi?.init(dataPath.absolutePath, language) ?: false
                
                if (success) {
                    isInitialized = true
                    currentLanguage = language
                    // S1715 pillar 1: same reading on the fallback path - see the note above.
                    Timber.d("S1715: page-seg mode after init (fast model): ${tessApi?.pageSegMode}")
                    Timber.d("Tesseract initialized successfully with standard fast model for $language")
                } else {
                    Timber.e("Tesseract initialization failed for $language")
                    initializationFailed = true
                }
                success
            } catch (e: Exception) {
                Timber.e(e, "Error initializing Tesseract fallback standard model")
                initializationFailed = true
                false
            }
        }
    }

    /**
     * Construct [TessBaseAPI], catching the native static-initializer failure as a [LinkageError]
     * (S0923). Returns null when the Tesseract native library cannot be loaded on this device, so the
     * caller degrades to init-failure instead of crashing the process.
     */
    private fun newTessBaseApiOrNull(): TessBaseAPI? =
        try {
            TessBaseAPI()
        } catch (e: LinkageError) {
            Timber.w(e, "Tesseract native library unavailable on this device")
            null
        }

    private fun checkAndDownloadData(dir: File, lang: String): Boolean {
        val file = File(dir, "$lang.traineddata")
        if (file.exists() && file.length() > 0) return true

        Timber.d("Downloading Tesseract data for $lang...")
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("$TESS_DATA_URL_BASE$lang.traineddata").openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_READ_TIMEOUT_MS
            connection.readTimeout = CONNECT_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Timber.d("Downloaded $lang.traineddata")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to download $lang.traineddata")
            // Clean up partial file
            if (file.exists()) file.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Recognize text from bitmap using Tesseract.
     * @param bitmap Image to process
     * @param languageCode Language code: "rus", "eng", etc.
     * @return Recognized text or null
     */
    override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): String? {
        val success = init(languageCode)
        if (!success) return null

        return withContext(Dispatchers.Default) {
            try {
                val preparedBitmap = prepareBitmapForTesseract(bitmap)
                if (preparedBitmap == null) {
                    Timber.w("Tesseract recognition skipped: bitmap is invalid")
                    return@withContext null
                }

                tessApi?.setImage(preparedBitmap)
                val text = tessApi?.utF8Text
                tessApi?.clear()
                text
            } catch (e: Exception) {
                if (isNonCriticalBitmapReadError(e)) {
                    Timber.w("Tesseract recognition skipped: failed to read bitmap")
                } else {
                    Timber.e(e, "Tesseract recognition failed")
                }
                null
            }
            // NOTE: Do NOT recycle preparedBitmap here.
            // Tesseract native code may still hold a reference after cancellation → SIGSEGV.
        }
    }

    /**
     * Recognize text blocks with coordinates.
     * @param bitmap Image to process
     * @param languageCode Language code: "rus", "eng", etc.
     */
    override suspend fun recognizeTextBlocks(bitmap: Bitmap, languageCode: String): List<OcrTextBlock>? {
        val success = init(languageCode)
        if (!success) return null

        return withContext(Dispatchers.Default) {
            try {
                val preparedBitmap = prepareBitmapForTesseract(bitmap)
                if (preparedBitmap == null) {
                    Timber.w("Tesseract block recognition skipped: bitmap is invalid")
                    return@withContext null
                }

                tessApi?.setImage(preparedBitmap)
                // Force recognition to populate results
                tessApi?.utF8Text 
                
                val iterator = tessApi?.resultIterator
                if (iterator == null) {
                    Timber.w("Tesseract resultIterator is null")
                    return@withContext null
                }
                
                Timber.d("TRANSLATION_DEBUG: Tesseract iteration START")
                val blocks = collectLinesWithWords(iterator)
                Timber.d("TRANSLATION_DEBUG: Tesseract iteration END - collected ${blocks.size} lines")
                
                // Filter duplicates and merge overlapping blocks
                val filteredBlocks = filterDuplicateAndOverlappingBlocks(blocks)
                Timber.d("TRANSLATION_DEBUG: After filtering: ${filteredBlocks.size} blocks (removed ${blocks.size - filteredBlocks.size} duplicates)")
                
                tessApi?.clear()
                filteredBlocks
            } catch (e: Exception) {
                if (isNonCriticalBitmapReadError(e)) {
                    Timber.w("Tesseract block recognition skipped: failed to read bitmap")
                } else {
                    Timber.e(e, "Tesseract block recognition failed")
                }
                null
            }
            // NOTE: Do NOT recycle preparedBitmap here.
            // Tesseract native code may still hold a reference after cancellation → SIGSEGV.
        }
    }

    private fun prepareBitmapForTesseract(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        return if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isPremultiplied) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun isNonCriticalBitmapReadError(error: Throwable): Boolean {
        val message = error.message ?: ""
        return error is RuntimeException && message.contains("Failed to read bitmap", ignoreCase = true)
    }

    override fun release() {
        try {
            tessApi?.stop()
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
            currentLanguage = ""
        } catch (e: Exception) {
            Timber.e(e, "Error releasing Tesseract")
        }
    }
    
    /**
     * S1711: walk the result at word level and assemble one [OcrTextBlock] per text line, carrying the words
     * that line is made of.
     *
     * The line-level text, box and confidence are read at `RIL_TEXTLINE` exactly as before, so what reaches
     * the caller is unchanged apart from the added words. No second recognition pass is involved: the words
     * are already computed inside the result this walk reads.
     */
    private fun collectLinesWithWords(iterator: ResultIterator): List<OcrTextBlock> {
        Timber.d("S1711: Tesseract word-level line collection entered")
        val blocks = mutableListOf<OcrTextBlock>()
        val words = mutableListOf<OcrWord>()
        iterator.begin()
        do {
            if (iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)) {
                words.clear()
            }
            val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            val wordBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
            if (!wordText.isNullOrBlank() && wordBox != null) {
                val wordConfidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                words.add(OcrWord(wordText.trim(), wordBox, wordConfidence))
            }
            val lineEnds = iterator.isAtFinalElement(
                TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE,
                TessBaseAPI.PageIteratorLevel.RIL_WORD
            )
            if (lineEnds) {
                closeLine(iterator, words, blocks)
            }
        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        return blocks
    }

    /** Emit the line the iterator currently sits in, with [words] as the words collected for it. */
    private fun closeLine(
        iterator: ResultIterator,
        words: MutableList<OcrWord>,
        blocks: MutableList<OcrTextBlock>,
    ) {
        val lineText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
        val lineBox = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
        if (!lineText.isNullOrBlank() && lineBox != null) {
            val lineConfidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
            blocks.add(OcrTextBlock(lineText, lineBox, lineConfidence, words.toList()))
        }
        words.clear()
    }

    /**
     * Filter duplicate and overlapping text blocks.
     * Removes blocks with identical or very similar text that overlap significantly.
     */
    private fun filterDuplicateAndOverlappingBlocks(blocks: List<OcrTextBlock>): List<OcrTextBlock> {
        if (blocks.isEmpty()) return blocks
        
        val result = mutableListOf<OcrTextBlock>()
        val processed = mutableSetOf<Int>()
        
        for (i in blocks.indices) {
            if (i in processed) continue
            
            val block1 = blocks[i]
            var isDuplicate = false
            
            // Check against already added blocks
            for (existingBlock in result) {
                // Check if text is similar (normalize whitespace for comparison)
                val text1 = block1.text.trim().replace("\\s+".toRegex(), " ")
                val text2 = existingBlock.text.trim().replace("\\s+".toRegex(), " ")
                
                // If texts are identical or one contains the other
                val textSimilar = text1 == text2 || 
                                  text1.contains(text2) || 
                                  text2.contains(text1) ||
                                  calculateTextSimilarity(text1, text2) > 0.8f
                
                if (textSimilar) {
                    // Check if bounding boxes overlap significantly
                    val overlap = calculateOverlapPercentage(block1.boundingBox, existingBlock.boundingBox)
                    if (overlap > 0.5f) {
                        // This is a duplicate - keep the one with higher confidence
                        if (block1.confidence > existingBlock.confidence) {
                            // Replace existing with this one
                            result.remove(existingBlock)
                            result.add(block1)
                        }
                        isDuplicate = true
                        break
                    }
                }
            }
            
            if (!isDuplicate) {
                result.add(block1)
            }
            
            processed.add(i)
        }
        
        return result
    }
    
    /**
     * Calculate text similarity using simple character overlap ratio.
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        if (text1 == text2) return 1f
        if (text1.isEmpty() || text2.isEmpty()) return 0f
        
        val shorter = if (text1.length < text2.length) text1 else text2
        val longer = if (text1.length < text2.length) text2 else text1
        
        var matchCount = 0
        for (char in shorter) {
            if (longer.contains(char, ignoreCase = true)) {
                matchCount++
            }
        }
        
        return matchCount.toFloat() / shorter.length
    }
    
    /**
     * Calculate overlap percentage between two rectangles.
     * Returns value from 0 (no overlap) to 1 (complete overlap).
     */
    private fun calculateOverlapPercentage(rect1: Rect, rect2: Rect): Float {
        if (!Rect.intersects(rect1, rect2)) return 0f
        
        val intersect = Rect(rect1)
        if (!intersect.intersect(rect2)) return 0f
        
        val intersectArea = intersect.width() * intersect.height()
        val rect1Area = rect1.width() * rect1.height()
        val rect2Area = rect2.width() * rect2.height()
        
        // Use the smaller rectangle as reference
        val smallerArea = minOf(rect1Area, rect2Area)
        if (smallerArea == 0) return 0f
        
        return intersectArea.toFloat() / smallerArea
    }
}
