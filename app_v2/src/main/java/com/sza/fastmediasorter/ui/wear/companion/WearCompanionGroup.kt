package com.sza.fastmediasorter.ui.wear.companion

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.viewinterop.AndroidView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import timber.log.Timber

/**
 * S2000: one collapsible group in the companion window, and the unit the window grows by.
 *
 * The scaffold knows nothing about which settings live inside it, which is what makes adding a
 * setting an edit inside one group rather than a rebuild of the window (strategic §5.1 pillars A
 * and B). The header is the [CollapsibleSectionHeader] the watch-settings section already used,
 * lifted here so every group looks and behaves the same rather than each inventing its own.
 *
 * S2865: [summary] is the one-line "what the watch holds now" line drawn under the title, in the
 * collapsed as well as the expanded state - the collapsed window had named the topic and said
 * nothing about its values. The header folds it into its own accessibility description, so it is
 * heard together with the title rather than as an orphan node.
 *
 * S2863: the header is the one control in this window drawn as a View rather than in Compose, and
 * the interop node Compose puts in the accessibility tree in its place is what a screen reader
 * actually meets - the description, click handler and title the [CollapsibleSectionHeader] sets on
 * its own row never cross that boundary. So the node's whole identity is declared here: the name,
 * the state, the button role and the click action. Before this it carried a state and nothing else,
 * which is an unnamed node no service can announce and no test can address - all four headers were
 * missing from a tree dump taken while they were on screen, so no group could be expanded with
 * TalkBack and the contents of a collapsed one were unreachable.
 */
@Composable
fun WearCompanionGroup(
    title: String,
    summary: String?,
    expanded: Boolean,
    tag: String,
    onExpandedChange: (Boolean) -> Unit,
    headerConfig: CompanionGroupHeader? = null,
    content: @Composable () -> Unit
) {
    val stateRes = if (expanded) {
        R.string.collapsible_section_state_expanded
    } else {
        R.string.collapsible_section_state_collapsed
    }
    val state = stringResource(stateRes)
    // Title and summary in the order CollapsibleSectionHeader.withSummary joins them, so the two
    // sides of the interop boundary announce one phrase rather than two arrangements of it.
    val description = if (summary.isNullOrBlank()) title else "$title, $summary"
    Timber.d("S2863: companion group header semantics declared - tag=$tag expanded=$expanded")

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = state
                role = Role.Button
                onClick {
                    onExpandedChange(!expanded)
                    true
                }
            },
        factory = { context -> CollapsibleSectionHeader(context).apply { setTitle(title) } },
        update = { header ->
            header.setTitle(title)
            header.setSummary(summary)
            header.setExpanded(expanded, notify = false)
            header.setOnExpandedChangeListener(onExpandedChange)
            if (headerConfig != null) {
                if (headerConfig.iconRes != null) {
                    header.setIcon(headerConfig.iconRes)
                }
                if (headerConfig.help != null) {
                    header.setHelp(headerConfig.help.titleRes, headerConfig.help.messageRes)
                }
            }
        }
    )

    if (expanded) {
        content()
    }
}
