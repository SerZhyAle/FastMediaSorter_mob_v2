package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.sza.fastmediasorter.wear.data.bodysensor.PpgWindowCsv
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.repository.PpgWindowRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * S3113: keeps each window as one CSV file under the app's private files directory, where `run-as` on a
 * debug build can pull it and nothing else can read it.
 */
class PpgWindowFileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : PpgWindowRepository {

    override suspend fun save(window: PpgWindow, label: String?): String? = withContext(Dispatchers.IO) {
        val name = fileNameOf(window, label)
        try {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            File(directory, name).writeText(PpgWindowCsv.format(window))
            name
        } catch (e: IOException) {
            Timber.e(e, "Could not archive a pulse-wave window")
            null
        }
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        File(context.filesDir, DIRECTORY).listFiles { file -> file.name.endsWith(EXTENSION) }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    /**
     * An unreadable file is a lost archive copy, not a crash: IO and a malformed body both answer null, and
     * the caller decides whether a missing window matters.
     */
    override suspend fun load(fileName: String): PpgWindow? = withContext(Dispatchers.IO) {
        try {
            PpgWindowCsv.parse(File(File(context.filesDir, DIRECTORY), fileName).readText())
        } catch (e: IOException) {
            Timber.w(e, "Could not read the archived pulse-wave window %s", fileName)
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "The archived pulse-wave window %s is malformed", fileName)
            null
        }
    }

    private fun fileNameOf(window: PpgWindow, label: String?): String {
        val suffix = label?.replace(UNSAFE_NAME_CHARACTERS, "-")?.let { "_$it" }.orEmpty()
        return "${window.startedAtMillis}$suffix$EXTENSION"
    }

    private companion object {
        const val DIRECTORY = "blood_pressure/ppg"
        const val EXTENSION = ".csv"
        val UNSAFE_NAME_CHARACTERS = Regex("[^A-Za-z0-9-]")
    }
}
