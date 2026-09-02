package com.sza.fastmediasorter.util

import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.di.ApplicationScopeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Run [block] on the lifecycle of the activity hosting this context, so a destroyed host cancels it.
 *
 * S2358: a dialog is not a [androidx.lifecycle.LifecycleOwner], so work started from one used to go
 * into a scope created at the launch site. Such a scope has no owner and is never cancelled - the host
 * dies, the work carries on, and its continuation reaches for a context whose window is gone. The host
 * is resolved through the same [findLifecycleOwner] the dialog binding uses, so both answer "who owns
 * this" identically.
 *
 * With no host behind the context, [block] goes to the application scope rather than to a scope made
 * here: an unowned scope is the defect this exists to remove, and refusing to launch would silently
 * drop an action the user asked for. Nothing is outlived on that branch - it is reached only when the
 * context is the application itself.
 *
 * Dispatch differs from the pattern this replaces. A lifecycle scope is `Dispatchers.Main`, so [block]
 * starts on the main thread and blocking work inside it needs an explicit `withContext(Dispatchers.IO)`.
 *
 * Use this for work whose result is delivered to the host - an intent, a toast, a view update. Work the
 * user expects to finish regardless, such as a file write they requested, belongs in [applicationScope].
 */
fun Context.launchBoundToHost(block: suspend CoroutineScope.() -> Unit): Job {
    val owner = findLifecycleOwner()
    if (owner == null) {
        Timber.w("launchBoundToHost: no LifecycleOwner behind ${javaClass.simpleName}, using app scope")
        return applicationScope().launch(block = block)
    }
    return owner.lifecycleScope.launch(block = block)
}

/**
 * The process-lifetime scope, reached from outside the Hilt graph.
 *
 * S2358: for work that must complete even though the screen that asked for it is gone. Dispatch is IO,
 * matching the provider in `AppModule`, so a main-thread step inside needs `withContext`.
 */
internal fun Context.applicationScope(): CoroutineScope =
    EntryPointAccessors
        .fromApplication(applicationContext, ApplicationScopeEntryPoint::class.java)
        .applicationScope()
