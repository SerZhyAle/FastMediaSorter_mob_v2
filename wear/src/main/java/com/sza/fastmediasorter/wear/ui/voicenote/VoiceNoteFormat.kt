package com.sza.fastmediasorter.wear.ui.voicenote

import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * S1862: a recording's length as m:ss, shared by the recorder's running figure and the note list.
 *
 * Not `DateUtils.formatElapsedTime`: that is an `android.text` call, and this module's unit tests run
 * on the plain JVM where every such call returns a "not mocked" stub. The transfer ceiling of section
 * 5.4 keeps a note far under an hour, so minutes and seconds cover the whole range anyway.
 */
internal fun formatVoiceNoteDuration(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0L)
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / SECONDS_PER_MINUTE,
        totalSeconds % SECONDS_PER_MINUTE
    )
}
