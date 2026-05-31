package com.sza.fastmediasorter.ui.game

import android.view.View
import androidx.activity.OnBackPressedCallback
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityGameHelpBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameHelpActivity : BaseActivity<ActivityGameHelpBinding>() {

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getViewBinding(): ActivityGameHelpBinding = ActivityGameHelpBinding.inflate(layoutInflater)

    override fun setupViews() {
        binding.btnGameHelpBack.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    override fun observeData() = Unit

    override fun getInitialFocusView(): View = binding.btnGameHelpBack
}