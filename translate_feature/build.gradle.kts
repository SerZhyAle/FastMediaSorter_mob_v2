plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.sza.fastmediasorter.translate_feature"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    flavorDimensions += listOf("version")

    productFlavors {
        create("standard") {
            dimension = "version"
        }
        create("noLegal") {
            dimension = "version"
        }
        create("vr") {
            dimension = "version"
        }
        create("legacy") {
            dimension = "version"
        }
        create("photos") {
            dimension = "version"
        }
        create("lite") {
            dimension = "version"
        }
    }

    buildTypes {
        getByName("debug") {
        }
        getByName("release") {
        }
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.add("../app_v2/src/translationMlKit/java")
        }
    }
}

dependencies {
    implementation(project(":app_v2"))
    
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.jakewharton.timber:timber:5.0.1")
    // translationMlKit source set calls Task.await() (kotlinx.coroutines.tasks); match app_v2's pin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
