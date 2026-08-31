package com.sza.fastmediasorter.ui.settings.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2256: the builder is the one place that decides how the grouped picker is arranged, so the two
 * surfaces cannot drift apart in section order, within-group order or duplicate rows. Each test below
 * pins one of those decisions.
 *
 * Resource ids are irrelevant here - the builder never resolves them - so the fixtures use distinct
 * sentinel ints rather than real `R` values, which keeps the test free of Android resources.
 */
class GesturePickerRowBuilderTest {

    private val builder = GesturePickerRowBuilder()

    @Test
    fun `sections follow group declaration order regardless of item order`() {
        val rows = builder.build(
            listOf(
                item("disabled", GestureActionGroup.DISABLED),
                item("launch", GestureActionGroup.LAUNCH),
                item("capture", GestureActionGroup.CAPTURE),
            ),
        )

        assertEquals(
            listOf(
                GestureActionGroup.CAPTURE.titleRes,
                GestureActionGroup.LAUNCH.titleRes,
                GestureActionGroup.DISABLED.titleRes,
            ),
            rows.filterIsInstance<GesturePickerRow.Header>().map { it.titleRes },
        )
    }

    @Test
    fun `a group with no item emits no header`() {
        val rows = builder.build(listOf(item("only", GestureActionGroup.SYSTEM)))

        assertEquals(
            listOf(GestureActionGroup.SYSTEM.titleRes),
            rows.filterIsInstance<GesturePickerRow.Header>().map { it.titleRes },
        )
    }

    @Test
    fun `items keep their supplied order inside a group`() {
        val rows = builder.build(
            listOf(
                item("second", GestureActionGroup.LAUNCH),
                item("first", GestureActionGroup.LAUNCH),
            ),
        )

        assertEquals(listOf("second", "first"), rows.entryKeys())
    }

    @Test
    fun `the launcher route leads its group`() {
        val rows = builder.build(
            items = listOf(item("assistant", GestureActionGroup.LAUNCH)),
            launcherRoute = item("all-apps", GestureActionGroup.LAUNCH),
        )

        assertEquals(listOf("all-apps", "assistant"), rows.entryKeys())
    }

    @Test
    fun `a launcher route the host also listed renders once`() {
        val rows = builder.build(
            items = listOf(item("all-apps", GestureActionGroup.LAUNCH), item("url", GestureActionGroup.LAUNCH)),
            launcherRoute = item("all-apps", GestureActionGroup.LAUNCH),
        )

        assertEquals(listOf("all-apps", "url"), rows.entryKeys())
    }

    @Test
    fun `an item carries its own metadata and enabled flag into its row`() {
        val rows = builder.build(listOf(item("locked", GestureActionGroup.SYSTEM, enabled = false)))

        val entry = rows.filterIsInstance<GesturePickerRow.Entry<String>>().single()
        assertEquals(LABEL_RES, entry.labelRes)
        assertEquals(EXPLANATION_RES, entry.explanationRes)
        assertEquals(ICON_RES, entry.iconRes)
        assertEquals(false, entry.enabled)
    }

    private fun List<GesturePickerRow<String>>.entryKeys(): List<String> =
        filterIsInstance<GesturePickerRow.Entry<String>>().map { it.actionKey }

    private fun item(
        key: String,
        group: GestureActionGroup,
        enabled: Boolean = true,
    ) = GesturePickerItem(
        key = key,
        meta = GestureActionMeta(group, LABEL_RES, EXPLANATION_RES, ICON_RES),
        enabled = enabled,
    )

    private companion object {
        const val LABEL_RES = 1
        const val EXPLANATION_RES = 2
        const val ICON_RES = 3
    }
}
