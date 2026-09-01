package com.sza.fastmediasorter.util

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber

/**
 * Show this builder's dialog and close it automatically when [owner] reaches `ON_DESTROY`.
 *
 * A bare `show()` throws the returned [AlertDialog] away, so nothing can close it later: the dialog
 * window then outlives a configuration change and keeps the destroyed Fragment and Activity alive
 * (S1447). S1197 solved the same leak by hand - a held field plus a dismiss call from the host - which
 * takes three coordinated edits per dialog and was never applied beyond the one helper it was written
 * for. Binding inside the show call needs none of them.
 *
 * Declared on [AlertDialog.Builder] rather than on `MaterialAlertDialogBuilder` so the two builder
 * types in use share one binding; the Material builder is a subclass of this one.
 *
 * @return the shown dialog, or `null` when [owner] is already destroyed and showing would leak at once.
 */
fun AlertDialog.Builder.showBoundTo(owner: LifecycleOwner): AlertDialog? {
    val lifecycle = owner.lifecycle
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    return show().bindTo(owner)
}

/**
 * [showBoundTo] bound to [fragment]'s view lifecycle, which dies on every configuration change and is
 * therefore the owner a dialog raised from a view must follow. Falls back to the fragment's own
 * lifecycle while no view exists, so a dialog raised outside the view window is still bound.
 */
fun AlertDialog.Builder.showBoundTo(fragment: Fragment): AlertDialog? =
    showBoundTo(fragment.viewLifecycleOwnerLiveData.value ?: fragment)

/**
 * Show this already-created dialog and close it automatically when [owner] reaches `ON_DESTROY`.
 *
 * The builder overloads cover a fluent chain. A site that needs the dialog before it is shown - to
 * attach a keyboard delegate, to disable a button, to reach a bound view - calls `create()` instead
 * and shows the result on its own line, which no extension on the builder can reach (S1456).
 *
 * @return the shown dialog, or `null` when [owner] is already destroyed and showing would leak at once.
 */
fun AlertDialog.showBoundTo(owner: LifecycleOwner): AlertDialog? {
    if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    show()
    return bindTo(owner)
}

/**
 * [showBoundTo] for a created dialog raised from a fragment view, following the same view-lifecycle
 * preference as the builder overload above.
 */
fun AlertDialog.showBoundTo(fragment: Fragment): AlertDialog? =
    showBoundTo(fragment.viewLifecycleOwnerLiveData.value ?: fragment)

/**
 * [showBoundTo] with the owner resolved from [context] instead of passed in.
 *
 * Many helpers hold a plain [Context] or an `android.app.Activity`, and neither is a [LifecycleOwner],
 * so they cannot name an owner without a constructor change. Unwrapping the [ContextWrapper] chain
 * finds the hosting activity, which a dialog context already has to be - a dialog raised on an
 * application context never gets a window token in the first place.
 *
 * Named apart from [showBoundTo] rather than added as a third overload: `Context` and `LifecycleOwner`
 * are unrelated types, so an activity argument would be ambiguous between them.
 */
fun AlertDialog.Builder.showBoundToHost(context: Context): AlertDialog? {
    val owner = context.findLifecycleOwner()
    if (owner == null) {
        Timber.w("showBoundToHost: no LifecycleOwner behind ${context.javaClass.simpleName}, dialog stays unbound")
        return show()
    }
    return showBoundTo(owner)
}

/**
 * [showBoundToHost] for a dialog that was already created, the shape a site takes when it configures
 * the dialog before showing it.
 */
fun AlertDialog.showBoundToHost(context: Context): AlertDialog? {
    val owner = context.findLifecycleOwner()
    if (owner == null) {
        Timber.w("showBoundToHost: no LifecycleOwner behind ${context.javaClass.simpleName}, dialog stays unbound")
        show()
        return this
    }
    return showBoundTo(owner)
}

/**
 * [showBoundToHost] for a builder of the platform `android.app.AlertDialog`, which a handful of older
 * screens still use.
 *
 * Migrating those screens to the AppCompat dialog would change how they look, which this binding
 * deliberately does not do (S1456): the leak is fixed where it is, and the dialog type stays.
 */
fun android.app.AlertDialog.Builder.showBoundToHost(context: Context): android.app.AlertDialog? {
    val owner = context.findLifecycleOwner()
    if (owner == null) {
        Timber.w("showBoundToHost: no LifecycleOwner behind ${context.javaClass.simpleName}, dialog stays unbound")
        return show()
    }
    return if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) null else show().bindTo(owner)
}

/**
 * [showBoundToHost] for an already-created platform `android.app.AlertDialog`.
 */
fun android.app.AlertDialog.showBoundToHost(context: Context): android.app.AlertDialog? {
    val owner = context.findLifecycleOwner()
    if (owner == null) {
        Timber.w("showBoundToHost: no LifecycleOwner behind ${context.javaClass.simpleName}, dialog stays unbound")
        show()
        return this
    }
    return if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
        null
    } else {
        show()
        bindTo(owner)
    }
}

/**
 * S2276: [showBoundTo] for a generic created [Dialog] (such as [com.sza.fastmediasorter.ui.settings.helpers.GesturePickerDialog]
 * or [com.sza.fastmediasorter.ui.dialog.ListSelectionDialog]).
 */
fun <T : Dialog> T.showBoundTo(owner: LifecycleOwner): T? {
    if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    show()
    return bindTo(owner)
}

/**
 * S2276: [showBoundTo] for a generic created [Dialog] raised from a fragment view.
 */
fun <T : Dialog> T.showBoundTo(fragment: Fragment): T? =
    showBoundTo(fragment.viewLifecycleOwnerLiveData.value ?: fragment)

/**
 * S2276: [showBoundToHost] for a generic created [Dialog].
 */
fun <T : Dialog> T.showBoundToHost(context: Context): T? {
    val owner = context.findLifecycleOwner()
    if (owner == null) {
        Timber.w("showBoundToHost: no LifecycleOwner behind ${context.javaClass.simpleName}, dialog stays unbound")
        show()
        return this
    }
    return showBoundTo(owner)
}

/**
 * First [LifecycleOwner] in this context's wrapper chain, or `null` when the chain reaches the
 * application context without passing one.
 */
private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

/**
 * Register the `ON_DESTROY` dismissal shared by every `showBoundTo` receiver.
 *
 * Generic over [Dialog] rather than written twice: the AppCompat dialog and the platform one share
 * no supertype below it, and the observer needs nothing either adds.
 */
internal fun <T : Dialog> T.bindTo(owner: LifecycleOwner): T {
    val lifecycle = owner.lifecycle
    // Deregistration happens in onDestroy rather than from an OnDismissListener: a listener set here
    // would silently overwrite the caller's own, which several settings helpers rely on to revert
    // their row when the dialog is cancelled. A dismissed dialog is therefore held until the owner
    // dies - one dead object on a lifecycle that is about to be collected anyway.
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isShowing) dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
    )
    return this
}
