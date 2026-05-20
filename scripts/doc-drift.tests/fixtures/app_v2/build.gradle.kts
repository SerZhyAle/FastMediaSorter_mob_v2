android {
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }

    productFlavors {
        create("standard") {
            minSdk = 26
        }
        create("lite") {
            minSdk = 26
        }
        create("legacy") {
            minSdk = 23
        }
    }
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.59")
    implementation("androidx.room:room-runtime:2.7.0")
}
