package com.sza.fastmediasorter.ui.flashlight

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.flashlight.helpers.FlashlightToggleManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Internal trampoline for launcher surfaces that toggle the physical camera flash. */
@AndroidEntryPoint
class FlashlightToggleActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var toggleManager: FlashlightToggleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accepted = toggleManager.toggle()
        if (!accepted) {
            Toast.makeText(this, R.string.physical_flashlight_unavailable, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
