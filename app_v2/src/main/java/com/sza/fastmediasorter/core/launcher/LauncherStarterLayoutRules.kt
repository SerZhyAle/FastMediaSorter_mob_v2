package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand

/**
 * S2309: the screen class -> section order, per-section item budget and screen count.
 *
 * The rules live in one table rather than inside the composition conditions because the screen class is
 * a second axis multiplied into an already 800-line starter table (strategic §5.3, §7): expressed as
 * branching, nine screen classes times the groups they order is a branch count; expressed as a base rule
 * plus two adjustment tables it stays one order and six adjustments.
 *
 * **The profile is deliberately not an input here.** Strategic §5 splits the two axes: the profile
 * decides intent - which groups have anything in them at all - and the screen class decides capacity -
 * how much of that reaches the first screen, in what order, over how many screens. §5.3 asks for the
 * order rule to hold "без нового ветвления в каждом профиле". A per-profile order was written first and
 * measured wrong: it moved the app-functions and android-apps sections off screen 0 for the media and
 * glance profiles, which broke six behavioural assertions that had encoded those sections' position
 * since S0404. Content varies by profile because the groups it earns vary; layout does not.
 *
 * The unit of order is the group, never the section key: the starter table emits
 * [LauncherCellCommand.SECTION_WIDGETS] and [LauncherCellCommand.SECTION_RESOURCES] twice with different
 * content - the profile gadgets against the utility widgets, the resource shortcuts against the media
 * windows - so a list of keys could not say which of the two an entry means.
 */
object LauncherStarterLayoutRules {

    /**
     * The content groups the starter table builds, each naming the section key it seeds.
     *
     * Every key is read from [LauncherCellCommand]; this file declares no key string of its own, because
     * a second spelling of a section key is a section that silently stops matching its own header.
     *
     * [APP_FUNCTIONS] and [LAUNCHER_ACTIONS] share a key and are one section on screen: they are two
     * groups only so that a budget can shorten the feature tiles without ever reaching the launcher's
     * own actions, which have no meaning as a subset (an "exit launcher mode" the budget cut leaves the
     * user inside a launcher they cannot leave).
     */
    enum class StarterSectionGroup(val sectionKey: String) {
        PROFILE_GADGETS(LauncherCellCommand.SECTION_WIDGETS),
        RESOURCES(LauncherCellCommand.SECTION_RESOURCES),
        APP_FUNCTIONS(LauncherCellCommand.SECTION_APP_FUNCTIONS),
        LAUNCHER_ACTIONS(LauncherCellCommand.SECTION_APP_FUNCTIONS),
        ANDROID_APPS(LauncherCellCommand.SECTION_ANDROID_APPS),
        GOOGLE_APPS(LauncherCellCommand.SECTION_GOOGLE),
        UTILITY_WIDGETS(LauncherCellCommand.SECTION_WIDGETS),
        MEDIA_WINDOWS(LauncherCellCommand.SECTION_RESOURCES),
        STREAMS(LauncherCellCommand.SECTION_MAIN),
    }

    /**
     * @param sectionOrder the groups in the order they are emitted; a group absent from it seeds nothing.
     * @param itemBudget the maximum number of items a group seeds. A group with no entry is unbounded.
     * @param firstScreenSections how many leading entries of [sectionOrder] stay on screen index 0.
     * @param screenCount how many screens the composed desktop fills, in [MIN_SCREEN_COUNT]..[MAX_SCREEN_COUNT].
     */
    data class Rule(
        val sectionOrder: List<StarterSectionGroup>,
        val itemBudget: Map<StarterSectionGroup, Int>,
        val firstScreenSections: Int,
        val screenCount: Int,
    )

    const val MIN_SCREEN_COUNT = 1
    const val MAX_SCREEN_COUNT = 5

    private const val DEFAULT_SCREEN_COUNT = 2

    // Six groups on screen 0 - through GOOGLE_APPS - which is the set the desktop carried before S2309
    // on the medium-wide class every adjustment below is stated relative to.
    private const val DEFAULT_FIRST_SCREEN_SECTIONS = 6

    // Budgets of the base rule. Named because a bare number in a map literal says nothing about which
    // group it caps, and because detekt reads every one of them as a magic number.
    private const val BUDGET_GADGETS = 6
    private const val BUDGET_RESOURCES = 8

    // Above the eleven feature tiles a fully equipped standard build offers, so the medium class trims
    // nothing that exists today and only a compact screen shortens the row.
    private const val BUDGET_APP_FUNCTIONS = 12
    private const val BUDGET_ANDROID_APPS = 12
    private const val BUDGET_GOOGLE_APPS = 10
    private const val BUDGET_UTILITY_WIDGETS = 4
    private const val BUDGET_MEDIA_WINDOWS = 3
    private const val BUDGET_STREAMS = 2

    // Per-group floor, so a shrinking adjustment never empties a group it is only meant to shorten.
    private const val MIN_BUDGET = 1

    /**
     * How each size scales a base rule. A compact screen fits fewer full grid rows, so it both shortens
     * every group and moves a section off screen 0; an expanded one does the reverse (strategic §5).
     */
    private data class SizeAdjustment(
        val budgetPercent: Int,
        val firstScreenSectionsDelta: Int,
    )

    private const val COMPACT_BUDGET_PERCENT = 60
    private const val MEDIUM_BUDGET_PERCENT = 100
    private const val EXPANDED_BUDGET_PERCENT = 140
    private const val PERCENT_BASE = 100

    private val SIZE_ADJUSTMENTS: Map<LauncherScreenClass.Size, SizeAdjustment> = mapOf(
        LauncherScreenClass.Size.COMPACT to SizeAdjustment(COMPACT_BUDGET_PERCENT, firstScreenSectionsDelta = -1),
        LauncherScreenClass.Size.MEDIUM to SizeAdjustment(MEDIUM_BUDGET_PERCENT, firstScreenSectionsDelta = 0),
        LauncherScreenClass.Size.EXPANDED to SizeAdjustment(EXPANDED_BUDGET_PERCENT, firstScreenSectionsDelta = 1),
    )

    /**
     * How each shape adjusts a base rule.
     *
     * An elongated screen loses one further section from screen 0 and pulls the utility widgets ahead of
     * the two app grids: a tall narrow screen shows few columns, so a row of small utility tiles costs a
     * fraction of what a row of an app grid costs to reach. A balanced shape gains a screen only on an
     * expanded size - that is where the grid is wide enough for the extra screen to hold whole sections
     * rather than the tail of one.
     */
    private data class ShapeAdjustment(
        val firstScreenSectionsDelta: Int,
        val screenCountDeltaWhenExpanded: Int,
        val utilitiesAheadOfAppGrids: Boolean,
    )

    private val SHAPE_ADJUSTMENTS: Map<LauncherScreenClass.Shape, ShapeAdjustment> = mapOf(
        LauncherScreenClass.Shape.BALANCED to ShapeAdjustment(
            firstScreenSectionsDelta = 0,
            screenCountDeltaWhenExpanded = 1,
            utilitiesAheadOfAppGrids = false,
        ),
        LauncherScreenClass.Shape.WIDE to ShapeAdjustment(
            firstScreenSectionsDelta = 0,
            screenCountDeltaWhenExpanded = 0,
            utilitiesAheadOfAppGrids = false,
        ),
        LauncherScreenClass.Shape.ELONGATED to ShapeAdjustment(
            firstScreenSectionsDelta = -1,
            screenCountDeltaWhenExpanded = 0,
            utilitiesAheadOfAppGrids = true,
        ),
    )

    /**
     * The rule for [screenClass].
     *
     * The medium-wide class returns the base rule unchanged, and the base rule is the layout the desktop
     * seeded before S2309 - so the class every existing behavioural test composes against still produces
     * exactly the desktop those tests were written for, and every other class is a stated departure from
     * it rather than an unmeasured redesign.
     */
    fun ruleFor(screenClass: LauncherScreenClass): Rule = adjust(baseRule(), screenClass)

    private fun adjust(base: Rule, screenClass: LauncherScreenClass): Rule {
        val size = SIZE_ADJUSTMENTS.getValue(screenClass.size)
        val shape = SHAPE_ADJUSTMENTS.getValue(screenClass.shape)
        val screenCountDelta = if (screenClass.size == LauncherScreenClass.Size.EXPANDED) {
            shape.screenCountDeltaWhenExpanded
        } else {
            0
        }
        val sectionOrder = if (shape.utilitiesAheadOfAppGrids) {
            utilitiesAheadOfAppGrids(base.sectionOrder)
        } else {
            base.sectionOrder
        }
        val cut = (base.firstScreenSections + size.firstScreenSectionsDelta + shape.firstScreenSectionsDelta)
            .coerceIn(1, sectionOrder.size)
        return base.copy(
            sectionOrder = sectionOrder,
            itemBudget = base.itemBudget.mapValues { (_, budget) -> scaleBudget(budget, size.budgetPercent) },
            firstScreenSections = keepActionsWithFeatures(sectionOrder, cut),
            screenCount = (base.screenCount + screenCountDelta).coerceIn(MIN_SCREEN_COUNT, MAX_SCREEN_COUNT),
        )
    }

    /**
     * Moves [StarterSectionGroup.UTILITY_WIDGETS] directly ahead of [StarterSectionGroup.ANDROID_APPS],
     * leaving every other entry where it was.
     *
     * Stated as a move rather than as a second literal order so the two orders cannot drift apart when a
     * group is added to one of them: there is one list in this file, and the shapes that depart from it
     * say how, not what.
     */
    private fun utilitiesAheadOfAppGrids(order: List<StarterSectionGroup>): List<StarterSectionGroup> {
        val rest = order.filterNot { it == StarterSectionGroup.UTILITY_WIDGETS }
        val at = rest.indexOf(StarterSectionGroup.ANDROID_APPS)
        if (at < 0) {
            return order
        }
        return rest.subList(0, at) + StarterSectionGroup.UTILITY_WIDGETS + rest.subList(at, rest.size)
    }

    /**
     * Pushes the screen cut past [StarterSectionGroup.LAUNCHER_ACTIONS] when it would otherwise fall
     * between it and the feature tiles it shares a section with.
     *
     * Splitting that pair across screens would put one section key on two screens, and a section is
     * addressed by its key alone - the two halves would fold as one and collide in the packing map. The
     * current adjustments cannot reach that cut, so this is a guard rather than a live branch; it exists
     * because the alternative is an invariant that only holds while nobody edits the numbers above.
     */
    private fun keepActionsWithFeatures(order: List<StarterSectionGroup>, cut: Int): Int =
        if (order.getOrNull(cut) == StarterSectionGroup.LAUNCHER_ACTIONS) cut + 1 else cut

    private fun scaleBudget(budget: Int, percent: Int): Int =
        (budget * percent / PERCENT_BASE).coerceAtLeast(MIN_BUDGET)

    /**
     * The base rule, stated for the medium-wide screen class the adjustments are relative to.
     *
     * The order is the one a desktop seeded before S2309: the device's own gadgets and resources lead,
     * the launcher's service groups follow, and the second-screen widget groups trail. Keeping it
     * identical is what makes this ticket a change of capacity rather than a silent redesign of what a
     * fresh desktop contains.
     */
    private fun baseRule() = Rule(
        sectionOrder = listOf(
            StarterSectionGroup.PROFILE_GADGETS,
            StarterSectionGroup.RESOURCES,
            StarterSectionGroup.APP_FUNCTIONS,
            StarterSectionGroup.LAUNCHER_ACTIONS,
            StarterSectionGroup.ANDROID_APPS,
            StarterSectionGroup.GOOGLE_APPS,
            StarterSectionGroup.UTILITY_WIDGETS,
            StarterSectionGroup.MEDIA_WINDOWS,
            StarterSectionGroup.STREAMS,
        ),
        itemBudget = defaultBudget(),
        firstScreenSections = DEFAULT_FIRST_SCREEN_SECTIONS,
        screenCount = DEFAULT_SCREEN_COUNT,
    )

    /**
     * [StarterSectionGroup.LAUNCHER_ACTIONS] deliberately has no entry: it is unbounded. Every item in it
     * is a way out of the launcher or into its settings, and a subset of those is not a smaller version
     * of the group, it is a desktop missing an exit.
     */
    private fun defaultBudget(): Map<StarterSectionGroup, Int> = mapOf(
        StarterSectionGroup.PROFILE_GADGETS to BUDGET_GADGETS,
        StarterSectionGroup.RESOURCES to BUDGET_RESOURCES,
        StarterSectionGroup.APP_FUNCTIONS to BUDGET_APP_FUNCTIONS,
        StarterSectionGroup.ANDROID_APPS to BUDGET_ANDROID_APPS,
        StarterSectionGroup.GOOGLE_APPS to BUDGET_GOOGLE_APPS,
        StarterSectionGroup.UTILITY_WIDGETS to BUDGET_UTILITY_WIDGETS,
        StarterSectionGroup.MEDIA_WINDOWS to BUDGET_MEDIA_WINDOWS,
        StarterSectionGroup.STREAMS to BUDGET_STREAMS,
    )
}
