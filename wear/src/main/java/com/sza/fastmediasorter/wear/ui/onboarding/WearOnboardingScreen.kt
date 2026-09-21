package com.sza.fastmediasorter.wear.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPermissionStep
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPlannedStep
import com.sza.fastmediasorter.wear.ui.common.StandardWearChip
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_NO_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * First-run walk: a welcome page, then - only when this build has something to ask - a page that says
 * what is about to happen, then one page per permission group, each followed by its own system request.
 *
 * Every answer advances, a refusal included: the feature behind a refused group asks again when it is
 * opened, so refusing here costs nothing and must not trap the user on the page (strategic §2).
 *
 * "Skip all" ends the whole walk at once and marks it done, for the same reason: nothing asked here is
 * final, so a user who wants the app now loses nothing by leaving every group unanswered (S3225).
 */
@Composable
fun WearOnboardingScreen(
    steps: List<WearOnboardingPlannedStep>,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val introPages = if (steps.isEmpty()) WELCOME_ONLY else WELCOME_AND_INTRO
    val pageCount = introPages + steps.size
    // Saveable: the system request is another window, and a configuration change behind it must not
    // restart the walk from the welcome page.
    var page by rememberSaveable { mutableIntStateOf(0) }
    val advance = {
        if (page + 1 >= pageCount) onFinished() else page += 1
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Timber.i("Onboarding permission answer: %s", results)
        advance()
    }
    val step = if (page >= introPages) steps.getOrNull(page - introPages) else null

    // A group granted earlier - a reinstall that kept runtime grants - is not asked again.
    LaunchedEffect(page) {
        if (step != null && step.permissions.all { context.isGranted(it) }) advance()
    }

    key(page) {
        OnboardingPage {
            when {
                page == 0 -> welcomeItems(onNext = advance)
                step == null -> introItems(onStart = advance, onSkipAll = onFinished)
                else -> stepItems(
                    step = step.step,
                    stepNumber = page - introPages + 1,
                    stepCount = steps.size,
                    onAllow = { launcher.launch(step.permissions.toTypedArray()) },
                    onSkip = advance,
                    onSkipAll = onFinished
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OnboardingPage(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            // The walk is drawn before the navigation root that opts the rest of the app in.
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
            state = listState,
            centered = true,
            content = content
        )
    }
}

private fun ScalingLazyListScope.welcomeItems(onNext: () -> Unit) {
    item {
        // AsyncImage for the adaptive launcher icon, for the reason given in BrandFrameScreen.
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE)
        )
    }
    titleAndBody(R.string.wear_onboarding_welcome_title, R.string.wear_onboarding_welcome_body)
    item {
        Text(
            text = stringResource(R.string.wear_onboarding_slogan),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_onboarding_next),
            onClick = onNext,
            modifier = Modifier.testTag(WearTestTags.WEAR_ONBOARDING_FORWARD)
        )
    }
}

private fun ScalingLazyListScope.introItems(onStart: () -> Unit, onSkipAll: () -> Unit) {
    titleAndBody(R.string.wear_onboarding_intro_title, R.string.wear_onboarding_intro_body)
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_onboarding_start),
            onClick = onStart,
            modifier = Modifier.testTag(WearTestTags.WEAR_ONBOARDING_FORWARD)
        )
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_onboarding_skip_all),
            onClick = onSkipAll,
            primary = false
        )
    }
}

private fun ScalingLazyListScope.stepItems(
    step: WearOnboardingPermissionStep,
    stepNumber: Int,
    stepCount: Int,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
) {
    val look = step.look()
    item {
        Text(
            text = stringResource(R.string.wear_onboarding_page_indicator, stepNumber, stepCount),
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Icon(
            imageVector = look.icon,
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
            tint = MaterialTheme.colors.onBackground
        )
    }
    titleAndBody(look.title, look.reason)
    item {
        StandardWearChip(label = stringResource(R.string.wear_onboarding_allow), onClick = onAllow)
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_onboarding_skip),
            onClick = onSkip,
            modifier = Modifier.testTag(WearTestTags.WEAR_ONBOARDING_FORWARD),
            primary = false
        )
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_onboarding_skip_all),
            onClick = onSkipAll,
            primary = false
        )
    }
}

private fun ScalingLazyListScope.titleAndBody(@StringRes title: Int, @StringRes body: Int) {
    item {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class StepLook(val icon: ImageVector, @StringRes val title: Int, @StringRes val reason: Int)

private fun WearOnboardingPermissionStep.look(): StepLook = when (this) {
    WearOnboardingPermissionStep.MEDIA -> StepLook(
        Icons.Default.FolderOpen,
        R.string.wear_onboarding_media_title,
        R.string.wear_onboarding_media_reason
    )
    WearOnboardingPermissionStep.MICROPHONE -> StepLook(
        Icons.Default.Mic,
        R.string.wear_onboarding_microphone_title,
        R.string.wear_onboarding_microphone_reason
    )
    WearOnboardingPermissionStep.NOTIFICATIONS -> StepLook(
        Icons.Default.Notifications,
        R.string.wear_onboarding_notifications_title,
        R.string.wear_onboarding_notifications_reason
    )
    WearOnboardingPermissionStep.HEART_RATE -> StepLook(
        Icons.Default.Favorite,
        R.string.wear_onboarding_heart_rate_title,
        R.string.wear_onboarding_heart_rate_reason
    )
    WearOnboardingPermissionStep.ACTIVITY -> StepLook(
        Icons.AutoMirrored.Filled.DirectionsWalk,
        R.string.wear_onboarding_activity_title,
        R.string.wear_onboarding_activity_reason
    )
    WearOnboardingPermissionStep.NEARBY_DEVICES -> StepLook(
        Icons.Default.Bluetooth,
        R.string.wear_onboarding_nearby_title,
        R.string.wear_onboarding_nearby_reason
    )
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private const val WELCOME_ONLY = 1
private const val WELCOME_AND_INTRO = 2
private val ICON_SIZE = 48.dp

/**
 * What the host hands the first-run gate: whether the walk is due, how to plan it, how to record it
 * as done, and how to read media access - before the walk and again once it is over.
 */
data class WearOnboardingEntry(
    val needed: Flow<Boolean>,
    val steps: () -> List<WearOnboardingPlannedStep>,
    val onFinished: () -> Unit,
    val hasMediaAccess: () -> Boolean,
    /**
     * S3362: whether this build reaches the user's media at all.
     *
     * The welcome page has exactly one thing to say, and `R.string.wear_onboarding_welcome_body`
     * promises browsing, playing and sorting media on the wrist. A build that offers none
     * of that and asks for no permission either would open on a promise it cannot keep - the class
     * of defect Play refused an earlier watch build for. Carried as a capability answer rather than
     * a flavor name so nothing on this path learns which build it is (CLAUDE.md Rule 14).
     */
    val offersMediaAccess: Boolean,
)
