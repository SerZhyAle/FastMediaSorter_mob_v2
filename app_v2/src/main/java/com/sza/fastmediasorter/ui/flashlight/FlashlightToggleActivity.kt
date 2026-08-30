package com.sza.fastmediasorter.ui.flashlight

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.ui.flashlight.helpers.FlashlightToggleManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/** Internal trampoline for launcher surfaces that toggle the physical camera flash. */
@AndroidEntryPoint
class FlashlightToggleActivity : AppCompatActivity() {

    @Inject lateinit var toggleManager: FlashlightToggleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("S2246: launcher physical flashlight toggle accepted=%s", toggleManager.toggle())
        finish()
    }
}
