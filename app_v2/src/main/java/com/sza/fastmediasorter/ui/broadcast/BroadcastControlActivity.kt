package com.sza.fastmediasorter.ui.broadcast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.ui.broadcast.helpers.BroadcastCastManager
import com.sza.fastmediasorter.ui.broadcast.helpers.BroadcastControlManager
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.common.help.HelpOverlayEntryManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Turns by the player's rotation settings (S4418), applied by `BroadcastLandscapeVideoManager`. */
@AndroidEntryPoint
class BroadcastControlActivity : AppCompatActivity(), SelfManagedScreenOrientation {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject
    lateinit var controlManager: BroadcastControlManager

    @Inject
    lateinit var castManager: BroadcastCastManager

    private lateinit var binding: ActivityBroadcastControlBinding
    private var helpEntry: HelpOverlayEntryManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBroadcastControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        controlManager.setup(this, binding)
        castManager.setup(this, binding.btnCastToDevices)
        helpEntry = HelpOverlayEntryManager(
            this, binding.root.parent as ViewGroup, javaClass.simpleName, null,
            reserveSpaceFor = binding.tvControlTitle,
        )
    }

    override fun onResume() {
        super.onResume()
        // Keep screen on while interacting on wake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Not a BaseActivity, so F1 reaches the help page here instead of through the shared key handling.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F1 &&
            HelpOpenManager.openIfPresent(this, BroadcastControlActivity::class.java.simpleName, null)
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        helpEntry?.release()
        helpEntry = null
        controlManager.onDetach()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, BroadcastControlActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
