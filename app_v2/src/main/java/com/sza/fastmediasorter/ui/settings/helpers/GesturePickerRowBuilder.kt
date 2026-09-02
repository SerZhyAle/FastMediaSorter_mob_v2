package com.sza.fastmediasorter.ui.settings.helpers

/**
 * S2256: one action a host offers in the grouped picker - its own key plus the catalog metadata that
 * decides where the row lands and how it reads.
 */
data class GesturePickerItem<out T : Any>(
    val key: T,
    val meta: GestureActionMeta,
    val enabled: Boolean = true,
)

/**
 * S2256: assembles the grouped picker's rows for any host.
 *
 * Both assignment surfaces - the edge-gesture slots and the launcher desktop swipes - feed their own
 * available-action list through here, so grouping, section order and within-group order are decided in
 * one place instead of once per host. A host decides only which actions it offers; it never decides how
 * they are arranged.
 */
class GesturePickerRowBuilder {

    /**
     * Emits one [GesturePickerRow.Header] per non-empty [GestureActionGroup] in declaration order,
     * followed by that group's items in the order [items] supplied them. Empty groups are skipped.
     *
     * [launcherRoutes] are the host's own launcher-local actions, which the launcher settings surface
     * carries as local values while the edge surface carries its one as a shared enum constant. They lead
     * their group in the order given, and a key already present in [items] is dropped so a route renders
     * exactly once no matter which way the host supplied it.
     *
     * S2301: a list rather than one nullable item - the launcher gained screen paging, which is local for
     * the same reason All apps is, and a single slot had room for neither of the two.
     */
    fun <T : Any> build(
        items: List<GesturePickerItem<T>>,
        launcherRoutes: List<GesturePickerItem<T>> = emptyList(),
    ): List<GesturePickerRow<T>> {
        val ordered = launcherRoutes + items
        val unique = ordered.distinctBy { it.key }
        return GestureActionGroup.entries.flatMap { group ->
            val inGroup = unique.filter { it.meta.group == group }
            if (inGroup.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    add(GesturePickerRow.Header(group.titleRes))
                    inGroup.forEach { add(it.toRow()) }
                }
            }
        }
    }

    private fun <T : Any> GesturePickerItem<T>.toRow(): GesturePickerRow.Entry<T> =
        GesturePickerRow.Entry(
            actionKey = key,
            labelRes = meta.labelRes,
            explanationRes = meta.explanationRes,
            iconRes = meta.iconRes,
            enabled = enabled,
        )
}
