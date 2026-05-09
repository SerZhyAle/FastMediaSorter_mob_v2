import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
    id("org.jetbrains.kotlin.plugin.compose")
}

// android.newDsl=false is intentionally set in gradle.properties (kapt compat).
// Remove once kapt → KSP migration is complete.
android {
    val hasReleaseKeystore = rootProject.file("keystore.properties").exists()
    val debugKeystorePropertiesFile = rootProject.file("debug.keystore.properties")
    val hasCustomDebugKeystore = debugKeystorePropertiesFile.exists()
    val requestedTasks = gradle.startParameter.taskNames
    val requiresReleaseSigning = requestedTasks.any {
        val t = it.lowercase()
        t.contains("release") && (t.contains("bundle") || t.contains("sign") || t.contains("assemble"))
    }

    namespace = "com.sza.fastmediasorter"
    // CRITICAL: Do not change - required for latest Android features and Play Store requirements
    compileSdk = 35
    // NDK r27c required: first NDK release that ships a 16 KB page-size aligned libc++_shared.so
    // (Google Play requirement since Nov 1 2025 for apps targeting Android 15+).
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.sza.fastmediasorter"
        // Minimum supported Android 8.0 (API 26). Legacy flavor covers API 23-25.
        minSdk = 26
        // Keep targetSdk aligned with compileSdk
        // CRITICAL: Do not change - required for Play Store compliance and latest Android behavior
        targetSdk = 35
        // Version is auto-updated by build scripts
        // versionName format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)
        // versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
        // Note: YYMMDDHHmm overflows Int32, using first digit of minutes only
        versionCode = 260509042
        versionName = "2.60.5090.424"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Note: locale filtering moved to androidResources.localeFilters (resourceConfigurations deprecated)
        
        // Screen size support (Android automatically selects resources)
        // - values-sw480dp: Compact screens (480x480+), smartwatches, small tablets
        // - values: Default/normal screens
        // - values-sw600dp: Tablets (600dp width+)
        // - values-sw720dp: Large tablets (720dp width+)
        
        // Dropbox App Key - User must provide a valid key
        manifestPlaceholders["dropboxAppKey"] = "dpy64e70kqobr6x"

        // === STARTUP DEBUG INFO ===
        // Owner trigger — read from local.properties (excluded from VCS)
        // If local.properties is absent or the key is missing, field is empty → no special behavior
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(FileInputStream(localPropsFile))
        }
        buildConfigField("String", "OWNER_TRIGGER", "\"${localProps.getProperty("sza.owner.trigger", "")}\"")

        // Git Hash
        // === STARTUP DEBUG INFO ===
        // Git Hash (Simplified for stability)
        buildConfigField("String", "GIT_HASH", "\"Unknown\"")

        // Build Time (Current)
        buildConfigField("String", "BUILD_TIME", "\"Unknown\"")
    }
    
    // Product Flavors: Different app versions for different use cases
    flavorDimensions += listOf("version")
    
    productFlavors {
        // Per-flavor CMake target filtering: only vr builds the native OpenXR bridge.
        // Non-vr flavors skip CMake entirely by declaring no build targets.
        // ABI selection is handled per-flavor (not per-buildType) because AGP merges
        // flavor+buildType ndk.abiFilters via UNION, not intersection. Setting abiFilters
        // on a buildType would leak extra slices (e.g. x86) into VR AABs. Keeping ABI
        // configuration flavor-local gives each flavor exactly what Play delivers to users.
        fun com.android.build.api.dsl.ProductFlavor.disableNativeBuild() {
            externalNativeBuild {
                cmake {
                    targets.clear()
                }
            }
            // Distribution ABIs for non-VR flavors: all four production ABIs.
            // Covers Android 8+ phones/tablets (arm64-v8a + armeabi-v7a), Chromebooks
            // and emulators (x86/x86_64). AAB per-device delivery keeps user download size
            // unchanged vs single-ABI. See PLAN/spec_ffmpeg-dts-multi-abi.md.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }

        // ===== STANDARD (Full Featured) =====
        create("standard") {
            dimension = "version"
            isDefault = true
            disableNativeBuild()
            // No applicationIdSuffix = keeps current package names
            // No versionNameSuffix = keeps current version format
            // Full feature set: Videos, Audio, Images, Cloud, Documents, Animations
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.ui.player.PlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "true")
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            // 16 KB compatible — safe for Google Play.
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== LITE (Lightweight, Local Files Only) =====
        create("lite") {
            dimension = "version"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-Lite"
            disableNativeBuild()
            // Local files only: No cloud, no heavy features
            // Target: Users with limited storage/bandwidth, older devices
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // Excluded per S0100 §6
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "false")        // No cloud providers
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")    // No PDF/EPUB/Text
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "false")    // No animations for speed
            buildConfigField("boolean", "ENABLE_EPUB", "false")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")   // No ML Kit
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")  // No background audio in lite
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "false")  // No default player in lite
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.ui.player.PlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // No wearable in lite
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "false")  // No audio playback in lite
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== PHOTOS (Images Only, with Cloud Support) =====
        create("photos") {
            dimension = "version"
            applicationIdSuffix = ".photos"
            versionNameSuffix = "-Photos"
            disableNativeBuild()
            // Images + GIFs only, no video/audio player
            // Target: Photo management, cloud photo backup/sync
            buildConfigField("boolean", "SUPPORT_VIDEO", "false")       // No video player
            buildConfigField("boolean", "SUPPORT_AUDIO", "false")       // No audio player
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // No audio support
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")       // Full image support
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")        // Cloud for photo backup
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")   // No documents
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")    // Keep animations for UI
            buildConfigField("boolean", "ENABLE_EPUB", "false")         // No EPUB
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")  // No translation needed
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")  // No audio support
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")  // Image-only default player
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.ui.player.PlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // No wearable in photos
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "false")  // No audio playback in photos
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== LEGACY (Full Features, Android 6.0+) =====
        create("legacy") {
            dimension = "version"
            // CRITICAL: Do not change - legacy flavor for Android 6/7 devices (API 23-25)
            // Standard flavor covers API 26+ (Android 8+); legacy covers the remaining API 23-25 gap.
            minSdk = 23  // Android 6.0 (Marshmallow)
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-Legacy"
            disableNativeBuild()
            // Full feature set but compatible with older Android versions
            // Target: Users with older Android devices (API 23-25)
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.ui.player.PlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "true")
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== VR (Full Features + OpenXR Headset Rendering) =====
        create("vr") {
            dimension = "version"
            applicationIdSuffix = ".vr"
            versionNameSuffix = "-VR"
            // Meta Quest 2/3/Pro use arm64-v8a exclusively; skip 32-bit to halve APK size.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    // Build only the JNI bridge target; OpenXR loader ships prebuilt in the AAR.
                    targets += listOf("openxr_native")
                    // OpenXR loader AAR ships only arm64-v8a — restrict CMake config to match,
                    // otherwise AGP tries to build openxr_native for every ABI in the buildType
                    // filter (armeabi-v7a/x86/x86_64 inherited from release buildType) and fails
                    // because those OpenXR slices do not exist. ndk.abiFilters above only
                    // governs packaging; externalNativeBuild.cmake.abiFilters governs configure.
                    abiFilters += listOf("arm64-v8a")
                    cppFlags += listOf("-std=c++17", "-Wall", "-Werror")
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-26",
                        // Gate OpenXR find_package in CMakeLists.txt: non-vr flavors omit this
                        // flag so CMake configure succeeds without the Khronos AAR on the path.
                        "-DENABLE_OPENXR=ON",
                        // Force new cmake config hash to avoid stale .tmp file lock (2026-04-21)
                        "-DFMS_BUILD_REVISION=3"
                    )
                }
            }
            // Full feature set identical to standard, plus VR headset rendering
            // Target: Meta Quest headsets for stereoscopic 3D video/photo viewing
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "true")
            // S0008 + S0019 (spec B landed): immersive HUD scene driver and interactive panel
            // composer are wired through VrInteractivePanelDriver / VrHudSceneDriver and dispatch
            // real playback commands. Flip to true so isImmersiveUiLocked() stops no-op'ing
            // OpenControls / OpenFileOps / Cheatsheet inside immersive.
            buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "true")
            // VR flavor routes all player launches to VrPlayerActivity (OpenXR host)
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.vr.VrPlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // Headset has no paired watch
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")
            buildConfigField("boolean", "SUPPORT_CAST", "false") // Horizon OS lacks Google Play Services Cast module
        }

        // ===== VR-UNLICENSED (ADB sideload only, always includes DTS) =====
        // Distribution: ADB install via Developer Mode — no Meta Horizon Store.
        // ~10% of VR users who prefer direct sideload over the store build.
        // This flavor always ships DTS (libdca via custom FFmpeg AAR): no store review restrictions.
        // If vr flavor is rejected by Meta due to DTS → ship vr without DTS, point sideloaders here.
        // See ADR-004 in spec_ffmpeg-custom-build-dts.md.
        create("vrUnlicensed") {
            dimension = "version"
            applicationIdSuffix = ".vr"          // Same app ID as vr — replaces it on the device
            versionNameSuffix = "-VR-Unlicensed"
            // Meta Quest 2/3/Pro: arm64-v8a only, same as vr.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    // Same OpenXR native bridge as vr flavor.
                    targets += listOf("openxr_native")
                    // Restrict CMake configure to arm64-v8a — OpenXR loader AAR ships only
                    // arm64. Without this, AGP attempts configure for every buildType ABI.
                    abiFilters += listOf("arm64-v8a")
                    cppFlags += listOf("-std=c++17", "-Wall", "-Werror")
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_PLATFORM=android-26",
                        "-DENABLE_OPENXR=ON"
                    )
                }
            }
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "true")
            // S0008 + S0019 (spec B landed): same flip as `vr` flavor — interactive panel + HUD
            // are wired; isImmersiveUiLocked() stops gating commands inside immersive.
            buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "true")
            buildConfigField("String", "PLAYER_ACTIVITY_CLASS", "\"com.sza.fastmediasorter.vr.VrPlayerActivity\"")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")
            buildConfigField("boolean", "ENABLE_DTS_DECODER", "true")  // Always true — no store restrictions
            buildConfigField("boolean", "SUPPORT_CAST", "false") // Horizon OS lacks Google Play Services Cast module
        }
    }
    
    // vrUnlicensed shares the same VR Kotlin/Java/C++ sources as vr.
    // AGP does not inherit flavor source sets automatically, so we add src/vr/ dirs explicitly.
    //
    // S0116 §3.2: streamingEnabled — Media3 HLS/DASH + MediaMuxer; streamingDisabled — NoOp pipeline for lite/photos.
    // Both shared source-sets are mounted into every flavor that needs them; AGP does not
    // expose pseudo-flavor inheritance, so each flavor explicitly maps to one of the two.
    sourceSets {
        getByName("vrUnlicensed") {
            java.directories.add("src/vr/java")
            res.directories.add("src/vr/res")
            manifest.srcFile("src/vr/AndroidManifest.xml")
            java.directories.add("src/streamingEnabled/java")
        }
        getByName("standard") { java.directories.add("src/streamingEnabled/java") }
        getByName("legacy") { java.directories.add("src/streamingEnabled/java") }
        getByName("vr") { java.directories.add("src/streamingEnabled/java") }
        getByName("lite") { java.directories.add("src/streamingDisabled/java") }
        getByName("photos") { java.directories.add("src/streamingDisabled/java") }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Required for Robolectric
            isReturnDefaultValues = true
        }
    }

    signingConfigs {
        create("debugCustom") {
            if (hasCustomDebugKeystore) {
                val debugProps = Properties()
                FileInputStream(debugKeystorePropertiesFile).use { inputStream ->
                    debugProps.load(inputStream)
                }

                val debugKeyAlias = debugProps.getProperty("keyAlias")
                val debugKeyPassword = debugProps.getProperty("keyPassword")
                val debugStorePassword = debugProps.getProperty("storePassword")
                val debugStorePath = debugProps.getProperty("storeFile")

                if (debugKeyAlias.isNullOrBlank() || debugKeyPassword.isNullOrBlank() ||
                    debugStorePassword.isNullOrBlank() || debugStorePath.isNullOrBlank()) {
                    throw GradleException(
                        "debug.keystore.properties is incomplete. Required keys: keyAlias, keyPassword, storePassword, storeFile"
                    )
                }

                val resolvedDebugStore = file(debugStorePath)
                if (!resolvedDebugStore.exists()) {
                    throw GradleException(
                        "Debug keystore file not found: ${resolvedDebugStore.absolutePath}. " +
                        "Fix storeFile in debug.keystore.properties."
                    )
                }

                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
                storeFile = resolvedDebugStore
                storePassword = debugStorePassword
            }
        }

        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                FileInputStream(keystorePropertiesFile).use { inputStream ->
                    keystoreProperties.load(inputStream)
                }
                
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Debug uses dedicated package/applicationId for separate OAuth client and signing setup.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
            // ABI selection is flavor-local (see productFlavors block) — not set here because
            // AGP merges buildType+flavor abiFilters as UNION, not intersection, which would
            // leak non-VR ABIs into VR debug AABs.
            if (hasCustomDebugKeystore) {
                signingConfig = signingConfigs.getByName("debugCustom")
            }
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "true")
            buildConfigField("boolean", "LOG_LINK_DOWNLOAD", "true")
            buildConfigField("boolean", "ENABLE_LEAKCANARY", "false")
            buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
            buildConfigField("boolean", "ENABLE_BACKGROUND_AUDIO", "true")
            // Dedicated Dropbox app key for debug (com.sza.fastmediasorter.debug).
            // Prevents the "Security alert" triggered when debug + release are both installed
            // and both register for the same db-<appKey>:// URI scheme.
            manifestPlaceholders["dropboxAppKey"] = "u43ocp6pqvwaiu1"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "false")
            buildConfigField("boolean", "LOG_LINK_DOWNLOAD", "false")
            buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
            buildConfigField("boolean", "ENABLE_BACKGROUND_AUDIO", "true")
            ndk {
                debugSymbolLevel = "FULL"
                // ABI selection is flavor-local (see productFlavors block) — AGP merges
                // buildType+flavor ndk.abiFilters as UNION, so a buildType-level list
                // would leak non-VR ABIs into VR AABs. Each flavor declares its own ABIs.
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else if (requiresReleaseSigning) {
                throw GradleException(
                    "Release signing is requested, but keystore.properties is missing in project root. " +
                    "Create keystore.properties with keyAlias/keyPassword/storeFile/storePassword and ensure storeFile exists."
                )
            }
        }
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        // Required for java.time.* on API 23-25 (legacy flavor). API 26+ has native support.
        isCoreLibraryDesugaringEnabled = true
        // CRITICAL: Do not change - Java 17 required for Kotlin 1.9.24 and modern Android libraries
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
        // Prefab consumes native headers/libs from AAR dependencies
        // (required by OpenXR loader AAR in vr flavor).
        prefab = true
    }

    // Native build (vr flavor only — gated via per-flavor CMake targets list below).
    // CMake glues Kotlin JNI calls to the OpenXR loader shipped in the AAR.
    externalNativeBuild {
        cmake {
            path = file("src/vr/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/versions/*/OSGI-INF/MANIFEST.MF" // BC & JSch conflict
            // Исключаем дубликаты нативных библиотек BouncyCastle
            pickFirsts += "**/*.so"
            
            // APK Size Optimization: Exclude unused BouncyCastle algorithms (~2-3 MB)

        }
        
        jniLibs {
            // 16 KB page size alignment for Android 15+ compatibility (required for Google Play since Nov 1, 2025)
            // Ensures all native libraries (.so) have LOAD segments aligned to 16 KB boundaries
            // Affects Tesseract OCR libraries: libjpeg.so, libleptonica.so, libpng.so, libtesseract.so
            useLegacyPackaging = false
        }
    }
    
    // APK Size Optimization: Keep only English, Russian, Ukrainian locales
    // Replaces the deprecated resourceConfigurations in defaultConfig
    androidResources {
        localeFilters += listOf("en", "ru", "uk")
    }

    // Force 16 KB page alignment for all native libraries
    // This is critical for Android 15+ devices with 16 KB page size
    // Without this, Google Play will reject the APK
    splits {
        abi {
            isEnable = false
        }
    }

    lint {
        checkAllWarnings = false
        // Fail CI on lint ERRORs; warnings only produce report
        abortOnError = true
        checkReleaseBuilds = false
        disable += "InvalidPackage"
        disable += "MissingTranslation"
        disable += "NewApi"
        disable += "UnsafeOptInUsageError"
        // False positive: 0dp with layout_weight in LinearLayout or as ConstraintLayout child
        disable += "Suspicious0dp"
        baseline = file("lint-baseline.xml")
        // HTML report for CI artifact upload
        htmlReport = true
        htmlOutput = file("build/reports/lint-results.html")
        xmlReport = true
        xmlOutput = file("build/reports/lint-results.xml")
    }
}

// Replaces the legacy applicationVariants.all { } block (removed in AGP 10.0).
// outputFileName wired lazily so versionName resolves after all variant merges.
androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: ""
        val flavorName = variant.flavorName ?: ""
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { vn ->
                    val v = vn ?: "unknown"
                    if (buildType == "release") "FastMediaSorter_${flavorName}_v${v}.apk"
                    else "FastMediaSorter_${flavorName}_${buildType}_v${v}.apk"
                }
            )
        }
    }
}

// CRITICAL: Do not change - must match compileOptions.targetCompatibility
// Replaces the deprecated android { kotlinOptions { jvmTarget } } block
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Core Library Desugaring: java.time.* and other Java 8+ APIs on API 23-25 (legacy flavor)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // Security (EncryptedSharedPreferences for cloud credentials)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Material Design 3
    implementation("com.google.android.material:material:1.13.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0") // For ProcessLifecycleOwner
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-android-compiler:2.57.2")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Baseline Profiles runtime installer
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
    
    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // DocumentFile for SAF support
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Print support
    implementation("androidx.print:print:1.0.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ExoPlayer (HLS/DASH re-enabled per-flavor below for S0116; SmoothStreaming stays excluded)
    implementation("androidx.media3:media3-exoplayer:1.2.1") {
        // Exclude SmoothStreaming - not used by url-download or playback
        exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
    }
    // S0116: HLS/DASH offline downloader is wired only into video-supporting market flavors.
    // lite/photos stay without these modules to preserve their APK size budget.
    "standardImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "standardImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "vrUnlicensedImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "vrUnlicensedImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-decoder:1.2.1") // Audio decoders for WAV and other formats
    implementation("androidx.media3:media3-session:1.2.1") // MediaSession for audio background playback
    implementation("androidx.media3:media3-effect:1.2.1")  // GlEffect API for SBS stereo crop rendering (Phase 2)
    
    // Image Loading - Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    
    // PhotoView for pinch-to-zoom and rotation support
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    
    // RecyclerView FastScroller (interactive scrollbar)
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
    
    // ExifInterface for image metadata (width, height, camera, GPS, etc.)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // ML Kit - Translation and Text Recognition (OCR)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")          // Latin script (also works for Cyrillic to some extent)
    implementation("com.google.mlkit:language-id:17.0.6")
    
    // Tesseract OCR (Offline, better Cyrillic support)
    implementation("cz.adaptech:tesseract4android:4.8.0") {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    
    // Network - SMB (uses BouncyCastle jdk15to18:1.72 via resolutionStrategy)
    implementation("com.hierynomus:smbj:0.12.1")
    
    // Network - SFTP (JSch for Android - better KEX support than SSHJ)
    implementation("com.github.mwiede:jsch:0.2.26")
    
    // Network - FTP
    implementation("commons-net:commons-net:3.10.0")
    
    // Wearable Data Layer — phone-side bridge to Wear OS companion
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Cloud Storage - Google Drive (REST API + Google Sign-In)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    
    // Network - Retrofit for iTunes Search API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.github.chuckerteam.chucker:library:4.0.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.0.0")
    
    // Cloud Storage - Dropbox
    implementation("com.dropbox.core:dropbox-core-sdk:5.4.5")
    
    // Cloud Storage - OneDrive (REST API + MSAL OAuth)
    implementation("com.microsoft.identity.client:msal:6.0.1")
    
    // Google Cast SDK + MediaRouter (Chromecast output from player)
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // NanoHTTPD — in-process HTTP proxy to serve local/cached files to Cast receiver
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
    // Required by LeakCanary for background heap analysis (RemoteListenableWorker)
    debugImplementation("androidx.work:work-multiprocess:2.9.0")
    
    // Document Support - EPUB
    implementation("io.documentnode:epub4j-core:4.2") {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation("org.jsoup:jsoup:1.17.2")

    // Markdown Rendering (for .md text files)
    implementation("io.noties.markwon:core:4.6.2")
    
    // Document Support - PDF extraction via built-in PdfRenderer (API 21+)
    // No external dependencies needed - metadata extraction removed to avoid conflicts
    
    // OpenXR loader — only for vr and vr-unlicensed flavors (headset XR rendering)
    "vrImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")
    "vrUnlicensedImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")

    // SW AV1 decoder (libgav1) — source-only extension, not published on Google Maven.
    // TODO: build from source (same pipeline as fms-ffmpeg-dts.aar) before enabling.
    // "standardImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
    // "legacyImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
    // "vrImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
    // "vrUnlicensedImplementation"("androidx.media3:media3-decoder-av1:1.2.1")

    // SW VP9 decoder (libvpx, incl. Profile 2 10-bit HDR) — source-only extension, not on Maven.
    // TODO: build from source before enabling.
    // "standardImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")
    // "legacyImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")
    // "vrImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")
    // "vrUnlicensedImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")

    // ── Custom FFmpeg AAR (DTS + APE/WMA/WavPack/TTA/DSD) ─────────────────────────────────────
    // DTS/extended codec decoder via custom FFmpeg AAR — built from media3 1.2.1 sources.
    // Build script: scripts/builders/build-ffmpeg-dts.sh
    // Spec: PLAN/spec_ffmpeg-custom-build-dts.md §7, Phase 3
    //
    // AAR built: app_v2/libs/fms-ffmpeg-dts.aar (libffmpegJNI.so arm64-v8a + classes.jar)
    // Rebuilt with NDK r25c + -Wl,-z,max-page-size=16384. readelf LOAD Align=0x4000 (16 KB). ✓ Play-safe.
    "standardImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "legacyImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "vrImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "vrUnlicensedImplementation"(files("libs/fms-ffmpeg-dts.aar"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework in JVM tests
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // S0116 Phase 07 step 0: MockWebServer for graceful-degradation instrumentation tests.
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")
}

// TEMPORARILY DISABLED: BouncyCastle resolutionStrategy (was needed for PDFBox)
// configurations.all {
//     resolutionStrategy {
//         force("org.bouncycastle:bcprov-jdk15to18:1.72")
//         force("org.bouncycastle:bcpkix-jdk15to18:1.72")
//         force("org.bouncycastle:bcutil-jdk15to18:1.72")
//     }
// }

kapt {
    correctErrorTypes = true
    javacOptions {
        option("-Xlint:-processing")
    }
}
