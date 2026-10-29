package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.BroadcastSettings
import com.sza.fastmediasorter.domain.repository.BroadcastLinkPasswordStore
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetBroadcastLinkPasswordUseCaseTest {

    private val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
    private val settings = FakeSettingsRepository(
        AppSettings(broadcast = BroadcastSettings(linkPasswordMode = BroadcastLinkPasswordMode.AUTO)),
    )
    private val useCase = SetBroadcastLinkPasswordUseCase(
        settings,
        mockk<BroadcastLinkPasswordStore>(relaxed = true),
        credentials,
    )

    @Test
    fun `with no live broadcast the dialog opens on the remembered mode`() = runTest {
        assertEquals(BroadcastLinkPasswordMode.AUTO, useCase.currentMode())
    }

    @Test
    fun `a live open link wins over a remembered automatic mode`() = runTest {
        credentials.beginPrepared("b1", BroadcastLinkChoice.Open, link = null)

        assertEquals(BroadcastLinkPasswordMode.NONE, useCase.currentMode())
    }

    @Test
    fun `a live generated link wins over a remembered none mode`() = runTest {
        settings.setSettings(
            AppSettings(broadcast = BroadcastSettings(linkPasswordMode = BroadcastLinkPasswordMode.NONE)),
        )
        credentials.begin("b1", BroadcastLinkChoice.Generated)

        assertEquals(BroadcastLinkPasswordMode.AUTO, useCase.currentMode())
    }

    @Test
    fun `a live custom link reads as custom and the remembered mode returns once it ends`() = runTest {
        credentials.begin("b1", BroadcastLinkChoice.Custom("secret"))
        assertEquals(BroadcastLinkPasswordMode.CUSTOM, useCase.currentMode())

        credentials.end()

        assertEquals(BroadcastLinkPasswordMode.AUTO, useCase.currentMode())
    }
}
