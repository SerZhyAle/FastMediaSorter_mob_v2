package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsMediaContainerBinding
import com.sza.fastmediasorter.ui.settings.MediaCategoryPagerAdapter

class MediaSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsMediaContainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsMediaContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = MediaCategoryPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        // Use adapter's dynamic tab titles based on flavor support
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
