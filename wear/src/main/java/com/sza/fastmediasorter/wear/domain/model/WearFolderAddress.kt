package com.sza.fastmediasorter.wear.domain.model

/** Separates the scheme letter from the rest of a token. */
private const val SCHEME_SEPARATOR = ':'

/** Marks a level that is a real directory in the app's own tree. */
private const val SCHEME_APP_OWNED = "f"

/** Marks a level reconstructed from the `RELATIVE_PATH` of MediaStore rows. */
private const val SCHEME_MEDIA_STORE = "m"

/** Marks a directory of a network source (S2694). */
private const val SCHEME_NETWORK = "n"

/**
 * Where one level of the watch-local folder walk lives.
 *
 * S2201 ADR-3 makes the walk span two structurally different halves of watch storage, and they are
 * reached by different mechanisms rather than by one path string:
 *
 * - [AppOwned] is a real directory under the app's own roots, enumerated with `java.io.File`. This is
 *   the only part of watch storage a filesystem walk reaches without a permission, and it is where
 *   files transferred from the phone land - MediaStore indexes none of them.
 * - [MediaStoreFolder] is a level of shared storage, which no app may walk at targetSdk 36 without
 *   special access. Its hierarchy is reconstructed by grouping rows on `RELATIVE_PATH`, so a folder
 *   holding no file cannot appear: MediaStore knows files, not directories.
 *
 * A single path string could not carry that distinction, and a data source that guessed it would walk
 * the wrong mechanism for half its levels.
 *
 * [asToken] and [parse] round-trip an address through a navigation argument. The token is placed in a
 * route by `WearRoutes.localFolder`, which percent-encodes it - folder names legally contain `&` and
 * `/`, and an unencoded one truncates the route or misses its pattern with no error and no log.
 */
sealed interface WearFolderAddress {

    /** The entrance of the walk: the app-owned roots followed by the top level of shared storage. */
    data object Root : WearFolderAddress

    /** A real directory, addressed by its absolute path. */
    data class AppOwned(val path: String) : WearFolderAddress

    /**
     * A shared-storage level, addressed by the `RELATIVE_PATH` prefix its rows share.
     *
     * Carries MediaStore's own trailing separator, so `DCIM/Camera/` and not `DCIM/Camera`; a prefix
     * comparison against a stored value has to match the form the column actually holds.
     */
    data class MediaStoreFolder(val relativePath: String) : WearFolderAddress

    /**
     * A directory of a network source, addressed by that source and a path inside it (S2694).
     *
     * [path] is already in the form the owning protocol client lists: share-relative for SMB, where
     * the share root is the **empty string**, and an absolute server path for FTP and SFTP. The
     * protocol itself is read from the source record and never from this scheme - a fourth protocol
     * is a fourth source type, not a fourth address scheme.
     */
    data class NetworkLevel(val sourceId: String, val path: String) : WearFolderAddress

    /** The route-safe form of this address. */
    fun asToken(): String = when (this) {
        is Root -> ""
        is AppOwned -> "$SCHEME_APP_OWNED$SCHEME_SEPARATOR$path"
        is MediaStoreFolder -> "$SCHEME_MEDIA_STORE$SCHEME_SEPARATOR$relativePath"
        is NetworkLevel -> "$SCHEME_NETWORK$SCHEME_SEPARATOR$sourceId$SCHEME_SEPARATOR$path"
    }

    companion object {

        /**
         * The address [token] names, or null when it names none.
         *
         * A blank token is [Root] rather than a failure: the walk's own route declares its argument
         * optional, so opening the entrance passes nothing at all.
         */
        fun parse(token: String?): WearFolderAddress? {
            if (token.isNullOrEmpty()) return Root
            val scheme = token.substringBefore(SCHEME_SEPARATOR, missingDelimiterValue = "")
            val value = token.substringAfter(SCHEME_SEPARATOR, missingDelimiterValue = "")
            return when {
                // S2694: judged before the emptiness test below, which this scheme does not share -
                // an SMB share root is legitimately the empty path, and refusing it here would send
                // the walk to the local root instead, a wrong screen rather than a visible failure.
                scheme == SCHEME_NETWORK -> parseNetworkLevel(value)
                value.isEmpty() -> null
                scheme == SCHEME_APP_OWNED -> AppOwned(value)
                scheme == SCHEME_MEDIA_STORE -> MediaStoreFolder(value)
                else -> null
            }
        }

        /**
         * The network level `<sourceId>:<path>` names, or null when it names no source.
         *
         * The path may be empty (the SMB share root) but the source identifier may not: without it
         * there is nothing to list against.
         */
        private fun parseNetworkLevel(value: String): WearFolderAddress? {
            val separator = value.indexOf(SCHEME_SEPARATOR)
            if (separator <= 0) return null
            return NetworkLevel(
                sourceId = value.substring(0, separator),
                path = value.substring(separator + 1)
            )
        }
    }
}
