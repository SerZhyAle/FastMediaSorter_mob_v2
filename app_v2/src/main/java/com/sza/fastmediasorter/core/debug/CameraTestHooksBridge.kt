package com.sza.fastmediasorter.core.debug

import android.content.Context
import timber.log.Timber
import java.util.function.IntConsumer

/**
 * S1986: reaches the debug-only camera test hook the same way [DebugToolsBridge] reaches the debug
 * tools - by name, so the release build simply has no such class and this becomes a no-op.
 *
 * The hook exists because a host cannot turn a connected phone: sensor injection is closed on retail
 * firmware and `user_rotation` never reaches a portrait-locked activity, so without it the capture
 * pipeline could only be measured in the pose the phone happened to be lying in.
 */
object CameraTestHooksBridge {

    private const val HOOKS_CLASS = "com.sza.fastmediasorter.core.debug.CameraTestHooks"

    /**
     * Registers the rotation-override receiver, returning an opaque token for [remove], or null when
     * the class is absent (every non-debug build) or registration failed.
     */
    fun installRotationOverride(context: Context, apply: IntConsumer): Any? = runCatching {
        val clazz = Class.forName(HOOKS_CLASS)
        val method = clazz.getMethod("installRotationOverride", Context::class.java, IntConsumer::class.java)
        method.invoke(null, context, apply)
    }.getOrElse { error ->
        if (error !is ClassNotFoundException) {
            Timber.w(error, "CameraTestHooksBridge: rotation override unavailable")
        }
        null
    }

    /**
     * S1988: registers the physical-lens pinning switch, returning a token for [remove], or null when
     * the class is absent (every non-debug build) or registration failed.
     */
    fun installLensPinningOverride(context: Context, onChanged: Runnable): Any? = runCatching {
        val clazz = Class.forName(HOOKS_CLASS)
        val method = clazz.getMethod("installLensPinningOverride", Context::class.java, Runnable::class.java)
        method.invoke(null, context, onChanged)
    }.getOrElse { error ->
        if (error !is ClassNotFoundException) {
            Timber.w(error, "CameraTestHooksBridge: lens pinning override unavailable")
        }
        null
    }

    /**
     * S1988: whether the sweep has asked for the physical sub-lens pin to be skipped.
     *
     * False on every failure, including the absent class: the shipped behaviour is to pin, so anything
     * this cannot answer must read as "pin", never as "the experiment is running".
     */
    fun isPhysicalLensPinningDisabled(): Boolean = runCatching {
        Class.forName(HOOKS_CLASS).getMethod("isPinningDisabled").invoke(null) as? Boolean
    }.getOrElse { error ->
        if (error !is ClassNotFoundException) {
            Timber.w(error, "CameraTestHooksBridge: lens pinning state unreadable")
        }
        null
    } ?: false

    /** Unregisters what [installRotationOverride] returned; a null token is nothing to undo. */
    fun remove(context: Context, token: Any?) {
        if (token == null) return
        runCatching {
            val clazz = Class.forName(HOOKS_CLASS)
            clazz.getMethod("remove", Context::class.java, Any::class.java).invoke(null, context, token)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                Timber.w(error, "CameraTestHooksBridge: could not remove the rotation override")
            }
        }
    }
}
