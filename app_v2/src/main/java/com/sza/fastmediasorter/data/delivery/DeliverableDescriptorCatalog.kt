package com.sza.fastmediasorter.data.delivery

import android.os.Build
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
 *
 * Native sets are ABI-specific: each hosted `.so` is published as `<abi>-<soname>` and pinned with
 * its own SHA-256/size per ABI (`temp/s0386_so_table.txt`). The descriptor is resolved for the
 * device's [primaryAbi] at injection time.
 */
object DeliverableDescriptorCatalog {

    private const val MIRROR =
        "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1"

    /** A single native library variant: its on-device name and the app-pinned integrity anchors. */
    private data class NativeLib(val soName: String, val sha256: String, val size: Long)

    /** ABIs we host payloads for, in descriptor preference order. */
    private val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    /**
     * Set B - Tesseract OCR stack (loaded in dependency order: jpeg/pngx → leptonica → tesseract).
     * Shipped on every OCR flavor (standard/legacy/noLegal/vr).
     */
    private val TESSERACT: Map<String, List<NativeLib>> = mapOf(
        "arm64-v8a" to listOf(
            NativeLib("libjpeg.so", "6add480209d2eaf049aaaaea5286ffe0f3b22f60a0efdfe54acc646b279673e3", 251_888L),
            NativeLib("libpngx.so", "8b9536ff4a59d9457bc2ec78c9b5356b795f50198b60b20df26fbc08addbd837", 231_856L),
            NativeLib("libleptonica.so", "52dbf89fc2d66865e1705ca57c8c1be7df6ab6e26e8c86cac5f083e9f5bd2ab4", 2_878_240L),
            NativeLib("libtesseract.so", "427fadce222815b2ea9d93e16957e899ad960cfe79c5a05060c18007ad0842d5", 4_152_872L)
        ),
        "armeabi-v7a" to listOf(
            NativeLib("libjpeg.so", "c8d539d61e4836866f0486ad402dfa9c65cb67ea55da98f87ed519f248c321c2", 187_096L),
            NativeLib("libpngx.so", "d5c0a6b68597c1da439a3e7f6e9c8cda96e91dc3c7fedae0bb6a1f14d474cc75", 172_204L),
            NativeLib("libleptonica.so", "86f000fe22012a97fbb58803eec4ccc3d419517b40b79c9779023ca886705d3c", 2_160_740L),
            NativeLib("libtesseract.so", "20c38126314970d70d68070ac3187054ae747732f0406b201dd56fd2b247a0a5", 3_002_448L)
        ),
        "x86" to listOf(
            NativeLib("libjpeg.so", "bea6c2d72aad67f659387e0c2b16c6d64f3a7e3a568f61336d68c308c8929216", 271_936L),
            NativeLib("libpngx.so", "b66f78ee033c8249b999a0ee290983903888556120d47caae091ca73865b8d9e", 246_360L),
            NativeLib("libleptonica.so", "be2729f055468d731b4f1c8a84d858e35fdb530e3652172c4b9c81a6a878815a", 2_969_648L),
            NativeLib("libtesseract.so", "b9b86e754cf280d074a91d9040b94b4f0404324e9fe14960df93edc0ef5fbb93", 4_323_340L)
        ),
        "x86_64" to listOf(
            NativeLib("libjpeg.so", "a89d41b688f471e408538e41b971e78652840a7a6a8aa0d41f19c224e014c6af", 281_728L),
            NativeLib("libpngx.so", "ac60e5a9864a22e929462c287fb591008a30dc9f7cb72a4abfd6cf96b75d420a", 243_592L),
            NativeLib("libleptonica.so", "7a6cbec999a94751cc0a1671159184724f0ae6bd2bce1b19bd3d784f33dd4729", 3_104_384L),
            NativeLib("libtesseract.so", "051671717680f6b6beb6d7b68afe47d0ef438adc8ecb182e296468d728639f9b", 4_334_632L)
        )
    )

    /**
     * Set B extra for noLegal - PaddleOCR (light_api_shared → lite_jni). Hosted for arm64-v8a only;
     * a noLegal x86_64 emulator slice gets Tesseract only (still a working OCR engine).
     */
    private val PADDLE: Map<String, List<NativeLib>> = mapOf(
        "arm64-v8a" to listOf(
            NativeLib("libpaddle_light_api_shared.so", "12a8779e1817d9165d34a8487678a4d226970b6e38f66c316034a287512d8e01", 5_011_432L),
            NativeLib("libpaddle_lite_jni.so", "97979c19ae2e457ba6d1c33ca45c1623caf8d395886be8d2e439a0197de879d3", 5_027_816L)
        )
    )

    /** Set D - FFmpeg DTS decoder (single `.so`, loaded by media3's FfmpegLibrary). */
    private val FFMPEG: Map<String, List<NativeLib>> = mapOf(
        "arm64-v8a" to listOf(NativeLib("libffmpegJNI.so", "b72f9a940cfb7ad5efe484603e187b0db60ccb240381b3c4340403b33f369e6a", 7_675_704L)),
        "armeabi-v7a" to listOf(NativeLib("libffmpegJNI.so", "94ccbe9d0c4cf911a9c42ddd70b5651e75d8e06a859a1480fa63178870d8a358", 6_867_888L)),
        "x86" to listOf(NativeLib("libffmpegJNI.so", "5802117cbabb22a375e51c2da26496c101fa80a898d066d031cb9b17f64b26f3", 6_610_888L)),
        "x86_64" to listOf(NativeLib("libffmpegJNI.so", "c85a80c531e9740a06d4c80800cb3f554d8f79a4d101a7103bb660f99429f5d1", 7_583_424L))
    )

    /**
     * Set C - audio-player background videos. Pure resource (no `System.load`); the SHA-256 is still
     * pinned because the bytes never change, so corruption/substitution is caught before use.
     */
    fun audioVisualizations(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.AUDIO_VISUALIZATIONS,
        files = listOf(
            resource("anim_audio_bg_1.mp4", "3945177f696510aa66112775e6ff31d1453d6b8d85a97fb45bbc2ad06f552bd1", 806_870L),
            resource("anim_audio_bg_2.mp4", "3b00a46540944ff344e17cc4e68514c72b4a74b5c077dc821b511f4db0b5295f", 1_173_098L),
            resource("anim_audio_bg_3.mp4", "7d3908bc9a16acf9684765d7c2fd6b82f451fc37fb8d8ef7ceee9a07b282fd22", 1_510_287L),
            resource("anim_audio_bg_4.mp4", "64608c49340b736739266c5cd5d9331f236f659a48c359005bc07908829e5376", 1_076_000L),
            resource("anim_audio_bg_5.mp4", "5fa53ca3f8429358a802d2ae19800de94d895a5fee3c211798fefdcf4546408b", 1_785_603L)
        )
    )

    /** Set B for store flavors (standard/legacy): Tesseract only. */
    fun ocrEnginesStore(abi: String = primaryAbi()): DeliverableSourceDescriptor =
        nativeDescriptor(DeliverableSet.OCR_ENGINES, abi, TESSERACT[abi].orEmpty())

    /** Set B for sideload/VR flavors (noLegal/vr): Tesseract + PaddleOCR (Paddle on arm64 only). */
    fun ocrEnginesNoLegal(abi: String = primaryAbi()): DeliverableSourceDescriptor =
        nativeDescriptor(DeliverableSet.OCR_ENGINES, abi, TESSERACT[abi].orEmpty() + PADDLE[abi].orEmpty())

    /** Set D for store/sideload/VR flavors that ship the DTS decoder. */
    fun ffmpegDts(abi: String = primaryAbi()): DeliverableSourceDescriptor =
        nativeDescriptor(DeliverableSet.FFMPEG_DTS, abi, FFMPEG[abi].orEmpty())

    /** First device ABI we host a payload for; defaults to arm64-v8a. */
    fun primaryAbi(): String =
        Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS } ?: "arm64-v8a"

    private fun nativeDescriptor(
        set: DeliverableSet,
        abi: String,
        libs: List<NativeLib>
    ): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = set,
        files = libs.map { lib ->
            PayloadFile(
                fileName = lib.soName,
                sources = listOf("$MIRROR/$abi-${lib.soName}"),
                sha256 = lib.sha256,
                minSize = lib.size
            )
        }
    )

    private fun resource(name: String, sha256: String, minSize: Long): PayloadFile =
        PayloadFile(fileName = name, sources = listOf("$MIRROR/$name"), sha256 = sha256, minSize = minSize)
}
