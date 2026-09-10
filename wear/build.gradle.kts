
import java.io.FileInputStream
import java.io.File
import java.time.Duration
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
//   versionCode  yyMMddHH * 10 + 6 + floor(minute / 15) (9 digits) - app_v2 uses the same 8-digit
//     prefix with floor(minute / 10) instead, so the phone owns last digits 0..5 and the watch owns
//     6..9. The two codes MUST differ: both modules publish under the same applicationId (S1681),
//     and Play refuses a release whose artifacts repeat a versionCode. The separator digit is a
//     partition, not an offset (S2721): the watch derives its code from the build instant alone,
//     which is what its independent release cadence requires.
// Gate: scripts/quality/assert-module-version-parity.ps1.
//
// S1873: the version has three sources, in this order.
//   1. -Pfms.version* passed on the command line. Always wins (ADR-2) - it is the only way the two
//      modules of one release, built by two invocations seconds apart, agree byte for byte.
//   2. The in-build stamp, applied when THIS invocation packages an artifact and nobody passed a
//      property. Covers the paths no wrapper script reaches.
//   3. The checked-in constant below, which after ADR-4 has no writer and is a deliberately
//      non-releasable sentinel.
// The watch takes its separator digit from the shared derivation, so the two modules still differ
// by exactly the documented rule rather than by two independently written formulas.
apply(from = rootProject.file("gradle/build-version-stamp.gradle.kts"))

val defaultAppVersionCode = 260901218
val defaultAppVersionName = "2.60.9012.140"

// S2585: single source for the unit-test task ceiling, shared with app_v2 through gradle.properties.
// Declared at the top level because testOptions.unitTests.all binds `it` to the Test task, which a
// nested provider lambda would shadow. Rationale and the measurement behind 20: the property itself.
val unitTestTimeoutMinutes: Long =
    providers.gradleProperty("fms.unitTestTimeoutMinutes").orNull?.toLongOrNull() ?: 20L

// S2851: the same fork count app_v2 reads, from the same gradle.properties value, so the two modules
// cannot drift apart. This module's suite measures 55 s serially and is not what the property was
// introduced for; it honours the value so that a host-wide budget stays one number, and so that a
// future watch suite does not have to rediscover the setting.
val unitTestMaxParallelForks: Int =
    providers.gradleProperty("fms.unitTestMaxParallelForks").orNull?.toIntOrNull()?.coerceAtLeast(1)
        ?: 1
val stampedAppVersionCode = extra.properties["fmsStampedWearVersionCode"] as Int?
val stampedAppVersionName = extra.properties["fmsStampedVersionName"] as String?
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
        // Three sources in one order - passed property, in-build stamp when this invocation
        // packages, checked-in sentinel. See the block above the constants for why each exists.
        versionCode = overrideAppVersionCode ?: stampedAppVersionCode ?: defaultAppVersionCode
        versionName = overrideAppVersionName ?: stampedAppVersionName ?: defaultAppVersionName

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

    // S2090: the watch gets the same two-variant split the phone has had for years. Until this block
    // existed there was nowhere to put a capability Play refuses on a watch, so the only available
    // answer was to delete it - which is what happened to ACCESS_FINE_LOCATION in S2013.
    //
    // Both flavor source sets now carry code. S2165 filled wear/src/noLegal with the extended
    // system-information contributor, and S2486 added wear/src/standard alongside it: a two-sided
    // @Binds contract has no implementation unless BOTH flavors declare one, so the withholding answer
    // is a real class in its own set rather than a default in src/main, which would diverge silently
    // from the flavor that overrides it. wear/src/noLegal/AndroidManifest.xml now exists too, created
    // the day a capability first needed a permission and merged by convention exactly as the paragraph
    // below predicted; S2457 and S2458 both reached that day at once, so it carries two unrelated
    // permission families - body sensors and activity recognition - and any third sibling ADDS to it
    // rather than rewriting it. wear/src/standard still has no manifest, and that absence is the
    // feature: it is what keeps the Play-distributed edition clear of the permissions whose review
    // left S1614 blocked.
    // See dev/FLAVOR_DEVELOPMENT_RULES.md Rule 8 for the shape a new capability must take, and note
    // that the ban on placeholder content in these sets still stands.
    flavorDimensions += listOf("version")

    productFlavors {
        create("standard") {
            dimension = "version"
            isDefault = true
        }

        create("noLegal") {
            dimension = "version"
            // S1681: no applicationIdSuffix, ever. Play Services routes Data Layer traffic by an AppKey
            // of (package name, signing certificate) and drops a mismatch inside WearableService, below
            // the app - so a suffix here would stop delivery in both directions while the watch went on
            // logging successful sends. app_v2's own noLegal carries no suffix for the same class of
            // reason (S0232), and S1951 shows the outcome when a flavor does take one.
            //
            // No manifest.srcFile either: on the phone that substitution REPLACED the auto-detected
            // flavor manifest and silently dropped the other entries, which had to be re-added through
            // androidComponents.onVariants. Leaving it unset means a future wear/src/noLegal/
            // AndroidManifest.xml is picked up by convention and merged normally.
            versionNameSuffix = "-NoLegal"
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

    sourceSets {
        // S2355: expose the exported Room schemas as androidTest assets so MigrationTestHelper can
        // load <db-fqcn>/<version>.json from the device at runtime. Mirrors what app_v2 does under
        // S1009 - without the mount the helper finds no schema and the test fails for a reason
        // unrelated to any migration, which is indistinguishable from the defect it exists to catch.
        getByName("androidTest") {
            assets.directories.add("schemas")
        }
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

    testOptions {
        unitTests {
            // S2437: without this, every android.jar stub throws "not mocked" instead of returning a
            // default, so VoiceNotePublisher.mimeTypeOf blew up on MimeTypeMap.getSingleton() and took
            // three tests with it. app_v2 has carried the flag since its first test; this module never
            // declared testOptions at all. isIncludeAndroidResources stays off deliberately - it is a
            // Robolectric requirement, and this module has no Robolectric.
            isReturnDefaultValues = true
            all {
                // S2585: the same ceiling app_v2 carries, from the same gradle.properties value, so
                // the two modules cannot drift to two different numbers. This module has never hung,
                // but the mechanism is not module-specific: any test spinning without checking the
                // interrupt flag holds its task open, and a held task holds Build.Wear. The timeout
                // ends the task so the wrapper reaches its finally and reaps the worker there.
                it.timeout.set(Duration.ofMinutes(unitTestTimeoutMinutes))
                it.maxParallelForks = unitTestMaxParallelForks
            }
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
    implementation("androidx.wear:wear-input:1.1.0")

    // S2496: RemoteActivityHelper - hands an ACTION_VIEW to the Wear OS companion so a link opens on
    // the paired phone. It ships in this artifact alone; androidx.wear:wear above does not carry it.
    // The -ktx artifact is what awaits its ListenableFuture: hand-rolling that bridge over
    // addListener leaves the future uncancelled when the calling scope dies.
    implementation("androidx.wear:wear-remote-interactions:1.1.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // S1955: tiles for the system carousel. The tile service and its layout library split at 1.2 and are
    // both maintained; both declare minSdk 23, so neither moves this module's floor of 28.
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-material:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-expression:1.4.2")

    // S2047: watch face complication data sources. watch-face APIs are deprecated at 1.3.0 while complication APIs are not.
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")

    // S2457: Health Services, for a single foreground heart-rate reading. Three things about this line
    // are deliberate and none of them is style.
    //   1. noLegalImplementation, not implementation. Play reviews both heart-rate permissions against six
    //      admitted use cases and a media sorter matches none, so the capability ships in the sideload
    //      flavor alone - and the library has no business in the standard artifact, where no permission
    //      exists to use it. This also keeps point 2 out of the standard manifest.
    //   2. The library declares minSdk 30 against this module's floor of 28, so the noLegal manifest
    //      carries a tools:overrideLibrary entry and the data source guards on SDK_INT at runtime.
    //      Raising the floor is not available: minSdk 28 is pinned above as a support commitment.
    //   3. The version is an RC because the Health Services line has never shipped a stable release -
    //      1.0.0 stopped at beta02 and the maintained branch is 1.1.0-rc02.
    // Quoted, not the accessor form: Kotlin DSL generates type-safe accessors for the base
    // configurations only, never for a product flavor's, so `noLegalImplementation(..)` is an
    // unresolved reference that fails SCRIPT COMPILATION - which breaks configuration for every
    // module, so detekt and every post-change closure in the repository died before running a
    // single check. app_v2 has used the quoted form for its six flavors since it gained them.
    "noLegalImplementation"("androidx.health:health-services-client:1.1.0-rc02")
    
    // Accompanist Permissions (for runtime permission handling)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Media3 for audio playback and streaming (S1708)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    
    // Coil for image loading (Compose-friendly)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.59")
    ksp("com.google.dagger:hilt-android-compiler:2.59")

    // Room - voice-note store (S1862). Version is deliberately kept equal to app_v2 and pinned in
    // docs/TECH_STACK.md; check-doc-vs-gradle.ps1 reads that pin. KSP is already applied above, so
    // this adds a processor, not a plugin.
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

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

    // S2509: declared outright rather than taken from converter-gson above. The broadcast descriptor
    // is a cross-module wire contract, and a transitive version that a Retrofit bump could change or
    // drop is not something a contract may rest on.
    implementation("com.google.code.gson:gson:2.10.1")

    // S2509: QR presentation of the broadcast descriptor. Core decoder only, exactly as app_v2 takes
    // it - the android-embedded artifact drags in a legacy camera1 stack the watch has no use for.
    implementation("com.google.zxing:core:3.5.3")
    
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
    // S2355: the BOM has to be on the androidTest classpath too, or the versionless coordinate
    // below has no version source and the configuration fails to resolve. It went unnoticed
    // because until this ticket no target ever resolved the watch's instrumented classpath -
    // which is the exact shape of miss the ticket exists to remove.
    androidTestImplementation(wearComposeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // S2355: MigrationTestHelper - the class that performs on a device the same schema comparison
    // Room performs on update - ships in room-testing and nowhere else. Version kept equal to the
    // room-runtime pin above and to app_v2, so both modules test against the same Room runtime.
    androidTestImplementation("androidx.room:room-testing:2.7.0")
}

ksp {
    // Export the Room schema JSON into a committed dir so a future migration is validatable (S0731
    // set the same rule for app_v2). Version 1 has no migration; the schema is what a version 2 will
    // be diffed against.
    arg("room.schemaLocation", "$projectDir/schemas")
}
