package com.sza.fastmediasorter.domain.usecase

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.sza.fastmediasorter.domain.model.TranslationModelPrewarmStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/**
 * A cancelled prewarm must propagate cancellation, not record [TranslationModelPrewarmStatus.Failed].
 * ML Kit is mocked: the model-presence task never completes, so the call suspends until cancelled.
 */
class PrewarmTranslationModelUseCaseTest {

    private val modelManager = mockk<RemoteModelManager>()

    @Before
    fun setUp() {
        mockkStatic(RemoteModelManager::class)
        every { RemoteModelManager.getInstance() } returns modelManager
        mockkConstructor(TranslateRemoteModel.Builder::class)
        every { anyConstructed<TranslateRemoteModel.Builder>().build() } returns mockk()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cancelled prewarm does not report Failed`() = runBlocking {
        val suspended = CompletableDeferred<Unit>()
        val pending = mockk<Task<Boolean>>(relaxed = true)
        every { pending.isComplete } returns false
        every { pending.addOnCompleteListener(any<Executor>(), any<OnCompleteListener<Boolean>>()) } answers {
            suspended.complete(Unit)
            pending
        }
        every { modelManager.isModelDownloaded(any()) } returns pending
        val useCase = PrewarmTranslationModelUseCase(setOf("mlkit"))

        val job = launch(Dispatchers.Default) { useCase.prewarm("de") }
        suspended.await()
        job.cancelAndJoin()

        assertEquals(TranslationModelPrewarmStatus.Idle, useCase.status.value)
    }
}
