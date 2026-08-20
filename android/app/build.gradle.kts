// Production shell. Holds no domain logic (docs/main-plan.md §3) — Application,
// Activity, navigation host, DI wiring, and the manifest that carries the backup
// rules R6 verifies. Real screens arrive with :feature:* at M3.
plugins {
    id("athar.android.application")
    id("athar.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val contentBaseUrl = providers.gradleProperty("athar.contentBaseUrl")
    .orElse(providers.environmentVariable("ATHAR_CONTENT_BASE_URL"))
val contentSigningKeyId = providers.gradleProperty("athar.contentSigningKeyId")
    .orElse(providers.environmentVariable("ATHAR_CONTENT_SIGNING_KEY_ID"))
val contentPublicKey = providers.gradleProperty("athar.contentPublicKeyDerBase64")
    .orElse(providers.environmentVariable("ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64"))

// Public, debug-only trust material for the M5 staging generation. The private key is not
// stored in the repository. Release/benchmark builds remain unconfigured unless CI supplies
// all three ATHAR_CONTENT_* values.
val stagingContentBaseUrl = "https://app-content.arthurarchive.com/app/v2/"
val stagingContentSigningKeyId = "athar-m5-staging-2026-08-20"
val stagingContentPublicKey = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAxI88ZL60naTm77afIZv6Tt/KALpIz6ZZ/7qHkxl+bKWTVWubLNZQlqGur7d+EBMpOQcSEI6PPApDbQDg8aSr6nk1yVKXSUut/zo5OEbPmjAxOjiwtFpPpOLKvEENhHNI3Vwv2iIiQ8rwfMnECWjNsYswE+F5pZR8AjhodiNB6sSWWXza6xcPBo7Sz3g0LpvrB8jmZRitWN1lru8rpqKcrGqoxslKoTY+WwjrNcKtfTMB7wQYOCOXzFK40BSQyQUuK3EJmWrPW5tdhmCABHiCnR2MtQIcDFLR5uRv6kM9gWw/qMcrLzG+Zg3V4ix+p5sk6RiXWaCKiKyg57PWt28FH0sOyrDXGvu1Sh+w2Aiu4G6Pzq2wfouTqXV5F4WXFaXPj3jsivI7b6rPBWI5HDsDAGt0XhtfpHkRQT4IOqRs1oGAUCJud1NFr6VtBZdXqitVwii6knUYqRua0hf7SW8baUohy6ChQU5GJ1HrFDLU+uAKSGPPsP5vMXzK1g4dCqLjAgMBAAE="

fun validateContentConfiguration(variant: String, baseUrl: String, keyId: String, publicKey: String) {
    val configured = listOf(baseUrl, keyId, publicKey).count(String::isNotBlank)
    require(configured == 0 || configured == 3) {
        "$variant app-content base URL, signing key ID, and public key must be configured together " +
            "(baseUrl=${baseUrl.isNotBlank()}, keyId=${keyId.isNotBlank()}, publicKey=${publicKey.isNotBlank()})"
    }
    if (configured == 3) {
        require(baseUrl.startsWith("https://") && baseUrl.endsWith("/app/v2/")) {
            "app-content base URL must be HTTPS and end with /app/v2/"
        }
    }
}

android {
    namespace = "com.atharchive"

    defaultConfig {
        applicationId = "com.atharchive"
        versionCode = 1
        versionName = "0.1.0-m0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ATHAR_CONTENT_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "ATHAR_CONTENT_SIGNING_KEY_ID", "".asBuildConfigString())
        buildConfigField("String", "ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64", "".asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            val baseUrl = contentBaseUrl.getOrElse(stagingContentBaseUrl)
            val keyId = contentSigningKeyId.getOrElse(stagingContentSigningKeyId)
            val publicKey = contentPublicKey.getOrElse(stagingContentPublicKey)
            validateContentConfiguration("debug", baseUrl, keyId, publicKey)
            buildConfigField("String", "ATHAR_CONTENT_BASE_URL", baseUrl.asBuildConfigString())
            buildConfigField("String", "ATHAR_CONTENT_SIGNING_KEY_ID", keyId.asBuildConfigString())
            buildConfigField("String", "ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64", publicKey.asBuildConfigString())
        }
        release {
            val baseUrl = contentBaseUrl.getOrElse("")
            val keyId = contentSigningKeyId.getOrElse("")
            val publicKey = contentPublicKey.getOrElse("")
            validateContentConfiguration("release", baseUrl, keyId, publicKey)
            buildConfigField("String", "ATHAR_CONTENT_BASE_URL", baseUrl.asBuildConfigString())
            buildConfigField("String", "ATHAR_CONTENT_SIGNING_KEY_ID", keyId.asBuildConfigString())
            buildConfigField("String", "ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64", publicKey.asBuildConfigString())
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
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.profileinstaller)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
