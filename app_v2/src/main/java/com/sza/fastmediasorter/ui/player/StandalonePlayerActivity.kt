package com.sza.fastmediasorter.ui.player

import android.os.Bundle
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone Activity for playing/viewing media opened from external sources (Intent.ACTION_VIEW).
 * Detached from the main resource/database tree.
 */
@AndroidEntryPoint
class StandalonePlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {

    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        // Initial setup for Phase 1 (empty for now)
    }

    override fun observeData() {
        // Data observation for Phase 1 (empty for now)
    }
}
