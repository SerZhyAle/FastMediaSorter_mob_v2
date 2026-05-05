pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val isWindowsHost = System.getProperty("os.name")
    ?.startsWith("Windows", ignoreCase = true) == true

buildCache {
    local {
        // Gradle 9.x intermittently fails while packing local build cache entries
        // on Windows (for example RClassOutputJar during resource processing).
        isEnabled = !isWindowsHost
    }
}

plugins {
    // id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Microsoft Duo SDK for MSAL (display-mask library)
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
        }
        // JitPack for PhotoView library
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "FastMediaSorter_v2"
include(":app_v2")
include(":wear")
