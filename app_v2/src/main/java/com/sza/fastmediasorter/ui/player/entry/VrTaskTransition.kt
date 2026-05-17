package com.sza.fastmediasorter.ui.player.entry

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.ui.main.MainActivity
import timber.log.Timber

/**
 * Exit-side task-swap contract for the Meta Hybrid App Model on HorizonOS.
 *
 * Phase 03 keeps the VR build structure alive but removes dead main-side immersive-entry
 * plumbing from `src/main`. The remaining responsibility here is only the vr-side return
 * path back into a live panel task.
 */
object VrTaskTransition {

    /** Key used by HorizonOS vrshell to relaunch the panel task from the home intent. */
    private const val EXTRA_LAUNCH_IN_HOME_PENDING_INTENT = "extra_launch_in_home_pending_intent"

    /**
     * Return from the VR task to a fresh MainActivity panel via HorizonOS home-intent.
     *
     * FLAG_IMMUTABLE is mandatory on API 31+ for PendingIntents that cross into another
     * process (vrshell fires the PendingIntent), and has been supported since API 23,
     * so it is always on for this app (minSdk 26 for the VR flavor).
     *
     * Use [exitImmersiveToFlatPlayer] for user-driven «exit to playback panel» — that path
     * carries playback context (file/position/resource) so the user is not stranded on
     * the file browser. This helper now only covers the recovery/browser-root fallback
     * path that the surviving VR runtime still uses.
     */
    fun exitImmersiveToPanel(source: Activity) {
        val ctx: Context = source.applicationContext
        val panelIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
        }
        // WHY: S0038 — FLAG_ACTIVITY_SINGLE_TOP prevents HorizonOS from creating a new window
        // when a panel VrPlayerActivity already exists at the top of its task (calls onNewIntent instead).
        // FLAG_ACTIVITY_NEW_TASK is kept: required for HorizonOS cross-process PendingIntent (vrshell fires it).
        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_LAUNCH_IN_HOME_PENDING_INTENT, pending)
        val targetClass = panelIntent.component?.className?.substringAfterLast('.')
        Timber.i("VrTaskTransition.exitImmersiveToPanel: routing via home-intent target=%s", targetClass)
        source.startActivity(home)
        source.finishAndRemoveTask()
    }

    /**
     * S0019 / S0038: user-driven «exit to flat playback panel» path. Direct startActivity,
     * NOT the home-intent recovery path.
     *
     * WHY this is direct (no PendingIntent home-intent): the recovery path was launching the
     * panel intent through HorizonOS vrshell as MAIN/LAUNCHER, which the OS treats as a fresh
     * task root and creates a new window. With FLAG_ACTIVITY_REORDER_TO_FRONT |
     * FLAG_ACTIVITY_SINGLE_TOP a pre-existing panel `PlayerActivity` is brought to the front
     * (calling onNewIntent); if no panel window exists, a single new `PlayerActivity` is
     * created — both outcomes avoid the «VrPlayerActivity → STANDARD_PANEL_FALLBACK →
     * PlayerActivity» two-hop relaunch that grew the task switcher.
     *
     * Caller MUST pass an intent that targets the standard 2D `PlayerActivity` (use
     * `PlayerActivity.createPanelIntent`), NOT the flavor-routed `PlayerActivity.createIntent`
     * (which on the VR flavor resolves to `VrPlayerActivity` and reproduces the clone bug).
     */
    fun exitImmersiveToFlatPlayer(source: Activity, playerIntent: Intent) {
        val targetClass = playerIntent.component?.className?.substringAfterLast('.')
        val filePath = playerIntent.getStringExtra("extra_initial_file_path")
            ?: playerIntent.getStringExtra("initialFilePath")
            ?: "<unknown>"
        playerIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        Timber.i(
            "VrTaskTransition.exitImmersiveToFlatPlayer: direct startActivity target=%s file=%s flags=0x%x",
            targetClass,
            filePath,
            playerIntent.flags,
        )
        Timber.d("VR_AUDIT/4: exitImmersiveToFlatPlayer target=%s file=%s flags=0x%x reuseSingleTop=true",
            targetClass, filePath, playerIntent.flags)
        Timber.d("VR_AUDIT/7: exitImmersiveToFlatPlayer flags=0x%x finishAndRemoveTask=true target=%s",
            playerIntent.flags, targetClass)
        source.startActivity(playerIntent)
        source.finishAndRemoveTask()
    }
}
