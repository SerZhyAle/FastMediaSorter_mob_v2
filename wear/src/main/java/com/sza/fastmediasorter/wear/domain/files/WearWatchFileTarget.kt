package com.sza.fastmediasorter.wear.domain.files

/**
 * The shared-storage collection a file copied to the watch is written into.
 *
 * Three and no more, because the watch's category lists read exactly three MediaStore collections: a
 * document copied to the watch would appear in no list at all, which is why "to watch" is withheld
 * for one rather than offered and left unfindable.
 */
enum class WearWatchFileCollection {
    AUDIO,
    VIDEO,
    IMAGE
}

/**
 * Decides, from the MIME type alone, whether a file may be copied to the watch and where it lands.
 *
 * Deliberately free of Android types: the capability policy asks it to withhold the operation for a
 * type with no collection, and the publisher asks it for the folder to insert into. Deriving that
 * twice is how a menu ends up offering a copy the write side cannot place.
 */
object WearWatchFileTarget {

    private const val AUDIO_PREFIX = "audio/"
    private const val VIDEO_PREFIX = "video/"
    private const val IMAGE_PREFIX = "image/"

    /** One folder for every collection, so a copy is found where the owner last found one. */
    private const val APP_FOLDER = "FastMediaSorter"

    /**
     * `null` for anything the watch lists nowhere - a document, an archive, or no declared type.
     *
     * Matched on the prefix rather than on a table of full types, because the subtype is open-ended:
     * the audio list holds `audio/mp4` and `audio/x-ms-wma` alike, and a table would have to be kept
     * in step with the platform's own.
     */
    fun collectionOf(mimeType: String?): WearWatchFileCollection? {
        val normalised = mimeType?.trim()?.lowercase().orEmpty()
        return when {
            normalised.startsWith(AUDIO_PREFIX) -> WearWatchFileCollection.AUDIO
            normalised.startsWith(VIDEO_PREFIX) -> WearWatchFileCollection.VIDEO
            normalised.startsWith(IMAGE_PREFIX) -> WearWatchFileCollection.IMAGE
            else -> null
        }
    }

    /** Ends with a separator - the shape `RELATIVE_PATH` already carries where this module writes it. */
    fun relativePathOf(collection: WearWatchFileCollection): String = when (collection) {
        WearWatchFileCollection.AUDIO -> "Music/$APP_FOLDER/"
        WearWatchFileCollection.VIDEO -> "Movies/$APP_FOLDER/"
        WearWatchFileCollection.IMAGE -> "Pictures/$APP_FOLDER/"
    }
}
