buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Pinned: AGP 9.2.0 - requires Gradle 9.x; see dev/TECH_REQUIREMENTS.md §11
        classpath("com.android.tools.build:gradle:9.2.1")
        // Pinned: Kotlin 2.2.10 - aligned with KSP 2.3.8 and Compose plugin 2.2.10
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.59")
    }
    
    // Check for compatible JDK version (Gradle 8.7+ supports up to Java 21, but 25 is definitely too new)
    if (JavaVersion.current() > JavaVersion.VERSION_21) {
        throw GradleException("This build is running on Java ${JavaVersion.current()}. It requires Java 17 or 21.\n" +
            "Please check your Gradle JDK setting in Android Studio:\n" +
            "Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.legacy-kapt") version "9.2.1" apply false
    id("com.google.devtools.ksp") version "2.3.8" apply false
    id("com.google.dagger.hilt.android") version "2.59" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // S0174: Chaquopy Python runtime - applied only in app_v2 (noLegal flavor).
    // 17.0.0: first version supporting AGP 9.x / Gradle 9.x (removes VersionNumber dependency).
    id("com.chaquo.python") version "17.0.0" apply false
    // S0720: detekt static analysis - resolved from gradlePluginPortal, applied per-subproject below.
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// S0720: detekt static analysis (lexical/AST + ktlint formatting ruleset), applied
// per-subproject. Deliberately a SEPARATE gate - not wired into assemble*; it must not
// change the runtime artifact or slow normal builds. Run via `:app_v2:detekt :wear:detekt`.
// No type resolution (no classpath/jvmTarget) keeps it fast and avoids a full compile.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline-${project.name}.xml")
        // Scan all source sets (incl. flavor dirs where .kt actually live). files("src")
        // resolves to <module>/src and skips build/ generated output.
        source.setFrom(files("src"))
    }

    dependencies {
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    }
}
