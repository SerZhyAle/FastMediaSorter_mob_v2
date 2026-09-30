package com.sza.fastmediasorter.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A lazy value that belongs to one view of its fragment, not to the fragment instance.
 *
 * A helper that captures the view binding must not outlive the view it was built from: a plain
 * `by lazy` field keeps the first, destroyed binding when the same fragment instance gets a new view.
 * The value is rebuilt on the first read after [Fragment.getViewLifecycleOwner] changes, so reading
 * it follows the same rule as reading the binding itself - only while a view exists.
 */
class ViewScopedLazy<T : Any>(private val initializer: () -> T) : ReadOnlyProperty<Fragment, T> {
    private var owner: LifecycleOwner? = null
    private var value: T? = null

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
        val current = thisRef.viewLifecycleOwner
        val cached = value
        if (cached != null && owner === current) return cached
        return initializer().also {
            value = it
            owner = current
        }
    }
}

fun <T : Any> viewScoped(initializer: () -> T): ViewScopedLazy<T> = ViewScopedLazy(initializer)
