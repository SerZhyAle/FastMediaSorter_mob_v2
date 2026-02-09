import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.sza.fastmediasorter.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sza.fastmediasorter.wear"
        minSdk = 28  // Wear OS 2.0+ support
        targetSdk = 35
        // Use timestamp-based version code: yyMMddHH (same formula as main app)
        versionCode = run {
            val now = LocalDateTime.now()
            Integer.parseInt(now.format(DateTimeFormatter.ofPattern("yyMMddHH")))
        }
        
        // Version format: Y.YM.MDDH.Hmm (same formula as main app)
        versionName = run {
            val now = LocalDateTime.now()
            val year = now.year.toString()
            val month = now.monthValue
            val day = now.dayOfMonth
            val hour = now.hour
            val minute = now.minute
            val firstYearDigit = year[0]
            val lastYearDigit = year.last()
            val monthStr = month.toString().padStart(2, '0')
            val dayStr = day.toString().padStart(2, '0')
            val hourStr = hour.toString().padStart(2, '0')
            val minuteStr = minute.toString().padStart(2, '0')
            "$firstYearDigit.$lastYearDigit${monthStr[0]}.${monthStr[1]}$dayStr${hourStr[0]}.${hourStr[1]}$minuteStr"
        }

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
        // Suppress ViewTranslationCallback compatibility warning for Wear OS 2.x
        // This class doesn't exist on Android < 31, but we're targeting API 28+
        // The app still works - this is just a framework compatibility issue
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
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    
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
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
