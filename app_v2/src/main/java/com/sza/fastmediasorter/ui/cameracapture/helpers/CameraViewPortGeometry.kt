package com.sza.fastmediasorter.ui.cameracapture.helpers

/**
 * S1986: the shape the capture pipeline must keep, as `ViewPort` reads it - width over height of the
 * VIEWFINDER, in the viewfinder's own coordinate space.
 *
 * Pure arithmetic, no Android type, so the rule is provable on the JVM. It has to be provable there:
 * the device sweep that measures this pipeline cannot see the moment a session binds, and both
 * defects below live in exactly that moment.
 *
 * Two things this exists to prevent, each measured on a Galaxy S21 rather than reasoned about:
 *
 * - The shape stated in LANDSCAPE form (`Rational(4, 3)`) beside the DEVICE pose. Held upright those
 *   two disagree: CameraX cropped the sensor buffer to 3:4 and the EXIF tag then turned that already
 *   turned frame a second time. The ultra-wide at 16:9 came out 1276x2268 instead of 4032x2268 -
 *   under a third of the height the viewfinder had shown, and lying on its side.
 * - The shape taken from the LIVE VIEW's pixels. The host resizes the viewfinder when the frame shape
 *   changes, and the rebind races that resize: asked at bind time, the view still answers with its
 *   previous size. A 4:3 shot then came out 4032x1814 - cropped to the shape of the whole screen,
 *   because that is what the view still was.
 *
 * So the shape comes from the SELECTION, which is decided before either race, and only the
 * screen-filling selection asks the screen - that selection is defined by the screen.
 */
internal object CameraViewPortGeometry {

    /** Width and height of a 16:9 stream as a portrait host shows it. */
    private const val PORTRAIT_16_9_WIDTH = 9
    private const val PORTRAIT_16_9_HEIGHT = 16

    /** Width and height of a 4:3 stream as a portrait host shows it. */
    private const val PORTRAIT_4_3_WIDTH = 3
    private const val PORTRAIT_4_3_HEIGHT = 4

    /**
     * The crop shape as (width, height), in the viewfinder's coordinate space.
     *
     * @param sixteenNine the selection asks for a 16:9 stream rather than a 4:3 one.
     * @param cropsToScreen the selection is the screen-filling one, whose shape is the screen's.
     * @param screenWidth screen width in pixels, only read when [cropsToScreen].
     * @param screenHeight screen height in pixels, only read when [cropsToScreen].
     */
    fun rationalFor(
        sixteenNine: Boolean,
        cropsToScreen: Boolean,
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Int, Int> {
        if (cropsToScreen && screenWidth > 0 && screenHeight > 0) {
            // Normalised to portrait, because that is the orientation this host is locked to and the
            // one the viewfinder is drawn in. An unnormalised pair would state the transpose whenever
            // the caller happened to read the screen the other way round.
            return minOf(screenWidth, screenHeight) to maxOf(screenWidth, screenHeight)
        }
        return if (sixteenNine) {
            PORTRAIT_16_9_WIDTH to PORTRAIT_16_9_HEIGHT
        } else {
            PORTRAIT_4_3_WIDTH to PORTRAIT_4_3_HEIGHT
        }
    }
}
