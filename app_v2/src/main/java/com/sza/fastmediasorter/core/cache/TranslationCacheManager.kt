package com.sza.fastmediasorter.core.cache

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Global singleton for managing translation cache across all PDF files.
 * Cache structure: fileName -> (pageIndex -> translatedText)
 *
 * Cleared on:
 * - App startup (FastMediaSorterApp.onCreate)
 * - "Clear cache" button in settings
 * - NOT cleared when switching between files (preserves translations)
 *
 * Thread-safety (S0729): writes happen on Main (putTranslation) while reads/clears run on IO
 * (putLensTranslation/getTranslation/clearAll). Both levels use ConcurrentHashMap so concurrent
 * access cannot throw ConcurrentModificationException or lose entries; insertion uses an atomic
 * putIfAbsent (API-23 safe, unlike Map.computeIfAbsent which is API 24+).
 */
object TranslationCacheManager {

    // Translation cache: fileName -> (pageIndex -> translated text)
    private val cache = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()

    // Lens style cache: fileName -> (pageIndex -> list of translated blocks)
    private val lensCache = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock>>>()
    
    /**
     * Get cached translation for specific file and page
     */
    fun getTranslation(filePath: String, pageIndex: Int): String? {
        return cache[filePath]?.get(pageIndex)
    }
    
    /**
     * Get cached lens translation blocks for specific file and page
     */
    fun getLensTranslation(filePath: String, pageIndex: Int): List<com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock>? {
        return lensCache[filePath]?.get(pageIndex)
    }
    
    /**
     * Cache translation for specific file and page
     */
    fun putTranslation(filePath: String, pageIndex: Int, translatedText: String) {
        // S0729: atomic get-or-create of the per-file page map; putIfAbsent collapses the race
        // where Main and IO both insert the same fileName concurrently.
        val pageMap = cache[filePath] ?: ConcurrentHashMap<Int, String>().let { fresh ->
            cache.putIfAbsent(filePath, fresh) ?: fresh
        }
        pageMap[pageIndex] = translatedText
        Timber.d("Cached translation for $filePath page $pageIndex")
    }
    
    /**
     * Cache lens translation blocks for specific file and page
     */
    fun putLensTranslation(filePath: String, pageIndex: Int, blocks: List<com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock>) {
        // S0729: same atomic get-or-create as putTranslation.
        val pageMap = lensCache[filePath] ?: ConcurrentHashMap<Int, List<com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock>>().let { fresh ->
            lensCache.putIfAbsent(filePath, fresh) ?: fresh
        }
        pageMap[pageIndex] = blocks
        Timber.d("Cached lens translation for $filePath page $pageIndex (${blocks.size} blocks)")
    }
    
    /**
     * Clear all translation cache
     */
    fun clearAll() {
        val size = cache.size
        val lensSize = lensCache.size
        cache.clear()
        lensCache.clear()
        Timber.d("Translation cache cleared ($size files, $lensSize lens files)")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val totalFiles = cache.size
        val totalPages = cache.values.sumOf { it.size }
        val totalLensFiles = lensCache.size
        val totalLensPages = lensCache.values.sumOf { it.size }
        return "Text: $totalFiles files/$totalPages pages, Lens: $totalLensFiles files/$totalLensPages pages"
    }
}
