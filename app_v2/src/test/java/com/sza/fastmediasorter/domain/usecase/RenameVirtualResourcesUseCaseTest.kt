package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.res.Configuration
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * S2627: the rewrite rule decides whether a folder name the USER typed survives a language switch,
 * and it had carried that decision untested since S1265 - so the fast path added here had nothing
 * to catch a regression in it.
 *
 * No expected string is written literally. Each case reads its value from the same string resources
 * the production code reads, because a hardcoded table that drifted from strings.xml is the exact
 * failure S1265's own KDoc records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "uk")
class RenameVirtualResourcesUseCaseTest {

    private lateinit var context: Context
    private lateinit var repository: ResourceRepository
    private lateinit var useCase: RenameVirtualResourcesUseCase
    private lateinit var originalDefault: Locale

    /** The language the pass will rewrite TO, resolved exactly as the production code resolves it. */
    private lateinit var currentLang: String

    /** Any declared language that is not [currentLang] - the one a stale record is seeded from. */
    private lateinit var otherLang: String

    @Before
    fun setUp() {
        originalDefault = Locale.getDefault()
        context = RuntimeEnvironment.getApplication()
        LocaleHelper.resetLanguage(context)
        repository = mockk(relaxed = true)
        useCase = RenameVirtualResourcesUseCase(context, repository)
        currentLang = LocaleHelper.getLanguage(context)
        otherLang = if (currentLang == "ru") "uk" else "ru"
    }

    @After
    fun tearDown() {
        // The default is process-global: leaving a foreign one behind would decide a neighbour's result.
        Locale.setDefault(originalDefault)
        LocaleHelper.resetLanguage(context)
    }

    @Test
    fun `a name left in another language is rewritten to the current one`() = runTest {
        val staleName = stringFor(R.string.virtual_all_music, otherLang)
        val staleComment = stringFor(R.string.virtual_comment_all_music, otherLang)
        givenResources(musicResource(name = staleName, comment = staleComment))

        useCase()

        val expectedName = stringFor(R.string.virtual_all_music, currentLang)
        val expectedComment = stringFor(R.string.virtual_comment_all_music, currentLang)
        coVerify(exactly = 1) {
            repository.updateResource(
                match { it.name == expectedName && it.comment == expectedComment }
            )
        }
    }

    @Test
    fun `a name the user typed is left untouched`() = runTest {
        givenResources(
            musicResource(
                name = "Roadtrip tapes",
                comment = stringFor(R.string.virtual_comment_all_music, currentLang)
            )
        )

        useCase()

        coVerify(exactly = 0) { repository.updateResource(any()) }
    }

    @Test
    fun `a record already in the current language is not written back`() = runTest {
        givenResources(
            musicResource(
                name = stringFor(R.string.virtual_all_music, currentLang),
                comment = stringFor(R.string.virtual_comment_all_music, currentLang)
            )
        )

        useCase()

        coVerify(exactly = 0) { repository.updateResource(any()) }
    }

    private fun givenResources(vararg resources: MediaResource) {
        coEvery { repository.getAllResourcesSync() } returns resources.toList()
    }

    private fun musicResource(name: String, comment: String) = MediaResource(
        id = 1L,
        name = name,
        comment = comment,
        path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
        type = ResourceType.LOCAL
    )

    private fun stringFor(resId: Int, languageTag: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return context.createConfigurationContext(configuration).getString(resId)
    }
}
