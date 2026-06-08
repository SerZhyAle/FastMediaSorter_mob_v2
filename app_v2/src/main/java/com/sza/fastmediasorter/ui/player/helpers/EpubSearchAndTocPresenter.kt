package com.sza.fastmediasorter.ui.player.helpers

import android.view.View
import android.webkit.WebView
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import io.documentnode.epub4j.domain.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val MAX_SEARCH_RESULTS = 500 // Limit cross-chapter results to prevent OOM (M-3 fix)

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

        // WebView.findAllAsync() is deprecated in API 16+ but still functional
        @Suppress("DEPRECATION")
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

        val context = root.context
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_epub_search, null)

        val etQuery = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchAllQuery)
        val searchProgress = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.searchProgress)
        val tvStatus = view.findViewById<android.widget.TextView>(R.id.tvSearchStatus)
        val rvResults = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSearchResults)

        rvResults.layoutManager = LinearLayoutManager(context)

        var searchJob: kotlinx.coroutines.Job? = null

        // Handle IME search action
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etQuery.text?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    searchJob?.cancel()
                    searchJob = performCrossChapterSearch(
                        book, query, context, bottomSheet,
                        searchProgress, tvStatus, rvResults
                    )
                }
                true
            } else false
        }

        bottomSheet.setContentView(view)

        // Cancel search coroutine when BottomSheet is dismissed (C-2 fix)
        bottomSheet.setOnDismissListener { searchJob?.cancel() }

        bottomSheet.show()

        // Focus input and show keyboard
        etQuery.requestFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        etQuery.postDelayed(
            { imm.showSoftInput(etQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) },
            200
        )

        Timber.d("EPUB: Cross-chapter search dialog shown")
    }

    /**
     * Execute cross-chapter search in background coroutine.
     */
    private fun performCrossChapterSearch(
        book: Book,
        query: String,
        context: android.content.Context,
        bottomSheet: com.google.android.material.bottomsheet.BottomSheetDialog,
        searchProgress: com.google.android.material.progressindicator.LinearProgressIndicator,
        tvStatus: android.widget.TextView,
        rvResults: androidx.recyclerview.widget.RecyclerView
    ): kotlinx.coroutines.Job {
        return coroutineScope.launch {
            // Show progress
            withContext(Dispatchers.Main) {
                searchProgress.visibility = android.view.View.VISIBLE
                tvStatus.visibility = android.view.View.VISIBLE
                tvStatus.text = context.getString(R.string.epub_searching)
                rvResults.adapter = null
            }

            // Scan all chapters on IO
            val results = withContext(Dispatchers.IO) {
                val allResults = mutableListOf<EpubSearchResult>()
                val spine = book.spine
                val contextWindow = 60 // chars around match

                for (i in 0 until spine.spineReferences.size) {
                    kotlinx.coroutines.yield() // Check cancellation

                    val spineRef = spine.spineReferences[i]
                    val resource = spineRef.resource
                    val title = resource.title?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}"

                    try {
                        val htmlContent = String(resource.data, Charsets.UTF_8)
                        val plainText = Jsoup.parse(htmlContent).text()

                        var searchFrom = 0
                        val lowerText = plainText.lowercase()
                        val lowerQuery = query.lowercase()

                        while (searchFrom < lowerText.length) {
                            val pos = lowerText.indexOf(lowerQuery, searchFrom)
                            if (pos < 0) break

                            // Limit total results to prevent OOM (M-3 fix)
                            if (allResults.size >= MAX_SEARCH_RESULTS) break

                            val snippetStart = (pos - contextWindow).coerceAtLeast(0)
                            val snippetEnd = (pos + query.length + contextWindow).coerceAtMost(plainText.length)
                            val snippet = buildString {
                                if (snippetStart > 0) append("…")
                                append(plainText.substring(snippetStart, snippetEnd))
                                if (snippetEnd < plainText.length) append("…")
                            }

                            allResults.add(
                                EpubSearchResult(
                                    chapterIndex = i,
                                    chapterTitle = title,
                                    contextSnippet = snippet,
                                    matchStartInText = pos,
                                    matchedText = plainText.substring(pos, pos + query.length)
                                )
                            )

                            searchFrom = pos + query.length
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "EPUB: Error searching chapter $i")
                    }

                    // Stop scanning more chapters if limit reached (M-3 fix)
                    if (allResults.size >= MAX_SEARCH_RESULTS) break
                }

                allResults
            }

            // Show results on Main
            withContext(Dispatchers.Main) {
                searchProgress.visibility = android.view.View.GONE

                if (results.isEmpty()) {
                    tvStatus.text = context.getString(R.string.epub_search_no_results)
                } else {
                    val chaptersWithMatches = results.map { it.chapterIndex }.distinct().size
                    val statusText = if (results.size >= MAX_SEARCH_RESULTS) {
                        "${context.getString(R.string.epub_search_results, results.size, chaptersWithMatches)} (max)"
                    } else {
                        context.getString(R.string.epub_search_results, results.size, chaptersWithMatches)
                    }
                    tvStatus.text = statusText

                    rvResults.adapter = EpubSearchResultAdapter(results) { result ->
                        bottomSheet.dismiss()
                        // Navigate to chapter and highlight using onPageFinished (M-2 fix)
                        coroutineScope.launch {
                            onNavigateToChapter(result.chapterIndex)
                            // Trigger in-page highlight after WebView finishes loading
                            kotlinx.coroutines.delay(300)
                            searchInEpub(query) {}
                        }
                    }
                }

                Timber.d("EPUB: Cross-chapter search for '$query': ${results.size} results")
            }
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
     * Show BottomSheetDialog with a RecyclerView chapter list.
     */
    private fun showTocBottomSheet(
        book: Book,
        context: android.content.Context,
        chapters: List<Pair<String, Int>>
    ) {
        val currentChapterIndex = currentChapterIndexProvider()
        val chapterCount = chapterCountProvider()

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_epub_toc, null)

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTocTitle)
        val tvProgress = view.findViewById<android.widget.TextView>(R.id.tvChapterProgress)
        val rvChapters = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTocChapters)

        tvTitle.text = book.title ?: context.getString(R.string.epub_table_of_contents)
        tvProgress.text = context.getString(R.string.epub_chapter_progress, currentChapterIndex + 1, chapterCount)

        val adapter = EpubTocAdapter(
            chapters = chapters,
            currentChapterSpineIndex = currentChapterIndex,
            onChapterSelected = { spineIndex ->
                bottomSheet.dismiss()
                coroutineScope.launch {
                    onNavigateToChapter(spineIndex)
                }
            }
        )

        rvChapters.layoutManager = LinearLayoutManager(context)
        rvChapters.adapter = adapter

        val currentPos = adapter.findCurrentChapterPosition()
        if (currentPos > 0) rvChapters.scrollToPosition(currentPos)

        bottomSheet.setContentView(view)
        bottomSheet.show()

        Timber.d("EPUB: TOC BottomSheet shown with ${chapters.size} entries")
    }
}
