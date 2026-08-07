package com.sza.fastmediasorter.ui.streams.helpers

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1477: injectable wrapper over [StreamTopicRubricCatalog] so the streams ViewModel can order rows by
 * the rubric label the user actually reads without holding an Activity. Resolution stays in the object;
 * this only supplies the application [Context].
 */
@Singleton
class StreamTopicLabelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun label(rubric: String?): String? = StreamTopicRubricCatalog.label(context, rubric)
}
