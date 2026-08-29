package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LaunchStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1401: the ordering and filtering rules of the all-apps screen, the one part of the ticket that is
 * pure logic and can therefore be settled without a device.
 */
class QueryAllAppsUseCaseTest {

    @Test
    fun `label order ignores case`() = runTest {
        val useCase = useCaseOf(app("b.pkg", "banana"), app("a.pkg", "Apple"), app("c.pkg", "cherry"))

        assertEquals(
            listOf("Apple", "banana", "cherry"),
            useCase.labelsFor(InstalledAppSortOrder.LABEL)
        )
    }

    @Test
    fun `descending flag reverses every order`() = runTest {
        val useCase = useCaseOf(
            app("a.pkg", "Apple", firstInstallTime = 100),
            app("b.pkg", "banana", firstInstallTime = 300),
            app("c.pkg", "cherry", firstInstallTime = 200)
        )

        for (order in InstalledAppSortOrder.entries) {
            val ascending = useCase(query = "", order = order, descending = false).first()
            val descending = useCase(query = "", order = order, descending = true).first()

            assertEquals("order $order", ascending.reversed(), descending)
        }
    }

    @Test
    fun `frequency order falls back to label order while no launch was recorded`() = runTest {
        val useCase = useCaseOf(app("b.pkg", "banana"), app("a.pkg", "Apple"))

        assertEquals(
            listOf("Apple", "banana"),
            useCase.labelsFor(InstalledAppSortOrder.LAUNCH_FREQUENCY)
        )
    }

    @Test
    fun `frequency order puts the most launched app first`() = runTest {
        val useCase = useCaseOf(
            apps = listOf(app("a.pkg", "Apple"), app("b.pkg", "banana"), app("c.pkg", "cherry")),
            stats = mapOf(
                statsKey("b.pkg") to LaunchStats(launchCount = 9, lastLaunchedAt = 10),
                statsKey("c.pkg") to LaunchStats(launchCount = 2, lastLaunchedAt = 99)
            )
        )

        assertEquals(
            listOf("banana", "cherry", "Apple"),
            useCase.labelsFor(InstalledAppSortOrder.LAUNCH_FREQUENCY)
        )
    }

    @Test
    fun `uncategorised apps land after every categorised one`() = runTest {
        val useCase = useCaseOf(
            app("a.pkg", "Apple", category = CATEGORY_UNDEFINED),
            app("b.pkg", "banana", category = 2),
            app("c.pkg", "cherry", category = 0)
        )

        assertEquals(
            listOf("cherry", "banana", "Apple"),
            useCase.labelsFor(InstalledAppSortOrder.CATEGORY)
        )
    }

    @Test
    fun `label groups keep the full list at all and bucket by first letter`() = runTest {
        val useCase = useCaseOf(
            app("z.pkg", "zebra"),
            app("b.pkg", "banana"),
            app("a.pkg", "Apple"),
            app("m.pkg", "mango"),
            app("c.pkg", "Cherry"),
        )

        val groups = useCase.groupedLabelsFor(InstalledAppSortOrder.LABEL)

        assertEquals(listOf("All", "A", "B", "C", "M", "Z"), groups.map { it.title })
        assertEquals(listOf("Apple", "banana", "Cherry", "mango", "zebra"), groups.first().items)
        assertEquals(listOf("Apple"), groups[1].items)
        assertEquals(listOf("banana"), groups[2].items)
        assertEquals(listOf("Cherry"), groups[3].items)
        assertEquals(listOf("mango"), groups[4].items)
        assertEquals(listOf("zebra"), groups[5].items)
    }

    @Test
    fun `query matches the label`() = runTest {
        val useCase = useCaseOf(app("a.pkg", "Apple"), app("b.pkg", "banana"))

        assertEquals(listOf("banana"), useCase.labelsFor(InstalledAppSortOrder.LABEL, query = "NAN"))
    }

    @Test
    fun `query matches the package name when the label does not`() = runTest {
        val useCase = useCaseOf(app("com.example.tools", "Apple"), app("b.pkg", "banana"))

        assertEquals(
            listOf("Apple"),
            useCase.labelsFor(InstalledAppSortOrder.LABEL, query = "example")
        )
    }

    @Test
    fun `ties break on the label so identical inputs never reshuffle`() = runTest {
        val useCase = useCaseOf(
            app("z.pkg", "zebra", firstInstallTime = 500),
            app("a.pkg", "Apple", firstInstallTime = 500),
            app("m.pkg", "mango", firstInstallTime = 500)
        )

        assertEquals(
            listOf("Apple", "mango", "zebra"),
            useCase.labelsFor(InstalledAppSortOrder.INSTALL_DATE)
        )
    }

    private suspend fun QueryAllAppsUseCase.labelsFor(
        order: InstalledAppSortOrder,
        query: String = ""
    ): List<String> = invoke(query, order, descending = false).first().map { it.label }

    private suspend fun QueryAllAppsUseCase.groupedLabelsFor(
        order: InstalledAppSortOrder,
        query: String = ""
    ): List<QueryAllAppsUseCase.AppGroup> = groupedApps(query, order, descending = false).first()

    private fun useCaseOf(vararg apps: InstalledApp) = useCaseOf(apps.toList(), emptyMap())

    private fun useCaseOf(
        apps: List<InstalledApp>,
        stats: Map<String, LaunchStats>
    ) = QueryAllAppsUseCase(FakeInstalledAppsRepository(apps, stats))

    private fun statsKey(packageName: String) = LauncherCellCommand.App(packageName).encode()

    private fun app(
        packageName: String,
        label: String,
        firstInstallTime: Long = 0,
        lastUpdateTime: Long = 0,
        category: Int = 0
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        category = category,
        isSystemApp = false,
        iconFile = null
    )

    private class FakeInstalledAppsRepository(
        private val apps: List<InstalledApp>,
        private val stats: Map<String, LaunchStats>
    ) : InstalledAppsRepository {

        override fun observeApps(): Flow<List<InstalledApp>> = flowOf(apps)

        override fun observeLaunchStats(): Flow<Map<String, LaunchStats>> = flowOf(stats)

        override suspend fun replaceAll(apps: List<InstalledApp>) = Unit

        override suspend fun upsert(app: InstalledApp) = Unit

        override suspend fun remove(packageName: String) = Unit

        override suspend fun cachedCount(): Int = apps.size

        override suspend fun cachedFormatVersion(): Int? = null

        override suspend fun clearLaunchStats() = Unit
    }

    private companion object {
        /** `ApplicationInfo.CATEGORY_UNDEFINED` - the framework constant is unavailable to a unit test. */
        const val CATEGORY_UNDEFINED = -1
    }
}
