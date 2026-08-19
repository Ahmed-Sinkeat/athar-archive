// Athar Android — multi-module build. Structure is fixed by docs/main-plan.md §3;
// modules are created only when a milestone needs them, not up front.
//
// Present now (pre-M0):
//   :core:athar-text  pure JVM — normalizer, normalizeWithMap, query builder
//   :app              production shell — Application, Activity, backup rules (R6)
//   :m0               throwaway harness for R1/R2/R3/R6/R7 — deleted after M0
//   :benchmark        macrobenchmarks; targets :m0 now, :app from M3
//
// Added later (do NOT create empty):
//   :core:data        M2   :core:ui         M3
//   :feature:library  M3   :feature:reader  M3
//   :feature:search   M5   :feature:audio   M7
pluginManagement {
    includeBuild("build-logic")
    resolutionStrategy {
        eachPlugin {
            when {
                requested.id.id.startsWith("com.android.") ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                requested.id.id == "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
                requested.id.id == "org.jetbrains.kotlin.plugin.serialization" ->
                    useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
                requested.id.id.startsWith("org.jetbrains.kotlin.") ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                requested.id.id == "com.google.devtools.ksp" ->
                    useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "athar-android"

include(":core:athar-text")
include(":app")
include(":m0")
include(":benchmark")
