package com.sza.fastmediasorter.wear.data.repository

import android.content.SharedPreferences
import io.mockk.mockk
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe in-memory stand-in for the two preference calls the watch stores make. `apply()`
 * publishes at once, which is what a real SharedPreferences does for its in-memory map.
 */
internal class InMemorySharedPreferences(
    private val delegate: SharedPreferences = mockk(relaxed = true)
) : SharedPreferences by delegate {

    val values = ConcurrentHashMap<String, String>()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor(
        private val editorDelegate: SharedPreferences.Editor = mockk(relaxed = true)
    ) : SharedPreferences.Editor by editorDelegate {

        private val pending = mutableMapOf<String, String?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun apply() {
            publish()
        }

        override fun commit(): Boolean {
            publish()
            return true
        }

        private fun publish() {
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
