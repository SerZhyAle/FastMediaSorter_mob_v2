package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GenerateUniqueCopyNameUseCaseTest {

    private val context = mockk<Context>()
    private lateinit var generate: GenerateUniqueCopyNameUseCase

    @Before
    fun setup() {
        every { context.getString(R.string.resource_copy_name, any()) } answers {
            "${formatArgs(args)[0]} (копия)"
        }
        every { context.getString(R.string.resource_copy_name_numbered, any(), any()) } answers {
            val format = formatArgs(args)
            "${format[0]} (копия ${format[1]})"
        }
        generate = GenerateUniqueCopyNameUseCase(context)
    }

    // MockK may hand the vararg format arguments over flattened or as one array, depending on the call site.
    private fun formatArgs(args: List<Any?>): List<Any?> =
        args.drop(1).flatMap { if (it is Array<*>) it.toList() else listOf(it) }

    @Test
    fun `suffix comes from the localized resource, then numbered variants`() {
        assertEquals("Album (копия)", generate("Album", emptySet()))
        assertEquals("Album (копия 1)", generate("Album", setOf("Album (копия)")))
        assertEquals("Album (копия 2)", generate("Album", setOf("Album (копия)", "Album (копия 1)")))
    }

    @Test
    fun `a name taken in another case is skipped`() {
        assertEquals("Album (копия 1)", generate("Album", setOf("album (КОПИЯ)")))
        assertEquals("Album (копия 2)", generate("Album", setOf("ALBUM (КОПИЯ)", " album (копия 1) ")))
    }

    @Test
    fun `blank source name falls back and the source is trimmed`() {
        assertEquals("Resource (копия)", generate("   ", emptySet()))
        assertEquals("Album (копия)", generate("  Album  ", emptySet()))
    }
}
