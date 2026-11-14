import java.net.URI
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

// The owner's upload key. Never stored in the repo; all four must be set to sign a release.
val releaseKeystore = providers.environmentVariable("ATHAR_ANDROID_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("ATHAR_ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ATHAR_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ATHAR_ANDROID_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

// Public trust material for the live generation, the debug default. The live root is signed
// only by this key; the old M5 staging key made every debug sync fail. The private key is not
// stored in the repository. Release/benchmark builds remain unconfigured unless CI supplies
// all three ATHAR_CONTENT_* values.
val stagingContentBaseUrl = "https://app-content.arthurarchive.com/app/v2/"
val stagingContentSigningKeyId = "athar-content-rsa-2026-10"
val stagingContentPublicKey = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEArqqKIRH9Plvum7YYqQSqckmeuJHdD+pzgXEyObZuvToNSu5ChHOahVGwnFcPPuAMruU5SwLSNP2BOnpNZyrpPWn8nfXINfkmLIS1QnBLs0KZRegkjCyL/Cg3MA0UeivoGGYJmrPyFQgEoaDROADO+QPA23Ql+Mo4NdswA+4w4y/hbdgP6CdvgjlRAZ0aM4Du8iPDlGKb13F+XZ/CAJVY47kNshVb379nPgYhQ8GFFIt3Ios9SDgMSpvPiSrwj0opZI6jiKu8Ie4g6iyy3ZPHtC++FmMpEV62ARAoNRMyO1OQodIFgrbUk1qnULFD1XnOb62Ew0ipgD4sTDJTWctRdx07biOhQ1i2j1D05UeJ5Zx2jwtSt0m5S620e3fiGjcU5tdbzMTv09DBS5QPuWrkUIlspiFcZOZWUDIocILfbxeoWdHVulKbkzrbd75JTTBfIcciyDpZHq3lr2QuwaPUoOCu/ZWeNdj4l0xUeCrtQKeNzqUWyQR59kdlqdAaILWFAgMBAAE="

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
        testApplicationId = "com.atharchive.instrumentation"
        versionCode = 11
        versionName = "1.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ATHAR_CONTENT_BASE_URL", "".asBuildConfigString())
        buildConfigField("String", "ATHAR_CONTENT_SIGNING_KEY_ID", "".asBuildConfigString())
        buildConfigField("String", "ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64", "".asBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    if (releaseSigningConfigured) {
        signingConfigs.create("production") {
            storeFile = file(requireNotNull(releaseKeystore))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
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
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("production")
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
        // A release build that installs beside the real app (old test phones): own id and name.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "أثر تجريبي")
        }
        // Benchmarks need a release-like build that is still installable locally; its own id
        // keeps it beside the reader's real app instead of replacing it (and its notes).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            applicationIdSuffix = ".bench"
            proguardFile("benchmark-rules.pro")
            resValue("string", "app_name", "أثر قياس")
        }
    }
}

dependencies {
    implementation(project(":core:athar-text"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    // Macrobenchmark needs this to drop the shader cache between cold starts;
    // without it StartupBenchmark cannot run at all.
    implementation(libs.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.haze)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.androidx.tracing)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.paging.compose)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    // The real org.json for JVM tests (android.jar ships stubs): UserAdhkarStore round-trip.
    testImplementation("org.json:json:20231013")
}

// A release with no origin is an app with no library; one with no keystore cannot be signed
// with the owner's key. Fail loudly instead of shipping either.
val verifyReleaseConfiguration by tasks.registering {
    val configuredContent = listOf(contentBaseUrl.orNull, contentSigningKeyId.orNull, contentPublicKey.orNull).all { !it.isNullOrBlank() }
    val signed = releaseSigningConfigured // local copy: doLast must not capture the script object (configuration cache)
    val baseUrl = contentBaseUrl.orNull.orEmpty()
    val bundledIndex = file("src/main/assets/app-content/index.json")
    val bundledCatalog = file("src/main/assets/app-content/catalog.db")
    doLast {
        // Without it a fresh install has no library until its first sync.
        // `pnpm app:bundle-catalog` writes it together with index.json, from the same generation.
        require(bundledCatalog.isFile) { "Missing app-content/catalog.db; run `pnpm app:bundle-catalog`." }
        // The bundled catalog must be the generation the origin serves: an unpublished one points
        // at packages that 404, and every book in the shipped app fails to open.
        // needs the network at release time; that is when the origin matters anyway.
        if (baseUrl.isNotBlank()) {
            val live = URI(baseUrl + "index.json").toURL().readBytes()
            require(bundledIndex.isFile && bundledIndex.readBytes().contentEquals(live)) {
                "Bundled app-content/index.json is not the published generation; run `pnpm app:bundle-catalog` (after `pnpm app:publish` if it is new)."
            }
        }
        require(configuredContent) {
            "Release requires ATHAR_CONTENT_BASE_URL, ATHAR_CONTENT_SIGNING_KEY_ID and ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64 (or their athar.* Gradle properties)."
        }
        require(signed) {
            "Release requires ATHAR_ANDROID_KEYSTORE, ATHAR_ANDROID_STORE_PASSWORD, ATHAR_ANDROID_KEY_ALIAS and ATHAR_ANDROID_KEY_PASSWORD."
        }
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease") }
    .configureEach { dependsOn(verifyReleaseConfiguration) }
