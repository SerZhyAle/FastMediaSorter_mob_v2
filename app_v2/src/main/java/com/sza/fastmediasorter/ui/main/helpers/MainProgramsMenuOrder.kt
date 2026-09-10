package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramEntry

/**
 * The join between a menu item's `order` and the sub-program registry entry behind it.
 *
 * S2673 is why this is a shared object rather than arithmetic at each call site: a menu order carries an
 * Android category in its high 16 bits, so a negative value makes `MenuBuilder.add` throw, and the registry's
 * own orders are therefore shifted by [MENU_ORDER_REGISTRY_BASE] before they reach the menu. Applying that
 * shift on the writing side and forgetting it on the reading side matches exactly one item out of sixteen,
 * which shows up as a menu of grey icons with one coloured entry wearing another program's tone - and nothing
 * fails. S2889 adds the panel and its overflow popup as further readers of the same join, so the arithmetic
 * moves here where one round-trip test covers every caller.
 */
object MainProgramsMenuOrder {

    /**
     * The offset separating registry orders from the four fixed items that sort around them.
     *
     * The fixed items sit below this base and broadcast sorts last, so the sequence the owner sees is the
     * one the registry declares.
     */
    const val MENU_ORDER_REGISTRY_BASE = 100

    /** The menu order to register [entry] under. */
    fun menuOrderFor(entry: SubProgramEntry): Int = MENU_ORDER_REGISTRY_BASE + entry.order

    /** The registry entry a menu item's [menuOrder] came from, or null when the item is not a sub-program. */
    fun entryForMenuOrder(menuOrder: Int): SubProgramEntry? =
        SubProgramCatalog.all().firstOrNull { menuOrderFor(it) == menuOrder }
}
