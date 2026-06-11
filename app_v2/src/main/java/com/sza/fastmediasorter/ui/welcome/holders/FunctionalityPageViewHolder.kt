package com.sza.fastmediasorter.ui.welcome.holders

import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.PageWelcomeFunctionalityBinding
import com.sza.fastmediasorter.ui.welcome.WelcomePage

/**
 * Functionality onboarding page (S0400). Thin renderer: hands its binding to the page's
 * onBindFunctionality callback, which is wired in WelcomeActivity to WelcomeFunctionalityController
 * (owner of all toggle/download logic). Keep the constructor signature
 * `(PageWelcomeFunctionalityBinding)` stable: WelcomePagerAdapter constructs it.
 */
class FunctionalityPageViewHolder(
    private val binding: PageWelcomeFunctionalityBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(page: WelcomePage) {
        page.onBindFunctionality?.invoke(binding)
    }
}
