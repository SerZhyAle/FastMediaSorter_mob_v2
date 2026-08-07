package com.sza.fastmediasorter.util

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
    val dialog = show()
    Timber.d("S1447: dialog bound to ${owner.javaClass.simpleName} (state ${lifecycle.currentState})")
    // Deregistration happens in onDestroy rather than from an OnDismissListener: a listener set here
    // would silently overwrite the caller's own, which several settings helpers rely on to revert
    // their row when the dialog is cancelled. A dismissed dialog is therefore held until the owner
    // dies - one dead object on a lifecycle that is about to be collected anyway.
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                Timber.d("S1447: owner destroyed, dialog showing=${dialog.isShowing}")
                if (dialog.isShowing) dialog.dismiss()
                owner.lifecycle.removeObserver(this)
            }
        }
    )
    return dialog
}

/**
 * [showBoundTo] bound to [fragment]'s view lifecycle, which dies on every configuration change and is
 * therefore the owner a dialog raised from a view must follow. Falls back to the fragment's own
 * lifecycle while no view exists, so a dialog raised outside the view window is still bound.
 */
fun AlertDialog.Builder.showBoundTo(fragment: Fragment): AlertDialog? =
    showBoundTo(fragment.viewLifecycleOwnerLiveData.value ?: fragment)
