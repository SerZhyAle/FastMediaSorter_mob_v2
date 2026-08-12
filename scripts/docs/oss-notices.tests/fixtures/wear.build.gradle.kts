// S1495 test fixture - synthetic wear build file. No product flavors.

dependencies {
    lintChecks(project(":lint-rules"))
    val wearComposeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(wearComposeBom)

    implementation("com.hierynomus:smbj:0.12.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
}
