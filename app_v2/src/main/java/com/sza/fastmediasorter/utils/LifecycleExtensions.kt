package com.sza.fastmediasorter.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
 */
fun <T> LifecycleOwner.collectOnLifecycle(
    flow: Flow<T>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(state) {
            flow.collect { block(it) }
        }
    }
}
