buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Pinned: AGP 9.2.0 — requires Gradle 9.x; see dev/TECH_REQUIREMENTS.md §11
        classpath("com.android.tools.build:gradle:9.2.0")
        // Pinned: Kotlin 2.2.10 — aligned with KSP 2.3.2 and Compose plugin 2.2.10
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
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
