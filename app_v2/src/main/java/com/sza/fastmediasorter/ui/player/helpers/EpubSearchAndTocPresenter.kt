package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.ui.player.sheets.EpubSearchBottomSheet
import com.sza.fastmediasorter.ui.player.sheets.EpubTocBottomSheet
import io.documentnode.epub4j.domain.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import timber.log.Timber

/**
 * Handles EPUB in-chapter search, cross-chapter full-text search, and
 * Table-of-Contents presentation (metadata TOC + spine fallback).
 *
 * Extracted from EpubViewerManager (S0002 Wave 42) to keep that class under 1500 LOC.
 *
 * Requires:
 * - [webViewProvider] - current WebView instance (nullable; searches silently no-op when null)
 * - [bookProvider] - current loaded Book (nullable)
 * - [currentChapterIndexProvider] - current chapter index
 * - [chapterCountProvider] - total chapter count
 * - [coroutineScope] - scope for background search jobs
 * - [onNavigateToChapter] - called when user selects a TOC/search result
 */
class EpubSearchAndTocPresenter(
    // S0380: root instead of ActivityPlayerUnifiedBinding (only used for context; works on trimmed layouts).
    private val root: View,
    private val coroutineScope: CoroutineScope,
    private val webViewProvider: () -> WebView?,
    private val bookProvider: () -> Book?,
    private val currentChapterIndexProvider: () -> Int,
    private val chapterCountProvider: () -> Int,
    private val onNavigateToChapter: suspend (Int) -> Unit
) {

    private companion object {
        // Limit cross-chapter results to prevent OOM (M-3 fix)
        const val MAX_SEARCH_RESULTS = 500

        /** Characters of surrounding text kept on each side of a match in the result snippet. */
        const val SNIPPET_CONTEXT_WINDOW = 60

        /** Lets the WebView finish loading the target chapter before the in-page highlight runs. */
        const val HIGHLIGHT_DELAY_MS = 300L
    }

    // ── In-chapter search ────────────────────────────────────────────────────

    /**
     * Search for text in the current EPUB chapter using WebView's built-in search.
     * WebView.findAllAsync() highlights matches automatically.
     *
     * @param query Search query (blank → clears highlights)
     * @param onResult Callback with number of matches found
     */
    fun searchInEpub(query: String, onResult: (Int) -> Unit = {}) {
        val webView = webViewProvider() ?: run {
            onResult(0)
            return
        }

        if (query.isBlank()) {
            webView.clearMatches()
            onResult(0)
            Timber.d("EPUB search cleared")
            return
        }

        // Set listener BEFORE triggering search (M-1 fix: listener must be set before findAllAsync)
        webView.setFindListener { _, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onResult(numberOfMatches)
                Timber.d("EPUB search for '$query': $numberOfMatches matches in current chapter")
            }
        }

        webView.findAllAsync(query)
    }

    /** Navigate to the next search match in the current chapter. */
    fun nextSearchMatch() {
        webViewProvider()?.findNext(true)
        Timber.d("EPUB: Next search match")
    }

    /** Navigate to the previous search match in the current chapter. */
    fun previousSearchMatch() {
        webViewProvider()?.findNext(false)
        Timber.d("EPUB: Previous search match")
    }

    /** Clear search highlighting in WebView. */
    fun clearSearch() {
        webViewProvider()?.clearMatches()
        Timber.d("EPUB: Search cleared")
    }

    // ── Cross-chapter search ─────────────────────────────────────────────────

    /**
     * Show cross-chapter search BottomSheet dialog.
     * Scans all spine chapters for matches, displays results with context snippets.
     * Tapping a result navigates to that chapter and highlights the match in-page.
     */
    fun showCrossChapterSearch() {
        val book = bookProvider()
        if (book == null) {
            Timber.w("EPUB: Cannot search - no book loaded")
            return
        }
        val host = root.context as? FragmentActivity
        if (host == null) {
            Timber.w("EPUB: host is not a FragmentActivity, cannot show search sheet")
            return
        }

        EpubSearchBottomSheet.newInstance(
            maxResults = MAX_SEARCH_RESULTS,
            searchProvider = { query -> scanChaptersFor(book, query) },
            onResultSelected = { result, query ->
                // Navigate to chapter and highlight using onPageFinished (M-2 fix)
                coroutineScope.launch {
                    onNavigateToChapter(result.chapterIndex)
                    // Trigger in-page highlight after WebView finishes loading
                    delay(HIGHLIGHT_DELAY_MS)
                    searchInEpub(query) {}
                }
            },
        ).show(host.supportFragmentManager, EpubSearchBottomSheet.TAG)

        Timber.d("EPUB: Cross-chapter search sheet shown")
    }

    /**
     * Scan every spine chapter for [query] on IO, capped at [MAX_SEARCH_RESULTS] to prevent OOM
     * (M-3 fix). Cooperatively cancellable - the caller's sheet cancels the job on dismissal.
     */
    private suspend fun scanChaptersFor(book: Book, query: String): List<EpubSearchResult> =
        withContext(Dispatchers.IO) {
            val allResults = mutableListOf<EpubSearchResult>()
            val spine = book.spine

            for (i in 0 until spine.spineReferences.size) {
                yield() // Check cancellation

                val spineRef = spine.spineReferences[i]
                val resource = spineRef.resource
                val title = resource.title?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}"

                try {
                    val htmlContent = String(resource.data, Charsets.UTF_8)
                    val plainText = Jsoup.parse(htmlContent).text()
                    collectMatches(plainText, query, i, title, allResults)
                } catch (e: Exception) {
                    e.warnUnlessCancellation("EPUB: Error searching chapter $i")
                }

                // Stop scanning more chapters if limit reached (M-3 fix)
                if (allResults.size >= MAX_SEARCH_RESULTS) break
            }

            Timber.d("EPUB: Cross-chapter search for '$query': ${allResults.size} results")
            allResults
        }

    private fun collectMatches(
        plainText: String,
        query: String,
        chapterIndex: Int,
        chapterTitle: String,
        output: MutableList<EpubSearchResult>
    ) {
        val lowerText = plainText.lowercase()
        val lowerQuery = query.lowercase()
        var searchFrom = 0

        while (searchFrom < lowerText.length && output.size < MAX_SEARCH_RESULTS) {
            val pos = lowerText.indexOf(lowerQuery, searchFrom)
            if (pos < 0) break

            val snippetStart = (pos - SNIPPET_CONTEXT_WINDOW).coerceAtLeast(0)
            val snippetEnd = (pos + query.length + SNIPPET_CONTEXT_WINDOW).coerceAtMost(plainText.length)
            val snippet = buildString {
                if (snippetStart > 0) append("…")
                append(plainText.substring(snippetStart, snippetEnd))
                if (snippetEnd < plainText.length) append("…")
            }

            output.add(
                EpubSearchResult(
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    contextSnippet = snippet,
                    matchStartInText = pos,
                    matchedText = plainText.substring(pos, pos + query.length)
                )
            )

            searchFrom = pos + query.length
        }
    }

    // ── Table of Contents ────────────────────────────────────────────────────

    /**
     * Show Table of Contents dialog for quick chapter navigation.
     * Uses metadata TOC when available; falls back to spine listing.
     */
    fun showTableOfContents() {
        val book = bookProvider()
        if (book == null) {
            Timber.w("EPUB: Cannot show TOC - no book loaded")
            return
        }

        val context = root.context
        val toc = book.tableOfContents
        val tocReferences = toc.tocReferences

        if (tocReferences.isEmpty()) {
            // Fallback: use spine if no TOC available
            showSpineBasedToc(book, context)
            return
        }

        // Build chapter list from TOC (flatten nested structure)
        val chapters = mutableListOf<Pair<String, Int>>() // Title to SpineIndex
        flattenToc(book, tocReferences, chapters, 0)

        if (chapters.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.epub_no_toc),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        showTocBottomSheet(book, context, chapters)
    }

    /**
     * Recursively flatten TOC structure into a simple list.
     */
    private fun flattenToc(
        book: Book,
        tocRefs: List<io.documentnode.epub4j.domain.TOCReference>,
        output: MutableList<Pair<String, Int>>,
        depth: Int
    ) {
        for (ref in tocRefs) {
            val indent = "  ".repeat(depth)
            val title = "$indent• ${ref.title ?: "Chapter ${output.size + 1}"}"

            val resource = ref.resource
            val spineIndex = findSpineIndexForResource(book, resource)

            if (spineIndex >= 0) {
                output.add(title to spineIndex)
            }

            if (ref.children.isNotEmpty()) {
                flattenToc(book, ref.children, output, depth + 1)
            }
        }
    }

    /**
     * Find spine index for a given resource (compare by href to handle different
     * Resource object instances for same file - M-5 fix).
     */
    private fun findSpineIndexForResource(book: Book, resource: io.documentnode.epub4j.domain.Resource?): Int {
        if (resource == null) return -1
        val spineRefs = book.spine.spineReferences
        val resourceHref = resource.href ?: return -1
        for (i in spineRefs.indices) {
            if (spineRefs[i].resource?.href == resourceHref) return i
        }
        return -1
    }

    /**
     * Fallback: show spine-based TOC when metadata TOC is empty.
     */
    private fun showSpineBasedToc(book: Book, context: android.content.Context) {
        val spine = book.spine

        if (spine.spineReferences.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "No chapters available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val chapters = spine.spineReferences.mapIndexed { index, spineRef ->
            val title = spineRef.resource.title
            val displayTitle = if (title.isNullOrBlank()) "Chapter ${index + 1}" else title
            Pair(displayTitle, index)
        }

        showTocBottomSheet(book, context, chapters)
        Timber.d("EPUB: Spine-based TOC BottomSheet shown with ${chapters.size} chapters")
    }

    /**
     * Show the recreation-safe TOC sheet with a RecyclerView chapter list.
     */
    private fun showTocBottomSheet(
        book: Book,
        context: android.content.Context,
        chapters: List<Pair<String, Int>>
    ) {
        val host = context as? FragmentActivity
        if (host == null) {
            Timber.w("EPUB: host is not a FragmentActivity, cannot show TOC sheet")
            return
        }

        EpubTocBottomSheet.newInstance(
            bookTitle = book.title,
            chapters = chapters,
            currentChapterIndex = currentChapterIndexProvider(),
            chapterCount = chapterCountProvider(),
            onChapterSelected = { spineIndex ->
                coroutineScope.launch { onNavigateToChapter(spineIndex) }
            },
        ).show(host.supportFragmentManager, EpubTocBottomSheet.TAG)

        Timber.d("EPUB: TOC BottomSheet shown with ${chapters.size} entries")
    }
}
