package com.sza.fastmediasorter.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearAppTheme(
    content: @Composable () -> Unit
) {
    // MaterialTheme is still called without arguments: this adds a tone beside the Material palette
    // and does not replace it, so no existing screen changes colour (S2161).
    CompositionLocalProvider(LocalWearAppColors provides WearAppColors()) {
        MaterialTheme(
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
