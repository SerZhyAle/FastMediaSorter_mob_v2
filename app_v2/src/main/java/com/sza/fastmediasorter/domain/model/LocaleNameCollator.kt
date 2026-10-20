package com.sza.fastmediasorter.domain.model

import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * MV-1.1: name sort collates in the interface locale. A root-locale lowercase plus code-point
 * compare puts Russian ё after я; a locale Collator at SECONDARY strength ignores case while
 * keeping diacritics distinct, so ё sorts beside е.
 */
object LocaleNameCollator {

    private val cache = ConcurrentHashMap<Locale, Comparator<String>>()

    fun comparator(locale: Locale): Comparator<String> = cache.computeIfAbsent(locale) { key ->
        val collator = Collator.getInstance(key)
        collator.strength = Collator.SECONDARY
        // Collator.compare is not thread-safe; sorts may run it from any thread.
        Comparator<String> { a, b -> synchronized(collator) { collator.compare(a, b) } }
    }
}
