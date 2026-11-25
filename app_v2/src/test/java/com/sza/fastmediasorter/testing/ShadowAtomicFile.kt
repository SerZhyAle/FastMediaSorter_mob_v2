package com.sza.fastmediasorter.testing

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces the private `AtomicFile.rename` that Robolectric runs from the real android-all image,
 * wired once in `src/test/resources/robolectric.properties`.
 *
 * S4558: that method ends in `File.renameTo`, which on Windows refuses to replace an existing
 * destination, so every `finishWrite` after the first was dropped (AtomicFile only logs the failure)
 * and a journal written twice read back as written once. On a device and on Linux `renameTo`
 * replaces, so the tests passed on CI and failed on the Windows workstation. Same host artifact as
 * S1449, which the DataStore tests avoid by choosing okio storage; `FillerSessionJournal` builds its
 * `AtomicFile` internally, so the host difference is closed here and production code stays unchanged.
 *
 * A class with a public constructor, not an `object`: Robolectric builds one shadow instance per
 * shadowed `AtomicFile` and needs an accessible constructor for it, even though only the static
 * method below is used.
 */
@Suppress("UtilityClassWithPublicConstructor")
@Implements(AtomicFile::class)
class ShadowAtomicFile {

    companion object {
        // Robolectric matches the private static method by name and parameter types.
        @JvmStatic
        @Implementation
        fun rename(source: File, dest: File) {
            try {
                Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                // The real method logs a failed rename instead of throwing; the shadow keeps that contract.
                Timber.e(e, "AtomicFile shadow failed to rename %s to %s", source, dest)
            }
        }
    }
}
