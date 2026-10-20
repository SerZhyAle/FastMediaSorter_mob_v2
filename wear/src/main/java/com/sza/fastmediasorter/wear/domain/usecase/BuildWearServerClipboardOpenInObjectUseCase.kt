package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WEAR_OPEN_IN_TEXT_LIMIT
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * S4381: turns a clipboard value just received from the server into what "Open in.." hands over.
 *
 * A text is read back from its file in the slot and goes as a string - the wearer copied words, and a messenger or
 * a browser wants words (strategic criterion 9). A text too long for an intent extra goes as the file it already
 * is. Every other kind goes as a file by its media-row URI or its slot path.
 */
class BuildWearServerClipboardOpenInObjectUseCase @Inject constructor() {

    suspend operator fun invoke(
        value: WearServerClipboardValue,
        saved: WearServerClipboardSaved,
    ): WearOpenInObject? = withContext(Dispatchers.IO) {
        if (value.kind == WearServerClipboardKind.TEXT) {
            textObject(saved)
        } else {
            WearOpenInObject.File(
                mimeType = value.mimeType ?: UNKNOWN_MIME_TYPE,
                displayName = saved.displayName,
                contentUri = saved.contentUri,
                path = saved.path,
            )
        }
    }

    private fun textObject(saved: WearServerClipboardSaved): WearOpenInObject? {
        val file = saved.path?.let(::File)?.takeIf { it.isFile }
        return when {
            file == null -> null
            file.length() > WEAR_OPEN_IN_TEXT_LIMIT ->
                WearOpenInObject.File(TEXT_MIME_TYPE, saved.displayName, path = file.absolutePath)
            else -> readText(file)
        }
    }

    private fun readText(file: File): WearOpenInObject? = try {
        WearOpenInObject.Text(file.readText())
    } catch (e: IOException) {
        Timber.w("Open in: the received text could not be read (%s)", e.javaClass.simpleName)
        null
    }

    private companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val UNKNOWN_MIME_TYPE = "application/octet-stream"
    }
}
