import com.android.build.api.dsl.CommonExtension

// Shared Android config for every Android module. minSdk 26 is fixed by
// docs/main-plan.md §0 — reach matters more than modern APIs for this audience.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<CommonExtension> {
    compileSdk = 36
    defaultConfig.minSdk = 26
    compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    compileOptions.targetCompatibility = JavaVersion.VERSION_11
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Reader work is coroutine-heavy; surface API misuse at compile time.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}
