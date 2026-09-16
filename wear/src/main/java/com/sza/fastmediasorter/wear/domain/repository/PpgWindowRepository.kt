package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow

/**
 * S3113: the archive of raw pulse-wave windows.
 *
 * Every window behind a calibration is kept, because the algorithm is the ticket's main risk (strategic
 * §7): a kept window lets an improved algorithm refit the model on the owner's existing cuff readings
 * instead of asking him to measure again.
 */
interface PpgWindowRepository {

    /**
     * Stores [window], with [label] appended to its file name when given. Returns the file name, or null
     * when the write failed - a lost archive copy must not cost the user the measurement it came from.
     */
    suspend fun save(window: PpgWindow, label: String?): String?

    /** File names of every archived window, oldest first. */
    suspend fun list(): List<String>

    /** The window stored under [fileName], or null when it is missing or unreadable. */
    suspend fun load(fileName: String): PpgWindow?
}
