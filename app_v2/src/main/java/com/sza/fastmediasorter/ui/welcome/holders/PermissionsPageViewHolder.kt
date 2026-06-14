package com.sza.fastmediasorter.ui.welcome.holders

import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.databinding.PageWelcomePermissionsBinding
import com.sza.fastmediasorter.ui.welcome.WelcomePage

/**
 * Permissions onboarding page (S0402). Thin renderer: hands its binding to the page's
 * onBindPermissions callback, which is wired in WelcomeActivity to WelcomePermissionsManager (owner
 * of the adaptive permission set + grant-all flow). Keep the constructor signature
 * `(PageWelcomePermissionsBinding)` stable: WelcomePagerAdapter constructs it.
 */
class PermissionsPageViewHolder(
    private val binding: PageWelcomePermissionsBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(page: WelcomePage) {
        page.onBindPermissions?.invoke(binding)
    }
}
