// S1495 test fixture - synthetic app_v2 build file. Not a buildable script.
// One case per shape the real build file uses.

plugins {
    id("com.android.application")
}

android {
    productFlavors {
        create("standard") { }
        create("noLegal") { }
    }
}

dependencies {
    lintChecks(project(":lint-rules"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")

    implementation("androidx.core:core-ktx:1.13.0")
    "noLegalImplementation"("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
    "vrImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")
    "standardImplementation"(files("libs/fms-ffmpeg-dts.aar"))

    implementation("io.documentnode:epub4j-core:4.2") {
        exclude(group = "xmlpull", module = "xmlpull")
    }

    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.0.0")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
    "benchmarkImplementation"("com.github.chuckerteam.chucker:library-no-op:4.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    ksp("com.google.dagger:hilt-android-compiler:2.59")

    // "standardImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
}
