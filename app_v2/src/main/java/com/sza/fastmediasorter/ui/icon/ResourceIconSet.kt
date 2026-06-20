package com.sza.fastmediasorter.ui.icon

/** Themed icon set corresponding to the five designer groups delivered in S0034. */
enum class ResourceIconSet(val setId: Int, val countInSet: Int) {
    MUSIC(1, 20),
    VIDEO(2, 20),
    IMAGE(3, 20),
    DOCS(4, 20),
    OTHER(5, 20);

    companion object {
        fun fromSetId(setId: Int): ResourceIconSet? =
            values().firstOrNull { it.setId == setId }
    }
}
