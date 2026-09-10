package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode

/**
 * S2773: the screen geometry in force, published to the whole screen tree.
 *
 * Read inside the shape helpers of [WearScreenScaffold] and inside the player command-row layer
 * ([com.sza.fastmediasorter.wear.ui.player.common], the second reader since S2803) and nowhere else.
 * A screen never reads this and never takes it as a parameter: it asks the shape layer how much room
 * there is, exactly as it did before this value existed, and the layer answers for the view in
 * force. That is what let the Play shape fix reach every screen as one change, and it is the
 * property worth keeping.
 *
 * The default is [WearGeometryMode.STORE] rather than the owner's preferred view on purpose: a subtree
 * composed outside the provider - a preview, a test host, a surface added later and not yet wired -
 * gets the geometry that passed review, never the one that did not.
 */
val LocalWearGeometryMode = compositionLocalOf { WearGeometryMode.STORE }
