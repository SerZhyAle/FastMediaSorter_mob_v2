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
    // [rev] is the element revision baked into the hosted file NAME (e.g. libtesseract-v1.so), so a
    // future incompatible build of an element uploads a NEW name (-v2) while the old file stays for
    // older app versions. The address (tag) is permanent; versioning lives in the filename (B2).
    private data class NativeLib(val soName: String, val sha256: String, val size: Long, val rev: String = "v1")

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

    // S0423: the TRANSLATION lib map + translation() descriptor were removed. Translation is bundled
    // in every translation-capable flavor (no on-demand download), so no payload descriptor exists.

    /**
     * Set C - audio-player background videos. Pure resource (no `System.load`); the SHA-256 is still
     * pinned because the bytes never change, so corruption/substitution is caught before use.
     */
    fun audioVisualizations(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.AUDIO_VISUALIZATIONS,
        files = listOf(
            resource("anim_audio_bg_1.mp4", "947a5bf459b7e6cf98ccdd804cf57b7d6826f087fac236eadd11b325f86f518f", 1_673_054L),
            resource("anim_audio_bg_2.mp4", "584f519f38deece5b5b31c5d410fe8c37762b902d16ed2c32d9d4fd382eb2a21", 1_674_941L),
            resource("anim_audio_bg_3.mp4", "6462d6bd0530404094b5e5250271f6b80fffb43d8be37cc0ebc1d493786d07cd", 1_679_910L),
            resource("anim_audio_bg_4.mp4", "e7be3ff5cbbb837128bbf5bed14ae3c6776197b2c9386bfad3358d2bf2b755b4", 1_684_364L),
            resource("anim_audio_bg_5.mp4", "99504d435047ad86e0cde9d2ba88ba2cd2551fbbc6b5a9cca743777110d5f954", 1_672_034L),
            resource("anim_audio_bg_6.mp4", "3b226e716683b66e2f2b754b1e72cf7bcde79238792f816d8c834331f23d7318", 2_080_752L),
            resource("anim_audio_bg_7.mp4", "f34c09c604a71930149a1f734a7976537c45332179200371a1b811cff9dd39ea", 2_071_845L),
            resource("anim_audio_bg_8.mp4", "4d052d2bb90bdd40b1664797cbd844dec8caa22cfc438fdf48f485e2f843e77c", 2_067_401L),
            resource("anim_audio_bg_9.mp4", "b60191cedf92f45213f0f7c6a4c4455ffd7b3a006683d91f329ceb60b433fdd9", 2_059_489L),
            resource("anim_audio_bg_10.mp4", "13b10fa129ae5c8f025ccd3cac836b90d9b4e18cbc29c2d9c4ba6802d2f70b6e", 2_075_937L),
            resource("anim_audio_bg_11.mp4", "dc5be5dd8359599dd83d0f49a007031fedc6ce550b7f353c4b911e0bc943c205", 2_076_890L)
        )
    )

    // Channel-preview atlas integrity pins (S1154), taken from the 2026-07-26 build published to the
    // mirror: 1881 tiles on an 8160x7560 sheet, produced by the offline packer
    // (`collect-stream-candidates.ps1 -WithChannelPreviews`). Regenerating the atlas means a new
    // element revision (-v2) plus new pins here, never a silent re-upload under the same name.
    private const val ATLAS_SHEET_SHA256 = "7d3e6422ae1fa7ff251b9cd8db20316b72313ffb713d54bc18403111738424ca"
    private const val ATLAS_SHEET_MIN_SIZE = 11_358_632L
    private const val ATLAS_COORDS_SHA256 = "be60d35c838d14e584350c2403f22faaa4077a5f0176ed1ceb75e7df760259d9"
    private const val ATLAS_COORDS_MIN_SIZE = 134_997L

    /**
     * Channel-preview atlas (S1154) - on-demand stream channel-preview sprite sheet + `url->index`
     * sidecar. Pure data payload (no `System.load`), so [RealDeliverableSetDownloader.isNativeCodeSet]
     * leaves it un-gated and it downloads on Play installs too. Contributed only on demand (never
     * bundled), on the streams flavors.
     */
    fun channelPreviewAtlas(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.CHANNEL_PREVIEW_ATLAS,
        files = listOf(
            resource("channel-preview-atlas.webp", ATLAS_SHEET_SHA256, ATLAS_SHEET_MIN_SIZE),
            resource("channel-preview-coords.json", ATLAS_COORDS_SHA256, ATLAS_COORDS_MIN_SIZE)
        )
    )

    // Stream logo atlas integrity pins (S1201), taken from the 2026-07-26 build published to the
    // mirror: 1838 tiles covering 2156 channels on an 8024x4352 sheet, produced by the offline packer
    // (`collect-stream-candidates.ps1 -WithStreamLogos`). Regenerating the atlas means a new element
    // revision (-v2) plus new pins here, never a silent re-upload under the same name.
    private const val LOGO_SHEET_SHA256 = "9d0763379afabadf802051e4fa40ab81889d570a9774d6f3b26e7dc05749d404"
    private const val LOGO_SHEET_MIN_SIZE = 6_645_666L
    private const val LOGO_COORDS_SHA256 = "9e41cc7e72e283f815089da408b11af665cc08f10bc8bb18ff4b3935a7a1e4c4"
    private const val LOGO_COORDS_MIN_SIZE = 142_799L

    /**
     * Stream logo atlas (S1201) - on-demand station-logo sprite sheet + `url->index` sidecar. Same
     * shape as [channelPreviewAtlas]: pure data payload, contributed only on demand, on the streams
     * flavors. It exists alongside rather than inside the preview atlas because a station with no video
     * track can never have a captured frame, and the two payloads are worth refusing independently.
     */
    fun streamLogoAtlas(): DeliverableSourceDescriptor = DeliverableSourceDescriptor(
        set = DeliverableSet.STREAM_LOGO_ATLAS,
        files = listOf(
            resource("stream-logo-atlas.webp", LOGO_SHEET_SHA256, LOGO_SHEET_MIN_SIZE),
            resource("stream-logo-coords.json", LOGO_COORDS_SHA256, LOGO_COORDS_MIN_SIZE)
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
                // On-device name stays unversioned (the engine loads `libtesseract.so`); only the
                // remote asset name carries the ABI + element revision.
                fileName = lib.soName,
                sources = listOf("$MIRROR/$abi-${withRev(lib.soName, lib.rev)}"),
                sha256 = lib.sha256,
                minSize = lib.size
            )
        }
    )

    private fun resource(name: String, sha256: String, minSize: Long, rev: String = "v1"): PayloadFile =
        PayloadFile(fileName = name, sources = listOf("$MIRROR/${withRev(name, rev)}"), sha256 = sha256, minSize = minSize)

    /** Insert the element revision before the extension: `libtesseract.so` + `v1` -> `libtesseract-v1.so`. */
    private fun withRev(name: String, rev: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0) "$name-$rev" else name.substring(0, dot) + "-" + rev + name.substring(dot)
    }
}
