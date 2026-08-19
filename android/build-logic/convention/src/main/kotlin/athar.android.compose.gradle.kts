import com.android.build.api.dsl.CommonExtension

// Applied on top of athar.android.application / athar.android.library by any
// module that has UI. Kept separate so :core:data can stay Compose-free.
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<CommonExtension> {
    buildFeatures.compose = true
}

dependencies {
    val bom = libs.findLibrary("compose-bom").get()
    add("implementation", platform(bom))
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())

    add("androidTestImplementation", platform(bom))
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
}
