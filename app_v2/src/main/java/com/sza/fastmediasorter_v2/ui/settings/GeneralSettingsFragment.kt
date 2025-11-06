package com.sza.fastmediasorter_v2.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.databinding.FragmentSettingsGeneralBinding
import kotlinx.coroutines.launch

class GeneralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsGeneralBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsGeneralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() {
        binding.btnLanguage.setOnClickListener {
            // TODO: Show language selection dialog
        }
        
        binding.switchPreventSleep.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(preventSleep = isChecked))
        }
        
        binding.switchSmallControls.setOnCheckedChangeListener { _, isChecked ->
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(showSmallControls = isChecked))
        }
        
        binding.etDefaultUser.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newUser = binding.etDefaultUser.text.toString()
                viewModel.updateSettings(current.copy(defaultUser = newUser))
            }
        }
        
        binding.etDefaultPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val current = viewModel.settings.value
                val newPassword = binding.etDefaultPassword.text.toString()
                viewModel.updateSettings(current.copy(defaultPassword = newPassword))
            }
        }
        
        binding.btnLocalFilesPermission.setOnClickListener {
            // TODO: Request local files permission
        }
        
        binding.btnNetworkPermission.setOnClickListener {
            // TODO: Request network permission
        }
        
        binding.btnShowLog.setOnClickListener {
            // TODO: Show log dialog
        }
        
        binding.btnShowSessionLog.setOnClickListener {
            // TODO: Show session log dialog
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    binding.switchPreventSleep.isChecked = settings.preventSleep
                    binding.switchSmallControls.isChecked = settings.showSmallControls
                    
                    if (binding.etDefaultUser.text.toString() != settings.defaultUser) {
                        binding.etDefaultUser.setText(settings.defaultUser)
                    }
                    
                    if (binding.etDefaultPassword.text.toString() != settings.defaultPassword) {
                        binding.etDefaultPassword.setText(settings.defaultPassword)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
