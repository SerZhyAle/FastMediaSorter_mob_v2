package com.sza.fastmediasorter.ui.player.entry

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.ui.main.MainActivity
import timber.log.Timber

/**
 * Task-swap contract for the Meta Hybrid App Model on HorizonOS.
 *
 * WHY this exists: HorizonOS only transitions an OpenXR session to
 * XR_SESSION_STATE_FOCUSED when the immersive Activity (a) declares
 * `com.oculus.intent.category.VR` and (b) runs in a task that does NOT
 * contain any `com.oculus.intent.category.2D` panel Activity. The VR
 * flavor's AndroidManifest gives [com.sza.fastmediasorter.vr.VrPlayerActivity]
 * a dedicated taskAffinity; this helper performs the matching runtime
 * handoff: FLAG_ACTIVITY_NEW_TASK + finishAndRemoveTask() on entry, and
 * a HorizonOS home-intent + PendingIntent on exit so the user lands on
 * a fresh MainActivity panel instead of a dead task.
 *
 * The helper is flavor-safe: [shouldEnterImmersiveTask] always returns
 * false when [BuildConfig.SUPPORT_VR_PLAYER] is false, so non-VR flavors
 * compile and behave identically to the pre-hybrid world.
 */
object VrTaskTransition {

    /** Key used by HorizonOS vrshell to relaunch the panel task from the home intent. */
    private const val EXTRA_LAUNCH_IN_HOME_PENDING_INTENT = "extra_launch_in_home_pending_intent"

    /**
     * True only for intents whose resolved target component is the VR immersive player.
     *
     * Explicit [com.sza.fastmediasorter.ui.player.PlayerActivity] intents (standard-player
     * override used by `BrowseEventHandler.createStandardPlayerIntent`) return false even
     * on the VR flavor — they must remain panel launches without the task swap.
     */
    fun shouldEnterImmersiveTask(intent: Intent): Boolean {
        if (!BuildConfig.SUPPORT_VR_PLAYER) return false
        val targetClassName = intent.component?.className ?: return false
        return targetClassName == BuildConfig.PLAYER_ACTIVITY_CLASS
    }

    /**
     * Launch VrPlayerActivity in its own task; remove the caller's panel task.
     *
     * ACTION_MAIN + FLAG_ACTIVITY_NEW_TASK is required by the hybrid-app sample so that
     * the VR Activity's declared taskAffinity creates a fresh task rather than stacking
     * onto the caller. finishAndRemoveTask() tears down the panel task so the compositor
     * no longer has a 2D window competing with the VR task for foreground — without this
     * the XR session stalls at VISIBLE and never reaches FOCUSED.
     */
    fun enterImmersive(source: Activity, vrIntent: Intent) {
        vrIntent.action = Intent.ACTION_MAIN
        vrIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Timber.i(
            "VrTaskTransition.enterImmersive: source=%s target=%s",
            source.javaClass.simpleName,
            vrIntent.component?.className,
        )
        source.startActivity(vrIntent)
        source.finishAndRemoveTask()
    }

    /**
     * Return from the VR task to a fresh MainActivity panel via HorizonOS home-intent.
     *
     * FLAG_IMMUTABLE is mandatory on API 31+ for PendingIntents that cross into another
     * process (vrshell fires the PendingIntent), and has been supported since API 23,
     * so it is always on for this app (minSdk 26 for the VR flavor).
     *
     * Use [exitImmersiveToFlatPlayer] for user-driven «exit to playback panel» — that path
     * carries playback context (file/position/resource) so the user is not stranded on
     * the file browser. This `exitImmersiveToPanel` overload is for recovery fallbacks
     * (XR init failure, file-ops dialog requesting browser navigation) where landing
     * on the browser root is the intended behaviour.
     */
    fun exitImmersiveToPanel(source: Activity, resumePlayerIntent: Intent? = null) {
        val ctx: Context = source.applicationContext
        val panelIntent = resumePlayerIntent ?: Intent(ctx, MainActivity::class.java).apply {
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
     * S0019: explicit user-driven «exit to flat playback panel» path. Carries playback
     * context (resource/file/position) so the user lands on the same file in the 2D
     * player rather than on the file-browser root.
     *
     * This is a thin wrapper over [exitImmersiveToPanel] that requires a non-null
     * `playerIntent`; the named function makes it impossible to accidentally fall back
     * to MainActivity for the user-driven exit (which was the symptom of the original
     * S0019 bug).
     */
    fun exitImmersiveToFlatPlayer(source: Activity, playerIntent: Intent) {
        val targetClass = playerIntent.component?.className?.substringAfterLast('.')
        val filePath = playerIntent.getStringExtra("extra_initial_file_path")
            ?: playerIntent.getStringExtra("initialFilePath")
            ?: "<unknown>"
        Timber.i(
            "VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent target=%s file=%s",
            targetClass,
            filePath,
        )
        exitImmersiveToPanel(source, playerIntent)
    }
}
