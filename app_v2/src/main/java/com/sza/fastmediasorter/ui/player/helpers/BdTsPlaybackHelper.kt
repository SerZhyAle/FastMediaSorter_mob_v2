package com.sza.fastmediasorter.ui.player.helpers

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sza.fastmediasorter.data.network.datasource.BdTsStripDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

// Only BD_192 requires header stripping; STANDARD_188 and UNKNOWN are plain pass-through.
internal fun shouldUseBdTsStripper(format: TsPacketFormat): Boolean = format == TsPacketFormat.BD_192

internal fun DataSource.Factory.wrapForBdTs(path: String): DataSource.Factory {
    val lower = path.lowercase()
    return if (lower.endsWith(".m2ts") || lower.endsWith(".m2t")) {
        BdTsStripDataSourceFactory(this)
    } else {
        this
    }
}

internal fun DataSource.Factory.wrapForBdTs(format: TsPacketFormat): DataSource.Factory =
    if (shouldUseBdTsStripper(format)) BdTsStripDataSourceFactory(this) else this

// FLAG_IGNORE_SPLICE_INFO_STREAM prevents ExoPlayer's SpliceInfoDecoder from crashing
// on Blu-ray TS SCTE-35 metadata packets (IllegalStateException in ParsableBitArray.skipBits).
internal fun DataSource.Factory.buildBdTsMediaSourceFactory(format: TsPacketFormat): DefaultMediaSourceFactory {
    val wrapped = wrapForBdTs(format)
    return if (format == TsPacketFormat.BD_192) {
        DefaultMediaSourceFactory(
            wrapped,
            DefaultExtractorsFactory().apply {
                setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM)
            }
        )
    } else {
        DefaultMediaSourceFactory(wrapped)
    }
}

internal suspend fun DataSource.Factory.detectTsFormatSuspend(uri: Uri): TsPacketFormat =
    withContext(Dispatchers.IO) {
        val source = createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(0L)
                .setLength(TsPacketFormatDetector.PROBE_BYTES.toLong())
                .build()
            source.open(spec)
            val probe = ByteArray(TsPacketFormatDetector.PROBE_BYTES)
            var totalRead = 0
            while (totalRead < TsPacketFormatDetector.PROBE_BYTES) {
                val n = source.read(probe, totalRead, TsPacketFormatDetector.PROBE_BYTES - totalRead)
                if (n == C.RESULT_END_OF_INPUT) break
                totalRead += n
            }
            TsPacketFormatDetector.detect(probe.copyOf(totalRead))
        } catch (e: Exception) {
            Timber.w(e, "detectTsFormatSuspend: probe failed for $uri")
            TsPacketFormat.UNKNOWN
        } finally {
            try { source.close() } catch (_: Exception) {}
        }
    }
