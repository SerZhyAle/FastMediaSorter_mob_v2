package com.sza.fastmediasorter.core.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Surface
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.function.IntConsumer

/**
 * S1986: lets a host-side sweep drive the camera screen - its rotation bucket and which mode it opens
 * in - because neither can be reached from a computer on a retail phone.
 *
 * Rotation: the camera host is portrait-locked, so it takes its capture rotation from the
 * accelerometer through `CameraOrientationManager`. Sensor injection is refused on retail firmware
 * (`dumpsys sensorservice data_injection` answers INVALID_OPERATION) and `settings put system
 * user_rotation` leaves a portrait-locked activity at ROTATION_0. Without this receiver the capture
 * pipeline below the bucket - targetRotation, EXIF, the aspect crop, the soft-zoom crop - can only be
 * observed in whichever pose the phone is physically lying in, and a four-pose matrix would need a
 * pair of hands for every cell.
 *
 * Opening the camera in a named mode is the sibling problem, solved by [CameraTestOpenReceiver] -
 * a manifest entry, because it must outlive every screen and so has no lifecycle edge to unregister on.
 *
 * What this deliberately does NOT cover: the mapping from accelerometer angle to bucket. That is a
 * pure function proven on the JVM (`CameraRotationBucketTest`), so overriding its output costs no
 * coverage.
 *
 * Debug builds only - this file lives in `src/debug`, so a release build has no such class and
 * [CameraTestHooksBridge] turns into a no-op.
 *
 * Usage:
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.CAMERA_TEST_ROTATION --ei rotation 1 \
 *     -p com.sza.fastmediasorter.debug
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.CAMERA_TEST_OPEN --es mode VIDEO \
 *     -p com.sza.fastmediasorter.debug
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.CAMERA_TEST_LENS_PINNING --ez disabled true \
 *     -p com.sza.fastmediasorter.debug
 */
object CameraTestHooks {

    const val ACTION_ROTATION = "com.sza.fastmediasorter.debug.CAMERA_TEST_ROTATION"

    /**
     * S1988: turns the physical sub-lens pin off, so the same scene can be measured with and without it.
     *
     * Strategic §2.4 has two surviving causes that every existing measurement fits equally well, and the
     * only thing that separates them is repeating a cell without `setPhysicalCameraId`. Pinning is
     * unconditional on every shipped path, so without this switch that comparison cannot be made at all.
     */
    const val ACTION_LENS_PINNING = "com.sza.fastmediasorter.debug.CAMERA_TEST_LENS_PINNING"

    /**
     * Result code a receiver answers with, so the caller can tell "applied" from "nobody listened".
     *
     * `am broadcast` prints "Broadcast completed: result=0" whether a receiver ran or not - measured
     * against a build carrying no such receiver - so without a distinctive code a sweep would happily
     * label four identical cells as four different device poses.
     */
    const val ACK_APPLIED = 1986

    private const val EXTRA_ROTATION = "rotation"
    private const val NO_ROTATION = -1
    private const val EXTRA_DISABLED = "disabled"

    // Read on the CameraX binding path and written on the main thread by the receiver below, so the
    // two threads must not disagree about which lens the next bind was told to use.
    @Volatile
    private var pinningDisabled = false

    private val VALID_ROTATIONS = setOf(
        Surface.ROTATION_0,
        Surface.ROTATION_90,
        Surface.ROTATION_180,
        Surface.ROTATION_270,
    )

    @JvmStatic
    fun installRotationOverride(context: Context, apply: IntConsumer): Any {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val rotation = intent?.getIntExtra(EXTRA_ROTATION, NO_ROTATION) ?: NO_ROTATION
                if (rotation !in VALID_ROTATIONS) {
                    // Named rather than ignored: a sweep that mistypes the extra must see why nothing
                    // moved, instead of reading the previous pose's photo as the new pose's answer.
                    Timber.w("CameraTestHooks: ignoring rotation override '$rotation' - expected 0..3")
                    return
                }
                Timber.i("CameraTestHooks: forcing camera rotation bucket to $rotation")
                apply.accept(rotation)
                acknowledge()
            }
        }
        return register(context, IntentFilter(ACTION_ROTATION), receiver)
    }

    /** S1988: false in every build that has no such broadcast, which is the shipped state. */
    @JvmStatic
    fun isPinningDisabled(): Boolean = pinningDisabled

    /**
     * S1988: installs the pinning switch and rebinds through [onChanged] as soon as it flips.
     *
     * The rebind is not optional. `applyPhysicalCameraId` is consulted while the use cases are being
     * built, so a flag changed after the bind describes a session that no longer exists, and the sweep
     * would photograph the previous state while believing it photographed the new one.
     */
    @JvmStatic
    fun installLensPinningOverride(context: Context, onChanged: Runnable): Any {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                pinningDisabled = intent?.getBooleanExtra(EXTRA_DISABLED, false) ?: false
                Timber.i("CameraTestHooks: physical lens pinning disabled=$pinningDisabled")
                onChanged.run()
                acknowledge()
            }
        }
        return register(context, IntentFilter(ACTION_LENS_PINNING), receiver)
    }

    @JvmStatic
    fun remove(context: Context, token: Any) {
        if (token !is BroadcastReceiver) return
        runCatching { context.unregisterReceiver(token) }
            .onFailure { Timber.w(it, "CameraTestHooks: receiver was not registered") }
    }

    private fun BroadcastReceiver.acknowledge() {
        // Ordered because `am broadcast` sends it that way; guarded so an unordered sender elsewhere
        // cannot crash the camera screen.
        if (isOrderedBroadcast) {
            resultCode = ACK_APPLIED
        }
    }

    private fun register(context: Context, filter: IntentFilter, receiver: BroadcastReceiver): Any {
        // One call site, not a per-API-level branch around two: the exported flag is a value here, and
        // two registration calls against one unregistration read as a leaked receiver to any counter -
        // including the repo's listener-symmetry gate, which is right to count them that way.
        // Exported on purpose: the sender is `adb shell am broadcast`, which is another uid.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return receiver
    }
}
