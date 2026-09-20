package fixture

// AlphaScreen renders good_marker. manifest_only is deliberately referenced by nothing here:
// that is the shape of a string alive in resources but named only by the manifest.
@Composable
fun AlphaScreen() {
    Text(stringResource(R.string.good_marker))
}

@Composable
fun BetaScreen() {
}

// S3358: the two catalogs' id sets, so a fixture entry's `homeSection` / `wearApp` is judged against
// a real enum rather than against an empty one. Shapes copied from the module: HomeSectionId is a
// plain enum, WearAppId carries a constructor argument, and both are read by the same extractor.
enum class HomeSectionId {
    RESOURCES,

    /**
     * A KDoc between members, which is what the module has and what the extractor must pass over.
     */
    APPS
}

enum class WearAppId(val canonicalKey: String) {
    CALCULATOR("calculator"),
    STOPWATCH("stopwatch")
}
