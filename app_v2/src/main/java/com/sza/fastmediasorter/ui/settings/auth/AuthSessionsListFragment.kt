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
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.link.auth.KnownAuthResources
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0116 §5.1 pillar K: settings sub-screen listing saved auth sessions with
 * single-tap delete. The "Add authorization" CTA is a `+` action in the host
 * Activity's toolbar (S0144) — see [WebViewAuthDialogFragment].
 */
@AndroidEntryPoint
class AuthSessionsListFragment : Fragment(), MenuProvider {

    private val viewModel: AuthSessionsListViewModel by viewModels()
    private lateinit var adapter: AuthSessionAdapter

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

        adapter = AuthSessionAdapter(onDelete = viewModel::delete)
        val list = view.findViewById<RecyclerView>(R.id.rvAuthSessions)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom + resources.getDimensionPixelSize(R.dimen.padding_large))
            insets
        }

        val empty = view.findViewById<TextView>(R.id.tvAuthSessionsEmpty)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessions.collect { items ->
                    adapter.submitList(items)
                    empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
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

    private fun promptForUrlAndOpenWebView() {
        Timber.d("S0144: auth-add picker shown")
        val resources = KnownAuthResources.all
        val labels = (resources.map { it.displayName } + getString(R.string.auth_add_enter_manually)).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_add_picker_title)
            .setItems(labels) { _, which ->
                val resource = resources.getOrNull(which)
                if (resource != null) {
                    WebViewAuthDialogFragment.newInstance(resource.loginUrl)
                        .show(parentFragmentManager, "s0116_webview_auth")
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
                    WebViewAuthDialogFragment.newInstance(url)
                        .show(parentFragmentManager, "s0116_webview_auth")
                } else {
                    com.google.android.material.snackbar.Snackbar
                        .make(requireView(), R.string.webview_auth_invalid_url, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
