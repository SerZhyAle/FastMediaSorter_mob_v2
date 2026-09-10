import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.sza.fastmediasorter.benchmark"
    // Aligned with app_v2 (S1149): Android 16 / API 36. targetSdk inherited via targetProjectPath.
    compileSdk = 36
    targetProjectPath = ":app_v2"

    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        // S0722: app_v2 declares a `version` flavor dimension the producer does not share, so
        // without an explicit pick dependency resolution cannot choose between its seven release
        // variants and the connected task fails before it reaches the device.
        missingDimensionStrategy("version", "standard")
    }

    buildTypes {
        // S0722: the journey entry points (BenchmarkRouteActivity, BenchmarkSetupReceiver) live in
        // app_v2's `benchmark` build type source set, which `release` does not include - a producer
        // variant named anything else consumes an app that cannot answer the journey intents.
        create("benchmark") {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.benchmark:benchmark-common:1.5.0-alpha07")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0-alpha07")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test:runner:1.5.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
