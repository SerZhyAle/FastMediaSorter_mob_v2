package com.sza.fastmediasorter.domain.usecase.apps

import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryNameTakenException
import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.repository.AppCategoryRepository
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LaunchStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryUseCasesTest {

    private val repository = FakeAppCategoryRepository()
    private val seed = SeedAppCategoriesUseCase(repository, NoInstalledApps)

    @Test
    fun `seeding creates one category per system category and leaves undefined apps unassigned`() = runTest {
        seed.seed(listOf(app("a", 0), app("b", 0), app("c", 2), app("d", UNDEFINED)))

        val catalog = repository.state.value
        assertEquals(setOf("system:0", "system:2"), catalog.categories.map { it.id }.toSet())
        assertEquals(setOf("system:0"), catalog.categoryIdsOf("b"))
        assertTrue(catalog.categoryIdsOf("d").isEmpty())
        assertEquals(setOf("a", "b", "c", "d"), catalog.seededPackages)
    }

    @Test
    fun `seeding the same apps twice writes nothing the second time`() = runTest {
        val apps = listOf(app("a", 0))
        seed.seed(apps)
        val writes = repository.writes

        seed.seed(apps)

        assertEquals(writes, repository.writes)
    }

    @Test
    fun `an app the user cleared is not seeded again`() = runTest {
        seed.seed(listOf(app("a", 0)))
        SetAppCategoriesUseCase(repository)("a", emptySet())

        seed.seed(listOf(app("a", 0), app("new", 1)))

        assertTrue(repository.state.value.categoryIdsOf("a").isEmpty())
        assertEquals(setOf("system:1"), repository.state.value.categoryIdsOf("new"))
    }

    @Test
    fun `one app keeps several categories`() = runTest {
        val create = CreateAppCategoryUseCase(repository)
        val work = create("Work").getOrThrow()
        val chat = create("Messengers").getOrThrow()

        SetAppCategoriesUseCase(repository)("com.slack", setOf(work, chat, "user:missing"))

        assertEquals(setOf(work, chat), repository.state.value.categoryIdsOf("com.slack"))
    }

    @Test
    fun `creating an existing name in another case returns that category`() = runTest {
        val create = CreateAppCategoryUseCase(repository)
        val first = create("Work").getOrThrow()

        assertEquals(first, create(" work ").getOrThrow())
        assertEquals(1, repository.state.value.categories.size)
    }

    @Test
    fun `a blank name is refused`() = runTest {
        assertTrue(CreateAppCategoryUseCase(repository)("  ").isFailure)
    }

    @Test
    fun `moving swaps neighbours and ignores a move past the end`() = runTest {
        repository.state.value = AppCategoryCatalog.EMPTY.copy(
            categories = listOf(category("x"), category("y"), category("z")),
        )
        val move = MoveAppCategoryUseCase(repository)

        move("z", listOf("x", "y", "z"), -1)
        assertEquals(listOf("x", "z", "y"), repository.state.value.order)

        move("x", listOf("x", "z", "y"), -1)
        assertEquals(listOf("x", "z", "y"), repository.state.value.order)
    }

    @Test
    fun `renaming stores the trimmed name and keeps the id`() = runTest {
        val id = CreateAppCategoryUseCase(repository)("Work").getOrThrow()

        RenameAppCategoryUseCase(repository)(id, "  Office ", labelOf).getOrThrow()

        val renamed = repository.state.value.categories.single()
        assertEquals(id, renamed.id)
        assertEquals("Office", renamed.name)
    }

    @Test
    fun `a renamed seeded category keeps its system category and is not seeded twice`() = runTest {
        seed.seed(listOf(app("a", 0)))

        RenameAppCategoryUseCase(repository)("system:0", "Play", labelOf).getOrThrow()
        seed.seed(listOf(app("a", 0), app("b", 0)))

        val catalog = repository.state.value
        assertEquals(1, catalog.categories.size)
        assertEquals("Play", catalog.categories.single().name)
        assertEquals(0, catalog.categories.single().systemCategory)
        assertEquals(setOf("system:0"), catalog.categoryIdsOf("b"))
    }

    @Test
    fun `a name another category shows is refused in any letter case`() = runTest {
        seed.seed(listOf(app("a", 0)))
        val work = CreateAppCategoryUseCase(repository)("Work").getOrThrow()
        val before = repository.state.value

        val result = RenameAppCategoryUseCase(repository)(work, " games ", labelOf)

        assertTrue(result.exceptionOrNull() is AppCategoryNameTakenException)
        assertEquals(before, repository.state.value)
    }

    @Test
    fun `a category may take its own name in another letter case`() = runTest {
        val id = CreateAppCategoryUseCase(repository)("Work").getOrThrow()

        RenameAppCategoryUseCase(repository)(id, "WORK", labelOf).getOrThrow()

        assertEquals("WORK", repository.state.value.categories.single().name)
    }

    @Test
    fun `renaming to a blank name or an unknown id changes nothing`() = runTest {
        val id = CreateAppCategoryUseCase(repository)("Work").getOrThrow()
        val before = repository.state.value
        val rename = RenameAppCategoryUseCase(repository)

        assertTrue(rename(id, "  ", labelOf).isFailure)
        rename("user:missing", "Office", labelOf).getOrThrow()

        assertEquals(before, repository.state.value)
    }

    private val labelOf: (AppCategory) -> String = { it.name ?: SYSTEM_CATEGORY_LABEL }

    private fun category(id: String) = AppCategory(id = id, name = id, systemCategory = null)

    private fun app(packageName: String, category: Int) = InstalledApp(
        packageName = packageName,
        label = packageName,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        category = category,
        isSystemApp = false,
        iconFile = null,
    )

    private class FakeAppCategoryRepository : AppCategoryRepository {
        val state = MutableStateFlow(AppCategoryCatalog.EMPTY)
        var writes = 0

        override fun observeCatalog(): Flow<AppCategoryCatalog> = state

        override suspend fun update(
            transform: (AppCategoryCatalog) -> AppCategoryCatalog,
        ): Result<AppCategoryCatalog> {
            val next = transform(state.value)
            if (next != state.value) {
                writes++
                state.value = next
            }
            return Result.success(next)
        }
    }

    private object NoInstalledApps : InstalledAppsRepository {
        override fun observeApps(): Flow<List<InstalledApp>> = emptyFlow()
        override suspend fun replaceAll(apps: List<InstalledApp>) = Unit
        override suspend fun upsert(app: InstalledApp) = Unit
        override suspend fun remove(packageName: String) = Unit
        override suspend fun cachedCount(): Int = 0
        override suspend fun cachedFormatVersion(): Int? = null
        override fun observeLaunchStats(): Flow<Map<String, LaunchStats>> = emptyFlow()
        override suspend fun clearLaunchStats() = Unit
    }

    private companion object {
        const val UNDEFINED = -1

        /** What a seeded category, which stores no name, is drawn as in these tests. */
        const val SYSTEM_CATEGORY_LABEL = "Games"
    }
}
