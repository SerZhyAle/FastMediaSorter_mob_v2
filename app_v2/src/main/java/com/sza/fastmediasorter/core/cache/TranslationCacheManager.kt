package com.sza.fastmediasorter.core.cache

import com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslatedTextBlock
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Per-page lens blocks of one document. Aliased so the map declarations stay readable. */
private typealias LensPageMap = ConcurrentHashMap<Int, List<TranslatedTextBlock>>

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

    // S1300: the outer maps used to be unbounded - every translated page of every document opened
    // during the process lifetime stayed resident (the app is built for days-long sessions), with
    // no eviction and no memory-pressure hook. Both levels are now MRU-capped by file, and
    // [trimForMemoryPressure] drops everything when the system asks for memory back.
    private const val MAX_CACHED_FILES = 8
    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f

    // Access-ordered map: reading a file's page map moves it to the most-recent end, so the eldest
    // entry LinkedHashMap evicts is genuinely the least recently used document.
    private const val ACCESS_ORDER = true

    private fun <V> mruCache(): MutableMap<String, V> = Collections.synchronizedMap(
        object : LinkedHashMap<String, V>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean =
                size > MAX_CACHED_FILES
        }
    )

    // Translation cache: fileName -> (pageIndex -> translated text)
    private val cache: MutableMap<String, ConcurrentHashMap<Int, String>> = mruCache()

    // Lens style cache: fileName -> (pageIndex -> list of translated blocks)
    private val lensCache: MutableMap<String, LensPageMap> = mruCache()

    /**
     * Get cached translation for specific file and page
     */
    fun getTranslation(filePath: String, pageIndex: Int): String? {
        return synchronized(cache) { cache[filePath] }?.get(pageIndex)
    }
    
    /**
     * Get cached lens translation blocks for specific file and page
     */
    fun getLensTranslation(filePath: String, pageIndex: Int): List<TranslatedTextBlock>? {
        return synchronized(lensCache) { lensCache[filePath] }?.get(pageIndex)
    }
    
    /**
     * Cache translation for specific file and page
     */
    fun putTranslation(filePath: String, pageIndex: Int, translatedText: String) {
        // S0729: atomic get-or-create of the per-file page map; putIfAbsent collapses the race
        // where Main and IO both insert the same fileName concurrently.
        // S1300: compound get-or-create under the wrapper's own monitor (the documented way to do
        // atomic multi-step work on Collections.synchronizedMap) - Map.putIfAbsent is API 24+.
        val pageMap = synchronized(cache) {
            cache[filePath] ?: ConcurrentHashMap<Int, String>().also { cache[filePath] = it }
        }
        pageMap[pageIndex] = translatedText
        Timber.d("Cached translation for $filePath page $pageIndex")
    }
    
    /**
     * Cache lens translation blocks for specific file and page
     */
    fun putLensTranslation(filePath: String, pageIndex: Int, blocks: List<TranslatedTextBlock>) {
        // S0729: same atomic get-or-create as putTranslation.
        val pageMap = synchronized(lensCache) {
            lensCache[filePath] ?: LensPageMap().also { lensCache[filePath] = it }
        }
        pageMap[pageIndex] = blocks
        Timber.d("Cached lens translation for $filePath page $pageIndex (${blocks.size} blocks)")
    }
    
    /**
     * Clear all translation cache
     */
    fun clearAll() {
        val size = synchronized(cache) {
            val n = cache.size
            cache.clear()
            n
        }
        val lensSize = synchronized(lensCache) {
            val n = lensCache.size
            lensCache.clear()
            n
        }
        Timber.d("Translation cache cleared ($size files, $lensSize lens files)")
    }

    /**
     * S1300: drop translations when the system asks for memory back. They are recomputable, unlike
     * user data, so releasing them is always safe - it only costs a re-translation on next open.
     */
    fun trimForMemoryPressure() {
        clearAll()
        Timber.i("Translation cache dropped under memory pressure")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        // Iteration over a synchronizedMap must hold its monitor (S1300: the map is also MRU-ordered,
        // so even a read reorders entries).
        val (totalFiles, totalPages) = synchronized(cache) { cache.size to cache.values.sumOf { it.size } }
        val (totalLensFiles, totalLensPages) =
            synchronized(lensCache) { lensCache.size to lensCache.values.sumOf { it.size } }
        return "Text: $totalFiles files/$totalPages pages, Lens: $totalLensFiles files/$totalLensPages pages"
    }
}
