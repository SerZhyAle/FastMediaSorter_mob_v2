package com.sza.fastmediasorter.data.repository.networkmonitor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "probe_target_store_prefs"
private const val KEY_TARGETS = "probe_targets"
private const val TARGET_SEPARATOR = "\u001E"
private const val MAX_TARGETS = 15

/**
 * S1617: small device-local store of recent diagnostic targets (IPs or hostnames), newest first.
 */
@Singleton
class ProbeTargetStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _targets = MutableStateFlow<List<String>>(emptyList())
    val targets: StateFlow<List<String>> = _targets.asStateFlow()

    init {
        _targets.value = loadTargets()
    }

    fun addTarget(target: String) {
        val clean = target.trim()
        if (clean.isEmpty()) return
        val current = _targets.value.toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val updated = current.take(MAX_TARGETS)
        _targets.value = updated
        saveTargets(updated)
    }

    fun removeTarget(target: String) {
        val clean = target.trim()
        val current = _targets.value.toMutableList()
        if (current.remove(clean)) {
            _targets.value = current
            saveTargets(current)
        }
    }

    fun clearAll() {
        _targets.value = emptyList()
        prefs.edit().remove(KEY_TARGETS).apply()
    }

    private fun loadTargets(): List<String> {
        val raw = prefs.getString(KEY_TARGETS, null) ?: return emptyList()
        return raw.split(TARGET_SEPARATOR).filter { it.isNotEmpty() }
    }

    private fun saveTargets(list: List<String>) {
        val raw = list.joinToString(TARGET_SEPARATOR)
        prefs.edit().putString(KEY_TARGETS, raw).apply()
    }
}
