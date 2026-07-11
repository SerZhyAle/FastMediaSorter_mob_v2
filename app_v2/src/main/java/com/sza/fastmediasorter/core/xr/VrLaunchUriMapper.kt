package com.sza.fastmediasorter.core.xr

import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaFile
import java.io.File

/**
 * Single source of the caller-side URI mapping used by every VR launch entry point
 * (browse file menu, player badge, immersive resource browser). A bare `/`-path is a
 * filesystem path that must be wrapped as a `file://` URI; `file://`/`content://` pass through.
 */
internal fun MediaFile.toLaunchUriString(): String = when {
    path.startsWith("file://") || path.startsWith("content://") -> path
    path.startsWith("/") -> Uri.fromFile(File(path)).toString()
    else -> path
}
