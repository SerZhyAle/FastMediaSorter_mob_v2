package com.sza.fastmediasorter.wear.ui.common.testlaunch

import android.content.Intent
import javax.inject.Inject

/** S3201: the release build honours no test parameters - every launch draws the stored settings. */
class ReleaseWearTestLaunchOverrideReader @Inject constructor() : WearTestLaunchOverrideReader {

    override fun read(intent: Intent): WearTestLaunchOverride? = null
}
