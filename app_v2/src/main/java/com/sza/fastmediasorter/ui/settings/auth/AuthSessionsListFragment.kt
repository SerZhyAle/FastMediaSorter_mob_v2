package com.sza.fastmediasorter.ui.settings.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.share.auth.WebViewAuthDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * S0116 §5.1 pillar K: settings sub-screen listing saved auth sessions with
 * single-tap delete and an "Add authorization" CTA that opens
 * [WebViewAuthDialogFragment] with a user-supplied URL.
 */
@AndroidEntryPoint
class AuthSessionsListFragment : Fragment() {

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
        adapter = AuthSessionAdapter(onDelete = viewModel::delete)
        val list = view.findViewById<RecyclerView>(R.id.rvAuthSessions)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        val empty = view.findViewById<TextView>(R.id.tvAuthSessionsEmpty)
        view.findViewById<MaterialButton>(R.id.btnAuthSessionsAdd).setOnClickListener {
            promptForUrlAndOpenWebView()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessions.collect { items ->
                    adapter.submitList(items)
                    empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun promptForUrlAndOpenWebView() {
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
