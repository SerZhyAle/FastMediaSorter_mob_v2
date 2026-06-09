package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import com.sza.fastmediasorter.domain.delivery.PayloadFile

/**
 * App-pinned download descriptors for the OSS / first-party deliverable sets (S0386 Phase 05).
 *
 * URLs point at our GitHub mirror release; the SHA-256 + minSize are compiled in as the integrity
 * anchors (strategic §6.1 B2), so a tampered mirror cannot substitute a payload. The version-keyed
 * remote manifest may override only the URLs at runtime ([DeliveryManifestDataSource]), never these
 * hashes. Flavor DI modules pick which of these descriptors their flavor actually delivers.
 */
object DeliverableDescriptorCatalog {

    private const val MIRROR =
        "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1"

    /**
     * Set C - audio-player background videos. Pure resource (no `System.load`); the SHA-256 is still
     * pinned because the bytes never change, so corruption/substitution is caught before use.
     */
    fun audioVisualizations(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.AUDIO_VISUALIZATIONS,
        files = listOf(
            payload("anim_audio_bg_1.mp4", "3945177f696510aa66112775e6ff31d1453d6b8d85a97fb45bbc2ad06f552bd1", 806_870L),
            payload("anim_audio_bg_2.mp4", "3b00a46540944ff344e17cc4e68514c72b4a74b5c077dc821b511f4db0b5295f", 1_173_098L),
            payload("anim_audio_bg_3.mp4", "7d3908bc9a16acf9684765d7c2fd6b82f451fc37fb8d8ef7ceee9a07b282fd22", 1_510_287L),
            payload("anim_audio_bg_4.mp4", "64608c49340b736739266c5cd5d9331f236f659a48c359005bc07908829e5376", 1_076_000L),
            payload("anim_audio_bg_5.mp4", "5fa53ca3f8429358a802d2ae19800de94d895a5fee3c211798fefdcf4546408b", 1_785_603L)
        )
    )

    private fun payload(name: String, sha256: String, minSize: Long): PayloadFile =
        PayloadFile(fileName = name, sources = listOf("$MIRROR/$name"), sha256 = sha256, minSize = minSize)
}
