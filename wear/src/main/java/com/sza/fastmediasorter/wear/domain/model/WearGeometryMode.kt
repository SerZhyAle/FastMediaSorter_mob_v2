package com.sza.fastmediasorter.wear.domain.model

/**
 * S2773: which of the module's two screen geometries the watch is laying content out with.
 *
 * The module keeps every number about screen shape in one layer, so a view is a single value read
 * inside those functions rather than a variant of each screen. A screen never receives this value and
 * never branches on it; it asks the shape layer how much room there is and gets the answer belonging
 * to the view in force.
 *
 * A value added here has to declare its own answer in every shape function that branches on this type.
 * A function that does not branch inherits [STORE] silently, which is a view that disagrees with the
 * rest of the screen it is drawn on.
 */
enum class WearGeometryMode {

    /**
     * The geometry Google Play reviewed: content stands clear of the arc on every screen, and a full
     * width band near an edge is narrowed or lowered until the chord admits it (S2273, S2770).
     */
    STORE,

    /**
     * The geometry that stood before that review: content is placed against the full width of the
     * display, and the round glass cuts the outer edges of what reaches it. That clipping is the point
     * of this view, not a defect in it - the owner asked for the layout he built rather than the one
     * the review bought.
     */
    ORIGINAL
}
