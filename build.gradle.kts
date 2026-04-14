buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
    // CRITICAL: Do not change - AGP 8.7.3 tested and stable for this project
    classpath("com.android.tools.build:gradle:9.1.1")
        // CRITICAL: Do not change - Kotlin 1.9.24 required for project stability
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.57.2")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.6")
    }
    
    // Check for compatible JDK version (Gradle 8.7+ supports up to Java 21, but 25 is definitely too new)
    if (JavaVersion.current() > JavaVersion.VERSION_21) {
        throw GradleException("This build is running on Java ${JavaVersion.current()}. It requires Java 17 or 21.\n" +
            "Please check your Gradle JDK setting in Android Studio:\n" +
            "Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
