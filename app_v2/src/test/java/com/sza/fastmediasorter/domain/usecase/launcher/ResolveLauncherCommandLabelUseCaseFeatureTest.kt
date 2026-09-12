package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.SubProgramAccentCatalog
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2889: covers the feature branch, which decides whether a desktop cell wears its sub-program's colour.
 *
 * The branch had no test of its own while the contact and stream branches did, and the failure it can
 * produce is silent in both directions: a sub-program cell with no tone looks like the old behaviour, and a
 * tone leaking onto an app or contact cell looks like a theme quirk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveLauncherCommandLabelUseCaseFeatureTest {

    private val useCase get() = ResolveLauncherCommandLabelUseCase(
        context = RuntimeEnvironment.getApplication(),
        resourceRepository = mockk(relaxed = true),
        streamSourceRepository = mockk(relaxed = true),
        scheduledOperationRepository = mockk(relaxed = true),
        resourceIconProvider = mockk(relaxed = true),
        appShortcutDataSource = mockk(relaxed = true),
        liveContactDataSource = mockk(relaxed = true),
        faviconAtlasStore = mockk(relaxed = true),
    )

    @Test
    fun `a feature cell carries the accent the catalog gives its route`() = runTest {
        val routeKey = InternalRouteCatalog.KEY_CALCULATOR

        val visual = useCase(LauncherCellCommand.Feature(routeKey))

        assertNotNull(visual)
        assertEquals(SubProgramAccentCatalog.accentFor(routeKey), visual?.accentRes)
    }

    @Test
    fun `a feature section carries the same accent as the plain feature`() = runTest {
        val routeKey = InternalRouteCatalog.KEY_CALCULATOR

        val section = useCase(LauncherCellCommand.FeatureSection(routeKey, sectionKey = "programs"))

        assertEquals(SubProgramAccentCatalog.accentFor(routeKey), section?.accentRes)
    }

    @Test
    fun `every route the launcher can seed resolves to a non-null accent`() = runTest {
        val missing = InternalRouteCatalog.all()
            .map { it.key }
            .filter { useCase(LauncherCellCommand.Feature(it))?.accentRes == null }

        assertEquals("feature cells resolving with no accent: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `an app cell carries no accent`() = runTest {
        val visual = useCase(LauncherCellCommand.App("com.example.absent"))

        assertNull(visual?.accentRes)
    }
}
