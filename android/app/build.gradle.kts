// Production shell. Holds no domain logic (docs/main-plan.md §3) — Application,
// Activity, navigation host, DI wiring, and the manifest that carries the backup
// rules R6 verifies. Real screens arrive with :feature:* at M3.
plugins {
    id("athar.android.application")
    id("athar.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.atharchive"

    defaultConfig {
        applicationId = "com.atharchive"
        versionCode = 1
        versionName = "0.1.0-m0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Benchmarks need a release-like build that is still installable locally.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

dependencies {
    implementation(project(":core:athar-text"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.profileinstaller)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
