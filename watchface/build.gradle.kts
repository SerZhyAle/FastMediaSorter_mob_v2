// S1677: FastMediaSorter watch face, Watch Face Format v4.
//
// A resource-only module: no Kotlin, no Java, no dependencies, and the manifest declares
// android:hasCode="false". The renderer lives in the Wear OS system image, so the package ships
// nothing but a declarative XML description and the resources it references.
plugins {
    id("com.android.application")
}

android {
    // No Kotlin toolchain: the module has no sources to compile, and leaving the toolchain on
    // would make an empty compile task part of every build of this module.
    enableKotlin = false

    namespace = "com.sza.fastmediasorter.watchface"
    compileSdk = 36

    defaultConfig {
        // Frozen anchor. Deliberately NOT com.sza.fastmediasorter: that id belongs to the phone app
        // and the watch companion jointly (S1681), and Play needs this watch face to be a separate
        // app entry with its own listing category.
        applicationId = "com.sza.fastmediasorter.watchface"

        // WFF v4 exists only from Wear OS 6 / API 36 - the format's own version table maps versions
        // 1..4 onto API 33, 34, 35 and 36. This floor is the format's, not a choice: the :wear module
        // sits on 28 and could never host this package.
        minSdk = 36
        targetSdk = 36

        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        // Code shrinking is ON in BOTH build types, which is the opposite of the usual arrangement
        // and is not a copy-paste slip. AGP generates an R class for the resources even in a module
        // with no sources, dexes it, and packages the result - measured here: the first build of this
        // module produced classes.dex and classes2.dex from nothing but R. A package that declares
        // android:hasCode="false" and then ships a dex is contradicting its own manifest, so R8 runs
        // purely to remove the class nobody asked for. Google's own WFF samples carry the same pair
        // of flags for the same reason.
        debug {
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            // Resource shrinking must stay OFF, even though code shrinking is on. The resource
            // shrinker decides reachability by following code references, and this module has no
            // code - so every drawable the watch face markup names looks unused and would be
            // stripped out of the shipped bundle.
            isShrinkResources = false
        }
    }
}
