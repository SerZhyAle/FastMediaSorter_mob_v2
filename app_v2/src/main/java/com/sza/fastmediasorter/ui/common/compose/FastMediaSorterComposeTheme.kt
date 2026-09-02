package com.sza.fastmediasorter.ui.common.compose

import android.content.Context
import androidx.annotation.AttrRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.google.android.material.color.MaterialColors
import androidx.appcompat.R.attr as AppCompatAttr
import com.google.android.material.R.attr as MaterialAttr

/**
 * Brightness is chosen from the resolved surface rather than from the system night mode, because the
 * accent overlays (S0569) set surface brightness independently of it - a light accent under system
 * dark mode would otherwise get a dark Compose baseline behind light attributes.
 */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * Hosts [content] under the View theme the surrounding Activity or Fragment already applied.
 *
 * A `setContent` block with no explicit color scheme falls back to the Material3 baseline palette,
 * which renders in stock purple and reads neither Theme.FastMediaSorter.App nor the accent overlay
 * the user picked (S0569) - the View widgets beside it stay themed, so the island looks foreign.
 * Every ComposeView in the app goes through this wrapper.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FastMediaSorterComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { context.resolveComposeColorScheme() }
    MaterialTheme(colorScheme = colorScheme) {
        // M3's MaterialTheme provides no LocalContentColor, and its composition-local default is a
        // hardcoded Color.Black - so any Text outside a Surface, Card or Scaffold renders black and
        // disappears on a dark background regardless of the scheme resolved above. Every island in
        // this app is hosted on a themed View surface, which makes onSurface the right default.
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            // S2096: every testTag below this point is published as an Android resource-id. Compose
            // emits no id of its own, so without this line the screen dumps as anonymous nodes and
            // adb.ps1 tap-id exits 8. Centralised here rather than per-screen after S2091 confirmed
            // the pattern works but needs repeating on each island.
            Box(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
                content()
            }
        }
    }
}

private fun Context.resolveComposeColorScheme(): ColorScheme {
    val surface = themeColor(MaterialAttr.colorSurface, Color.White)
    val base = if (surface.luminance() < DARK_SURFACE_LUMINANCE) darkColorScheme() else lightColorScheme()
    // M3 tints elevated surfaces with the primary role; no colorSurfaceTint attribute exists to read.
    val primary = themeColor(AppCompatAttr.colorPrimary, base.primary)
    return base.copy(
        primary = primary,
        onPrimary = themeColor(MaterialAttr.colorOnPrimary, base.onPrimary),
        primaryContainer = themeColor(MaterialAttr.colorPrimaryContainer, base.primaryContainer),
        onPrimaryContainer = themeColor(MaterialAttr.colorOnPrimaryContainer, base.onPrimaryContainer),
        inversePrimary = themeColor(MaterialAttr.colorPrimaryInverse, base.inversePrimary),
        secondary = themeColor(MaterialAttr.colorSecondary, base.secondary),
        onSecondary = themeColor(MaterialAttr.colorOnSecondary, base.onSecondary),
        secondaryContainer = themeColor(MaterialAttr.colorSecondaryContainer, base.secondaryContainer),
        onSecondaryContainer = themeColor(MaterialAttr.colorOnSecondaryContainer, base.onSecondaryContainer),
        tertiary = themeColor(MaterialAttr.colorTertiary, base.tertiary),
        onTertiary = themeColor(MaterialAttr.colorOnTertiary, base.onTertiary),
        tertiaryContainer = themeColor(MaterialAttr.colorTertiaryContainer, base.tertiaryContainer),
        onTertiaryContainer = themeColor(MaterialAttr.colorOnTertiaryContainer, base.onTertiaryContainer),
        background = themeColor(android.R.attr.colorBackground, base.background),
        onBackground = themeColor(MaterialAttr.colorOnBackground, base.onBackground),
        surface = surface,
        onSurface = themeColor(MaterialAttr.colorOnSurface, base.onSurface),
        surfaceVariant = themeColor(MaterialAttr.colorSurfaceVariant, base.surfaceVariant),
        onSurfaceVariant = themeColor(MaterialAttr.colorOnSurfaceVariant, base.onSurfaceVariant),
        surfaceTint = primary,
        inverseSurface = themeColor(MaterialAttr.colorSurfaceInverse, base.inverseSurface),
        inverseOnSurface = themeColor(MaterialAttr.colorOnSurfaceInverse, base.inverseOnSurface),
        error = themeColor(AppCompatAttr.colorError, base.error),
        onError = themeColor(MaterialAttr.colorOnError, base.onError),
        errorContainer = themeColor(MaterialAttr.colorErrorContainer, base.errorContainer),
        onErrorContainer = themeColor(MaterialAttr.colorOnErrorContainer, base.onErrorContainer),
        outline = themeColor(MaterialAttr.colorOutline, base.outline),
        outlineVariant = themeColor(MaterialAttr.colorOutlineVariant, base.outlineVariant),
        surfaceBright = themeColor(MaterialAttr.colorSurfaceBright, base.surfaceBright),
        surfaceDim = themeColor(MaterialAttr.colorSurfaceDim, base.surfaceDim),
        surfaceContainer = themeColor(MaterialAttr.colorSurfaceContainer, base.surfaceContainer),
        surfaceContainerHigh = themeColor(MaterialAttr.colorSurfaceContainerHigh, base.surfaceContainerHigh),
        surfaceContainerHighest = themeColor(MaterialAttr.colorSurfaceContainerHighest, base.surfaceContainerHighest),
        surfaceContainerLow = themeColor(MaterialAttr.colorSurfaceContainerLow, base.surfaceContainerLow),
        surfaceContainerLowest = themeColor(MaterialAttr.colorSurfaceContainerLowest, base.surfaceContainerLowest),
    )
}

private fun Context.themeColor(@AttrRes attr: Int, fallback: Color): Color =
    Color(MaterialColors.getColor(this, attr, fallback.toArgb()))
