package com.sza.fastmediasorter.ui.applaunchpanel

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent host launched by the gesture dispatcher. It only shows [AppLaunchPanelDialogFragment]
 * over the foreground app and finishes itself once the dialog goes away (tap-outside / Back / tile
 * launch), so the translucent host never lingers as a visible task. No business logic here (Rule 3).
 */
@AndroidEntryPoint
class AppLaunchPanelActivity : AppCompatActivity() {

    private val panelLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
            if (f is AppLaunchPanelDialogFragment && !isFinishing) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.registerFragmentLifecycleCallbacks(panelLifecycleCallbacks, false)
        if (savedInstanceState == null &&
            supportFragmentManager.findFragmentByTag(AppLaunchPanelDialogFragment.TAG) == null
        ) {
            AppLaunchPanelDialogFragment().show(
                supportFragmentManager, AppLaunchPanelDialogFragment.TAG
            )
        }
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(panelLifecycleCallbacks)
        super.onDestroy()
    }
}
