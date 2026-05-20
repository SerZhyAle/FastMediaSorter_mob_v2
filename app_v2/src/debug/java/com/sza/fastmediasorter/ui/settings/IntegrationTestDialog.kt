package com.sza.fastmediasorter.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.databinding.DialogIntegrationTestBinding
import com.sza.fastmediasorter.domain.usecase.IntegrationTestRunner.TestGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Dialog for running automated integration tests.
 * Only available in DEBUG builds.
 */
@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class IntegrationTestDialog : DialogFragment() {
    
    private var _binding: DialogIntegrationTestBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: IntegrationTestViewModel by viewModels()
    
    private lateinit var allChips: List<Chip>
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIntegrationTestBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        // Collect all chips for enabling/disabling during tests
        allChips = listOf(
            binding.chipAll,
            binding.chipFailedOnly,
            binding.chipLocal,
            binding.chipNetwork,
            binding.chipImage,
            binding.chipGif,
            binding.chipMatrixCopy,
            binding.chipMatrixMove,
            binding.chipMatrixOther,
            binding.chipSettings,
            binding.chipFavorites,
            binding.chipMetadata,
            binding.chipAudio,
            binding.chipNetworkUtils,
            binding.chipCache,
            binding.chipScanning,
            binding.chipCloudProviders,
            binding.chipThreeDVr
        )

        // S0241: 3DVR sweep removed - no VR flavor left to drive it.
        binding.chipThreeDVr.visibility = View.GONE
        
        // Setup chip click listeners
        binding.chipAll.setOnClickListener { runTestGroup(TestGroup.ALL) }
        binding.chipFailedOnly.setOnClickListener { runTestGroup(TestGroup.FAILED_ONLY) }
        binding.chipLocal.setOnClickListener { runTestGroup(TestGroup.LOCAL) }
        binding.chipNetwork.setOnClickListener { runTestGroup(TestGroup.NETWORK_COPY) }
        binding.chipImage.setOnClickListener { runTestGroup(TestGroup.IMAGE) }
        binding.chipGif.setOnClickListener { runTestGroup(TestGroup.GIF) }
        binding.chipMatrixCopy.setOnClickListener { runTestGroup(TestGroup.MATRIX_COPY) }
        binding.chipMatrixMove.setOnClickListener { runTestGroup(TestGroup.MATRIX_MOVE) }
        binding.chipMatrixOther.setOnClickListener { runTestGroup(TestGroup.MATRIX_OTHER) }
        binding.chipSettings.setOnClickListener { runTestGroup(TestGroup.SETTINGS) }
        binding.chipFavorites.setOnClickListener { runTestGroup(TestGroup.FAVORITES) }
        binding.chipMetadata.setOnClickListener { runTestGroup(TestGroup.METADATA) }
        binding.chipAudio.setOnClickListener { runTestGroup(TestGroup.AUDIO) }
        binding.chipNetworkUtils.setOnClickListener { runTestGroup(TestGroup.NETWORK_UTILS) }
        binding.chipCache.setOnClickListener { runTestGroup(TestGroup.CACHE) }
        binding.chipScanning.setOnClickListener { runTestGroup(TestGroup.SCANNING) }
        binding.chipCloudProviders.setOnClickListener { runTestGroup(TestGroup.CLOUD_PROVIDERS) }
        binding.chipThreeDVr.setOnClickListener { runTestGroup(TestGroup.THREE_D_VR) }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        binding.btnImportLog.setOnClickListener {
            viewModel.importLogFromClipboard(requireContext())
        }
        
        binding.btnCopyLog.setOnClickListener {
            viewModel.copyLogToClipboard(requireContext())
        }
        
        binding.btnSaveLog.setOnClickListener {
            viewModel.saveLogToFile(requireContext())
        }
    }
    
    private fun runTestGroup(group: TestGroup) {
        viewModel.runTestGroup(group)
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testProgress.collect { progress ->
                progress?.let {
                    binding.progressBar.max = it.totalTests
                    binding.progressBar.progress = it.currentTest
                    binding.tvProgress.text = "${it.currentTest}/${it.totalTests}: ${it.testName}"
                    binding.tvStatus.text = it.status
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testLog.collect { log ->
                binding.tvLog.text = log
                // Auto-scroll to bottom
                binding.scrollView.post {
                    binding.scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isRunning.collect { isRunning ->
                // Enable/disable all chips during test run
                allChips.forEach { chip -> chip.isEnabled = !isRunning }
                binding.btnClose.isEnabled = !isRunning
                binding.progressBar.visibility = if (isRunning) View.VISIBLE else View.GONE
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        // Make dialog full screen
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        const val TAG = "IntegrationTestDialog"
        
        /**
         * Check if integration tests are available (DEBUG builds only).
         */
        fun isAvailable(): Boolean = BuildConfig.DEBUG
    }
}
