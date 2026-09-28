package com.sza.fastmediasorter.ui.player

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogSlideshowSettingsBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
@android.annotation.SuppressLint("SetTextI18n")
class SlideshowSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogSlideshowSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by activityViewModels()

    // S3761: one in-flight display-name lookup; a newer emission cancels the previous one
    private var filenameResolveJob: Job? = null

    // Activity Result Launcher for picking music
    private val selectMusicLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            handleMusicSelection(uri)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSlideshowSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Close button
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // Interval Slider
        binding.sliderInterval.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvIntervalValue.text = "${value.toInt()}s"
            }
        }

        binding.sliderInterval.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {
                // No-op
            }

            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                // Commit value to ViewModel
                viewModel.setSlideShowInterval(slider.value.toLong() * 1000L)
            }
        })

        // Play to End toggle
        binding.cbPlayToEnd.setOnCheckedChangeListener { isChecked ->
            viewModel.setPlayToEndInSlideshow(isChecked)
        }

        // Music Selection
        binding.btnSelectMusic.setOnClickListener {
            openMusicPicker()
        }

        binding.btnClearMusic.setOnClickListener {
            viewModel.setSlideshowMusic(null)
        }
    }

    private fun observeViewModel() {
        collectOnLifecycle(viewModel.state) { state ->
            val intervalSec = (state.slideShowInterval / 1000L).toFloat()
            binding.sliderInterval.value = intervalSec.coerceIn(1f, 60f)
            binding.tvIntervalValue.text = "${intervalSec.toInt()}s"

            // Update Play to End
            binding.cbPlayToEnd.isChecked = state.playToEndInSlideshow

            val musicUri = state.slideshowMusicUri
            if (musicUri != null) {
                val uri = Uri.parse(musicUri)
                binding.btnClearMusic.visibility = View.VISIBLE
                binding.btnSelectMusic.text = getString(R.string.select_music_file) // "Change Music" ?
                // S3761: the display name used to resolve synchronously here - render the fallback
                // first, then query ContentResolver off-main so a slow provider never blocks Main
                binding.tvMusicStatus.text = uri.lastPathSegment ?: "Unknown File"
                filenameResolveJob?.cancel()
                filenameResolveJob = viewLifecycleOwner.lifecycleScope.launch {
                    val resolved = withContext(Dispatchers.IO) { getFileName(uri) }
                    Timber.d("S3761: slideshow music label resolved off-main")
                    _binding?.tvMusicStatus?.text = resolved ?: uri.lastPathSegment ?: "Unknown File"
                }
            } else {
                filenameResolveJob?.cancel()
                binding.tvMusicStatus.text = getString(R.string.no_music_selected)
                binding.btnClearMusic.visibility = View.GONE
                binding.btnSelectMusic.text = getString(R.string.select_music_file)
            }
        }
    }

    private fun openMusicPicker() {
        try {
            selectMusicLauncher.launch(arrayOf("audio/*"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch music picker")
        }
    }

    private fun handleMusicSelection(uri: Uri) {
        try {
            // Take persistable permission so we can access it later
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

            viewModel.setSlideshowMusic(uri.toString())
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to take persistable uri permission")
            // Still set it, might work for this session
            viewModel.setSlideshowMusic(uri.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error handling music selection")
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // Settings panel: all controls apply immediately, so there is no positive action - no-op
        // confirm keeps Esc-dismiss and focus traversal without a false Enter-confirm.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
        binding.btnClose.requestFocus()
    }
}
