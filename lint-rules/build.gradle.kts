plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:32.2.1")
    compileOnly("com.android.tools.lint:lint-checks:32.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.android.tools.lint:lint-api:32.2.1")
    testImplementation("com.android.tools.lint:lint-tests:32.2.1")
}
