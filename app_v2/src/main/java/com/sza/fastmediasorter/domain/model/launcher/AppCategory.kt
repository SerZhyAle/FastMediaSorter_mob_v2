package com.sza.fastmediasorter.domain.model.launcher

/**
 * S4530: one group of the all-apps screen.
 *
 * A category seeded from the system keeps the publisher category integer and no name, so its label is
 * resolved at draw time and follows a locale change; a category the user created keeps his name.
 */
data class AppCategory(
    val id: String,
    val name: String?,
    val systemCategory: Int?,
)

/**
 * S4530: the user's whole category state - categories, their order and which apps sit in which.
 *
 * Kept apart from the installed-app cache, which is derived data rebuilt from the system: a rebuild must
 * never be able to erase what the user assigned (strategic ADR-1). The "Unknown" group is never stored -
 * it is whatever installed app has no assignment here.
 *
 * [order] stays empty until the user reorders once; until then the groups follow their labels'
 * alphabet. [seededPackages] records every package whose categories were already decided, so an app the
 * user cleared is not handed its system category again on the next refresh.
 */
data class AppCategoryCatalog(
    val categories: List<AppCategory>,
    val order: List<String>,
    val assignments: Map<String, Set<String>>,
    val seededPackages: Set<String>,
) {

    /** Only ids that still name a category: a stale id must not keep an app out of "Unknown". */
    fun categoryIdsOf(packageName: String): Set<String> {
        val assigned = assignments[packageName].orEmpty()
        if (assigned.isEmpty()) return emptySet()
        val known = categories.mapTo(HashSet()) { it.id }
        return assigned.filterTo(LinkedHashSet()) { it in known }
    }

    fun orderedCategories(labelOf: (AppCategory) -> String): List<AppCategory> {
        val byLabel = compareBy<AppCategory, String>(String.CASE_INSENSITIVE_ORDER) { labelOf(it) }
            .thenBy { it.id }
        if (order.isEmpty()) return categories.sortedWith(byLabel)
        val byId = categories.associateBy { it.id }
        val placed = order.mapNotNull { byId[it] }
        val placedIds = placed.mapTo(HashSet()) { it.id }
        return placed + categories.filter { it.id !in placedIds }.sortedWith(byLabel)
    }

    companion object {
        const val SYSTEM_CATEGORY_ID_PREFIX = "system:"
        const val USER_CATEGORY_ID_PREFIX = "user:"

        val EMPTY = AppCategoryCatalog(
            categories = emptyList(),
            order = emptyList(),
            assignments = emptyMap(),
            seededPackages = emptySet(),
        )
    }
}
