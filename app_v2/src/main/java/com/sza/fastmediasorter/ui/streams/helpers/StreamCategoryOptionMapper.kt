package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option

/** Presents stable catalog category ids through localized labels without changing filter equality. */
object StreamCategoryOptionMapper {

    fun categoryOptions(context: Context, categories: List<String>): List<Option> =
        categories.map { category -> Option(id = category, label = label(context, category) ?: category) }

    fun label(context: Context, category: String?): String? =
        category?.let { labelRes(it)?.let(context::getString) ?: it }

    /**
     * Context-free so a mapping that holds no Android context can still name the resource; null for an id the
     * catalog does not carry, such as a category an owner typed.
     */
    @StringRes
    fun labelRes(category: String): Int? = when (category) {
        "Radio" -> R.string.streams_category_radio
        "Live TV" -> R.string.streams_category_live_tv
        "On-demand video" -> R.string.streams_category_on_demand_video
        "Test streams" -> R.string.streams_category_test_streams
        else -> null
    }
}
