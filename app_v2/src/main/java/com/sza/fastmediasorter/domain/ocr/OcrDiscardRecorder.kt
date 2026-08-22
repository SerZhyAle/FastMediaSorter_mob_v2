package com.sza.fastmediasorter.domain.ocr

/**
 * S1712: keeps what the filter threw away for one recognition, so a page that produced no plate can be
 * told apart from a page where four correctly read captions were discarded.
 *
 * The channel is off by default. While it is off nothing is allocated - the recorder answers [isEnabled]
 * with false and the caller skips building a record at all, which is the whole cost budget the strategic
 * §3.2 allows: one flag comparison per fragment.
 *
 * The records carry recognised text, that is the content of the user's own picture. They stay in memory,
 * are replaced by the next recognition, and are never written to a permanent log - a hard constraint of the
 * strategic §3.2, not a preference.
 */
class OcrDiscardRecorder {

    /** One discarded fragment and the condition it failed. */
    data class Record(
        val text: String,
        val confidence: Float,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val verdict: OcrBlockFilter.Verdict,
    ) {
        /**
         * One machine-readable line: the strategic §3.1 wants a distribution built from a device dump, not
         * prose read by eye.
         */
        fun toLine(): String =
            "$verdict conf=$confidence box=${left}x$top+${width}x$height text=${text.trim()}"
    }

    /** Everything the last recognition threw away, oldest first. Empty when the channel is off. */
    val lastRun: List<Record> get() = records

    /** Number of fragments the last recognition accepted - the denominator of any ratio. */
    var lastAcceptedCount: Int = 0
        private set

    private val records = mutableListOf<Record>()
    private var enabled: Boolean = false

    fun isEnabled(): Boolean = enabled

    /**
     * Turn the channel on or off. Turning it off drops what was collected, because a stale record read
     * later would be attributed to the wrong image.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            records.clear()
            lastAcceptedCount = 0
        }
    }

    /** Start a new recognition. Does nothing while the channel is off. */
    fun beginRun() {
        if (!enabled) {
            return
        }
        records.clear()
        lastAcceptedCount = 0
    }

    /**
     * Record one evaluated fragment. Accepted fragments only move the counter - the strategic §5.1 pillar 4
     * forbids the pipeline reading any of this back, so nothing else is kept about them.
     */
    fun record(block: OcrTextBlock, verdict: OcrBlockFilter.Verdict) {
        if (!enabled) {
            return
        }
        if (verdict == OcrBlockFilter.Verdict.ACCEPTED) {
            lastAcceptedCount++
            return
        }
        records.add(
            Record(
                text = block.text,
                confidence = block.confidence,
                left = block.boundingBox.left,
                top = block.boundingBox.top,
                width = block.boundingBox.width(),
                height = block.boundingBox.height(),
                verdict = verdict,
            )
        )
    }

    /** How many fragments failed each condition in the last run. */
    fun countsByVerdict(): Map<OcrBlockFilter.Verdict, Int> =
        records.groupingBy { it.verdict }.eachCount()
}
