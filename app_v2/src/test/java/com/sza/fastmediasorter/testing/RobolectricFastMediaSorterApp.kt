package com.sza.fastmediasorter.testing

import com.sza.fastmediasorter.FastMediaSorterApp
import org.robolectric.Shadows

/**
 * The application every Robolectric test in `app_v2` runs against, wired once in
 * `src/test/resources/robolectric.properties` rather than per class.
 *
 * S2750: on API 26..32 `ContextCompat.registerReceiver(.., RECEIVER_NOT_EXPORTED)` has no platform
 * flag to pass, so androidx.core emulates a non-exported receiver by registering it behind the
 * signature permission `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and refuses to
 * proceed unless the app already holds it. The manifest declares and uses it, but Robolectric grants
 * no manifest permission on its own, so every test pinned below SDK 33 died in
 * `FastMediaSorterApp.onCreate` before its first assertion. Granting it here keeps the real startup
 * graph and the real receiver registration under test - the alternatives were to skip the watcher in
 * tests or to change how the app registers on device, and both hide what they claim to check.
 */
class RobolectricFastMediaSorterApp : FastMediaSorterApp() {

    override fun onCreate() {
        Shadows.shadowOf(this).grantPermissions(packageName + DYNAMIC_RECEIVER_PERMISSION_SUFFIX)
        super.onCreate()
    }

    private companion object {
        const val DYNAMIC_RECEIVER_PERMISSION_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    }
}
