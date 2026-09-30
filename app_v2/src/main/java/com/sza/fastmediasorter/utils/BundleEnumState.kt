package com.sza.fastmediasorter.utils

import android.os.Bundle

/**
 * Saved-state codec for a transient enum a picker callback depends on - the kind, slot or direction
 * chosen before a system or child picker opens. Stored by constant name so a restore after an app
 * update that renamed or dropped the constant yields `null` instead of crashing [enumValueOf].
 */
fun Bundle.putEnumName(key: String, value: Enum<*>?) {
    if (value == null) remove(key) else putString(key, value.name)
}

inline fun <reified E : Enum<E>> Bundle?.getEnumByName(key: String): E? {
    val name = this?.getString(key) ?: return null
    return enumValues<E>().firstOrNull { it.name == name }
}
