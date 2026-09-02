// Deliberately a plain Kotlin/JVM module: the health engine is the part of the app
// that most needs fast, hermetic tests, and keeping it off the Android classpath is
// what makes `./gradlew :data:health:test` run in milliseconds on the JVM.
// It is also the KMP-ready seam described in the architecture doc.
plugins {
    id("worldtv.jvm.library")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.javax.inject)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}
