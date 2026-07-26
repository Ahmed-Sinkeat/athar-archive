// Pure-JVM module — no Android SDK needed to run its tests, only a JDK +
// gradle (see android/README.md). The app module will depend on it later.
// UNVERIFIED: written before the toolchain exists on this machine; if the
// kotlin plugin version fails to resolve, bump it to whatever is current.
plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
