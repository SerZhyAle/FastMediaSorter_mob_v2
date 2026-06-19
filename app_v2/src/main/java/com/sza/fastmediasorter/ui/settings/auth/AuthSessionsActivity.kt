package com.sza.fastmediasorter.ui.settings.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.children
import androidx.fragment.app.commit
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityAuthSessionsBinding
import com.sza.fastmediasorter.ui.common.input.UiSurface
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AuthSessionsActivity : BaseActivity<ActivityAuthSessionsBinding>() {

    /** S0289 Phase 09: multimodal surface marker - auth sessions live under the settings host. */
    @Suppress("unused")
    private val multimodalInputSurface: UiSurface = UiSurface.SETTINGS

    override fun getViewBinding(): ActivityAuthSessionsBinding =
        ActivityAuthSessionsBinding.inflate(layoutInflater)

    /** S0289 Phase 09: route mouse wheel onto the sessions list. */
    override fun getMouseScrollTargetView(): View? =
        binding.authSessionsContainer.findViewById(R.id.rvAuthSessions)

    // S0510: no own F1 handler - declare the surface so BaseActivity's global F1 opens input help.
    override fun getInputHelpSurface(): UiSurface = UiSurface.SETTINGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.authSessionsContainer, AuthSessionsListFragment())
            }
        }
    }

    override fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun observeData() = Unit

    override fun getInitialFocusView(): View? {
        val sessionsList = binding.authSessionsContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAuthSessions)
        val firstSessionItem = sessionsList?.findViewHolderForAdapterPosition(0)?.itemView
        return firstSessionItem
            ?: sessionsList
            ?: binding.toolbar.children.firstOrNull { it.isClickable }
            ?: binding.toolbar
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AuthSessionsActivity::class.java))
        }
    }
}
