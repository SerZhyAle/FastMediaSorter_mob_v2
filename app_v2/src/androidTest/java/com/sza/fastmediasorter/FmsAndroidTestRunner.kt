package com.sza.fastmediasorter

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Default device-test runner. It runs under the real `@HiltAndroidApp` application, where a
 * `@HiltAndroidTest` class cannot start at all, so those classes are filtered out here and run in
 * their own pass under [FmsHiltTestRunner]. An explicit annotation filter from the caller wins.
 */
open class FmsAndroidTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        val args = arguments ?: Bundle()
        applyAnnotationFilter(args)
        super.onCreate(args)
    }

    protected open fun applyAnnotationFilter(args: Bundle) {
        if (args.getString(ARG_ANNOTATION) == null && args.getString(ARG_NOT_ANNOTATION) == null) {
            args.putString(ARG_NOT_ANNOTATION, HILT_TEST_ANNOTATION)
        }
    }

    protected companion object {
        const val ARG_ANNOTATION = "annotation"
        const val ARG_NOT_ANNOTATION = "notAnnotation"
        const val HILT_TEST_ANNOTATION = "dagger.hilt.android.testing.HiltAndroidTest"
    }
}
