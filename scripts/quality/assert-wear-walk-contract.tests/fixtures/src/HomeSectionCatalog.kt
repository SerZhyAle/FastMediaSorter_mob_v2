package fixture

// S4210: the catalog shape the phone check reads. RESOURCES is added under a helper whose body reads
// the companion state, the way the module writes it; APPS is added under no condition at all, so a
// paired-phone marker on it is the declaration the gate must refuse.
object HomeSectionCatalog {

    fun sectionsFor(visibility: HomeSectionVisibility): List<HomeSection> = buildList {
        if (visibility.showsResources()) {
            add(
                HomeSection(
                    id = HomeSectionId.RESOURCES,
                    labelRes = R.string.good_marker
                )
            )
        }
        add(
            HomeSection(
                id = HomeSectionId.APPS,
                labelRes = R.string.good_marker
            )
        )
    }

    private fun HomeSectionVisibility.showsResources(): Boolean =
        offersRemoteSources && phoneCompanion == PhoneCompanionState.PRESENT
}
