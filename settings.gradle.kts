pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
