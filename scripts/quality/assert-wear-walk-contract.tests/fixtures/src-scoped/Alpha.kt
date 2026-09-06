package fixture

// S2621 scoped-attribution fixture. The two screens live in SEPARATE files on purpose: the whole
// point of -ChangedFiles is telling a divergence in the presented set from one in a neighbour's
// file, and a single-file tree cannot express that difference.
//
// Neither file is named *Screen.kt, so the rename channel (screenSourceChanged) stays off unless a
// case deliberately passes such a path.
@Composable
fun AlphaScreen() {
    Text(stringResource(R.string.good_marker))
}
