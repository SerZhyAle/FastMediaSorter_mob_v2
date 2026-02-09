package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manages Tesseract OCR engine for offline text recognition.
 * Provides better support for Cyrillic than ML Kit's Latin recognizer.
 * Automatically downloads traineddata files from GitHub (tessdata_fast) if missing.
 */
class TesseractManager(private val context: Context) {

    private var tessApi: TessBaseAPI? = null
    private var isInitialized = false
    private var initializationFailed = false
    private var currentLanguage: String = "" // Track current language to allow re-init

    companion object {
        private const val TESS_DATA_DIR = "tessdata"
        // URL for fast models (smaller, faster, slightly less accurate)
        private const val TESS_DATA_URL_BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
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
            try {
                val dataPath = File(context.filesDir, "tesseract")
                val tessDataPath = File(dataPath, TESS_DATA_DIR)
                if (!tessDataPath.exists()) {
                    tessDataPath.mkdirs()
                }

                // Download data for requested language
                val dataDownloaded = checkAndDownloadData(tessDataPath, language)
                if (!dataDownloaded) {
                    Timber.e("Could not download Tesseract data for $language")
                    initializationFailed = true
                    return@withContext false
                }

                tessApi = TessBaseAPI()
                
                // Initialize for SINGLE language only (no mixing!)
                Timber.d("Initializing Tesseract with language: $language")
                val success = tessApi?.init(dataPath.absolutePath, language) ?: false
                
                if (success) {
                    isInitialized = true
                    currentLanguage = language
                    Timber.d("Tesseract initialized successfully for $language")
                } else {
                    Timber.e("Tesseract initialization failed for $language")
                    initializationFailed = true
                }
                success
            } catch (e: Exception) {
                Timber.e(e, "Error initializing Tesseract")
                initializationFailed = true
                false
            }
        }
    }

    private fun checkAndDownloadData(dir: File, lang: String): Boolean {
        val file = File(dir, "$lang.traineddata")
        if (file.exists() && file.length() > 0) return true

        Timber.d("Downloading Tesseract data for $lang...")
        return try {
            URL("$TESS_DATA_URL_BASE$lang.traineddata").openStream().use { input ->
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
        }
    }

    /**
     * Recognize text from bitmap using Tesseract.
     * @param bitmap Image to process
     * @param language Language code: "rus", "eng", etc.
     * @return Recognized text or null
     */
    suspend fun recognizeText(bitmap: Bitmap, language: String = "rus"): String? {
        val success = init(language)
        if (!success) return null

        return withContext(Dispatchers.Default) {
            try {
                tessApi?.setImage(bitmap)
                val text = tessApi?.utF8Text
                tessApi?.clear()
                text
            } catch (e: Exception) {
                Timber.e(e, "Tesseract recognition failed")
                null
            }
        }
    }

    data class OcrBlock(
        val text: String,
        val boundingBox: Rect,
        val confidence: Float
    )

    /**
     * Recognize text blocks with coordinates.
     * @param bitmap Image to process
     * @param language Language code: "rus", "eng", etc.
     */
    suspend fun recognizeTextBlocks(bitmap: Bitmap, language: String = "rus"): List<OcrBlock>? {
        val success = init(language)
        if (!success) return null

        return withContext(Dispatchers.Default) {
            try {
                tessApi?.setImage(bitmap)
                // Force recognition to populate results
                tessApi?.utF8Text 
                
                val iterator = tessApi?.resultIterator
                if (iterator == null) {
                    Timber.w("Tesseract resultIterator is null")
                    return@withContext null
                }
                
                val blocks = mutableListOf<OcrBlock>()
                
                Timber.d("TRANSLATION_DEBUG: Tesseract iteration START")
                var blockCount = 0
                iterator.begin()
                do {
                    blockCount++
                    // Use RIL_TEXTLINE for more precise text positioning (better than RIL_PARA)
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                    Timber.d("TRANSLATION_DEBUG: Block $blockCount - text length=${text?.length}, isBlank=${text.isNullOrBlank()}")
                    if (!text.isNullOrBlank()) {
                        val box = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                        val confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                        Timber.d("TRANSLATION_DEBUG: Block $blockCount - box=$box, confidence=$confidence")
                        Timber.d("TRANSLATION_DEBUG: Block $blockCount - text='${text.take(50)}'")
                        if (box != null) {
                            blocks.add(OcrBlock(text, box, confidence))
                        } else {
                            Timber.w("TRANSLATION_DEBUG: Block $blockCount - box is NULL!")
                        }
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                
                Timber.d("TRANSLATION_DEBUG: Tesseract iteration END - collected ${blocks.size} blocks from $blockCount iterations")
                
                // Filter duplicates and merge overlapping blocks
                val filteredBlocks = filterDuplicateAndOverlappingBlocks(blocks)
                Timber.d("TRANSLATION_DEBUG: After filtering: ${filteredBlocks.size} blocks (removed ${blocks.size - filteredBlocks.size} duplicates)")
                
                tessApi?.clear()
                filteredBlocks
            } catch (e: Exception) {
                Timber.e(e, "Tesseract block recognition failed")
                null
            }
        }
    }

    fun release() {
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
     * Filter duplicate and overlapping text blocks.
     * Removes blocks with identical or very similar text that overlap significantly.
     */
    private fun filterDuplicateAndOverlappingBlocks(blocks: List<OcrBlock>): List<OcrBlock> {
        if (blocks.isEmpty()) return blocks
        
        val result = mutableListOf<OcrBlock>()
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
