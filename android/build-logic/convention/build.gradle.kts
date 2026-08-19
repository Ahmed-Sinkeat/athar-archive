plugins {
    `kotlin-dsl`
}

dependencies {
    // Needed on the classpath so the precompiled script plugins in src/main/kotlin
    // can call android { } / kotlin { } extensions directly.
    compileOnly(libs.gradle.plugin.android)
    compileOnly(libs.gradle.plugin.kotlin)
    implementation(libs.gradle.plugin.compose)
}

kotlin {
    jvmToolchain(21)
}
