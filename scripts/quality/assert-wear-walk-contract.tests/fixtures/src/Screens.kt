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
