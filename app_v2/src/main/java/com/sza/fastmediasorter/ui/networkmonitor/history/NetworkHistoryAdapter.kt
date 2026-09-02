package com.sza.fastmediasorter.ui.networkmonitor.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemNetworkMeasurementBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind

/**
 * S1433: the stored measurements, newest first.
 *
 * A `ListAdapter` rather than a joined text block, unlike the Bluetooth section's short device list: the
 * history is capped in the hundreds, and inflating that many rows into a `ScrollView` would build the whole
 * list on every store write.
 */
class NetworkHistoryAdapter :
    ListAdapter<NetworkMeasurement, NetworkHistoryAdapter.MeasurementViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val binding = ItemNetworkMeasurementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return MeasurementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MeasurementViewHolder(
        private val binding: ItemNetworkMeasurementBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(measurement: NetworkMeasurement) {
            val context = binding.root.context
            binding.measurementTime.text = DateUtils.formatDateTime(
                context,
                measurement.takenAtMillis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
            )
            binding.measurementKind.setText(measurement.kind.toLabelRes())
            binding.measurementNetwork.text = measurement.networkLabel
            binding.measurementResult.text = measurement.resultText
            binding.measurementOutcome.setText(
                if (measurement.succeeded) {
                    R.string.network_monitor_history_outcome_ok
                } else {
                    R.string.network_monitor_history_outcome_failed
                }
            )
        }
    }

    private companion object {

        /**
         * Identity is the store's own id, and content is the whole row.
         *
         * A stored measurement is never edited, so equality by value is exact here rather than an
         * approximation - a row that differs at all is a different measurement.
         */
        val DIFF = object : DiffUtil.ItemCallback<NetworkMeasurement>() {

            override fun areItemsTheSame(oldItem: NetworkMeasurement, newItem: NetworkMeasurement): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: NetworkMeasurement,
                newItem: NetworkMeasurement,
            ): Boolean = oldItem == newItem
        }
    }
}

@StringRes
private fun NetworkMeasurementKind.toLabelRes(): Int = when (this) {
    NetworkMeasurementKind.SPEED_TEST -> R.string.network_monitor_history_kind_speed_test
    NetworkMeasurementKind.RESOURCE_CHECK -> R.string.network_monitor_history_kind_resource_check
    NetworkMeasurementKind.EXTERNAL_IP -> R.string.network_monitor_history_kind_external_ip
    NetworkMeasurementKind.SUBNET_SCAN -> R.string.network_monitor_history_kind_subnet_scan
    NetworkMeasurementKind.PING -> R.string.network_monitor_history_kind_ping
    NetworkMeasurementKind.TRACEROUTE -> R.string.network_monitor_history_kind_traceroute
}
