package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog.Option

/** Presents stable catalog category ids through localized labels without changing filter equality. */
object StreamCategoryOptionMapper {

    fun categoryOptions(context: Context, categories: List<String>): List<Option> =
        categories.map { category -> Option(id = category, label = label(context, category) ?: category) }

    fun label(context: Context, category: String?): String? = when (category) {
        null -> null
        "Radio" -> context.getString(R.string.streams_category_radio)
        "Live TV" -> context.getString(R.string.streams_category_live_tv)
        "On-demand video" -> context.getString(R.string.streams_category_on_demand_video)
        "Test streams" -> context.getString(R.string.streams_category_test_streams)
        else -> category
    }
}
