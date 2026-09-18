package com.sza.fastmediasorter.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.domain.model.WearColorScheme

@Composable
fun WearAppTheme(
    scheme: WearColorScheme = WearColorScheme.DEFAULT,
    content: @Composable () -> Unit
) {
    // S2522: the palette is the single seam a scheme reaches the app through - no screen holds a colour
    // literal, so every screen repaints from this one argument. The default keeps a call site that does
    // not choose a scheme rendering exactly what it rendered before the setting existed.
    val palette = paletteFor(scheme)
    CompositionLocalProvider(
        LocalWearAppColors provides WearAppColors(isLight = scheme.isLight),
        // Wear Compose's MaterialTheme does not provide LocalContentColor and its own default is a
        // hardcoded white, so a Text that names no colour ignores the palette entirely. That matched
        // the right answer while the app was dark-only and stops at the first light scheme, where the
        // white lands on a white surface. Under DARK this provides Color.White - the same value the
        // library default already produced - so an owner who never opens the setting sees no change.
        LocalContentColor provides palette.onBackground
    ) {
        MaterialTheme(
            colors = palette,
            typography = WearAppTypography,
            content = content
        )
    }
}

/** Reads the app's own tones. Shaped like `MaterialTheme` so a call site reads the same way. */
object WearAppTheme {

    val colors: WearAppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWearAppColors.current
}
