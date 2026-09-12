package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.FakeWearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearNodeDescriptor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetWatchDisplayNameUseCaseTest {

    @Test
    fun `the model code in parentheses is stripped`() = runTest {
        val name = resolve(localNodeNamed("Galaxy Watch7 (8CRZ)"))

        assertEquals("Galaxy Watch7", name)
    }

    @Test
    fun `a name without a parenthetical passes through`() = runTest {
        val name = resolve(localNodeNamed("Galaxy Watch7"))

        assertEquals("Galaxy Watch7", name)
    }

    /** An empty caption is worse than a technical one, so a name that is only a code keeps it. */
    @Test
    fun `a name that is nothing but a model code keeps it`() = runTest {
        val name = resolve(localNodeNamed("(8CRZ)"))

        assertEquals("(8CRZ)", name)
    }

    @Test
    fun `a silent bridge falls back to the model`() = runTest {
        val source = FakeWearSystemInfoDataSource().apply {
            local = null
            model = "SM-L310"
        }

        assertEquals("SM-L310", resolve(source))
    }

    @Test
    fun `a blank node name falls back to the model`() = runTest {
        val source = localNodeNamed("   ").apply { model = "SM-L310" }

        assertEquals("SM-L310", resolve(source))
    }

    /** Neither half answered: the caption is still owed, so it is a word rather than nothing. */
    @Test
    fun `neither the bridge nor the platform names the device`() = runTest {
        val source = FakeWearSystemInfoDataSource().apply {
            local = null
            model = null
        }

        assertEquals("Watch", resolve(source))
    }

    private suspend fun resolve(source: FakeWearSystemInfoDataSource): String =
        GetWatchDisplayNameUseCase(source).invoke()

    private fun localNodeNamed(displayName: String) = FakeWearSystemInfoDataSource().apply {
        local = WearNodeDescriptor(id = "w1", displayName = displayName, isNearby = true)
    }
}
