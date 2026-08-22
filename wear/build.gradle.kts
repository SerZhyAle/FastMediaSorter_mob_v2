
import java.io.FileInputStream
import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versioning is one system across both modules, stamped together by
// scripts/release/build-release-spectrum.ps1 from a single timestamp:
//   versionName  Y.YM.MDDH.Hmm  - byte-identical to app_v2, the watch and the phone ship one version.
//   versionCode  yyMMddHH (8 digits) - app_v2 appends the first minute digit and gets 9. The two
//     codes MUST differ: both modules publish under the same applicationId (S1681), and Play refuses
//     a release whose artifacts repeat a versionCode. Wear = app_v2 code without its last digit.
// Gate: scripts/quality/assert-module-version-parity.ps1.
val defaultAppVersionCode = 26081516
val defaultAppVersionName = "2.60.8151.612"
val overrideAppVersionCode = providers.gradleProperty("fms.versionCode").orNull?.let { raw ->
    raw.toIntOrNull() ?: throw GradleException("Invalid -Pfms.versionCode value: '$raw'")
}
val overrideAppVersionName = providers.gradleProperty("fms.versionName").orNull

fun findRootSecretFile(vararg relativePaths: String): File? =
    relativePaths
        .asSequence()
        .map(rootProject::file)
        .firstOrNull(File::exists)

fun resolveSiblingPath(baseFile: File, rawPath: String): File {
    val direct = File(rawPath)
    return if (direct.isAbsolute) direct else File(baseFile.parentFile, rawPath).normalize()
}

android {
    val releaseKeystorePropertiesFile = findRootSecretFile(".secrets/keystore.properties", "keystore.properties")
    val hasReleaseKeystore = releaseKeystorePropertiesFile != null
    val requestedTasks = gradle.startParameter.taskNames
    val requiresReleaseSigning = requestedTasks.any {
        val t = it.lowercase()
        t.contains("release") && (t.contains("bundle") || t.contains("sign") || t.contains("assemble"))
    }

    namespace = "com.sza.fastmediasorter.wear"
    // CRITICAL: Do not change - required for latest Wear OS features
    compileSdk = 36

    defaultConfig {
        // S1681: MUST stay identical to app_v2's applicationId. Play Services routes Data Layer
        // traffic by an AppKey of (package name, signing certificate) and drops anything whose
        // package name differs across the two devices, inside WearableService and below the app -
        // so a mismatch is invisible to both sides: the watch logs a sent message and the phone app
        // is simply never called. While this read "com.sza.fastmediasorter.wear", no payload was
        // ever deliverable in either direction. The code namespace above deliberately keeps the
        // .wear segment - only the install identity has to match.
        applicationId = "com.sza.fastmediasorter"
        // CRITICAL: Do not change - minimum Wear OS 2.0+ (API 28) support
        minSdk = 28  // Wear OS 2.0+ support
        // CRITICAL: Do not change - required for Wear OS Play Store compliance
        targetSdk = 36
        // Version is kept in sync with app_v2 by build-with-version.ps1 / release scripts
        // via -Pfms.versionCode and -Pfms.versionName properties.
        // Default values provide stable configuration cache in local builds.
        versionCode = overrideAppVersionCode ?: defaultAppVersionCode
        versionName = overrideAppVersionName ?: defaultAppVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = releaseKeystorePropertiesFile
            if (keystorePropertiesFile != null) {
                val keystoreProperties = Properties()
                FileInputStream(keystorePropertiesFile).use { inputStream ->
                    keystoreProperties.load(inputStream)
                }
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = resolveSiblingPath(
                    keystorePropertiesFile,
                    keystoreProperties["storeFile"] as String
                )
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign wear release with the shared project release key so the published
            // Wear OS asset is sideload-installable (S0394). Same keystore as app_v2 -
            // one pinned fingerprint covers the whole spectrum.
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else if (requiresReleaseSigning) {
                throw GradleException(
                    "Wear release signing is requested, but .secrets/keystore.properties is missing " +
                    "(root keystore.properties is still accepted as a fallback). " +
                    "Create keystore.properties with keyAlias/keyPassword/storeFile/storePassword and ensure storeFile exists."
                )
            }
        }
    }

    compileOptions {
        // CRITICAL: Do not change - Java 17 required for Kotlin 1.9.24 and modern Wear OS libraries
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // S1679: mirrors the S0385 exclusion set from app_v2/build.gradle.kts. BouncyCastle
            // arrives transitively through com.hierynomus:smbj and carries the post-quantum PICNIC
            // data tables, which no code path here reaches - SMB, FTP and SFTP use only classical
            // crypto. R8 shrinks code but never java resources inside a jar, so these survived
            // minification and were 1,214,815 bytes, 10.1 % of the 12,031,273-byte release APK,
            // stored uncompressed. Keep this set equal to app_v2's: the two drifted apart silently
            // once already, which is the whole reason this ticket exists.
            excludes += "org/bouncycastle/pqc/crypto/picnic/**"
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
        }
    }
}

kotlin {
    compilerOptions {
        // CRITICAL: Do not change - must match compileOptions.targetCompatibility
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        // S1804: Kotlin 2.2 warns (KT-73255) that an annotation on a constructor parameter currently
        // lands on the parameter only and will also land on the field in a future release. app_v2
        // settled this on 2026-04-11 with the same flag; the wear module never got it, which is why
        // the warning resurfaced here. Same answer in both modules beats two answers to one question.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    lintChecks(project(":lint-rules"))
    // Wear OS Compose - Using compatible BOM version for wear-compose 1.2.1
    // compose-bom 2024.02.00 includes compose-animation-core 1.6.x compatible with wear-compose 1.2.x
    val wearComposeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(wearComposeBom)
    
    // Wear OS Compose libraries - pinned to compatible versions
    implementation("androidx.wear.compose:compose-material:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.2.1")
    implementation("androidx.wear.compose:compose-navigation:1.2.1")
    
    // Hilt Navigation Compose (for hiltViewModel)
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Compose UI basics
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Activity Compose
    implementation("androidx.activity:activity-compose:1.10.1")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Wear OS essentials
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.wear:wear:1.3.0")
    
    // Accompanist Permissions (for runtime permission handling)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Media3 for audio playback and streaming (S1708)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    
    // Coil for image loading (Compose-friendly)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-android-compiler:2.59")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // DataStore for settings - kept in lockstep with app_v2 (S1449); 1.0.0 cannot rewrite its
    // own file on Windows, and two versions of one library across modules is a future trap.
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    
    // Retrofit for album art API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // SMB client for network storage
    implementation("com.hierynomus:smbj:0.12.1")

    // FTP client (S0111 Phase 04)
    implementation("commons-net:commons-net:3.10.0")

    // SFTP client - JSch (lighter than SSHJ, no BouncyCastle conflict with SMBJ) (S0111 Phase 04).
    // Version is deliberately kept equal to app_v2 and enforced by check-doc-vs-gradle.ps1 (S1496).
    implementation("com.github.mwiede:jsch:0.2.26")
    
    // Encrypted storage for credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // S1697: the watch ViewModels sit on Data Layer clients that need an Android Context to build,
    // so a state test can only exist here with a mocking library. Same version as app_v2.
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
