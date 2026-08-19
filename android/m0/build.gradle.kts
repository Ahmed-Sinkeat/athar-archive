// THROWAWAY. One harness app for the M0 prototypes that need a device
// (docs/main-plan.md §20): R1 selection, R2/R2b FTS, R3 import, R7 end-to-end.
// R4 is Node/TS and R5 is pure JVM — neither needs this module.
//
// Deleted at the end of M0. Anything worth keeping migrates to :benchmark as a
// macrobenchmark or to a module's own tests — nothing here graduates into
// production by staying put.
plugins {
    id("athar.android.application")
    id("athar.android.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.atharchive.m0"

    defaultConfig {
        applicationId = "com.atharchive.m0"
        versionCode = 1
        versionName = "m0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:athar-text"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.profileinstaller)

    // R8 — Navigation 3 state restoration and per-entry ViewModel stores.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // R2 / R2b / R3 — Room 3 + BundledSQLiteDriver (D1)
    implementation(libs.room.runtime)
    implementation(libs.room.paging)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    // R1 — virtualised reader surface
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // R3 — package download
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    testImplementation(libs.junit4)
}
