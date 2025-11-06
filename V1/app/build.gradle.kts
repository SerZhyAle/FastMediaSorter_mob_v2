import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.sza.fastmediasorter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sza.fastmediasorter"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Disable logging in release
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
        }
        debug {
            isTestCoverageEnabled = true
            isDebuggable = true
            // Enable logging in debug
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "fastmediasorter.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // Room schema export configuration
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0")

    // BouncyCastle provider for jCIFS-ng MD4 algorithm support
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // SMB client library
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // PhotoView for zoom/pinch functionality
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // ExoPlayer for video playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
}

// Custom test tasks
tasks.register("runAllTests") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")
    group = "verification"
    description = "Run all unit and instrumented tests"
}

tasks.register("runUnitTests") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Run unit tests only"
}

tasks.register("runInstrumentedTests") {
    dependsOn("connectedDebugAndroidTest")
    group = "verification"
    description = "Run instrumented tests only (requires connected device/emulator)"
}
