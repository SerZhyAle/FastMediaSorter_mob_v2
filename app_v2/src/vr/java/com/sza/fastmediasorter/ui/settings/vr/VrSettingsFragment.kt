package com.sza.fastmediasorter.ui.settings.vr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.xr.MasterTogglePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Stage 0 VR Settings tab body (S0245).
 *
 * Surfaces the single master toggle that gates VR feature visibility. Placeholder section
 * below the toggle hints at future Stage 1+ controls but does nothing on Stage 0.
 */
@AndroidEntryPoint
class VrSettingsFragment : Fragment() {

    @Inject lateinit var preferences: MasterTogglePreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_vr_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val switch = view.findViewById<MaterialSwitch>(R.id.masterToggleSwitch)
        val row = view.findViewById<View>(R.id.masterToggleRow)

        row.setOnClickListener { switch.toggle() }

        switch.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                preferences.setEnabled(isChecked)
                Timber.d("VrSettingsFragment: master toggle changed -> $isChecked")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.enabled
                    .onEach { switch.isChecked = it }
                    .collect()
            }
        }
    }
}
