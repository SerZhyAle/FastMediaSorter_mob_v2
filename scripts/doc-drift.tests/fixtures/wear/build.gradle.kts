// Fixture for the wear module (S1496). Get-GradlePins reads wear/build.gradle.kts through
// Read-RequiredText, which throws when the file is absent - without this fixture the whole
// GradleParser suite threw at load and every assertion in it was skipped, not passed.
//
// compileSdk and targetSdk deliberately match fixtures/app_v2/build.gradle.kts, because
// Get-SharedModulePin throws on a mismatch between the two modules.

android {
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
    }
}

dependencies {
    // Shared with the app fixture at the same version - the passing side of the cross-module rule.
    implementation("com.google.dagger:hilt-android:2.59")
    // Declared only here - a one-module coordinate must not be compared against anything.
    implementation("androidx.wear:wear:1.3.0")
}
