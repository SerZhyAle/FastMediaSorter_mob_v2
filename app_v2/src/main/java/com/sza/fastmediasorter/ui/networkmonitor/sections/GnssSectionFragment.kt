package com.sza.fastmediasorter.ui.networkmonitor.sections

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentNetworkMonitorGnssBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssConstellation
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSatellite
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssTrackState
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.networkmonitor.helpers.ChartValueUnit
import com.sza.fastmediasorter.ui.networkmonitor.helpers.NetworkMonitorPermissionManager
import com.sza.fastmediasorter.ui.networkmonitor.helpers.SignalChartBinder
import com.sza.fastmediasorter.ui.networkmonitor.helpers.formatChartValue
import com.sza.fastmediasorter.ui.networkmonitor.helpers.toReasonRes
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.Locale

/**
 * S1433: the GNSS subscreen - the satellite list, the counts, the C/N0 chart, the current position and the
 * track-recording opt-in.
 *
 * The satellite view and the position are shown as two separate blocks with two separate reasons, because
 * they answer different questions: the constellation says whether the receiver works, the coordinate says
 * where the device is, and a receiver can report a full sky while holding no fix at all.
 */
@AndroidEntryPoint
class GnssSectionFragment : Fragment() {

    private var _binding: FragmentNetworkMonitorGnssBinding? = null
    private val binding
        get() = requireNotNull(_binding) { "Binding is valid only between onCreateView and onDestroyView" }

    private val viewModel: GnssSectionViewModel by viewModels()

    private val permissionManager = NetworkMonitorPermissionManager(this)

    private var chartBinder: SignalChartBinder? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNetworkMonitorGnssBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chartBinder = SignalChartBinder(
            chart = binding.gnssChart.chartSeries,
            summaryView = binding.gnssChart.chartSummary,
            emptyView = binding.gnssChart.chartEmpty,
            unit = ChartValueUnit.DB_HZ,
        )
        binding.gnssChart.chartHeading.setText(R.string.network_monitor_gnss_chart_heading)
        trackRow().setOnCheckedChangeListener { enabled -> viewModel.onRecordTrackChanged(enabled) }
        binding.btnGnssShareTrack.setOnClickListener { viewModel.onShareTrackRequested() }
        collectOnLifecycle(viewModel.uiState) { render(it) }
        collectOnLifecycle(viewModel.recordTrack) { trackRow().setCheckedSilently(it) }
        collectOnLifecycle(viewModel.track) { renderTrack(it) }
        collectOnLifecycle(viewModel.shareOutcome) { applyShareOutcome(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chartBinder = null
        _binding = null
    }

    private fun render(state: GnssSectionUiState) {
        renderSatellites(state)
        renderPosition(state)
        chartBinder?.render(state.signal)
    }

    private fun renderSatellites(state: GnssSectionUiState) {
        val snapshot = state.gnss.data
        binding.gnssDetailsGroup.isVisible = snapshot != null
        binding.gnssAvailability.isVisible = snapshot == null
        if (!permissionManager.bind(binding.gnssAvailability, state.gnss.availability)) {
            binding.gnssAvailability.setText(state.gnss.availability.toReasonRes())
        }
        binding.gnssVisibleValue.text = snapshot?.visibleCount?.toString() ?: unknown()
        binding.gnssUsedValue.text = snapshot?.usedInFixCount?.toString() ?: unknown()
        binding.gnssAcquiring.isVisible = state.isAcquiring
        val satellites = snapshot?.satellites.orEmpty()
        binding.gnssSatelliteList.isVisible = satellites.isNotEmpty()
        binding.gnssSatelliteList.text = satellites.joinToString(separator = "\n") { describe(it) }
    }

    private fun describe(satellite: GnssSatellite): String = getString(
        R.string.network_monitor_gnss_satellite_line,
        constellationLabel(satellite.constellation),
        satellite.satelliteId,
        satellite.cn0DbHz?.let { requireContext().formatChartValue(it, ChartValueUnit.DB_HZ) } ?: unknown(),
        getString(
            if (satellite.usedInFix) {
                R.string.network_monitor_gnss_used_in_fix
            } else {
                R.string.network_monitor_gnss_not_in_fix
            }
        ),
    )

    /**
     * Constellation names are proper nouns - GPS, GLONASS, Galileo, BeiDou - and are written the same way in
     * every locale this app ships, so they are not translated and no string key exists for them. Only the
     * platform's "no idea which system" case needs a word, and it borrows the one the Monitor already uses.
     */
    private fun constellationLabel(constellation: GnssConstellation): String =
        if (constellation == GnssConstellation.UNKNOWN) unknown() else constellation.name

    private fun renderPosition(state: GnssSectionUiState) {
        val snapshot = state.gnss.data
        val coordinate = snapshot?.coordinate
        binding.gnssPositionGroup.isVisible = coordinate != null
        binding.gnssPositionAvailability.isVisible = coordinate == null
        val availability = snapshot?.coordinateAvailability ?: state.gnss.availability
        if (!permissionManager.bind(binding.gnssPositionAvailability, availability)) {
            binding.gnssPositionAvailability.setText(availability.toReasonRes())
        }
        binding.gnssLatitudeValue.text = coordinate?.let { degrees(it.latitudeDegrees) } ?: unknown()
        binding.gnssLongitudeValue.text = coordinate?.let { degrees(it.longitudeDegrees) } ?: unknown()
        binding.gnssAccuracyValue.text = coordinate?.accuracyMeters?.let { meters(it) } ?: unknown()
        binding.gnssFixTimeValue.text = coordinate?.let { fixTime(it.fixedAtMillis) } ?: unknown()
    }

    /**
     * The share button appears only once a session file exists.
     *
     * With the setting off there is no file, and offering to share one would promise a track that was never
     * written - strategic §11 criterion 2 in its smallest form.
     */
    private fun renderTrack(state: GnssTrackState) {
        binding.gnssTrackPoints.isVisible = state.recording
        binding.gnssTrackPoints.text =
            getString(R.string.network_monitor_gnss_track_points, state.pointCount)
        binding.btnGnssShareTrack.isVisible = state.trackFilePath != null
    }

    private fun applyShareOutcome(outcome: GnssShareOutcome) {
        when (outcome) {
            is GnssShareOutcome.Ready -> startShare(outcome.uri)
            GnssShareOutcome.Unavailable -> {
                binding.gnssTrackNote.setText(R.string.network_monitor_gnss_track_share_failed)
                binding.gnssTrackNote.isVisible = true
            }
        }
    }

    /**
     * No `ActivityNotFoundException` guard: the chooser is part of the system and always resolves, so a catch
     * here would be a branch that can never run.
     */
    private fun startShare(uri: Uri) {
        binding.gnssTrackNote.isVisible = false
        val share = Intent(Intent.ACTION_SEND).apply {
            type = TRACK_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(share, getString(R.string.network_monitor_gnss_track_share))
        )
    }

    private fun trackRow(): SettingsToggleRow = binding.gnssTrackRow.root

    private fun degrees(value: Double): String =
        String.format(Locale.getDefault(), DEGREES_FORMAT, value)

    private fun meters(value: Float): String = getString(
        R.string.network_monitor_gnss_accuracy_value,
        String.format(Locale.getDefault(), METERS_FORMAT, value),
    )

    /** The device's own clock format, because a fix time is read against the clock on the same screen. */
    private fun fixTime(millis: Long): String =
        DateFormat.getTimeFormat(requireContext()).format(Date(millis))

    private fun unknown(): String = getString(R.string.network_monitor_value_unknown)

    private companion object {

        /** Five decimals is about a metre, which is finer than any consumer receiver reports. */
        const val DEGREES_FORMAT = "%.5f"

        const val METERS_FORMAT = "%.0f"

        /** The recorder writes one comma-separated line per point. */
        const val TRACK_MIME_TYPE = "text/csv"
    }
}
