package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Intent
import kotlinx.coroutines.flow.Flow

/**
 * One producer of launcher status-strip signals.
 *
 * The strip must not know which source it is talking to, so that the set of sources can grow without the
 * strip changing - S1465 adds foreign notifications behind this same interface (S1421 ADR-4). This is the
 * shape `NowPlayingSource` already uses for the Now Playing gadget's two backends.
 */
interface LauncherSignalSource {

    /**
     * @return this source's signals over time, an empty list whenever it has none.
     *
     * A flow rather than a snapshot getter, because the producers differ in kind: the transfer and
     * background-work sources are already change-notified and would otherwise need a collector running for
     * the app's whole life just to answer a synchronous call, while the playback source has no notification
     * at all and polls. Each decides its own cadence, and nothing runs while the strip is not collecting.
     */
    fun observe(): Flow<List<LauncherSignal>>

    /**
     * @return where a tap on [signal] goes, or null when this source did not produce it or the signal has
     * no screen behind it. Null is a real answer: the strip drops the tap target instead of starting a
     * no-op activity, so a ripple never promises something that does not happen.
     */
    fun open(signal: LauncherSignal): Intent?
}
