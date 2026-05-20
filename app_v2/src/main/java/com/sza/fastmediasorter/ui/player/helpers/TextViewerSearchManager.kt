package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

class TextViewerSearchManager(
    private val safeViews: PlayerBindingSafeViews,
) {
    fun searchText(query: String): Int {
        if (query.isBlank()) {
            clearSearch()
            return 0
        }

        val fullText = safeViews.tvTextContent.text.toString()
        if (fullText.isBlank()) return 0

        val matchCount =
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(fullText).count()
        Timber.d("Search for '$query' found $matchCount matches")
        return matchCount
    }

    fun highlightSearchMatch(query: String, matchIndex: Int) {
        if (query.isBlank()) return

        val fullText = safeViews.tvTextContent.text.toString()
        val matches =
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(fullText).toList()

        if (matchIndex >= 0 && matchIndex < matches.size) {
            val match = matches[matchIndex]
            val start = match.range.first

            safeViews.tvTextContent.layout?.let { layout ->
                val line = layout.getLineForOffset(start)
                safeViews.textScrollView.smoothScrollTo(0, layout.getLineTop(line) - 100)
            }
            Timber.d("Highlighted match $matchIndex at position $start-${match.range.last + 1}")
        }
    }

    fun clearSearch() {
        Timber.d("Search cleared")
    }

    fun scrollToTop() {
        safeViews.textScrollView.post {
            safeViews.textScrollView.fullScroll(android.view.View.FOCUS_UP)
        }
        UserActionLogger.logNavigation("Top", "TextViewerManager")
    }

    fun scrollToBottom() {
        safeViews.textScrollView.post {
            safeViews.textScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
        UserActionLogger.logNavigation("Bottom", "TextViewerManager")
    }
}
