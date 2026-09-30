package com.sza.fastmediasorter

import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.testing.HiltTestApplication

/**
 * The Hilt pass, selected with `-Pfms.hiltTestRunner=true`. It swaps in [HiltTestApplication] so
 * `@TestInstallIn` / `@UninstallModules` replacements take effect, and runs only
 * `@HiltAndroidTest` classes, because every other test needs the production application.
 */
class FmsHiltTestRunner : FmsAndroidTestRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun applyAnnotationFilter(args: Bundle) {
        args.remove(ARG_NOT_ANNOTATION)
        args.putString(ARG_ANNOTATION, HILT_TEST_ANNOTATION)
    }
}
