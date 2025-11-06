package com.sza.fastmediasorter_v2.ui.settings

import androidx.activity.viewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    private val viewModel: SettingsViewModel by viewModels()
    
    override fun getViewBinding(): ActivitySettingsBinding {
        return ActivitySettingsBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        val adapter = SettingsPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "General"
                1 -> "Media"
                2 -> "Playback"
                3 -> "Destinations"
                else -> ""
            }
        }.attach()
    }

    override fun observeData() {
        // Settings are observed in individual fragments
    }
}
