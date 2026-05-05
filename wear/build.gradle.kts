
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sza.fastmediasorter.wear"
    // CRITICAL: Do not change - required for latest Wear OS features
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sza.fastmediasorter.wear"
        // CRITICAL: Do not change - minimum Wear OS 2.0+ (API 28) support
        minSdk = 28  // Wear OS 2.0+ support
        // CRITICAL: Do not change - required for Wear OS Play Store compliance
        targetSdk = 35
        // Version is kept in sync with app_v2 by build-with-version.ps1
        // versionCode format: yyMMddHH (8 digits, same base as main app minus minute digit)
        // versionName format: Y.YM.MDDH.Hmm (identical to main app)
        versionCode = 26050417
        versionName = "2.60.5041.750"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        }
    }

    compileOptions {
        // CRITICAL: Do not change - Java 17 required for Kotlin 1.9.24 and modern Wear OS libraries
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // CRITICAL: Do not change - must match compileOptions.targetCompatibility
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
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
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Wear OS essentials
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.wear:wear:1.3.0")
    
    // Accompanist Permissions (for runtime permission handling)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // Media3 for audio playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    
    // Coil for image loading (Compose-friendly)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-android-compiler:2.57.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Retrofit for album art API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // SMB client for network storage
    implementation("com.hierynomus:smbj:0.12.1")
    
    // Encrypted storage for credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
