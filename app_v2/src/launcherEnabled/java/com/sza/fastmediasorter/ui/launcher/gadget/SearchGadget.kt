package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherSearchBinding
import com.sza.fastmediasorter.ui.launcher.search.WebSearchLaunchManager
import dagger.Lazy
import javax.inject.Inject

/**
 * S1566: a web search field on the desktop, so a query costs one tap instead of a trip through the
 * all-apps list. Our own view, never the Google AppWidget (ADR-1).
 *
 * The only gadget that takes text input. That is sanctioned rather than a deviation: the cell card is
 * deliberately non-focusable so a gadget's inner controls are the D-pad stops, and edit mode lays a scrim
 * over the whole cell, which keeps the field inert while the desktop is being rearranged.
 */
class SearchGadget @Inject constructor(
    private val webSearch: Lazy<WebSearchLaunchManager>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_SEARCH
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_search
    override val iconRes: Int = R.drawable.ic_search
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View {
        return SearchGadgetView(container.context, webSearch.get())
    }
}

private class SearchGadgetView(
    context: Context,
    private val webSearch: WebSearchLaunchManager,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherSearchBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.gadgetSearchInput.setOnEditorActionListener { _, actionId, _ ->
            val isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
            if (isSearch) {
                submit()
            }
            isSearch
        }
    }

    /**
     * Nothing about the query survives the tap - not in the cell param, not in a history (ADR-2): a search
     * string left on a home screen is personal data visible to whoever picks the device up.
     */
    private fun submit() {
        val query = binding.gadgetSearchInput.text?.toString().orEmpty()
        if (query.isBlank()) return
        if (webSearch.launch(context, query)) {
            binding.gadgetSearchInput.text?.clear()
            binding.gadgetSearchMessage.isVisible = false
            hideKeyboard()
        } else {
            binding.gadgetSearchMessage.setText(R.string.launcher_gadget_search_no_browser)
            binding.gadgetSearchMessage.isVisible = true
        }
    }

    private fun hideKeyboard() {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(windowToken, 0)
        binding.gadgetSearchInput.clearFocus()
    }
}
