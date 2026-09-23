package com.sza.fastmediasorter.ui.player.sheets

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.BottomSheetEpubSearchBinding
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet
import com.sza.fastmediasorter.ui.common.showSoftInputImplicitly
import com.sza.fastmediasorter.ui.player.helpers.EpubSearchResult
import com.sza.fastmediasorter.ui.player.helpers.EpubSearchResultAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Recreation-safe replacement for the raw `BottomSheetDialog` cross-chapter search that
 * `EpubSearchAndTocPresenter` used to build (S3244). The scan itself stays in the presenter, which
 * owns the [com.sza.fastmediasorter.ui.player.helpers.EpubSearchResult] model and the book; this
 * sheet only drives the query field and renders what the provider returns, so the search job is
 * bound to the sheet's own view lifecycle and dies with it.
 */
class EpubSearchBottomSheet : BaseAppBottomSheet() {

    override val contentLayout: Int = R.layout.bottom_sheet_epub_search
    override val requestKey: String = REQUEST_KEY

    private var searchProvider: (suspend (String) -> List<EpubSearchResult>)? = null
    private var onResultSelected: ((EpubSearchResult, String) -> Unit)? = null
    private var maxResults: Int = 0
    private var searchJob: Job? = null

    companion object {
        const val TAG = "epub_search_sheet"
        private const val REQUEST_KEY = "epub_search_sheet"
        private const val MIN_QUERY_LENGTH = 2
        private const val KEYBOARD_DELAY_MS = 200L

        fun newInstance(
            maxResults: Int,
            searchProvider: suspend (String) -> List<EpubSearchResult>,
            onResultSelected: (EpubSearchResult, String) -> Unit,
        ): EpubSearchBottomSheet = EpubSearchBottomSheet().also {
            it.maxResults = maxResults
            it.searchProvider = searchProvider
            it.onResultSelected = onResultSelected
        }
    }

    override fun bindContent(content: View) {
        val provider = searchProvider
        if (provider == null) {
            dismiss()
            return
        }
        val binding = BottomSheetEpubSearchBinding.bind(content)
        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.etSearchAllQuery.setOnEditorActionListener { _, actionId, _ ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            if (isSearchAction) {
                val query = binding.etSearchAllQuery.text?.toString()?.trim().orEmpty()
                if (query.length >= MIN_QUERY_LENGTH) runSearch(binding, provider, query)
            }
            isSearchAction
        }
        binding.etSearchAllQuery.requestFocus()
        val inputMethodManager = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.etSearchAllQuery.postDelayed(
            { inputMethodManager.showSoftInputImplicitly(binding.etSearchAllQuery) },
            KEYBOARD_DELAY_MS,
        )
    }

    private fun runSearch(
        binding: BottomSheetEpubSearchBinding,
        provider: suspend (String) -> List<EpubSearchResult>,
        query: String,
    ) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.searchProgress.isVisible = true
            binding.tvSearchStatus.isVisible = true
            binding.tvSearchStatus.text = getString(R.string.epub_searching)
            binding.rvSearchResults.adapter = null

            val results = provider(query)

            binding.searchProgress.isVisible = false
            binding.tvSearchStatus.text = statusTextFor(results)
            if (results.isNotEmpty()) {
                binding.rvSearchResults.adapter = EpubSearchResultAdapter(results) { result ->
                    dismiss()
                    onResultSelected?.invoke(result, query)
                }
            }
        }
    }

    private fun statusTextFor(results: List<EpubSearchResult>): String {
        if (results.isEmpty()) return getString(R.string.epub_search_no_results)
        val chaptersWithMatches = results.map { it.chapterIndex }.distinct().size
        val summary = getString(R.string.epub_search_results, results.size, chaptersWithMatches)
        return if (results.size >= maxResults) "$summary (max)" else summary
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        searchJob = null
        super.onDestroyView()
    }
}
