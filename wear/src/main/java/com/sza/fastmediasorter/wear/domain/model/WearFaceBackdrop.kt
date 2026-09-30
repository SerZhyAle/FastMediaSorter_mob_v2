package com.sza.fastmediasorter.wear.domain.model

/**
 * S3707: what the watch face draws behind its dial, derived from the watch app's own backdrop so the
 * two match one to one.
 *
 * [ordinal] is the digit the face encoder packs and the face scene decodes, so the order is frozen.
 * S3708: [PHOTO] only hides the face's waves and particles; the picture itself reaches the face through
 * its own hidden PHOTO_IMAGE slot, because a style code can carry nothing but a number.
 */
enum class WearFaceBackdrop {
    ANIMATION,
    STILL,
    NONE,
    PHOTO;

    companion object {
        /** Disabled animations freeze the app's backdrop through the reduced power policy; the face follows. */
        fun of(background: WearBackground, animationsDisabled: Boolean): WearFaceBackdrop = when (background) {
            is WearBackground.None -> NONE
            is WearBackground.BrandedStill -> STILL
            is WearBackground.BrandedAnimation -> if (animationsDisabled) STILL else ANIMATION
            // The app draws a delivered frame still whether or not animations are disabled.
            is WearBackground.Image -> PHOTO
        }
    }
}
