package com.sza.fastmediasorter.core.debug

import android.app.Application
import android.content.Context
import android.content.Intent
import timber.log.Timber

object DebugToolsBridge {

    private const val BOOTSTRAP_CLASS = "com.sza.fastmediasorter.core.debug.DebugToolsBootstrap"

    fun install(application: Application) {
        invokeStatic("install", arrayOf(Application::class.java), arrayOf(application))
    }

    fun onCoroutineException(throwable: Throwable) {
        invokeStatic("onCoroutineException", arrayOf(Throwable::class.java), arrayOf(throwable))
    }

    fun maybeCreateDebugMenuIntent(context: Context): Intent? {
        return try {
            val clazz = Class.forName(BOOTSTRAP_CLASS)
            val method = clazz.getMethod("createDebugMenuIntent", Context::class.java)
            method.invoke(null, context) as? Intent
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeStatic(methodName: String, parameterTypes: Array<Class<*>>, args: Array<Any>) {
        try {
            val clazz = Class.forName(BOOTSTRAP_CLASS)
            val method = clazz.getMethod(methodName, *parameterTypes)
            method.invoke(null, *args)
        } catch (_: ClassNotFoundException) {
            // No-op for non-debug builds
        } catch (t: Throwable) {
            Timber.w(t, "DebugToolsBridge failed: $methodName")
        }
    }
}