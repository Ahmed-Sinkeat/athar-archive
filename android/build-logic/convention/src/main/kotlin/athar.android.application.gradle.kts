import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("athar.android.base")
}

extensions.configure<ApplicationExtension> {
    defaultConfig.targetSdk = 36
}
