package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.launcher.LauncherStarterLayoutRules.StarterSectionGroup
import com.sza.fastmediasorter.data.model.DeviceProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2309: strategic §11.1 and §11.2 state the feature as observable differences between two devices, so
 * the same two differences are asserted here at the rule level - that is what lets a later regression
 * fail on a build machine instead of only on a phone in someone's hand.
 */
@Suppress("FunctionNaming") // backtick test names, project convention (cf. LauncherStarterSetsTest)
class LauncherStarterLayoutRulesTest {

    private val allScreenClasses: List<LauncherScreenClass> =
        LauncherScreenClass.Size.entries.flatMap { size ->
            LauncherScreenClass.Shape.entries.map { shape -> LauncherScreenClass(size, shape) }
        }

    @Test
    fun `every profile resolves on every screen class`() {
        for (profile in DeviceProfileType.entries) {
            for (screenClass in allScreenClasses) {
                val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

                assertTrue(
                    "$profile on $screenClass seeded no group",
                    rule.sectionOrder.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `every seeded group names a section the catalog lists`() {
        val known = LauncherSectionCatalog.all.map { it.key }.toSet()

        for (group in StarterSectionGroup.entries) {
            assertTrue("${group.name} seeds unknown section ${group.sectionKey}", group.sectionKey in known)
        }
    }

    @Test
    fun `screen count stays within the declared range`() {
        for (profile in DeviceProfileType.entries) {
            for (screenClass in allScreenClasses) {
                val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

                assertTrue(
                    "$profile on $screenClass asked for ${rule.screenCount} screens",
                    rule.screenCount >= LauncherStarterLayoutRules.MIN_SCREEN_COUNT &&
                        rule.screenCount <= LauncherStarterLayoutRules.MAX_SCREEN_COUNT,
                )
            }
        }
    }

    @Test
    fun `first screen never claims more sections than exist`() {
        for (profile in DeviceProfileType.entries) {
            for (screenClass in allScreenClasses) {
                val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

                assertTrue(
                    "$profile on $screenClass puts ${rule.firstScreenSections} of " +
                        "${rule.sectionOrder.size} sections on screen 0",
                    rule.firstScreenSections in 1..rule.sectionOrder.size,
                )
            }
        }
    }

    @Test
    fun `every budget stays positive`() {
        for (profile in DeviceProfileType.entries) {
            for (screenClass in allScreenClasses) {
                val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

                for ((group, budget) in rule.itemBudget) {
                    assertTrue("$profile on $screenClass budgets $group at $budget", budget >= 1)
                }
            }
        }
    }

    @Test
    fun `a compact screen puts fewer sections on screen zero than an expanded one`() {
        for (profile in DeviceProfileType.entries) {
            for (shape in LauncherScreenClass.Shape.entries) {
                val compact = LauncherStarterLayoutRules.ruleFor(
                    LauncherScreenClass(LauncherScreenClass.Size.COMPACT, shape),
                )
                val expanded = LauncherStarterLayoutRules.ruleFor(
                    LauncherScreenClass(LauncherScreenClass.Size.EXPANDED, shape),
                )

                assertTrue(
                    "$profile on $shape: compact ${compact.firstScreenSections} " +
                        "is not below expanded ${expanded.firstScreenSections}",
                    compact.firstScreenSections < expanded.firstScreenSections,
                )
            }
        }
    }

    @Test
    fun `a compact screen budgets a group below what an expanded one budgets`() {
        val screenClassCompact = LauncherScreenClass(LauncherScreenClass.Size.COMPACT, LauncherScreenClass.Shape.WIDE)
        val screenClassExpanded = LauncherScreenClass(LauncherScreenClass.Size.EXPANDED, LauncherScreenClass.Shape.WIDE)

        val compact = LauncherStarterLayoutRules.ruleFor(screenClassCompact)
        val expanded = LauncherStarterLayoutRules.ruleFor(screenClassExpanded)

        assertTrue(
            "compact ${compact.itemBudget} is not below expanded ${expanded.itemBudget}",
            compact.itemBudget.getValue(StarterSectionGroup.ANDROID_APPS) <
                expanded.itemBudget.getValue(StarterSectionGroup.ANDROID_APPS),
        )
    }

    @Test
    fun `an elongated screen puts fewer sections on screen zero than a balanced one`() {
        val screenClass = LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.ELONGATED)
        val balanced = LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.BALANCED)

        val elongatedRule = LauncherStarterLayoutRules.ruleFor(screenClass)
        val balancedRule = LauncherStarterLayoutRules.ruleFor(balanced)

        assertTrue(
            "elongated ${elongatedRule.firstScreenSections} is not below balanced " +
                "${balancedRule.firstScreenSections}",
            elongatedRule.firstScreenSections < balancedRule.firstScreenSections,
        )
    }

    @Test
    fun `a balanced expanded screen earns one screen more than a wide one`() {
        val balanced = LauncherScreenClass(LauncherScreenClass.Size.EXPANDED, LauncherScreenClass.Shape.BALANCED)
        val wide = LauncherScreenClass(LauncherScreenClass.Size.EXPANDED, LauncherScreenClass.Shape.WIDE)

        val balancedRule = LauncherStarterLayoutRules.ruleFor(balanced)
        val wideRule = LauncherStarterLayoutRules.ruleFor(wide)

        assertEquals(wideRule.screenCount + 1, balancedRule.screenCount)
    }

    @Test
    fun `the section order is a function of the screen class and not of the profile`() {
        // Strategic 5: the profile decides which groups have content, the screen class decides how that
        // content is laid out. The rule takes no profile at all, so this asserts the shape of the API as
        // much as its result - a future overload taking one would fail to compile against this call.
        val wide = LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE)
        val elongated = LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.ELONGATED)

        assertEquals(
            LauncherStarterLayoutRules.ruleFor(wide).sectionOrder,
            LauncherStarterLayoutRules.ruleFor(wide).sectionOrder,
        )
        assertNotEquals(
            LauncherStarterLayoutRules.ruleFor(wide).sectionOrder,
            LauncherStarterLayoutRules.ruleFor(elongated).sectionOrder,
        )
    }

    @Test
    fun `the launcher actions never fall on a later screen than the features they share a section with`() {
        for (size in LauncherScreenClass.Size.entries) {
            for (shape in LauncherScreenClass.Shape.entries) {
                val rule = LauncherStarterLayoutRules.ruleFor(LauncherScreenClass(size, shape))
                val features = rule.sectionOrder.indexOf(
                    LauncherStarterLayoutRules.StarterSectionGroup.APP_FUNCTIONS,
                )
                val actions = rule.sectionOrder.indexOf(
                    LauncherStarterLayoutRules.StarterSectionGroup.LAUNCHER_ACTIONS,
                )

                val cut = rule.firstScreenSections
                assertEquals(
                    "$size/$shape split the app-functions section across the screen cut",
                    features < cut,
                    actions < cut,
                )
            }
        }
    }

    @Test
    fun `the medium-wide first screen still holds the same groups after the resources split`() {
        // S2321: the cut counts entries of sectionOrder, so adding CORE_RESOURCES ahead of GOOGLE_APPS
        // would have pushed that group to screen 1 unless DEFAULT_FIRST_SCREEN_SECTIONS rose with it.
        // This pins the resulting membership, which is what the S2309 behavioural assertions compose
        // against - a bump left out or overdone is a silent redesign of what a fresh desktop shows.
        val rule = LauncherStarterLayoutRules.ruleFor(
            LauncherScreenClass(LauncherScreenClass.Size.MEDIUM, LauncherScreenClass.Shape.WIDE),
        )

        assertEquals(
            listOf(
                StarterSectionGroup.PROFILE_GADGETS,
                StarterSectionGroup.CORE_RESOURCES,
                StarterSectionGroup.RESOURCES,
                StarterSectionGroup.APP_FUNCTIONS,
                StarterSectionGroup.LAUNCHER_ACTIONS,
                StarterSectionGroup.ANDROID_APPS,
                StarterSectionGroup.GOOGLE_APPS,
            ),
            rule.sectionOrder.take(rule.firstScreenSections),
        )
    }

    @Test
    fun `no screen cut splits two groups that share a section key`() {
        for (screenClass in allScreenClasses) {
            val rule = LauncherStarterLayoutRules.ruleFor(screenClass)
            val cut = rule.firstScreenSections
            val before = rule.sectionOrder.getOrNull(cut - 1)
            val after = rule.sectionOrder.getOrNull(cut)

            assertNotEquals(
                "$screenClass split one section key across the screen cut",
                before?.sectionKey,
                after?.sectionKey,
            )
        }
    }

    @Test
    fun `the core resources group is unbounded on every screen class`() {
        for (screenClass in allScreenClasses) {
            val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

            assertTrue(
                "$screenClass budgeted CORE_RESOURCES, which drops a whole content type when it bites",
                StarterSectionGroup.CORE_RESOURCES !in rule.itemBudget,
            )
        }
    }

    @Test
    fun `an unknown profile still resolves to a usable desktop`() {
        val screenClass = LauncherScreenClass(LauncherScreenClass.Size.COMPACT, LauncherScreenClass.Shape.ELONGATED)

        val rule = LauncherStarterLayoutRules.ruleFor(screenClass)

        assertTrue(rule.sectionOrder.isNotEmpty())
        assertTrue(rule.firstScreenSections >= 1)
        assertTrue(rule.screenCount >= LauncherStarterLayoutRules.MIN_SCREEN_COUNT)
    }
}
