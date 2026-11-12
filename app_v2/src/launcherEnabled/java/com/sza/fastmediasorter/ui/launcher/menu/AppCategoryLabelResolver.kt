package com.sza.fastmediasorter.ui.launcher.menu

import android.content.res.Resources
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.AppCategory

/**
 * S4530: the label a category is drawn with.
 *
 * A seeded category stores no name, so its label comes from resources on every draw and follows a
 * language change; a category the user named keeps his name.
 */
class AppCategoryLabelResolver(private val resources: Resources) {

    fun labelOf(category: AppCategory): String {
        val name = category.name?.takeIf { it.isNotBlank() }
        return name ?: resources.getString(systemLabelOf(category.systemCategory))
    }

    fun unknownLabel(): String = resources.getString(R.string.launcher_app_category_unknown)

    private fun systemLabelOf(systemCategory: Int?): Int = when (systemCategory) {
        CATEGORY_GAME -> R.string.launcher_app_category_game
        CATEGORY_AUDIO -> R.string.launcher_app_category_audio
        CATEGORY_VIDEO -> R.string.launcher_app_category_video
        CATEGORY_IMAGE -> R.string.launcher_app_category_image
        CATEGORY_SOCIAL -> R.string.launcher_app_category_social
        CATEGORY_NEWS -> R.string.launcher_app_category_news
        CATEGORY_MAPS -> R.string.launcher_app_category_maps
        CATEGORY_PRODUCTIVITY -> R.string.launcher_app_category_productivity
        CATEGORY_ACCESSIBILITY -> R.string.launcher_app_category_accessibility
        else -> R.string.launcher_app_category_unknown
    }

    private companion object {
        // Mirror ApplicationInfo.CATEGORY_*, which the catalog stores as plain integers; CATEGORY_ACCESSIBILITY
        // is API 31 and would otherwise need a version gate on a value that is only compared.
        const val CATEGORY_GAME = 0
        const val CATEGORY_AUDIO = 1
        const val CATEGORY_VIDEO = 2
        const val CATEGORY_IMAGE = 3
        const val CATEGORY_SOCIAL = 4
        const val CATEGORY_NEWS = 5
        const val CATEGORY_MAPS = 6
        const val CATEGORY_PRODUCTIVITY = 7
        const val CATEGORY_ACCESSIBILITY = 8
    }
}
