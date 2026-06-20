package com.sza.fastmediasorter.ui.icon

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R

/**
 * Constant registry mapping `ico-XX-NNN` icon ids to `@DrawableRes` integers.
 * All 60 entries are listed by hand - no reflection used.
 */
object ResourceIconRegistry {

    /** Separator used in public icon ids (e.g. `ico-01-001`). */
    private const val SEP = "-"

    /** Backing map: key = `ico-NN-NNN`, value = R.drawable.ico_NN_NNN */
    private val registry: Map<String, Int> = mapOf(
        // Set 01 - Music
        "ico-01-001" to R.drawable.ico_01_001,
        "ico-01-002" to R.drawable.ico_01_002,
        "ico-01-003" to R.drawable.ico_01_003,
        "ico-01-004" to R.drawable.ico_01_004,
        "ico-01-005" to R.drawable.ico_01_005,
        "ico-01-006" to R.drawable.ico_01_006,
        "ico-01-007" to R.drawable.ico_01_007,
        "ico-01-008" to R.drawable.ico_01_008,
        "ico-01-009" to R.drawable.ico_01_009,
        "ico-01-010" to R.drawable.ico_01_010,
        "ico-01-011" to R.drawable.ico_01_011,
        "ico-01-012" to R.drawable.ico_01_012,
        "ico-01-013" to R.drawable.ico_01_013,
        "ico-01-014" to R.drawable.ico_01_014,
        "ico-01-015" to R.drawable.ico_01_015,
        "ico-01-016" to R.drawable.ico_01_016,
        "ico-01-017" to R.drawable.ico_01_017,
        "ico-01-018" to R.drawable.ico_01_018,
        "ico-01-019" to R.drawable.ico_01_019,
        "ico-01-020" to R.drawable.ico_01_020,
        // Set 02 - Video
        "ico-02-001" to R.drawable.ico_02_001,
        "ico-02-002" to R.drawable.ico_02_002,
        "ico-02-003" to R.drawable.ico_02_003,
        "ico-02-004" to R.drawable.ico_02_004,
        "ico-02-005" to R.drawable.ico_02_005,
        "ico-02-006" to R.drawable.ico_02_006,
        "ico-02-007" to R.drawable.ico_02_007,
        "ico-02-008" to R.drawable.ico_02_008,
        "ico-02-009" to R.drawable.ico_02_009,
        "ico-02-010" to R.drawable.ico_02_010,
        "ico-02-011" to R.drawable.ico_02_011,
        "ico-02-012" to R.drawable.ico_02_012,
        "ico-02-013" to R.drawable.ico_02_013,
        "ico-02-014" to R.drawable.ico_02_014,
        "ico-02-015" to R.drawable.ico_02_015,
        "ico-02-016" to R.drawable.ico_02_016,
        "ico-02-017" to R.drawable.ico_02_017,
        "ico-02-018" to R.drawable.ico_02_018,
        "ico-02-019" to R.drawable.ico_02_019,
        "ico-02-020" to R.drawable.ico_02_020,
        // Set 03 - Image
        "ico-03-001" to R.drawable.ico_03_001,
        "ico-03-002" to R.drawable.ico_03_002,
        "ico-03-003" to R.drawable.ico_03_003,
        "ico-03-004" to R.drawable.ico_03_004,
        "ico-03-005" to R.drawable.ico_03_005,
        "ico-03-006" to R.drawable.ico_03_006,
        "ico-03-007" to R.drawable.ico_03_007,
        "ico-03-008" to R.drawable.ico_03_008,
        "ico-03-009" to R.drawable.ico_03_009,
        "ico-03-010" to R.drawable.ico_03_010,
        "ico-03-011" to R.drawable.ico_03_011,
        "ico-03-012" to R.drawable.ico_03_012,
        "ico-03-013" to R.drawable.ico_03_013,
        "ico-03-014" to R.drawable.ico_03_014,
        "ico-03-015" to R.drawable.ico_03_015,
        "ico-03-016" to R.drawable.ico_03_016,
        "ico-03-017" to R.drawable.ico_03_017,
        "ico-03-018" to R.drawable.ico_03_018,
        "ico-03-019" to R.drawable.ico_03_019,
        "ico-03-020" to R.drawable.ico_03_020,
        // Set 04 - Documents
        "ico-04-001" to R.drawable.ico_04_001,
        "ico-04-002" to R.drawable.ico_04_002,
        "ico-04-003" to R.drawable.ico_04_003,
        "ico-04-004" to R.drawable.ico_04_004,
        "ico-04-005" to R.drawable.ico_04_005,
        "ico-04-006" to R.drawable.ico_04_006,
        "ico-04-007" to R.drawable.ico_04_007,
        "ico-04-008" to R.drawable.ico_04_008,
        "ico-04-009" to R.drawable.ico_04_009,
        "ico-04-010" to R.drawable.ico_04_010,
        "ico-04-011" to R.drawable.ico_04_011,
        "ico-04-012" to R.drawable.ico_04_012,
        "ico-04-013" to R.drawable.ico_04_013,
        "ico-04-014" to R.drawable.ico_04_014,
        "ico-04-015" to R.drawable.ico_04_015,
        "ico-04-016" to R.drawable.ico_04_016,
        "ico-04-017" to R.drawable.ico_04_017,
        "ico-04-018" to R.drawable.ico_04_018,
        "ico-04-019" to R.drawable.ico_04_019,
        "ico-04-020" to R.drawable.ico_04_020,
        // Set 05 - Other / Abstract
        "ico-05-001" to R.drawable.ico_05_001,
        "ico-05-002" to R.drawable.ico_05_002,
        "ico-05-003" to R.drawable.ico_05_003,
        "ico-05-004" to R.drawable.ico_05_004,
        "ico-05-005" to R.drawable.ico_05_005,
        "ico-05-006" to R.drawable.ico_05_006,
        "ico-05-007" to R.drawable.ico_05_007,
        "ico-05-008" to R.drawable.ico_05_008,
        "ico-05-009" to R.drawable.ico_05_009,
        "ico-05-010" to R.drawable.ico_05_010,
        "ico-05-011" to R.drawable.ico_05_011,
        "ico-05-012" to R.drawable.ico_05_012,
        "ico-05-013" to R.drawable.ico_05_013,
        "ico-05-014" to R.drawable.ico_05_014,
        "ico-05-015" to R.drawable.ico_05_015,
        "ico-05-016" to R.drawable.ico_05_016,
        "ico-05-017" to R.drawable.ico_05_017,
        "ico-05-018" to R.drawable.ico_05_018,
        "ico-05-019" to R.drawable.ico_05_019,
        "ico-05-020" to R.drawable.ico_05_020,
    )

    // Validates `ico-XX-NNN` format (XX = set id, NNN = ordinal)
    private val ID_REGEX = Regex("^ico-(\\d{2})-(\\d{3})$")

    /** Returns the drawable res id for [iconId], or null if the id is unknown or null. */
    @DrawableRes
    fun resolveDrawable(iconId: String?): Int? = iconId?.let { registry[it] }

    /** Returns all ids belonging to [set] in ordinal order. */
    fun idsFor(set: ResourceIconSet): List<String> {
        val prefix = "ico-%02d-".format(set.setId)
        return registry.keys.filter { it.startsWith(prefix) }.sorted()
    }

    /**
     * Parses an icon id of the form `ico-XX-NNN`.
     * Returns the [ResourceIconSet] and ordinal (1-based), or null for malformed input.
     */
    fun parseId(iconId: String): Pair<ResourceIconSet, Int>? {
        val m = ID_REGEX.matchEntire(iconId) ?: return null
        val set = ResourceIconSet.fromSetId(m.groupValues[1].toInt()) ?: return null
        val ordinal = m.groupValues[2].toInt()
        return Pair(set, ordinal)
    }

    /** Returns true if [iconId] is a known, well-formed id. */
    fun isValid(iconId: String): Boolean = registry.containsKey(iconId)

    /** Picks a random id from [set]. */
    fun randomIdFor(
        set: ResourceIconSet,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): String = idsFor(set).random(random)

    /** Returns the first id of [set] (`ico-0X-001`), used for predefined resources. */
    fun firstIdFor(set: ResourceIconSet): String = "ico-%02d-001".format(set.setId)
}
