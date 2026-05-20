package com.sza.fastmediasorter.ui.settings.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.browser.CctAvailabilityChecker
import com.sza.fastmediasorter.data.browser.CctUnavailableException
import com.sza.fastmediasorter.data.browser.GoogleDomainBrowserLauncher
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AuthSessionsListFragment : Fragment(), MenuProvider {

    private val viewModel: AuthSessionsListViewModel by viewModels()
    private lateinit var adapter: AuthAccountGroupAdapter

    @Inject lateinit var googleDomainBrowserLauncher: GoogleDomainBrowserLauncher
    @Inject lateinit var cctChecker: CctAvailabilityChecker

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_auth_sessions_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        adapter = AuthAccountGroupAdapter(
            onAddAccount = { host, loginUrl -> launchAddAccount(host, loginUrl) },
            onDelete = { host, account ->
                val displayName = account.visibleLabel(getString(R.string.s0157_dismissed_label))
                showDeleteConfirmation(host, account.accountId, displayName)
            },
            onRename = { host, accountId, currentName ->
                showRenameDialog(host, accountId, currentName)
            },
            onRelogin = { host, accountId, loginUrl ->
                launchRelogin(host, accountId, loginUrl)
            },
        )

        val list = view.findViewById<RecyclerView>(R.id.rvAuthSessions)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        ViewCompat.setOnApplyWindowInsetsListener(list) { recyclerView, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            recyclerView.updatePadding(bottom = bottom + resources.getDimensionPixelSize(R.dimen.padding_large))
            insets
        }

        val empty = view.findViewById<TextView>(R.id.tvAuthSessionsEmpty)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.accountGroups.collect { groups ->
                    adapter.submitList(adapter.buildItems(groups))
                    empty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.auth_sessions_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return if (menuItem.itemId == R.id.action_add_auth_session) {
            promptForUrlAndOpenWebView()
            true
        } else {
            false
        }
    }

    private fun launchAddAccount(host: String, loginUrl: String) {
        if (loginUrl.isNotBlank()) {
            Timber.i("AuthSessionsListFragment: adding account for host=%s", host)
            openAuthWebView(loginUrl, "s0155_webview_add_account")
        } else {
            promptForManualUrl()
        }
    }

    private fun showDeleteConfirmation(host: String, accountId: String, displayName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.s0155_delete_account_confirm, displayName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteAccount(host, accountId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(host: String, accountId: String, currentName: String) {
        val input = TextInputEditText(requireContext()).apply { setText(currentName) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.s0155_rename_account_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotBlank()) {
                    viewModel.updateDisplayName(host, accountId, newName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchRelogin(host: String, accountId: String, loginUrl: String) {
        Timber.i("AuthSessionsListFragment: re-login for host=%s accountId=%s", host, accountId)
        val url = if (loginUrl.isNotBlank()) loginUrl else KnownAuthResources.matchHost(host)?.loginUrl ?: return
        openAuthWebView(url, "s0155_webview_relogin")
    }

    private fun promptForUrlAndOpenWebView() {
        val resources = KnownAuthResources.all
        val labels = (resources.map { it.displayName } + getString(R.string.auth_add_enter_manually)).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_add_picker_title)
            .setItems(labels) { _, which ->
                val resource = resources.getOrNull(which)
                if (resource != null) {
                    openAuthWebView(resource.loginUrl, "s0116_webview_auth")
                } else {
                    promptForManualUrl()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptForManualUrl() {
        val input = TextInputEditText(requireContext()).apply {
            hint = getString(R.string.webview_auth_url_prompt)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_sessions_add_button)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    openAuthWebView(url, "s0116_webview_auth")
                } else {
                    Snackbar.make(requireView(), R.string.webview_auth_invalid_url, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * S0200 - host-aware router. Google-domain URLs go to Chrome Custom Tabs; everything else
     * keeps the legacy WebView flow. CCT-unavailable triggers the refusal dialog.
     */
    private fun openAuthWebView(url: String, tag: String) {
        try {
            googleDomainBrowserLauncher.routeAuthUrl(requireContext(), url) { fallbackUrl ->
                WebViewAuthDialogFragment.newInstance(fallbackUrl).show(parentFragmentManager, tag)
            }
        } catch (e: CctUnavailableException) {
            Timber.w(e, "AuthSessionsListFragment: CCT unavailable for url=%s", url)
            showCctUnavailableDialog { openAuthWebView(url, tag) }
        }
    }

    private fun showCctUnavailableDialog(onRetry: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.s0200_cct_unavailable_title)
            .setMessage(R.string.s0200_cct_unavailable_message)
            .setPositiveButton(R.string.s0200_cct_unavailable_retry) { _, _ ->
                if (cctChecker.isAvailable()) onRetry() else showCctUnavailableDialog(onRetry)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}