package com.sza.fastmediasorter.domain.ocr

/**
 * What the image handed to the recogniser actually is.
 *
 * The engine must be told a resolution, and the honest answer differs per kind rather than per
 * pixel count (S1715, S1876). Each member names where its resolution comes from:
 *
 * - [RENDERED_DOCUMENT_PAGE] - we rendered it ourselves, so the value is arithmetic on the page's
 *   point size and the bitmap width, not a guess.
 * - [SCREENSHOT] - the screen density is known to the system exactly.
 * - [CAMERA_PHOTO] - the scene size is not stored anywhere, but it follows from the subject
 *   distance and the 35 mm-equivalent focal length when EXIF carries both, which makes this kind
 *   arithmetic too for such photos (S1876). Only a photo carrying neither tag is left estimated,
 *   and it takes the floor rather than an assumed page size.
 * - [UNKNOWN] - the caller could not tell. It yields the floor rather than a plausible-looking
 *   number, because a wrong declared resolution is worse than a deliberately conservative one.
 *
 * A file opened from disk is not a fourth kind: it is a [CAMERA_PHOTO] or a [SCREENSHOT], and which
 * one is decided from EXIF the app already extracts (make, model, focal length) rather than from its
 * dimensions. Deciding it by size is the guess this type exists to remove.
 */
enum class OcrSourceKind {
    CAMERA_PHOTO,
    SCREENSHOT,
    RENDERED_DOCUMENT_PAGE,
    UNKNOWN,
}
