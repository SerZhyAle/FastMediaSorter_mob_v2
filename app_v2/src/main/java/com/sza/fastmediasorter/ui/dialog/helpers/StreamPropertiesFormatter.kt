package com.sza.fastmediasorter.ui.dialog.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * S1474: turns a stored channel and a measured transmission into the three groups the "about this
 * channel" window shows.
 *
 * Takes its strings and its date rendering through [StreamInfoResources] rather than holding an Android
 * context: the window is a view, the wording is a resource, and this class is neither - keeping it free
 * of the framework is what lets the whole mapping be unit-tested.
 */
class StreamPropertiesFormatter(private val resources: StreamInfoResources) {

    /** The channel itself - what it is and where it points. */
    fun channelGroup(entity: StreamSourceEntity): StreamInfoGroup = StreamInfoGroup(
        titleRes = R.string.stream_info_group_channel,
        properties = listOf(
            row(R.string.stream_info_label_title, entity.title),
            row(R.string.stream_info_label_address, entity.url),
            row(R.string.stream_info_label_kind, resources.string(kindRes(entity.mediaKind))),
        ),
    )

    /**
     * What this device's list remembers about it, as opposed to what the transmission carries.
     *
     * S1502: [lastPlayOutcome] arrives as a value rather than being read off [entity] - it left the
     * catalog row for its own table, and this class stays a pure mapping over what it is given.
     */
    fun catalogGroup(entity: StreamSourceEntity, lastPlayOutcome: String?): StreamInfoGroup = StreamInfoGroup(
        titleRes = R.string.stream_info_group_catalog,
        properties = listOf(
            row(R.string.stream_info_label_origin, resources.string(originRes(entity.sourceOrigin))),
            row(R.string.stream_info_label_added, resources.dateTime(entity.addedAt)),
            row(R.string.stream_info_label_last_played, entity.lastPlayedAt?.let(resources::dateTime)),
            row(R.string.stream_info_label_last_outcome, resources.string(outcomeRes(lastPlayOutcome))),
            row(R.string.stream_info_label_pinned, resources.string(if (entity.pinned) R.string.yes else R.string.no)),
            row(R.string.streams_filter_topic, entity.topic),
            row(R.string.streams_filter_category, entity.category),
            row(R.string.streams_filter_language, entity.language),
            row(R.string.streams_filter_country, entity.country),
            row(R.string.stream_info_label_access, resources.string(accessRes(entity.access))),
        ),
    )

    /**
     * What the engine reported. Every row is present even when nothing was reported (ADR-5), because a
     * missing row would read as "this stream has no audio" rather than "we have not been told yet".
     */
    fun measuredGroup(formats: StreamMeasuredFormats): StreamInfoGroup = StreamInfoGroup(
        titleRes = R.string.stream_info_group_measured,
        properties = listOf(
            measured(R.string.stream_info_label_video_codec, formats.videoMimeType),
            measured(R.string.stream_info_label_picture_size, pictureSize(formats)),
            measured(R.string.stream_info_label_frame_rate, formats.frameRate?.let(resources::frameRate)),
            measured(R.string.stream_info_label_video_bitrate, bitrate(formats.videoBitrate)),
            measured(R.string.stream_info_label_audio_codec, formats.audioMimeType),
            measured(R.string.stream_info_label_audio_channels, formats.audioChannelCount?.toString()),
            measured(R.string.stream_info_label_sample_rate, formats.audioSampleRate?.let(resources::sampleRate)),
            measured(R.string.stream_info_label_audio_bitrate, bitrate(formats.audioBitrate)),
            measured(R.string.stream_info_label_observed_rate, formats.observedBitrate?.let(resources::bitrate)),
        ),
    )

    /** Every group of a stored channel, in reading order. */
    fun readout(
        entity: StreamSourceEntity,
        formats: StreamMeasuredFormats,
        lastPlayOutcome: String?,
    ): StreamInfoReadout.Resolved =
        StreamInfoReadout.Resolved(
            listOf(channelGroup(entity), catalogGroup(entity, lastPlayOutcome), measuredGroup(formats)),
        )

    /**
     * The whole window as plain text, one row per line, so a bug report can carry it.
     *
     * Group titles are included: without them "Not reported" three times over says nothing about which
     * three values were not reported.
     */
    fun asPlainText(readout: StreamInfoReadout.Resolved): String = buildString {
        readout.groups.forEach { group ->
            appendLine(resources.string(group.titleRes))
            group.properties.forEach { property ->
                appendLine("${resources.string(property.labelRes)}: ${displayValue(property.value)}")
            }
        }
    }.trimEnd()

    private fun displayValue(value: StreamInfoValue): String = when (value) {
        is StreamInfoValue.Text -> value.value
        StreamInfoValue.Empty -> resources.string(R.string.stream_info_state_empty)
        StreamInfoValue.Unavailable -> resources.string(R.string.stream_info_state_unavailable)
    }

    /** Half a picture size is not a picture size, so both dimensions are needed before a row can say one. */
    private fun pictureSize(formats: StreamMeasuredFormats): String? {
        val width = formats.videoWidth
        val height = formats.videoHeight
        return if (width == null || height == null) null else resources.pictureSize(width, height)
    }

    /** The two declared bitrates arrive as Int, the observed one as Long; the label reads the same. */
    private fun bitrate(bitsPerSecond: Int?): String? = bitsPerSecond?.let { resources.bitrate(it.toLong()) }

    /** A stored field that was never filled is [StreamInfoValue.Empty] - the row stays, the value does not. */
    private fun row(@StringRes labelRes: Int, value: String?): StreamInfoProperty = StreamInfoProperty(
        labelRes = labelRes,
        value = if (value.isNullOrBlank()) StreamInfoValue.Empty else StreamInfoValue.Text(value),
    )

    /** A measured field we were not told is [StreamInfoValue.Unavailable], never empty and never zero. */
    private fun measured(@StringRes labelRes: Int, value: String?): StreamInfoProperty = StreamInfoProperty(
        labelRes = labelRes,
        value = if (value.isNullOrBlank()) StreamInfoValue.Unavailable else StreamInfoValue.Text(value),
    )

    /** An unknown stored code falls back to the code itself rather than throwing: the row must still render. */
    @StringRes
    private fun kindRes(code: String): Int = when (code.uppercase()) {
        KIND_AUDIO -> R.string.stream_info_kind_audio
        KIND_VIDEO -> R.string.stream_info_kind_video
        KIND_RTSP -> R.string.stream_info_kind_rtsp
        else -> R.string.stream_info_state_empty
    }

    @StringRes
    private fun originRes(code: String): Int = when (code.uppercase()) {
        ORIGIN_MANUAL -> R.string.stream_info_origin_manual
        ORIGIN_IMPORTED -> R.string.stream_info_origin_imported
        ORIGIN_CATALOG -> R.string.stream_info_origin_catalog
        else -> R.string.stream_info_state_empty
    }

    @StringRes
    private fun outcomeRes(code: String?): Int = when (code?.uppercase()) {
        OUTCOME_OK -> R.string.stream_info_outcome_ok
        OUTCOME_FAIL -> R.string.stream_info_outcome_fail
        else -> R.string.stream_info_outcome_never
    }

    /** Blank and null both mean open: S1117 stores the restriction, not its absence. */
    @StringRes
    private fun accessRes(code: String?): Int = when (code?.trim()?.lowercase()) {
        ACCESS_GEO -> R.string.stream_info_access_geo
        else -> R.string.stream_info_access_open
    }

    private companion object {
        const val KIND_AUDIO = "AUDIO"
        const val KIND_VIDEO = "VIDEO"
        const val KIND_RTSP = "RTSP"

        const val ORIGIN_MANUAL = "MANUAL"
        const val ORIGIN_IMPORTED = "IMPORTED"
        const val ORIGIN_CATALOG = "CATALOG"

        const val OUTCOME_OK = "OK"
        const val OUTCOME_FAIL = "FAIL"

        const val ACCESS_GEO = "geo"
    }
}

/**
 * S1474: everything the formatter needs from the platform, named so a test can supply it without one.
 */
interface StreamInfoResources {

    fun string(@StringRes resId: Int): String

    /** Rendered with the device's own date and time format, not one this ticket invents. */
    fun dateTime(epochMillis: Long): String

    fun pictureSize(width: Int, height: Int): String

    fun frameRate(value: Float): String

    fun bitrate(bitsPerSecond: Long): String

    fun sampleRate(hertz: Int): String
}
