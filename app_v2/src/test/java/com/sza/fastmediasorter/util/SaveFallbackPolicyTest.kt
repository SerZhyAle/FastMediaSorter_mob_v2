package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory.PublicCollection.Kind
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFallbackPolicyTest {

    private fun resource(type: ResourceType) =
        MediaResource(id = 1, name = "dest", path = "/dest", type = type)

    @Test
    fun `null resource falls back with NoResourceConfigured`() {
        val decision = SaveFallbackPolicy.decide(null, MediaType.IMAGE, isResourceReachable = true)
        assertTrue(decision is SaveFallbackPolicy.Decision.UseLocalFallback)
        assertEquals(
            SaveFallbackReason.NoResourceConfigured,
            (decision as SaveFallbackPolicy.Decision.UseLocalFallback).reason
        )
    }

    @Test
    fun `local resource is always used regardless of reachability`() {
        val unreachable = SaveFallbackPolicy.decide(resource(ResourceType.LOCAL), MediaType.IMAGE, false)
        assertTrue(unreachable is SaveFallbackPolicy.Decision.UseResource)
    }

    @Test
    fun `reachable network resource is used`() {
        val decision = SaveFallbackPolicy.decide(resource(ResourceType.SMB), MediaType.VIDEO, true)
        assertTrue(decision is SaveFallbackPolicy.Decision.UseResource)
    }

    @Test
    fun `unreachable network resource falls back with ResourceUnavailable`() {
        val decision = SaveFallbackPolicy.decide(resource(ResourceType.CLOUD), MediaType.AUDIO, false)
        assertTrue(decision is SaveFallbackPolicy.Decision.UseLocalFallback)
        assertEquals(
            SaveFallbackReason.ResourceUnavailable,
            (decision as SaveFallbackPolicy.Decision.UseLocalFallback).reason
        )
    }

    @Test
    fun `media types map to expected public collections`() {
        assertEquals(Kind.IMAGES, SaveFallbackPolicy.fallbackCollection(MediaType.IMAGE).first)
        assertEquals(Kind.IMAGES, SaveFallbackPolicy.fallbackCollection(MediaType.GIF).first)
        assertEquals(Kind.VIDEO, SaveFallbackPolicy.fallbackCollection(MediaType.VIDEO).first)
        assertEquals(Kind.AUDIO, SaveFallbackPolicy.fallbackCollection(MediaType.AUDIO).first)
        assertEquals(Kind.DOWNLOADS, SaveFallbackPolicy.fallbackCollection(MediaType.PDF).first)
    }
}
