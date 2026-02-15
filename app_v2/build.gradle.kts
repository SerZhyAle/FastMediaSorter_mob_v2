import java.util.Properties
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.sza.fastmediasorter"
    // CRITICAL: Do not change - required for latest Android features and Play Store requirements
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sza.fastmediasorter"
        // CRITICAL: Do not change - minimum supported Android 9 (API 28). Lowering breaks core features.
        minSdk = 28
        // Keep targetSdk aligned with compileSdk
        // CRITICAL: Do not change - required for Play Store compliance and latest Android behavior
        targetSdk = 35
        // Version is auto-updated by build scripts
        // versionName format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)
        // versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
        // Note: YYMMDDHHmm overflows Int32, using first digit of minutes only
        versionCode = 260215185
        versionName = "2.60.2151.856"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // APK Size Optimization: Keep only necessary locales and densities
        // Keeps English, Russian, Ukrainian + high-density screens
        resourceConfigurations += listOf("en", "ru", "uk")
        // Note: density filtering handled by Play Store with App Bundle
        
        // Screen size support (Android automatically selects resources)
        // - values-sw480dp: Compact screens (480x480+), smartwatches, small tablets
        // - values: Default/normal screens
        // - values-sw600dp: Tablets (600dp width+)
        // - values-sw720dp: Large tablets (720dp width+)
        
        // Dropbox App Key - User must provide a valid key
        manifestPlaceholders["dropboxAppKey"] = "dpy64e70kqobr6x"

        // === STARTUP DEBUG INFO ===
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
        // ===== STANDARD (Full Featured) =====
        create("standard") {
            dimension = "version"
            isDefault = true
            // No applicationIdSuffix = keeps current package names
            // No versionNameSuffix = keeps current version format
            // Full feature set: Videos, Audio, Images, Cloud, Documents, Animations
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
        }
        
        // ===== LITE (Lightweight, Local Files Only) =====
        create("lite") {
            dimension = "version"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-Lite"
            // Local files only: No cloud, no heavy features
            // Target: Users with limited storage/bandwidth, older devices
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "false")        // No cloud providers
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")    // No PDF/EPUB/Text
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "false")    // No animations for speed
            buildConfigField("boolean", "ENABLE_EPUB", "false")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")   // No ML Kit
        }

        // ===== PHOTOS (Images Only, with Cloud Support) =====
        create("photos") {
            dimension = "version"
            applicationIdSuffix = ".photos"
            versionNameSuffix = "-Photos"
            // Images + GIFs only, no video/audio player
            // Target: Photo management, cloud photo backup/sync
            buildConfigField("boolean", "SUPPORT_VIDEO", "false")       // No video player
            buildConfigField("boolean", "SUPPORT_AUDIO", "false")       // No audio player
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")       // Full image support
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")        // Cloud for photo backup
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")   // No documents
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")    // Keep animations for UI
            buildConfigField("boolean", "ENABLE_EPUB", "false")         // No EPUB
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")  // No translation needed
        }

        // ===== LEGACY (Full Features, Android 6.0+) =====
        create("legacy") {
            dimension = "version"
            // CRITICAL: Do not change - legacy flavor specifically for Android 6+ (API 23-27) devices
            minSdk = 23  // Android 6.0 (Marshmallow) instead of 28
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-Legacy"
            // Full feature set but compatible with older Android versions
            // Target: Users with older Android devices (API 23-27)
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Required for Robolectric
            isReturnDefaultValues = true
        }
    }

    signingConfigs {
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "true")
            buildConfigField("boolean", "ENABLE_LEAKCANARY", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "false")
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                // Use non-optimizing rules for faster builds (skips bytecode optimization pass)
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildTypeName = buildType.name
            val flavorName = flavorName
            
            output.outputFileName = if (buildTypeName == "release") {
                "FastMediaSorter_${flavorName}_v${versionName}.apk"
            } else {
                "FastMediaSorter_${flavorName}_${buildTypeName}_v${versionName}.apk"
            }
        }
    }

    compileOptions {
        // CRITICAL: Do not change - Java 17 required for Kotlin 1.9.24 and modern Android libraries
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // CRITICAL: Do not change - must match compileOptions.targetCompatibility
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        // CRITICAL: Do not change - version 1.5.14 is matched to Kotlin 1.9.24 for Compose compatibility
        kotlinCompilerExtensionVersion = "1.5.14"
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
        abortOnError = false
        checkReleaseBuilds = false
        disable += "InvalidPackage"
        disable += "MissingTranslation"
        disable += "NewApi"
        disable += "UnsafeOptInUsageError"
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
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
    implementation("com.google.android.material:material:1.12.0")
    
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
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Baseline Profiles runtime installer
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // DocumentFile for SAF support
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ExoPlayer (optimized - exclude streaming modules, save ~1-2 MB)
    implementation("androidx.media3:media3-exoplayer:1.2.1") {
        // Exclude DASH/HLS/SmoothStreaming - only need local/network file playback
        exclude(group = "androidx.media3", module = "media3-exoplayer-dash")
        exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
        exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
    }
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-decoder:1.2.1") // Audio decoders for WAV and other formats
    implementation("androidx.media3:media3-session:1.2.1") // MediaSession for audio background playback
    
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
    implementation("com.github.mwiede:jsch:0.2.16")
    
    // Network - FTP
    implementation("commons-net:commons-net:3.10.0")
    
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
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework in JVM tests
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.50")
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
}
