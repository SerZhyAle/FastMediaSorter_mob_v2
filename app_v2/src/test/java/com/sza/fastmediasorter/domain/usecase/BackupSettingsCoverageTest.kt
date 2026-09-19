package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BroadcastSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S2632: [BackupSettings] lists the settings it carries by hand, and the compiler cannot see a field that
 * was never listed - a missing field is not a type error, just an absent line. So a setting added to the
 * model but not to the backup is lost on every restore, silently: for the DTO it does not exist, which is
 * why neither the backup nor the restore reports anything.
 *
 * This test pins the two sides together by reflection. It is a RATCHET, not a completeness claim: 132
 * model fields are genuinely absent from the backup today (S2648 owns splitting them into "should be
 * backed up", "deliberately excluded" and "derived"), and [KNOWN_UNCOVERED] freezes exactly that set. A
 * 133rd cannot appear without failing here.
 *
 * The comparison is on equality rather than containment on purpose: containment would let a rename slip
 * through, because the renamed field would leave the baseline and re-enter as a new uncovered name.
 *
 * Sibling check: [SettingsDefaultParityTest] (S2631) compares the DEFAULT VALUES of fields present on
 * both sides. It cannot see this defect at all - a field absent from [BackupSettings] has no default to
 * compare - and this one cannot see that defect. Two invariants, two files.
 */
class BackupSettingsCoverageTest {

    private companion object {
        /**
         * A reference to a nested settings group is structure, not a setting: the group's own leaves are
         * walked separately, so counting the reference would double-count the whole group.
         */
        val GROUP_TYPES: Set<Class<*>> = setOf(
            LauncherSettings::class.java,
            ScreenshotGestureSettings::class.java,
            BroadcastSettings::class.java
        )

        /**
         * S2648: the nested groups [BackupSettings] carries beside its flat fields. A settings field is
         * covered when it appears at either level, so the walk below reads the group TYPES rather than
         * the values - every group reference defaults to null, and a null carries no field names.
         */
        val BACKUP_GROUP_TYPES: List<Class<*>> = listOf(
            BackupSettings.ScreenshotGesture::class.java,
            BackupSettings.LauncherExtra::class.java,
            BackupSettings.Capture::class.java,
            BackupSettings.Programs::class.java,
            BackupSettings.Streams::class.java,
            BackupSettings.Appearance::class.java,
            BackupSettings.PlayerExtra::class.java,
            BackupSettings.Integration::class.java
        )

        /**
         * Model fields with no counterpart in [BackupSettings]. Every entry is a setting that does NOT
         * survive a backup/restore round trip, and every entry carries the reason it must not.
         *
         * S2632 froze 132 names here as a ratchet over a defect it had measured but not fixed; S2648
         * classified all 132 and carried 124 of them, leaving eight; S2843 triaged the ten fields that
         * had drifted in since, carrying nine and adding the ninth entry below. Growing this list
         * needs a stated reason, because each new line is one more setting the user loses when they
         * move to a new device.
         */
        val KNOWN_UNCOVERED: Set<String> = setOf(
            // Deliberately excluded - the value is real, but it belongs to one device or one person.
            // Opaque per-lens capture memory; lens ids address one device's hardware.
            "cameraLensSettings",
            // Camera lens selected for video broadcast; lens ids address one device's hardware.
            "cameraLensId",
            // Screen-capture consent, given on a device by the person holding it. A restored "already
            // accepted" would suppress a warning that person never saw.
            "screenCaptureDisclosureAccepted",
            // The same consent for continuous recording, separate since S0774.
            "screenRecordingDisclosureAccepted",
            // Step-counter baseline read against this device's own sensor; another device's baseline
            // renders a wrong step count.
            "stepsResetCount",
            "stepsResetTimestamp",
            // S2843: this phone's stable identity as a broadcast source. Restored onto a second phone
            // it would give two sources one identity, and a receiver that scanned either of them would
            // keep overwriting one catalog entry instead of holding two.
            // S3222: a leaf of the `broadcast` group, so the walk reports it under its unprefixed name.
            "sourceDeviceId",

            // Derived, session-scoped or dead - there is nothing durable to carry.
            // A content:// URI whose read permission was granted to this install and does not travel.
            "lastSelectedLocalFolder",
            // A row id in the local database; restore reassigns resource ids, so the number points nowhere.
            "lastUsedResourceId",
            // Marked legacy in the model and read by nothing.
            "slideshowMusicUri"
        )
    }

    @Test
    fun backupSettings_coversEveryModelField_exceptTheFrozenBaseline() {
        val backupNames = backupFieldNames()
        val uncovered = sortedSetOf<String>()
        for ((instance, prefix) in modelGroups()) {
            for (name in leafFieldNames(instance)) {
                if (!isCovered(name, prefix, backupNames)) {
                    uncovered += name
                }
            }
        }

        val problems = mutableListOf<String>()
        for (name in uncovered - KNOWN_UNCOVERED) {
            problems += "$name: in the settings model, absent from BackupSettings - either carry it in " +
                "the backup, or add it to KNOWN_UNCOVERED and say why it must not be carried"
        }
        for (name in KNOWN_UNCOVERED - uncovered) {
            problems += "$name: listed in KNOWN_UNCOVERED but now covered or gone - drop the stale entry"
        }

        assertEquals(emptyList<String>(), problems.sorted())
    }

    /**
     * The prefix is the naming convention [BackupSettings] uses for a nested group: `trayShowClock` in
     * [LauncherSettings] is `launcherTrayShowClock` in the backup. Top-level [AppSettings] fields carry
     * no prefix, so both halves of the check collapse to the same lookup for them.
     */
    private fun isCovered(name: String, prefix: String, backupNames: Set<String>): Boolean {
        val prefixed = if (prefix.isEmpty()) {
            name
        } else {
            prefix + name.replaceFirstChar { it.uppercaseChar() }
        }
        return name in backupNames || prefixed in backupNames
    }

    /**
     * S2648: every name the backup can carry - the flat fields of [BackupSettings] plus the fields of
     * each nested group. Without the groups this walk would report the 124 settings S2648 carried as
     * still uncovered, because none of them sits at the top level any more.
     */
    private fun backupFieldNames(): Set<String> {
        val names = leafFieldNames(BackupSettings()).toMutableSet()
        for (group in BACKUP_GROUP_TYPES) {
            for (field in group.declaredFields) {
                if (field.isSynthetic || Modifier.isStatic(field.modifiers)) {
                    continue
                }
                names += field.name
            }
        }
        return names
    }

    private fun modelGroups(): List<Pair<Any, String>> = listOf(
        AppSettings() to "",
        LauncherSettings() to "launcher",
        ScreenshotGestureSettings() to "screenshotGesture",
        BroadcastSettings() to "broadcast"
    )

    /**
     * Instance fields only. A `static` field is never a constructor property - the Compose compiler adds
     * a `$stable` static to every stable class, and it belongs to no settings model.
     */
    private fun leafFieldNames(instance: Any): List<String> {
        val names = mutableListOf<String>()
        for (field in instance.javaClass.declaredFields) {
            val skipped = field.isSynthetic ||
                Modifier.isStatic(field.modifiers) ||
                field.type in GROUP_TYPES
            if (!skipped) {
                names += field.name
            }
        }
        return names
    }
}
