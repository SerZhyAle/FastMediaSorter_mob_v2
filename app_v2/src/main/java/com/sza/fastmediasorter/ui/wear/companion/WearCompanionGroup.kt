package com.sza.fastmediasorter.ui.wear.companion

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader

/**
 * S2000: one collapsible group in the companion window, and the unit the window grows by.
 *
 * The scaffold knows nothing about which settings live inside it, which is what makes adding a
 * setting an edit inside one group rather than a rebuild of the window (strategic §5.1 pillars A
 * and B). The header is the [CollapsibleSectionHeader] the watch-settings section already used,
 * lifted here so every group looks and behaves the same rather than each inventing its own.
 *
 * The state is announced to accessibility services as well as drawn: an arrow that only rotates
 * leaves a screen-reader user with no way to tell a collapsed group from an empty one
 * (strategic §3.2 "Доступность").
 */
@Composable
fun WearCompanionGroup(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val stateRes = if (expanded) {
        R.string.collapsible_section_state_expanded
    } else {
        R.string.collapsible_section_state_collapsed
    }
    val state = stringResource(stateRes)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = state },
        factory = { context -> CollapsibleSectionHeader(context).apply { setTitle(title) } },
        update = { header ->
            header.setTitle(title)
            header.setExpanded(expanded, notify = false)
            header.setOnExpandedChangeListener(onExpandedChange)
        }
    )

    if (expanded) {
        content()
    }
}
