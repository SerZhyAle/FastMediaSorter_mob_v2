package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.databinding.DialogStreamInfoBinding
import com.sza.fastmediasorter.ui.dialog.helpers.StreamFormatProbeManager
import com.sza.fastmediasorter.ui.dialog.helpers.StreamInfoGroup
import com.sza.fastmediasorter.ui.dialog.helpers.StreamInfoProperty
import com.sza.fastmediasorter.ui.dialog.helpers.StreamInfoReadout
import com.sza.fastmediasorter.ui.dialog.helpers.StreamInfoResources
import com.sza.fastmediasorter.ui.dialog.helpers.StreamInfoValue
import com.sza.fastmediasorter.ui.dialog.helpers.StreamMeasuredFormats
import com.sza.fastmediasorter.ui.dialog.helpers.StreamPropertiesFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * S1474: "about this channel" - what the catalog stores about a channel and what its transmission
 * actually carries, measured once when the window opens.
 *
 * A separate window rather than a branch of the file-properties dialog, by the owner's ruling of
 * 2026-08-07 (strategic §3.3). Opens in three shapes: a stored channel, a stored channel plus the engine
 * already playing it, or a bare url for a channel that is not in the list.
 *
 * Holds no business logic: the wording and the code-to-word mapping belong to [StreamPropertiesFormatter],
 * the format extraction to [StreamFormatProbeManager]. What is here is inflation, state switching and
 * event wiring.
 */
@UnstableApi
class StreamInfoDialog(
    context: Context,
    private val entity: StreamSourceEntity?,
    private val url: String,
    /**
     * An engine someone else owns and is already playing this channel on. When present the readout is
     * taken off it, because a server allowing one session per client would drop the user's picture if a
     * second connection were opened. Never released here.
     */
    private val playingEngine: Player? = null,
    /**
     * S1502: the last recorded play outcome, resolved by the caller. It no longer lives on [entity] -
     * a probe result writes its own table so it cannot re-emit the catalog to the streams list.
     */
    private val lastPlayOutcome: String? = null,
) : Dialog(context) {

    private lateinit var binding: DialogStreamInfoBinding

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var measurement: Job? = null

    private val infoResources = DialogStreamInfoResources(context)
    private val formatter = StreamPropertiesFormatter(infoResources)
    private val probe = StreamFormatProbeManager(context.applicationContext, scope)

    /** Whatever the window currently shows, so the copy action never claims more than the reader sees. */
    private var shownGroups: List<StreamInfoGroup> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogStreamInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderStoredGroups()
        binding.streamInfoCopy.setOnClickListener { copyReadout() }
        binding.streamInfoClose.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        startMeasurement()
    }

    override fun dismiss() {
        stopMeasurement()
        super.dismiss()
    }

    override fun onStop() {
        stopMeasurement()
        super.onStop()
    }

    private fun stopMeasurement() {
        measurement?.cancel()
        measurement = null
        probe.cancel()
    }

    /**
     * The stored half renders at once - it is already on this device, so making the reader wait for a
     * network measurement before seeing it would be a delay with no cause.
     */
    private fun renderStoredGroups() {
        val stored = entity
        if (stored == null) {
            binding.streamInfoNotInCatalog.visibility = View.VISIBLE
            binding.streamInfoCatalogHeading.visibility = View.GONE
            addRows(binding.streamInfoChannelRows, listOf(addressOnlyRow()))
            shownGroups = listOf(addressOnlyGroup())
            return
        }
        val channel = formatter.channelGroup(stored)
        val catalog = formatter.catalogGroup(stored, lastPlayOutcome)
        addRows(binding.streamInfoChannelRows, channel.properties)
        addRows(binding.streamInfoCatalogRows, catalog.properties)
        shownGroups = listOf(channel, catalog)
    }

    private fun startMeasurement() {
        if (measurement != null) return
        binding.streamInfoMeasuredState.visibility = View.VISIBLE
        binding.streamInfoMeasuredState.setText(R.string.stream_info_state_measuring)
        measurement = scope.launch {
            val engine = playingEngine
            val measured = if (engine != null) probe.readFrom(engine) else probe.measure(url)
            renderMeasured(measured)
        }
    }

    /**
     * A channel that answered nothing at all gets the failure line; one that answered partially gets its
     * rows, each unanswered field reading "not reported" (ADR-5).
     */
    private fun renderMeasured(measured: StreamMeasuredFormats) {
        val group = formatter.measuredGroup(measured)
        val nothingReported = group.properties.all { it.value == StreamInfoValue.Unavailable }
        if (nothingReported) {
            binding.streamInfoMeasuredState.setText(R.string.stream_info_state_unavailable)
        } else {
            binding.streamInfoMeasuredState.visibility = View.GONE
        }
        addRows(binding.streamInfoMeasuredRows, group.properties)
        shownGroups = shownGroups + group
    }

    private fun copyReadout() {
        val text = formatter.asPlainText(StreamInfoReadout.Resolved(shownGroups))
        context.copyTextToClipboard(infoResources.string(R.string.stream_info_window_title), text)
    }

    private fun addRows(container: ViewGroup, properties: List<StreamInfoProperty>) {
        container.removeAllViews()
        properties.forEach { container.addView(rowView(it)) }
    }

    /**
     * Built in code rather than inflated: one label plus one value is cheaper as two `TextView`s than as a
     * layout file whose only content would be those two views.
     */
    private fun rowView(property: StreamInfoProperty): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        row.addView(
            TextView(context).apply {
                text = infoResources.string(property.labelRes)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, LABEL_WEIGHT)
            },
        )
        row.addView(
            TextView(context).apply {
                text = displayValue(property.value)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, VALUE_WEIGHT)
            },
        )
        return row
    }

    private fun displayValue(value: StreamInfoValue): String = when (value) {
        is StreamInfoValue.Text -> value.value
        StreamInfoValue.Empty -> infoResources.string(R.string.stream_info_state_empty)
        StreamInfoValue.Unavailable -> infoResources.string(R.string.stream_info_state_unavailable)
    }

    private fun addressOnlyRow(): StreamInfoProperty =
        StreamInfoProperty(R.string.stream_info_label_address, StreamInfoValue.Text(url))

    private fun addressOnlyGroup(): StreamInfoGroup =
        StreamInfoGroup(R.string.stream_info_group_channel, listOf(addressOnlyRow()))

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val LABEL_WEIGHT = 2f
        const val VALUE_WEIGHT = 3f
    }
}

/** S1474: the platform side of [StreamInfoResources] - the half a unit test replaces with a fake. */
private class DialogStreamInfoResources(private val context: Context) : StreamInfoResources {

    override fun string(@StringRes resId: Int): String = context.getString(resId)

    /** The device's own date and time format, so the window reads like the rest of the system. */
    override fun dateTime(epochMillis: Long): String {
        val date = DateFormat.getDateFormat(context).format(epochMillis)
        val time = DateFormat.getTimeFormat(context).format(epochMillis)
        return "$date $time"
    }

    override fun pictureSize(width: Int, height: Int): String =
        context.getString(R.string.stream_info_value_picture_size, width, height)

    override fun frameRate(value: Float): String =
        context.getString(R.string.stream_info_value_frame_rate, String.format(Locale.getDefault(), "%.1f", value))

    /** Shown in kbit/s: bits per second on a video channel is a seven-digit number nobody reads. */
    override fun bitrate(bitsPerSecond: Long): String =
        context.getString(R.string.stream_info_value_bitrate, (bitsPerSecond / BITS_PER_KBIT).toString())

    override fun sampleRate(hertz: Int): String =
        context.getString(R.string.stream_info_value_sample_rate, hertz.toString())

    private companion object {
        const val BITS_PER_KBIT = 1000L
    }
}
