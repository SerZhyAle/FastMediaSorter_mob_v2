package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.capture.LocalCaptureDestinationWriter
import java.io.File
import javax.inject.Inject

/**
 * Writes a finished capture file into an on-device folder under a name that is free there
 * (CAPTURE-OUTPUT rules 5-6). The UI-side capture helpers reach the collision-aware writer through
 * this use case instead of importing the data layer.
 */
class WriteCaptureFileUseCase @Inject constructor(
    private val captureWriter: LocalCaptureDestinationWriter,
) {

    /** [location] is the committed path or URI; [displayName] is the name the file really got. */
    data class SavedCapture(val location: String, val displayName: String)

    suspend operator fun invoke(tempFile: File, folderPath: String, name: String): Result<SavedCapture> =
        captureWriter.writeCapture(tempFile, folderPath, name)
            .map { SavedCapture(it.location, it.displayName) }
}
