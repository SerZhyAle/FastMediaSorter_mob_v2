package com.sza.fastmediasorter.domain.usecase

import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BroadcastSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * S2631: a default declared twice is a default that will diverge.
 *
 * [BackupSettings] restates every [AppSettings] / [LauncherSettings] default so that a backup missing a
 * key still restores something; the restored value then travels straight into `AppSettings` through
 * `BackupMapper.toAppSettings`, so a mismatch here is a user-visible wrong setting after a restore -
 * one that a fresh install would never produce. This test pins the two sides together by reflection so
 * a future default change in the source of truth cannot silently leave the backup copy behind.
 *
 * A field is compared only when the source of truth carries the same name and the same type. Skipping
 * on type mismatch is deliberate: the backup stores several settings as portable string tokens whose
 * source-of-truth counterpart is an enum, and those are a different contract. Skipping on a null backup
 * default is deliberate too: a nullable backup field is a tri-state ("absent" vs "explicitly set"), so
 * it has no default to mirror.
 */
class SettingsDefaultParityTest {

    private companion object {
        const val LAUNCHER_PREFIX = "launcher"

        // S3222: the second prefixed group. Without it the fourteen broadcast pairs stop being
        // compared at all - an absent source of truth reads as "nothing to compare", not as a
        // divergence, so the defect this test exists to catch would go quiet for them.
        const val BROADCAST_PREFIX = "broadcast"
    }

    /**
     * S2631 research item 1: proves the declared defaults in [BackupSettings] are actually reachable
     * through the restore path. Kotlin emits a synthetic no-argument constructor when every primary
     * constructor parameter has a default, and Gson prefers a declared no-argument constructor over
     * unsafe allocation - so a key missing from the JSON restores the declared default rather than the
     * JVM zero. Both halves have to hold; if either stops holding, every non-null default in the class
     * silently becomes `false` / `0` and this test is what says so.
     */
    @Test
    fun gsonDeserialization_withEveryKeyAbsent_appliesDeclaredDefaults() {
        val restored = GsonBuilder().setLenient().create().fromJson("{}", BackupSettings::class.java)

        assertEquals(BackupSettings(), restored)
    }

    @Test
    fun backupSettingsDefaults_matchTheirSourceOfTruth() {
        val backupDefaults = fieldValues(BackupSettings())
        val appDefaults = fieldValues(AppSettings())
        val launcherDefaults = fieldValues(LauncherSettings())
        val broadcastDefaults = fieldValues(BroadcastSettings())

        val divergences = backupDefaults.mapNotNull { (name, backupValue) ->
            val source = sourceOfTruthFor(name, appDefaults, launcherDefaults, broadcastDefaults)
            divergenceOrNull(name, backupValue, source)
        }

        assertEquals(emptyList<String>(), divergences)
    }

    /**
     * Null on every reason not to compare: a tri-state backup field (null default), a backup field the
     * source of truth does not declare, a pair whose types differ because the backup stores a portable
     * string token for an enum, or a pair that already agrees.
     */
    private fun divergenceOrNull(name: String, backupValue: Any?, sourceValue: Any?): String? {
        val diverged = backupValue != null &&
            sourceValue != null &&
            sourceValue.javaClass == backupValue.javaClass &&
            sourceValue != backupValue
        return if (diverged) "$name: BackupSettings=$backupValue, source of truth=$sourceValue" else null
    }

    private fun sourceOfTruthFor(
        name: String,
        appDefaults: Map<String, Any?>,
        launcherDefaults: Map<String, Any?>,
        broadcastDefaults: Map<String, Any?>
    ): Any? {
        val unprefixedLauncher = name.removePrefix(LAUNCHER_PREFIX).replaceFirstChar { it.lowercaseChar() }
        val unprefixedBroadcast = name.removePrefix(BROADCAST_PREFIX).replaceFirstChar { it.lowercaseChar() }
        return appDefaults[name]
            ?: launcherDefaults[name]
            ?: launcherDefaults[unprefixedLauncher]
            ?: broadcastDefaults[unprefixedBroadcast]
    }

    /**
     * Instance fields only. A `static` field is never a constructor default - the Compose compiler adds
     * a `$stable` static to every stable class, and comparing those reported a divergence between two
     * classes that declare no such property at all.
     */
    private fun fieldValues(instance: Any): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>()
        for (field in instance.javaClass.declaredFields) {
            if (field.isSynthetic || Modifier.isStatic(field.modifiers)) {
                continue
            }
            values[field.name] = readField(field, instance)
        }
        return values
    }

    private fun readField(field: Field, instance: Any): Any? {
        field.isAccessible = true
        return field.get(instance)
    }
}
