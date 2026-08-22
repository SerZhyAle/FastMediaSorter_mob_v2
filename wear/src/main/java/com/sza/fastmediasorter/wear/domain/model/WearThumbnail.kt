package com.sza.fastmediasorter.wear.domain.model

import android.graphics.Bitmap

/**
 * What a file list knows about one cell's picture.
 *
 * [Unavailable] and [Loading] are deliberately separate: a file that cannot carry a preview must
 * never be asked again, while a read that has not finished must not be mistaken for a final answer.
 * Collapsing the two into a nullable bitmap would reopen a network connection on every scroll.
 */
sealed interface WearThumbnail {

    /** The picture is ready to draw. */
    data class Ready(val bitmap: Bitmap) : WearThumbnail

    /** This file has no obtainable preview. Asking again would cost the same and answer the same. */
    data object Unavailable : WearThumbnail

    /** No answer yet. The cell draws its type icon meanwhile. */
    data object Loading : WearThumbnail
}
