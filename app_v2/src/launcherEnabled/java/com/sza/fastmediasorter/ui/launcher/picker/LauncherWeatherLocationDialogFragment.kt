package com.sza.fastmediasorter.ui.launcher.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogLauncherWeatherLocationBinding
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.usecase.weather.SearchWeatherLocationsUseCase
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0426: collects a city name, resolves it through the keyless geocoder and reports the picked place
 * back as an encoded [WeatherLocation]. The same dialog serves both entry points - placing a new
 * weather cell (no cell id) and re-pointing an existing one (with a cell id) - so the host, not the
 * dialog, decides what to do with the result.
 */
@AndroidEntryPoint
class LauncherWeatherLocationDialogFragment : DialogFragment() {

    @Inject
    lateinit var searchLocations: SearchWeatherLocationsUseCase

    private var _binding: DialogLauncherWeatherLocationBinding? = null
    private val binding get() = _binding!!

    private var cellId: Long = NO_CELL_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        cellId = requireArguments().getLong(ARG_CELL_ID, NO_CELL_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogLauncherWeatherLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnWeatherLocationCancel.setOnClickListener { dismiss() }
        binding.btnWeatherLocationSearch.setOnClickListener { runSearch() }
        binding.editWeatherLocationQuery.setOnEditorActionListener { _, _, _ ->
            runSearch()
            true
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun runSearch() {
        val query = binding.editWeatherLocationQuery.text?.toString().orEmpty()
        showStatus(R.string.launcher_weather_location_searching)
        binding.btnWeatherLocationSearch.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val places = searchLocations(query)
            _binding?.let { safeBinding ->
                safeBinding.btnWeatherLocationSearch.isEnabled = true
                if (places.isEmpty()) {
                    showStatus(R.string.launcher_weather_location_empty)
                } else {
                    showPlaces(places)
                }
            }
        }
    }

    private fun showPlaces(places: List<WeatherLocation>) {
        val options = places.map { Option(id = it.encode(), label = it.label) }
        SearchableOptionPickerDialog.newInstance(
            title = getString(R.string.launcher_weather_location_title),
            options = options,
            selectedId = null,
            includeResetRow = false,
        ) { picked ->
            picked?.id?.let(::publish)
        }.show(parentFragmentManager, SearchableOptionPickerDialog.TAG)
    }

    private fun publish(encodedLocation: String) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()
        setFragmentResult(
            requestKey,
            bundleOf(RESULT_LOCATION to encodedLocation, RESULT_CELL_ID to cellId),
        )
        dismiss()
    }

    private fun showStatus(messageRes: Int) {
        binding.tvWeatherLocationStatus.setText(messageRes)
        binding.tvWeatherLocationStatus.isVisible = true
    }

    companion object {
        const val TAG = "LauncherWeatherLocationDialog"
        const val RESULT_LOCATION = "result_location"
        const val RESULT_CELL_ID = "result_cell_id"
        const val NO_CELL_ID = -1L

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_CELL_ID = "arg_cell_id"

        fun newInstance(requestKey: String, cellId: Long = NO_CELL_ID): LauncherWeatherLocationDialogFragment =
            LauncherWeatherLocationDialogFragment().apply {
                arguments = bundleOf(ARG_REQUEST_KEY to requestKey, ARG_CELL_ID to cellId)
            }
    }
}
