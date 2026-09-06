package com.sza.fastmediasorter.widget.registry

import android.content.Context
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2613: the calculator and the stopwatch ship switched off, and before this suite neither picker
 * entry consulted its switch - so the in-app "add widget" list offered a placement for a program the
 * rest of the application treats as disabled.
 *
 * The gate is asserted through `entries()`, the ungated static table, rather than
 * `availableEntries()`: the latter also applies the manifest gate, which needs a real
 * `AppWidgetManager` and would make this a device test to prove a property of the table.
 */
class HomeWidgetCatalogSettingGateTest {

    private val catalog = HomeWidgetCatalog(
        context = mockk<Context>(relaxed = true),
        settingsRepository = mockk<SettingsRepository>(relaxed = true),
    )

    private fun gateOf(gadgetKey: String): (AppSettings) -> Boolean {
        val entry = catalog.entries().firstOrNull { it.gadgetKey == gadgetKey }
        assertNotNull("no catalog entry declares '$gadgetKey'", entry)
        val gate = entry?.settingGate
        assertNotNull(
            "'$gadgetKey' launches a sub-program with a runtime switch, so it must carry a settingGate " +
                "- without one the picker offers it while the program is off (S1856 precedent)",
            gate,
        )
        return gate!!
    }

    @Test
    fun `the calculator entry follows its own switch`() {
        val gate = gateOf(CALCULATOR_KEY)
        assertFalse(
            "the calculator ships off, so the default settings must not offer its widget",
            gate(AppSettings()),
        )
        assertTrue(
            "the calculator widget must be offered once the user turns the calculator on",
            gate(AppSettings(enableCalculator = true)),
        )
    }

    @Test
    fun `the stopwatch entry follows its own switch`() {
        val gate = gateOf(STOPWATCH_KEY)
        assertFalse(
            "the stopwatch ships off, so the default settings must not offer its widget",
            gate(AppSettings()),
        )
        assertTrue(
            "the stopwatch widget must be offered once the user turns the stopwatch on",
            gate(AppSettings(enableStopwatch = true)),
        )
    }

    @Test
    fun `each gate reads only its own switch`() {
        // The two entries were written from one template, which is how a copied lambda ends up asking
        // the neighbour's question - a mistake both tests above would pass unchanged.
        assertFalse(
            "the calculator widget must not appear because the stopwatch was turned on",
            gateOf(CALCULATOR_KEY)(AppSettings(enableStopwatch = true)),
        )
        assertFalse(
            "the stopwatch widget must not appear because the calculator was turned on",
            gateOf(STOPWATCH_KEY)(AppSettings(enableCalculator = true)),
        )
    }

    private companion object {
        const val CALCULATOR_KEY = "calculator"
        const val STOPWATCH_KEY = "stopwatch"
    }
}
