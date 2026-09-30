package com.sza.fastmediasorter.domain.ocr

/**
 * S1712: keeps what the filter threw away for one recognition, so a page that produced no plate can be
 * told apart from a page where four correctly read captions were discarded.
 *
 * A new instance is off, and while it is off nothing is allocated - one flag comparison per fragment. Shipped
 * builds turn it on in [com.sza.fastmediasorter.ui.player.helpers.RecognitionBackend] (S3446, `OCR-OVERLAY`
 * rule 12): a bug report comes from the log, and a switch would have to be on before the bug happened.
 *
 * The records carry recognised text, that is the content of the user's own picture. They stay in memory,
 * are replaced by the next recognition, and only their text-free forms ([Record.toLogLine], [summaryLine])
 * reach a log - a hard constraint of the S1712 strategic §3.2, not a preference.
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

        /** [toLine] without the text: the only form allowed into the app log, which outlives the recognition. */
        fun toLogLine(): String = "$verdict conf=$confidence box=${left}x$top+${width}x$height"
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

    /**
     * One text-free line per recognition, written also when [readCount] is 0: `OCR-OVERLAY` rule 12 exists for
     * the image that produced nothing, where "the engine read nothing" and "every line was dropped" must differ.
     */
    fun summaryLine(readCount: Int): String =
        "read=$readCount accepted=$lastAcceptedCount dropped=${records.size} by=${countsByVerdict()}"
}
