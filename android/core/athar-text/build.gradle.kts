// Pure JVM — NO Android dependencies, ever (docs/main-plan.md §3). That is what
// keeps the highest-risk logic (normalizer, source-range map, query builder)
// testable in milliseconds without an emulator, and it is the module that would
// become athar-core if Rust or iOS ever justified it.
//
// Runs standalone: `./gradlew :core:athar-text:test`
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    listOf("athar.m0.sqlite", "athar.repoRoot").forEach { key ->
        providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
    }
}
