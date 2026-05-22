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
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class AuthSessionsActivity : BaseActivity<ActivityAuthSessionsBinding>() {

    override fun getViewBinding(): ActivityAuthSessionsBinding =
        ActivityAuthSessionsBinding.inflate(layoutInflater)

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
