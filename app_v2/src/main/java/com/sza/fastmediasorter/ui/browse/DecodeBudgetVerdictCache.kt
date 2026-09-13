package com.sza.fastmediasorter.ui.browse

import java.util.concurrent.ConcurrentHashMap

/**
 * S3072: decode-budget verdict per file path, remembered for the process lifetime.
 *
 * The verdict is derived from the file's own header and a constant target size, so it cannot change
 * while the app runs. [cached] never touches the disk and is what the bind thread may ask; [resolve]
 * runs the header read and must only be called off the main thread. Concurrent because the bind thread
 * reads while IO workers write.
 */
internal class DecodeBudgetVerdictCache {

    private val verdicts = ConcurrentHashMap<String, Boolean>()

    /** The remembered verdict, or null when [path] has not been measured yet. */
    fun cached(path: String): Boolean? = verdicts[path]

    /**
     * Returns the remembered verdict or measures it once.
     *
     * A null from [measure] means the file could not be measured: the budget has no opinion, so the
     * answer is false and nothing is remembered - the ordinary decode path is still allowed to try.
     */
    fun resolve(path: String, measure: (String) -> Boolean?): Boolean =
        verdicts[path] ?: measure(path)?.also { verdicts[path] = it } ?: false

    companion object {
        /** Only plain local paths have a header this cache can read; remote schemes get no opinion. */
        fun isMeasurable(path: String): Boolean = path.isNotBlank() && !path.contains("://")
    }
}
