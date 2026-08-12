package com.sza.fastmediasorter.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects [flow] on [Lifecycle.State.STARTED] (or [state]) without manual launch/repeatOnLifecycle boilerplate.
 * For Fragment receivers this uses [Fragment.viewLifecycleOwner], which is the safe choice -
 * never use the Fragment itself as LifecycleOwner for view-bound collection.
 * Do NOT apply to CREATED/RESUMED usages - those require explicit repeatOnLifecycle calls.
 */
fun <T> Fragment.collectOnLifecycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(state) {
            flow.collect { block(it) }
        }
    }
}

/**
 * Collects [flow] on [Lifecycle.State.STARTED] (or [state]) for Activities and other LifecycleOwners
 * (including Manager/Helper classes that receive a LifecycleOwner parameter).
 *
 * S1415: returns the [Job] so a caller that has to stop collecting *before* the lifecycle ends - a settings
 * switch turning an indicator off, say - can cancel it instead of collecting into a hidden view. Callers
 * that only need lifecycle-scoped collection ignore the return value and are unaffected.
 */
fun <T> LifecycleOwner.collectOnLifecycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit
): Job = lifecycleScope.launch {
    repeatOnLifecycle(state) {
        flow.collect { block(it) }
    }
}
